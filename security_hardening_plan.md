# Security Hardening Plan — webapp-foundation

> **Scope.** Security review of the `webapp-foundation` base project — the fenced spine every
> generated client site inherits. Because fixes here propagate to *every* deployment, this is
> high-leverage work: one correct change hardens all sites, one wrong default weakens all of them.
>
> **Deployment architecture of record.** Each generated site is hosted as:
> - **Frontend** — static build served from **Amazon S3**.
> - **Backend** — Spring Boot on a single **EC2 `t4g.medium`** (one instance per site).
> - **Database** — self-managed **PostgreSQL on an EBS volume** attached to that EC2.
>
> This architecture is assumed throughout; several resolutions are AWS-native (CloudFront, ACM,
> IAM instance roles, KMS, WAF, Security Groups) rather than application code.

**Status legend:** 🔴 Critical (blocks real-money launch) · 🟠 High · 🟡 Medium · 🟢 Already handled well

**Resolution status:** ☐ Not started · ◐ In progress · ☑ Done

---

## 0. Executive summary

The foundation has a solid authorization core (stateless JWT, DB-reloaded authorities, BCrypt,
non-root container, tight STAFF/ADMIN allowlist) but ships several **insecure defaults** and
**missing payment-integrity checks** that are dangerous for a platform handling payments. Under the
S3 + EC2 + EBS architecture, transport encryption and secrets management become AWS-native concerns,
and **CORS becomes mandatory** because the frontend and backend now live on separate origins.

Nothing from the review could be fully dropped. The architecture changes *how* we solve transport
(#1) and secrets (#10), lets WAF absorb part of rate-limiting (#6), and simplifies token revocation
(#8, no distributed store needed) — but every finding still applies, and the split-origin topology
*adds* three new infrastructure findings (A, B, C).

---

## 1. 🔴 No TLS — all traffic travels in cleartext

**Issue.** The app serves plain HTTP on `8080`; `docker-compose.yml` publishes it directly
(`ports: "8080:8080"`). There is no HTTPS, HSTS, or HTTP→HTTPS redirect. Passwords, JWTs, OTP codes,
and Razorpay payloads all cross the wire unencrypted.

**Files.** `docker-compose.yml`, `backend/src/main/resources/application.properties`.

**Resolution (AWS-native for this architecture).**
- **Frontend (S3):** serve via **CloudFront** with an **ACM** certificate (see finding B). Do not
  use S3 static-website hosting (HTTP-only).
- **Backend (EC2):** terminate TLS at an **ALB with an ACM cert** (or CloudFront → EC2). Redirect
  HTTP→HTTPS at the LB; add **HSTS**.
- In Spring, set `server.forward-headers-strategy=framework` so the app honors `X-Forwarded-Proto`
  and builds correct absolute URLs (reset links, etc.) behind the LB.
- If S3 is ever used as an *external* media endpoint, use `https` (`s3.endpoint`).

**Status:** ☐

---

## 2. 🔴 JWT signing key has a committed public default

**Issue.** `application.properties` sets
`jwt.secret=${JWT_SECRET:ZGVtby1vbmx5...}` — a default key committed to the repo. If `JWT_SECRET`
is unset, the app boots with a signing key known to anyone with the foundation, allowing forged
`role: SUPER_ADMIN` tokens → full takeover.

**Files.** `backend/src/main/resources/application.properties:12`, `util/JwtUtil.java`.

**Resolution.**
- Remove the baked-in default.
- Fail fast at startup (`@PostConstruct`) if `JWT_SECRET` is unset or shorter than 32 bytes.
- Apply the same hard-fail to `RAZORPAY_KEY_ID/SECRET/WEBHOOK_SECRET` in production.
- Source the secret from **SSM Parameter Store / Secrets Manager** (see finding #10), not a
  plaintext `.env` on the instance.

**Status:** ☑ Implemented + test-proven on `feat/security-hardening` — demo default removed from base
properties (moved to the dev profile); `JwtUtil.validateSigningKey()` fails fast if the secret is
missing or < 32 bytes. Verified by `JwtUtilValidationTest` (T2, 3 cases).

---

## 3. 🔴 Payment amount is client-supplied; endpoint is unauthenticated

**Issue.** `/api/v1/payments/**` matches no rule in `SecurityConfig` and falls through to
`anyRequest().permitAll()`. `CreatePaymentRequest.amount` is taken straight from the request body
(`PaymentService.createOrder`), and `verify()` checks only the gateway *signature* — never that the
captured amount equals a server-computed order total. A client can open a ₹1 order for a ₹10,000
cart and the signature still verifies.

**Files.** `config/SecurityConfig.java:51`, `controller/PaymentController.java`,
`service/PaymentService.java:46-98`, `dto/CreatePaymentRequest.java`.

**Resolution.**
- Require authentication on `/api/v1/payments/**`; bind the `Payment` to the authenticated user.
- Derive the amount **server-side** from the order/cart; never trust the request body.
- Persist the expected amount + currency; in `verify()` and `handleWebhook()` assert
  `captured.amount == expected.amount && currency matches` before marking `CAPTURED`.
- **Contract sync:** adding auth / changing the payment surface requires updating
  `backend/FOUNDATION_CONTRACT.md` (`## Payments`) and `foundation.manifest.json` in the same commit.

**Status:** ◐ Implemented on `feat/security-hardening`. `SecurityConfig` now requires auth on
`/api/v1/payments/**` (webhook stays public). The authoritative guard is in the fenced order spine:
`OrderService.onPaymentCaptured` refuses to settle unless the captured amount equals the order's
server-computed `total`, auditing `ORDER_PAYMENT_MISMATCH` on mismatch — so a cut-price gateway order
opened against an order's `referenceId` can never mark it paid. The legit path (`placeOrder`) already
derives the amount server-side; `PaymentService` stays business-agnostic (no order dependency). The
`## Payments` contract card documents the enforced rules + the consumer obligation. **Test-proven:**
`SecurityHardeningTest` (T3) confirms anonymous payment endpoints are denied (401/403); the
amount-binding guard (`onPaymentCaptured`) is proven by `PaymentAmountIntegrityTest` (a ₹1 capture
against a ₹10000 order leaves it `AWAITING_PAYMENT`, audits `ORDER_PAYMENT_MISMATCH`).
**Done:** the standalone public `create-order` endpoint has been **retired** — payment orders are
opened server-side via `PaymentService.createOrder` (`OrderService.placeOrder`), removing the
client-chosen-amount surface entirely (frontend `createPaymentOrder` removed; contract card updated).

**Status:** ☐

---

## 4. 🔴 Webhook signature verification fails *open*

**Issue.** `RazorpayPaymentGateway.verifyWebhook()` returns `true` when `webhookSecret` is blank
(`:69-71`), and `/api/v1/payments/webhook` is public. With `RAZORPAY_WEBHOOK_SECRET` unset in prod,
anyone can POST a forged `payment.captured` and mark arbitrary orders paid. The generic
`ConnectorWebhookController` similarly trusts each connector to self-verify.

**Files.** `gateway/RazorpayPaymentGateway.java:68-77`, `controller/PaymentController.java:35-41`,
`controller/ConnectorWebhookController.java`.

**Resolution.**
- Fail **closed**: if no webhook secret is configured, reject the webhook (or refuse to start in
  prod). Never treat "no secret" as "trusted".
- Verify the signature over the **raw** request body before parsing.

**Status:** ◐ Implemented on `feat/security-hardening` — `RazorpayPaymentGateway.verifyWebhook()` now
returns `allowUnsigned` (default **false**) instead of `true` when no secret is set; the fenced
`PaymentGateway` javadoc now mandates fail-closed. Dev opts in via `razorpay.webhook.allow-unsigned`.
**Test-proven:** `RazorpayWebhookFailClosedTest` (T4) green (3 cases).

---

## 5. 🔴 Default admin credentials seed a verified SUPER_ADMIN

**Issue.** `AdminInitializer` seeds `owner@yourbusiness.com` / `changeme123` as `SUPER_ADMIN`,
`emailVerified=true`. Any deployment that doesn't override `ADMIN_PASSWORD` ships with a known admin
login.

**Files.** `config/AdminInitializer.java`, `application.properties:19-21`.

**Resolution.**
- Refuse to seed a known-default password in production (fail fast, or require a forced password
  change on first login).
- Never log the seeded credentials.

**Status:** ☑ Implemented + test-proven on `feat/security-hardening` — `AdminInitializer` throws at
startup if the admin password is blank or the known default (`changeme123`) unless
`admin.allow-insecure-default=true` (dev profile only). Verified by `AdminInitializerSeedGuardTest`
(T5, 3 cases).

---

## 6. 🟠 No rate limiting or account lockout on auth endpoints

**Issue.** `/login`, `/forgot-password`, `/verify-2fa`, `/verify-signup` have no throttling →
credential stuffing, OTP/email bombing, reset-link spraying. (OTP *guesses* are capped at 5 via
`OTP_MAX_ATTEMPTS` — good — but the surrounding endpoints are not.)

**Files.** `controller/AuthController.java`, `service/OtpService.java`.

**Resolution.**
- Front the stack with **AWS WAF** (on CloudFront/ALB) for volumetric and credential-stuffing
  protection (rate-based rules).
- Keep an **in-app per-account lockout** with exponential backoff on repeated login failures.
- Single EC2 per site → an in-memory limiter (e.g. bucket4j local) is sufficient; no distributed
  store required.

**Status:** ☑ Implemented + test-proven on `feat/security-hardening` — `LoginRateLimiter` (in-memory,
per-account) locks out after `app.security.login.max-attempts` (default 5) failures for
`app.security.login.lockout-minutes` (15), wired into `AuthController.login`; a bad-credentials login
now returns 401 (was an unmapped 500). Verified by `AuthHardeningTest.login_locksOutAfterRepeatedFailures`
(5×401 then 429). WAF/IP-level throttling remains an infra add (step 1).

---

## 7. 🟠 Challenge & reset tokens live 24h and are replayable

**Issue.** `challengeToken()` reuses `jwt.expiration-ms` (24h), so "short-lived" 2FA / signup /
reset challenges are valid for a full day and are not invalidated after use. A reset link leaked via
email forwarding, proxy logs, or `Referer` is replayable for 24h.

**Files.** `controller/AuthController.java:233-250`, `util/JwtUtil.java`.

**Resolution.**
- Give reset/2FA/verify challenges a short dedicated TTL (5–15 min).
- Make them **single-use**: store a `jti`/nonce, or check a `passwordChangedAt` timestamp so a used
  reset token stops validating.

**Status:** ☑ Implemented + test-proven on `feat/security-hardening` — challenge/reset tokens now use a
short dedicated TTL (`jwt.challenge-expiration-ms`, 15 min default) via `JwtUtil.generateChallengeToken`;
reset links are **single-use** (they carry the user's `tokenVersion`, bumped on reset — see #8).
Verified by `AuthHardeningTest.resetLink_isSingleUse` (second use → 401).

---

## 8. 🟠 No token revocation / real logout

**Issue.** `JwtUtil.validateToken` checks only subject + expiry. Changing a password, disabling 2FA,
deleting, or demoting a user does **not** invalidate already-issued JWTs — a stolen 24h token stays
valid until expiry.

**Files.** `util/JwtUtil.java:58-61`, `security/JwtAuthFilter.java`.

**Resolution.**
- Add a `tokenVersion` / `passwordChangedAt` claim, checked on every request; bump it on password
  change, role change, deactivation, and "log out everywhere".
- Single EC2 → the version can live in the DB / in-memory cache; no Redis needed.

**Status:** ☑ Implemented + test-proven on `feat/security-hardening` — `User.tokenVersion` is embedded
in every access token (claim `tv`) and checked in `JwtUtil.validateToken` (so `JwtAuthFilter` enforces
it); `AuthController` bumps it on password change and reset, and `UserAdminService.changeRole` bumps it
on a role change (a demotion revokes existing sessions), revoking all existing tokens. Verified by
`AuthHardeningTest.accessToken_isRejectedAfterTokenVersionBump` + `roleChange_bumpsTokenVersion`.
(Delete removes the user outright, so no bump is needed; there is no separate "deactivated" state.)

---

## 9. 🟡 Weak password policy

**Issue.** Minimum is 8 chars with one letter + one digit; no breached/common-password check — thin
for a payments platform.

**Files.** `security/PasswordPolicy.java`.

**Resolution.** Raise the floor and add a denylist of common passwords (or a breach check).

**Status:** ☑ Implemented + test-proven on `feat/security-hardening` — `PasswordPolicy` now requires
≥ 10 chars incl. a letter and a digit, and rejects a built-in common-password denylist. Verified by
`SecurityHardeningTest.register_rejectsWeakAndCommonPasswords` (T9).

---

## 10. 🟡 Weak default secrets → move to AWS-native secret management

**Issue.** `S3_ACCESS_KEY/SECRET=minioadmin`, `DB_PASSWORD=changeme`. Fine for local dev, dangerous
if they survive into a real deployment. All secrets sit in a plaintext `.env` on the instance.

**Files.** `application.properties`, `.env.example`, `config/S3Config.java`.

**Resolution (refined for this architecture).**
- **S3 access:** drop static S3 keys entirely in prod. Attach an **IAM instance role** to the EC2;
  the AWS SDK picks up instance-role credentials automatically. (MinIO + `minioadmin` is now
  **dev-only** and no longer a production concern.)
- **DB:** Postgres on local EBS is not internet-reachable, which lowers exposure severity — but it's
  still the crown jewels. Enable **EBS encryption at rest (KMS)** and set a strong, unique password.
- **All secrets** (DB, JWT, Razorpay): load from **SSM Parameter Store / Secrets Manager** at boot
  rather than a plaintext `.env`.

**Status:** ☐

---

## 11. 🟡 `ddl-auto=update` in production

**Issue.** `spring.jpa.hibernate.ddl-auto=update` lets the app mutate the live schema on deploy →
drift and accidental data-loss risk, worse on a single DB-on-same-host instance.

**Files.** `application.properties`.

**Resolution.** Adopt **Flyway/Liquibase** migrations for anything production-bound; set
`ddl-auto=validate` in prod.

**Status:** ☐

---

## Architecture-introduced findings (S3 + EC2 split origins)

### A. 🔴 CORS is now mandatory and must be a strict allowlist

**Issue.** No CORS configuration exists in the backend (confirmed — zero matches). When the SPA was
served same-origin by Spring (`SpaController`), there was no CORS surface. Now the frontend lives on
an **S3/CloudFront origin** and the API on a **separate EC2/ALB origin**, so every browser API call
is cross-origin and will be blocked until CORS is configured.

**Resolution.**
- Configure an **explicit origin allowlist** (the site's CloudFront domain only), driven by an env
  var, with `Authorization` allowed.
- **Never** use `Access-Control-Allow-Origin: *`, especially with a bearer token in the header.
- **Contract sync:** CORS env belongs in `foundation.manifest.json` (`config.env`).

**Status:** ◐ Implemented on `feat/security-hardening` — `SecurityConfig` adds a strict
`CorsConfigurationSource` from `app.cors.allowed-origins` (comma-split, credentials on, no `*`),
wired via `http.cors(...)`; `APP_CORS_ALLOWED_ORIGINS` added to the auth `config.env` in the manifest
and to `.env.example`. **Test-proven:** `SecurityHardeningTest` (TA) green — disallowed origin gets no
ACAO header; allowed origin is echoed exactly (never `*`).

### B. 🟠 S3 frontend bucket must be private + CloudFront OAC

**Issue.** S3 static-website hosting is HTTP-only and requires a public bucket.

**Resolution.** Use a **private bucket with Block Public Access ON**, fronted by **CloudFront with
Origin Access Control (OAC)** and an **ACM** cert. This also delivers HTTPS for the frontend
(covers #1 frontend half).

**Status:** ☐

### C. 🟠 Instance exposure — Security Groups, SSH, IMDSv2

**Issue.** One EC2 runs both app and DB; defaults can over-expose it.

**Resolution.**
- Backend `8080` accepts traffic **only from the ALB/CloudFront security group**, never `0.0.0.0/0`.
- Postgres `5432` bound to localhost / closed in the SG.
- No public SSH (`22`); use a bastion or **SSM Session Manager**.
- Enforce **IMDSv2-only** so an app-side SSRF can't steal the instance role's credentials via the
  metadata endpoint (directly protects the IAM role from #10).

**Status:** ☐

---

## What's already handled well 🟢

- Non-root container user (`Dockerfile`).
- BCrypt password hashing (`PasswordEncoderConfig`).
- Stateless JWT with CSRF correctly disabled (token-in-header, not cookie → no CSRF surface).
- Authorities re-loaded from the DB on every request, not trusted from the JWT `role` claim.
- `forgot-password` does not reveal account existence.
- OTP guess-attempt capping (`OTP_MAX_ATTEMPTS`).
- Audit logging of auth events (`AuditService`), with the no-secrets-in-logs rule.
- MinIO not published to the internet; `.env` gitignored and untracked.
- Tight STAFF-vs-ADMIN authorization allowlist in `SecurityConfig`.

---

## Portability — build once, migrate freely

**Design goal.** After the hardening pass, changing environments or swapping infrastructure
(EBS-Postgres → RDS, MinIO → real S3, `.env` → Secrets Manager, single-EC2 → autoscaled) must be a
**config / Terraform diff with ZERO code change**. The governing principle is 12-factor: every
environment-specific value lives behind a stable env var or the AWS default credential chain.

**Acceptance criterion.** A migration is a config diff, not a code diff. If swapping infra requires
editing Java/TS, the seam is in the wrong place — fix the seam, not the deployment.

### Already migration-friendly ✓

- **Database URL fully externalized** — `spring.datasource.url=${DB_URL}`. The only hardcoded value
  is the dev default in `.env.example` (`jdbc:postgresql://db:5432/...`, the compose service name).
  At the app layer, **EBS Postgres → RDS is already just changing `DB_URL`**.
- **S3 endpoint / region / path-style externalized** (`S3Config`) — MinIO → real S3 is config, except
  the credentials seam (P1 below).
- **Audit log lives in the DB, not the local filesystem** — travels with the database automatically.

### One-time seam fixes that lock in churn-free migration

**P1. 🟠 S3 credential provider chain (enabler for IAM roles).**
`config/S3Config.java:33` always uses `StaticCredentialsProvider`. Blanking the keys to use an EC2
**IAM instance role** currently breaks the client. Fix once: when keys are blank, fall back to
`DefaultCredentialsProvider` (auto-resolves instance role → env → profile). Afterwards MinIO
(static keys) → S3 (IAM role) is config-only, permanently.
**Status:** ☐

**P2. 🟠 Spring profiles — `dev` (permissive) vs `prod` (strict, fail-fast).**
Today a single `application.properties` carries dev-safe defaults. Split once: `dev` keeps
MinIO/mailpit/relaxed secrets; `prod` has **no defaults and refuses to boot** if `JWT_SECRET`, DB,
Razorpay, or CORS origins are unset. This is what prevents constant re-tweaking — misconfiguration is
caught at startup, not re-patched after an incident. Hardening findings #2, #4, #5 become
*prod-profile config*, not per-environment code edits.
**Status:** ◐ Implemented on `feat/security-hardening` — base `application.properties` activates
`${SPRING_PROFILES_ACTIVE:dev}`; `application-dev.properties` holds convenience defaults and
`application-prod.properties` is strict/empty. Compile-verified; runtime verification pending.

**P3. 🟡 Flyway migrations (replaces `ddl-auto=update`).**
No Flyway/Liquibase on the classpath today; the app mutates its own schema (fragile on RDS). Add
Flyway once so the schema is **versioned and applies identically to EBS-Postgres or RDS** — migration
becomes `pg_dump | restore` (or DMS) + repoint `DB_URL`, with Flyway keeping environments in lockstep.
(Shares the fix for finding #11.)
**Status:** ☐

**P4. 🟡 Decouple the bundled `db` container from the app deploy.**
Move the `db` service in `docker-compose.yml` to a **dev-only compose profile/override**, so "switch
to RDS" means *don't run the db container and set `DB_URL`* — the app already doesn't care.
**Status:** ☐

### What EBS → RDS costs after these fixes

A pure config diff — no rebuild, no code review:

```
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/app?sslmode=require   # was ...@db:5432...
DB_USERNAME / DB_PASSWORD   -> sourced from Secrets Manager
# stop deploying the bundled `db` container
```

Same shape for MinIO → S3 (blank `S3_ENDPOINT`, `S3_PATH_STYLE=false`, drop static keys → IAM role)
and for secrets (`.env` → SSM / Secrets Manager injected as env vars — the app only ever sees env
vars, so *where* they come from is purely an infra decision).

> **Contract sync.** P1–P4 change config env / fenced config surface; update
> `foundation.manifest.json` (`config.env`, `config.compose`) and, where a signature changes, the
> relevant `FOUNDATION_CONTRACT.md` in the same commit.

---

## Recommended sequencing

1. **Infra layer (no app code)** — CloudFront+OAC+ACM for S3 frontend (B); ALB+ACM + HTTPS redirect +
   HSTS for EC2 (#1); WAF (#6); EBS encryption + Secrets Manager + IAM instance role (#10); tight
   Security Groups, SSM, IMDSv2 (C).
2. **Fail-fast config pass (low-risk code)** — remove JWT/admin/secret defaults (#2, #5), fail-closed
   webhook (#4), CORS allowlist from env (A).
3. **Payment integrity (focused code)** — server-side amount binding + amount check, require auth on
   `/payments/**` (#3).
4. **Auth hardening (code)** — rate limit/lockout (#6), short single-use challenge tokens (#7),
   token-version revocation (#8), stronger password policy (#9), Flyway migrations (#11).

> **Contract-card rule.** Any change touching a fenced module's public surface (payments/auth/config
> env) MUST update `backend/FOUNDATION_CONTRACT.md` / `frontend/FOUNDATION_CONTRACT.md` and
> `foundation.manifest.json` in the **same commit** (see `CLAUDE.md`). A fenced change with a stale
> card ships broken ground truth to every generated project.

---

## Security test matrix

One test per finding. Each row is a repeatable check: run it **before** the fix to confirm the
vulnerability (should FAIL / be exploitable), and **after** to confirm the resolution (should PASS /
be blocked). `base` = the backend base URL (prod: HTTPS behind the LB).

**Result legend:** ☐ Not run · ✗ Vulnerable (pre-fix expected) · ✓ Secure (post-fix expected)

| ID | Finding | How to test | Expected after fix (PASS) | Result |
|----|---------|-------------|----------------------------|--------|
| T1 | #1 TLS | `curl -I http://<host>/` and hit the API over `http://`. Run SSL Labs / `testssl.sh` against the LB. | HTTP 301→HTTPS; HSTS header present; no plaintext endpoint reachable; TLS 1.2+ only. | ☐ |
| T2 | #2 JWT default | Unit-test `JwtUtil.validateSigningKey()` with a blank, short, and 32+ byte secret. | Blank/short secret throws (app refuses to start); 32+ byte secret accepted. | ✓ `JwtUtilValidationTest` (3 cases) |
| T3 | #3 Payment amount | Call `/api/v1/payments/verify` anonymously; fire a capture whose amount ≠ the order total. | Payment endpoints require auth (401/403 anon); no public create-order; a mismatched capture leaves the order unpaid. | ✓ auth (`SecurityHardeningTest.paymentVerify_isRejectedForAnonymousCaller`) + amount binding (`PaymentAmountIntegrityTest`) |
| T4 | #4 Webhook fail-open | Unit-test `RazorpayPaymentGateway.verifyWebhook`: no secret + allowUnsigned=false; no secret + allowUnsigned=true; configured secret + forged signature. | No secret → rejected (fail closed) unless unsigned explicitly allowed (dev); forged signature → rejected. | ✓ `RazorpayWebhookFailClosedTest` (3 cases) |
| T5 | #5 Default admin | Unit-test `AdminInitializer.run()` with a blank/known-default password and `allow-insecure-default` false vs true. | Blank/default password throws unless the dev escape hatch is set. | ✓ `AdminInitializerSeedGuardTest` (3 cases) |
| T6 | #6 Rate limiting | Repeated `/api/v1/auth/login` with bad passwords for one account. | Lockout after N failures (per account); locked attempt → 429. WAF/IP-level is an infra add. | ✓ `AuthHardeningTest.login_locksOutAfterRepeatedFailures` (5×401 then 429) |
| T7 | #7 Reset-token replay | Build a reset link, use it twice. | Short TTL (15 min); second use rejected (single-use via tokenVersion). | ✓ `AuthHardeningTest.resetLink_isSingleUse` |
| T8 | #8 Token revocation | Issue a JWT, bump the user's tokenVersion (password change), reuse the old JWT. | Old JWT rejected (401/403) after the bump. | ✓ `AuthHardeningTest.accessToken_isRejectedAfterTokenVersionBump` |
| T9 | #9 Password policy | Register with `password1` (short + common). | Weak/common passwords rejected (400); ≥10 chars + letter + digit + denylist. | ✓ `SecurityHardeningTest.register_rejectsWeakAndCommonPasswords` |
| T10 | #10 Secrets | On the instance: `ls -l .env` (mode), `docker inspect` the container, try reading secrets as a non-root local user. Confirm app reads from SSM/Secrets Manager. | `.env` is `600`/owned by service user (or absent); secrets sourced from Secrets Manager; EBS encrypted at rest. | ☐ |
| T11 | #11 Schema mgmt | Inspect startup logs for Hibernate DDL; check `flyway_schema_history` exists. | `ddl-auto=validate` in prod; schema applied via versioned Flyway migrations, no auto-mutation. | ☐ |
| TA | A CORS | From a disallowed origin (browser or `curl -H "Origin: https://evil.example"`) call the API; inspect `Access-Control-Allow-Origin`. | Only the configured origin is echoed; no `*`; disallowed origins get no ACAO header. | ✓ `SecurityHardeningTest.cors_disallowedOrigin_isNotEchoed` + `cors_allowedOrigin_isEchoedAndNeverWildcard` |
| TB | B S3 bucket | `curl` the S3 bucket REST/website URL directly; check Block Public Access + bucket policy. | Direct bucket access denied; content served only via CloudFront (OAC) over HTTPS. | ☐ |
| TC | C Instance exposure | External port scan of the EC2 (`nmap`); attempt `:8080`, `:5432`, `:22` from the internet; query IMDS from inside the app host. | Only LB→8080 reachable; 5432/22 closed to the internet; IMDSv2-only (IMDSv1 token-less call fails). | ☐ |
| TP1 | P1 S3 creds | Blank `S3_ACCESS_KEY/SECRET`, attach an IAM instance role, exercise media upload/download. | S3 works via the instance role (DefaultCredentialsProvider) with no static keys and no code change. | ☐ |
| TP2 | P2 Profiles | Boot `prod` profile with required env missing, then fully set. | Fails fast when any required secret/origin is missing; boots clean when all are set. | ☐ |
| TP3 | P3 Flyway / RDS swap | Point `DB_URL` at a fresh RDS instance; boot the app. | Flyway migrates the RDS schema cleanly; app runs unchanged — config-only migration. | ☐ |
| T-reg | Regression | Run `/security-review` on the branch diff and the existing backend/frontend test suites. | No new findings; all gates green. | ☐ |

> **Automate where cheap.** T3, T4, T7, T8, T9, TA belong in the backend integration test suite
> (MockMvc / Testcontainers) so they re-run on every build; T1, TB, TC, T10 are infra checks suited
> to a deployment smoke test; T6 and the RDS swap (TP3) are best run against a staging environment.

### Automated suite

Six test classes under `backend/src/test/java/com/webappfoundation/`:

- **`security/SecurityHardeningTest`** — `@SpringBootTest` + `@ActiveProfiles("test")` MockMvc: T3
  (payment auth), TA (CORS allow/deny, no wildcard), T9 (weak/common password rejected).
- **`security/AuthHardeningTest`** — T6 (login lockout → 429), T7 (reset link single-use), T8 (access
  token revoked after a `tokenVersion` bump).
- **`security/PaymentAmountIntegrityTest`** — T3 amount binding (a sub-total capture leaves the order
  `AWAITING_PAYMENT`, audits `ORDER_PAYMENT_MISMATCH`).
- **`security/RazorpayWebhookFailClosedTest`** — T4 webhook fail-closed (unit; the `test` profile uses
  the demo gateway).
- **`util/JwtUtilValidationTest`** — T2 fail-fast signing-key check.
- **`config/AdminInitializerSeedGuardTest`** — T5 default-admin seed guard.

**Test profile / datastore.** `src/test/resources/application-test.properties` runs the full context
on **in-memory H2 (PostgreSQL mode)** — no Postgres/Docker — and supplies test-only secrets so the
fail-fast startup checks pass. `com.h2database:h2` is a test-scope dependency.

**Verified:** `mvn test` is green — **18 tests, 0 failures, 0 skipped** (no `@Disabled` remain). This
project is on Spring Boot **4.1.0** (Spring Framework 7): `@AutoConfigureMockMvc` lives at
`org.springframework.boot.webmvc.test.autoconfigure` (moved from `…test.autoconfigure.web.servlet`);
the classic `MockMvcRequestBuilders`/`MockMvcResultMatchers` API is unchanged.

> **Note — no prior regression net.** There was no `src/test` tree at all, so no automated test ran
> before this — itself a hardening gap. (The pom's `spring-boot-starter-webmvc-test` etc. are *valid*
> Boot 4 per-module test starters, not typos — verified during `test-compile`; likewise
> `spring-boot-starter-webmvc` is Boot 4's rename of `spring-boot-starter-web`.) The real
> `spring-boot-starter-test` was added for JUnit 5 / Hamcrest / Mockito.

---

*Prepared as a security review of `webapp-foundation`. Findings reference files at the time of
review; verify line numbers before acting on them.*
