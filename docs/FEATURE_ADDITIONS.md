# Foundation Feature Additions — Implementation Spec

**Status:** Planned (spec only — no code written yet)
**Author:** generated for yashdahat, 2026-09-27
**Target repo:** `webapp-foundation` (this repo)
**Reference implementation:** `prakash-stores` @ `feature/clothing-store-ecommerce-31c78b9a`
(local clone: `/Users/amardahat/Projects/prakash-stores`)

---

## Context

The foundation template (Spring Boot 4.1.0 / Java 17 backend + React 19 / Vite / shadcn frontend)
is cloned and extended by the `business-discovery` code-generation pipeline for every generated
client site. We want seven capabilities to become part of the foundation itself so every generated
project inherits them:

1. Email — full SMTP support (transactional email)
2. Admin image-upload React component (multiple images per entity)
3. Customer support tickets (threaded, with email notification)
4. CSV/spreadsheet bulk upload on admin pages (**compulsory** on every admin data table)
5. ag-grid for all admin tables (**admin-only**)
6. Global toasters with a **rule**: every API call surfaces a toast
7. Stronger auth — **email-OTP two-factor authentication**

Most of these already exist in `prakash-stores` and can be ported almost verbatim (2FA is the only
one that must be built fresh — prakash-stores only has the `input-otp` UI component). The foundation
already ships several enabling pieces: `spring-boot-starter-mail` (wired but unused), MinIO storage +
`MediaAsset` media library, `sonner` (installed but **not mounted**), and JWT auth.

### Non-negotiable process rule

> **Never touch the `business-discovery` critical pipeline for feature onboarding.** Features are
> added to the foundation *only* by editing this repo: ship the code, then declare it in
> `foundation.manifest.json` and the matching `FOUNDATION_CONTRACT.md` card **in the same commit**.

Every change below must, in the same commit:
1. Ship backend + frontend code.
2. Add/update the `##` section in `backend/FOUNDATION_CONTRACT.md` or `frontend/FOUNDATION_CONTRACT.md`
   with exact signatures (a build-time `FoundationCardIntegrity` drift check warns otherwise).
3. Add/update the feature entry in `foundation.manifest.json` (`id`, `core`, `requires`, `backend`,
   `frontend`, `config`).

---

## Design decisions (confirmed)

| Topic | Decision |
|---|---|
| 2FA mechanism | **Email OTP** — 6-digit code emailed on login via the new `EmailService`. Per-user opt-in toggle. |
| Support model | **Full threaded conversation** — `SupportCase` + `SupportMessage`; customer and admin exchange multiple messages; email on each admin reply. |
| Toaster rule | **Global axios interceptor** (auto `toast.error` on any failed request) **+ mutation helper** for success toasts; rule documented in the contract so generated code inherits it. |
| CSV upload | **Compulsory** — every admin entity table ships a bulk-upload dialog + backend `bulk-upload` endpoint. Documented as a foundation rule. |
| ag-grid | **Admin-only** — all admin tables use the shared `AdminDataGrid`; public pages keep shadcn/native. |

---

## Foundation manifest entries (summary)

New/updated entries in `foundation.manifest.json`. Existing `auth` and `gallery` entries get their
`fenced`/`env`/`compose` lists extended; the rest are new feature blocks.

| Feature id | core | requires | new compose svc | new env vars |
|---|---|---|---|---|
| `email` | true | — | `mailpit` (local dev only) | `SMTP_*`, `MAIL_FROM` |
| `auth` (extend) | true | email | — | `OTP_TTL_MINUTES` |
| `gallery` (extend) | false | auth | — | — |
| `support` | false | auth, email | — | — |
| `bulk-upload` | false | auth | — | — |
| `admin-grid` | false | auth | — | — |
| `toasts` | true | — | — | — |

> Note: `auth` gains a `requires: ["email"]` edge because email-OTP login depends on `EmailService`.
> Keep `email` `core: true` so the dependency is always satisfiable.

---

## Feature 1 — Email / SMTP

**Goal:** A reusable, non-blocking email service + an event-driven notification layer.

### Backend
- **New** `service/EmailService.java` — `boolean sendHtml(String to, String subject, String html)`.
  Uses `JavaMailSender` + `MimeMessageHelper`. **Never throws** — logs failures and returns false so
  business flows never break on SMTP outage. Reads `mail.from`.
  - Port from `prakash-stores/.../service/EmailService.java`.
- **New** `notification/EmailNotificationListener.java` — `@Async` +
  `@TransactionalEventListener(phase = AFTER_COMMIT)` handlers that build inline HTML and call
  `EmailService`. Foundation ships listeners only for foundation events (e.g. support replies);
  generated projects add their own.
  - Port from `prakash-stores/.../notification/EmailNotificationListener.java`.
- **Enable async:** add `@EnableAsync` (new `config/AsyncConfig.java` or on the main application class).
- **Config** (`application.properties`) — extend existing mail block:
  ```properties
  spring.mail.host=${SMTP_HOST:localhost}
  spring.mail.port=${SMTP_PORT:587}
  spring.mail.username=${SMTP_USERNAME:}
  spring.mail.password=${SMTP_PASSWORD:}
  spring.mail.properties.mail.smtp.auth=${SMTP_AUTH:false}
  spring.mail.properties.mail.smtp.starttls.enable=${SMTP_STARTTLS:false}
  mail.from=${MAIL_FROM:No Reply <no-reply@example.local>}
  management.health.mail.enabled=false
  ```
- **Dependency:** already present (`spring-boot-starter-mail`) — no pom change.

### Local dev SMTP (docker-compose)
Add a **mailpit** service so local runs can send/inspect email without a real relay:
```yaml
mailpit:
  image: axllent/mailpit
  ports: ["8025:8025"]     # web UI
  restart: unless-stopped
```
Point local `.env` at it: `SMTP_HOST=mailpit`, `SMTP_PORT=1025`, `SMTP_AUTH=false`, `SMTP_STARTTLS=false`.

### Env / .env.example additions
`SMTP_AUTH`, `SMTP_STARTTLS`, `MAIL_FROM` (the `SMTP_HOST/PORT/USERNAME/PASSWORD` already exist).

### Contract card (`backend/FOUNDATION_CONTRACT.md`) — new `## Email` section
```
class EmailService { boolean sendHtml(String to, String subject, String html); }   // never throws
// Domain notifications: publish a Spring event, add an @Async @TransactionalEventListener(AFTER_COMMIT)
// in notification/ that calls emailService.sendHtml(...). Config ready; inject EmailService anywhere.
```

### Manifest
```json
{ "id": "email", "core": true, "requires": [],
  "backend": { "packages": ["notification"], "fenced": ["EmailService", "EmailNotificationListener", "AsyncConfig"] },
  "frontend": {},
  "config": { "env": ["SMTP_HOST","SMTP_PORT","SMTP_USERNAME","SMTP_PASSWORD","SMTP_AUTH","SMTP_STARTTLS","MAIL_FROM"], "compose": ["mailpit"] } }
```

---

## Feature 2 — Admin multi-image upload component

**Goal:** An admin-only React component to attach one or many images to any entity (product, event,
brand, …), backed by the existing media library.

### Backend
The media library already exists (`MediaAsset`, `MediaAssetService`, `AdminMediaController`,
`MinioStorageService`, `GET /api/v1/media/{key}`, `/api/v1/admin/media`). **No new storage code needed.**

- **Entity convention for multiple images:** entities store an ordered list of media URLs via
  ```java
  @ElementCollection
  @CollectionTable(name = "<entity>_images", joinColumns = @JoinColumn(name = "<entity>_id"))
  @OrderColumn(name = "image_order")
  @Column(name = "image_url")
  private List<String> images = new ArrayList<>();
  ```
  plus an optional `String imageUrl` primary/thumbnail field. This is a **documented pattern** in the
  contract, not foundation-shipped code (entities are per-project).
  - Reference: `prakash-stores/.../model/Product.java` (`images` + `product_images` table).

### Frontend
- **New** `components/admin/MultiImagePicker.tsx` — props `{ value: string[]; onChange: (next: string[]) => void }`.
  Upload new files (`uploadMedia({ file })`), pick from existing library (`useMedia()`), reorder, remove.
  Toasts on upload success/failure. **Admin-only** (used inside `/admin/**` pages).
- **New** `components/admin/ImagePicker.tsx` — single-URL variant (`{ value: string|null; onChange }`).
  - Port both from `prakash-stores/.../components/admin/{MultiImagePicker,ImagePicker}.tsx`.
- Reuses existing `services/mediaService.ts` + `hooks/useMedia.ts` (already in foundation). No new service.

### Contract card (`frontend/FOUNDATION_CONTRACT.md`) — extend `## Media library` section
```
import MultiImagePicker from '@/components/admin/MultiImagePicker';  // admin only
  <MultiImagePicker value={string[]} onChange={(next) => ...} />     // upload/pick/reorder/remove
import ImagePicker from '@/components/admin/ImagePicker';            // admin only, single URL
// Entity images: store List<String> images (@ElementCollection, <entity>_images) + optional imageUrl.
```

### Manifest — extend `gallery` feature
Add to `gallery.frontend.fenced`: `MultiImagePicker`, `ImagePicker`.

---

## Feature 3 — Customer support (threaded tickets + email)

**Goal:** A customer opens a ticket; customer and admin exchange messages in a thread; the customer
gets an email whenever staff replies.

### Backend (upgraded from prakash-stores' single-response model to a real thread)
- **New** `model/SupportCase.java`: `Long id; Integer userId; String subject; SupportStatus status;
  Instant createdAt; Instant updatedAt;` (+ convenience `customerName/customerEmail` resolved into DTO).
- **New** `model/SupportMessage.java`: `Long id; Long caseId; SupportAuthor author; String body;
  Instant createdAt;` — one row per message.
- **New** `model/SupportStatus.java` enum: `OPEN, IN_PROGRESS, RESOLVED, CLOSED`.
- **New** `model/SupportAuthor.java` enum: `CUSTOMER, STAFF`.
- **New** repos: `SupportCaseRepository`, `SupportMessageRepository`
  (`findByCaseIdOrderByCreatedAtAsc`, `findByUserIdOrderByUpdatedAtDesc`, `findAllByOrderByUpdatedAtDesc`).
- **New** `service/SupportService.java`:
  - `SupportCaseDto open(Integer userId, CreateSupportCaseRequest req)` — creates case + first CUSTOMER message.
  - `SupportCaseDto reply(Integer userId, Long caseId, PostMessageRequest req)` — customer adds a message (ownership-checked).
  - `List<SupportCaseDto> myCases(Integer userId)` / `SupportThreadDto thread(Integer userId, Long caseId)`.
  - Admin: `List<SupportCaseDto> all()`, `SupportThreadDto adminThread(Long caseId)`,
    `SupportThreadDto adminReply(Long caseId, AdminReplyRequest req)` — adds STAFF message, may change status,
    **publishes `SupportRepliedEvent`** → `EmailNotificationListener` emails the customer.
- **New** `event/SupportRepliedEvent.java`.
- **New** DTOs: `SupportCaseDto`, `SupportThreadDto` (case + `List<SupportMessageDto>`),
  `SupportMessageDto`, `CreateSupportCaseRequest{subject,message}`, `PostMessageRequest{body}`,
  `AdminReplyRequest{body,status?}`.
- **New** controllers:
  - `controller/SupportController.java` (customer, `@CurrentUser Integer userId`):
    `POST /api/v1/support`, `GET /api/v1/support`, `GET /api/v1/support/{id}`, `POST /api/v1/support/{id}/messages`.
  - `controller/admin/AdminSupportController.java` (ADMIN):
    `GET /api/v1/admin/support`, `GET /api/v1/admin/support/{id}`, `POST /api/v1/admin/support/{id}/reply`.
  - Reference (single-response version to adapt): `prakash-stores/.../{SupportController,admin/AdminSupportController,service/SupportCaseService}.java`.

### Frontend
- **New** `services/supportService.ts` + `hooks/supportHooks.ts` (React Query).
- **New** `types/support.ts` — `SupportCaseDto`, `SupportThreadDto`, `SupportMessageDto`, enums.
- **New customer page** `pages/SupportPage.tsx` (route `/support`, `RequireAuth`): open ticket + list own
  cases + open a thread view showing all messages, reply box.
- **New admin page** `pages/admin/AdminSupportPage.tsx` (route `/admin/support`, `RequireAdmin`):
  `AdminDataGrid` of all cases → detail panel `components/admin/support/SupportThreadPanel.tsx`
  (thread + status dropdown + reply box).
- Register routes: add `SUPPORT` page (gate `AUTH`, nav) + `ADMIN_SUPPORT` (gate `ADMIN`, nav) so the
  App shell route table and admin nav pick them up.

### Contract card (`backend` + `frontend`) — new `## Support` sections
Document entities, enums, endpoints, DTO shapes, and the frontend service/hook/route names + gates.

### Manifest — new `support` feature
```json
{ "id": "support", "core": false, "requires": ["auth","email"],
  "backend": { "controllers": ["SupportController.java","admin/AdminSupportController.java"],
               "packages": [], "fenced": ["SupportCase","SupportMessage","SupportStatus","SupportAuthor",
               "SupportService","SupportCaseRepository","SupportMessageRepository","SupportRepliedEvent",
               "SupportController","AdminSupportController"] },
  "frontend": { "modules": ["/services/supportservice","/hooks/supporthooks","/types/support"],
                "fenced": ["supportService"],
                "pages": [{ "key": "SUPPORT", "gate": "AUTH", "nav": true },
                          { "key": "ADMIN_SUPPORT", "gate": "ADMIN", "nav": true }] },
  "config": { "env": [], "compose": [] } }
```

---

## Feature 4 — CSV / spreadsheet bulk upload (compulsory on admin tables)

**Goal:** Every admin data table can be populated from a `.csv` (and `.xlsx`) file.

### Backend
- **New** `service/SpreadsheetParser.java` — parses `.csv` (Apache Commons CSV) and `.xlsx`
  (Apache POI) into `List<Map<String,String>>` (lower-cased, trimmed headers).
  - Port from `prakash-stores/.../service/SpreadsheetParser.java`.
- **New** `dto/BulkUploadResult.java` — `{ int created; int skipped; List<String> errors }` (builder).
- **Per-entity import methods** live in each entity's service (`bulkImport(MultipartFile) → BulkUploadResult`),
  exposed at `POST /api/v1/admin/<resource>/bulk-upload`. Foundation ships the *parser + result DTO +
  documented contract*; the per-entity endpoint is generated per project (entities are per-project).
- **Dependencies (pom.xml):**
  ```xml
  <dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId><version>5.3.0</version></dependency>
  <dependency><groupId>org.apache.commons</groupId><artifactId>commons-csv</artifactId><version>1.11.0</version></dependency>
  ```

### Frontend
- **New** `components/admin/BulkUploadDialog.tsx` — file input (`.csv,.xlsx`), calls an injected
  upload fn, shows `BulkUploadResult` summary (created/skipped + error rows) via toasts + list.
  - Port from `prakash-stores/.../components/admin/ExcelUploadDialog.tsx` (rename for clarity).
- **New** `types/bulkUpload.ts` — `BulkUploadResult`.
- Generated admin list pages wire a "Bulk upload" button → `BulkUploadDialog` with the entity's
  `POST .../bulk-upload` service call.

### Foundation rule (document in `frontend/FOUNDATION_CONTRACT.md` + `CLAUDE.md`)
> **Every admin entity list page MUST include a `BulkUploadDialog`, and every admin entity MUST expose
> `POST /api/v1/admin/<resource>/bulk-upload` returning `BulkUploadResult`.** Use `SpreadsheetParser`
> on the backend. This is compulsory.

### Contract cards — new `## Bulk upload` sections (backend + frontend)
Document `SpreadsheetParser`, `BulkUploadResult`, `BulkUploadDialog` signature, and the endpoint convention.

### Manifest — new `bulk-upload` feature
```json
{ "id": "bulk-upload", "core": false, "requires": ["auth"],
  "backend": { "fenced": ["SpreadsheetParser","BulkUploadResult"] },
  "frontend": { "modules": ["/components/admin/bulkuploaddialog","/types/bulkupload"],
                "fenced": ["BulkUploadDialog"] },
  "config": { "env": [], "compose": [] } }
```

---

## Feature 5 — ag-grid for admin tables (admin-only)

**Goal:** A single reusable grid wrapper for all admin tables; public pages keep shadcn.

### Frontend
- **New** `components/admin/AdminDataGrid.tsx` — generic
  `<AdminDataGrid<T> rowData columnDefs actions? pagination? pageSize? height? emptyMessage? />`.
  Registers `AllCommunityModule`, dark quartz theme, floating text filters, pagination
  (`[10,20,50]`), optional right-pinned kebab actions column.
- **New** `components/admin/RowActionsCell.tsx` — kebab menu cell renderer (edit/delete/custom).
  - Port both from `prakash-stores/.../components/admin/{AdminDataGrid,RowActionsCell}.tsx`.
- **Dependencies (package.json):**
  ```json
  "ag-grid-community": "^36.2.0",
  "ag-grid-react": "^36.2.0"
  ```

### Foundation rule (document in `frontend/FOUNDATION_CONTRACT.md`)
> **All admin tables use `AdminDataGrid`. ag-grid is admin-only** — do NOT use it on public pages
> (keep shadcn `Table` / native there).

### Contract card — new `## Admin data grid` section
Document the `AdminDataGrid` props, `RowAction<T>` shape, and the `ColDef`/`CustomCellRendererProps`
imports from `ag-grid-community` / `ag-grid-react`.

### Manifest — new `admin-grid` feature
```json
{ "id": "admin-grid", "core": false, "requires": ["auth"],
  "frontend": { "modules": ["/components/admin/admindatagrid","/components/admin/rowactionscell"],
                "fenced": ["AdminDataGrid","RowActionsCell"] },
  "config": { "env": [], "compose": [] } }
```

---

## Feature 6 — Global toasters + API rule

**Goal:** Mount toasts globally and make "show a toast on every API call" an enforced foundation rule.

### Frontend
- **Mount the Toaster (currently missing):** add `<Toaster />` (from `@/components/ui/sonner`) into the
  frozen App shell. Since `App.tsx`/`AppProviders.tsx` are fenced, mount it inside `AppProviders.tsx`
  (the provider tree) so it renders once at root. — *This is the one fenced-shell edit required; do it
  in the foundation, not the pipeline.*
- **Global error interceptor** in `api/client.ts` — add a **response** interceptor:
  ```ts
  apiClient.interceptors.response.use(
    (r) => r,
    (error) => {
      const msg = error?.response?.data?.message ?? error?.message ?? 'Something went wrong';
      toast.error(msg);
      return Promise.reject(error);
    }
  );
  ```
  (keep the existing request interceptor that attaches the JWT).
- **Success helper** — **new** `lib/apiToast.ts`:
  ```ts
  export async function withToast<T>(p: Promise<T>, success: string): Promise<T> {
    const r = await p; toast.success(success); return r;   // errors already toasted by interceptor
  }
  ```
  Mutations wrap their call: `await withToast(supportService.reply(...), 'Reply sent')`.

### Foundation rule (document in `frontend/FOUNDATION_CONTRACT.md` + `CLAUDE.md`)
> **Toasts are global.** Failed requests auto-toast via the `apiClient` response interceptor — do NOT
> add manual `toast.error` after a caught request error (it would double-toast). For success feedback
> on mutations, wrap the call in `withToast(promise, 'message')`. `import { toast } from 'sonner'`.

### Contract card — new `## Toasts & API feedback` section
Document the mounted `<Toaster/>`, the auto-error interceptor behaviour, and `withToast`.

### Manifest — new `toasts` feature
```json
{ "id": "toasts", "core": true, "requires": [],
  "frontend": { "modules": ["/lib/apitoast"], "fenced": ["withToast"] },
  "config": { "env": [], "compose": [] } }
```

---

## Feature 7 — Email-OTP two-factor auth

**Goal:** Optional per-user 2FA. When enabled, login is two-step: password → 6-digit code emailed to
the user → JWT issued only after the code verifies. (Built fresh — not present in prakash-stores.)

### Backend
- **Extend** `model/User.java`:
  ```java
  private boolean twoFactorEnabled = false;
  private String  otpCodeHash;        // BCrypt hash of the current code (never store plaintext)
  private Instant otpExpiresAt;
  private int     otpAttempts;        // lock after N failures
  ```
  Same-commit: update the `## Auth & users` card `class User { ... }` signature.
- **New** `service/OtpService.java` — `void issue(User u)` (generate 6-digit, hash, set TTL, email via
  `EmailService`), `boolean verify(User u, String code)` (check hash + expiry + attempts).
- **Extend** `AuthController` / `AuthService` login flow:
  - `POST /api/v1/auth/login` — if `twoFactorEnabled`, validate password then issue OTP and return
    `{ "twoFactorRequired": true, "challengeId": "<opaque>" }` (**no JWT yet**). If disabled, behave as today.
  - **New** `POST /api/v1/auth/verify-2fa` — body `{ challengeId, code }` → verifies OTP → returns
    `{ token }`. The `challengeId` maps to the pending user (short-lived, server-side or signed).
  - **New** `POST /api/v1/auth/2fa/enable` + `POST /api/v1/auth/2fa/disable` (authenticated) — toggle
    `twoFactorEnabled` (enable can require a confirming OTP).
- **Config:** `app.security.otp.ttl-minutes=${OTP_TTL_MINUTES:10}`, `app.security.otp.max-attempts=5`.
- **Password hardening (bundle here):** enforce a min-strength policy on register/change (length ≥ 8,
  mixed classes) via a small validator; keep BCrypt (already strength-tuned).

### Frontend
- **Extend** `authService.ts`: `login()` returns a discriminated union
  `{ token } | { twoFactorRequired: true; challengeId }`; add `verifyTwoFactor({ challengeId, code })`,
  `enableTwoFactor()`, `disableTwoFactor()`.
- **Extend** `context/AuthContext.tsx` `login()` — if `twoFactorRequired`, surface a challenge state so
  the UI can route to the OTP step; only persist the token after `verifyTwoFactor`.
- **Extend** `pages/LoginPage.tsx` — second step: OTP entry using existing
  `components/ui/input-otp.tsx` (already in foundation). (`prakash-stores` has this component too.)
- **New** account setting to enable/disable 2FA (small section on an account/settings page or an
  admin-visible control).

### Contract card (`backend/FOUNDATION_CONTRACT.md`) — extend `## Auth & users`
```
class User { ...; boolean twoFactorEnabled; }            // 2FA fields (otp* internal)
class OtpService { void issue(User u); boolean verify(User u, String code); }
POST /api/v1/auth/login       -> { token } OR { twoFactorRequired:true, challengeId }
POST /api/v1/auth/verify-2fa  body { challengeId, code } -> { token }
POST /api/v1/auth/2fa/enable  (auth)   POST /api/v1/auth/2fa/disable (auth)
```
Frontend card: document the `login()` union return + `verifyTwoFactor/enableTwoFactor/disableTwoFactor`.

### Manifest — extend `auth`
- `auth.requires` → `["email"]`.
- Add to `auth.backend.fenced`: `OtpService`.
- Add to `auth.config.env`: `OTP_TTL_MINUTES`.

---

## Consolidated env var additions (`.env.example`)

```bash
# Email
SMTP_AUTH=false
SMTP_STARTTLS=false
MAIL_FROM=No Reply <no-reply@example.local>
# (SMTP_HOST/PORT/USERNAME/PASSWORD already present)

# 2FA
OTP_TTL_MINUTES=10
```

## Consolidated dependency additions

**backend/pom.xml:** `poi-ooxml:5.3.0`, `commons-csv:1.11.0`. (`spring-boot-starter-mail`, AWS S3 SDK
already present.)

**frontend/package.json:** `ag-grid-community@^36.2.0`, `ag-grid-react@^36.2.0`.
(`sonner`, `input-otp` already present.)

**docker-compose.yml:** add `mailpit` (local dev SMTP inspection).

---

## Recommended implementation order

1. **Email** (Feature 1) — everything else that emails depends on it.
2. **Toasts** (Feature 6) — mount `<Toaster/>` + interceptor early so all later UI inherits it.
3. **ag-grid** (Feature 5) + **Bulk upload** (Feature 4) — shared admin infra used by support/admin pages.
4. **Multi-image picker** (Feature 2) — small, self-contained.
5. **Support tickets** (Feature 3) — uses email + grid + toasts.
6. **Email-OTP 2FA** (Feature 7) — uses email; touches the fenced auth spine, do last & carefully.

Each step is one commit that includes code + `foundation.manifest.json` + `FOUNDATION_CONTRACT.md`.

---

## Verification

Run the foundation locally (`docker-compose up --build`) and confirm end-to-end:

- **Email:** trigger a support reply → mailpit UI at `http://localhost:8025` shows the message.
- **Toasts:** cause a failing request (e.g. bad login) → a single error toast appears (no double toast);
  a successful mutation shows one success toast.
- **ag-grid + bulk upload:** open an admin table → grid renders, filter/sort/paginate work; upload a
  small CSV → `BulkUploadResult` summary toast (created/skipped/errors).
- **Multi-image:** in an admin entity form, upload 2+ images, reorder, remove; confirm the entity
  persists an ordered `images` list and they render on the public page.
- **Support thread:** as a customer open a ticket + reply; as admin reply and change status; confirm
  both sides see the full thread and the customer received an email.
- **2FA:** enable 2FA for a user → next login prompts for the emailed 6-digit code; wrong/expired code
  rejected; correct code issues the JWT; disabling 2FA restores single-step login.
- **Contract integrity:** the `business-discovery` worker build (`FoundationCardIntegrity`) reports **no
  drift** — every new fenced class is present in its `FOUNDATION_CONTRACT.md` card and the manifest.
- **Backend build:** `./mvnw -q -pl backend compile`; **frontend build:** `npm --prefix frontend run build`.

---

## Reference file map (prakash-stores → foundation)

| Feature | prakash-stores source | foundation target |
|---|---|---|
| Email | `service/EmailService.java`, `notification/EmailNotificationListener.java` | same package names |
| Multi-image | `components/admin/{MultiImagePicker,ImagePicker}.tsx` | same |
| Support | `model/SupportCase.java`, `service/SupportCaseService.java`, `controller/{SupportController,admin/AdminSupportController}.java` | upgrade single-response → `SupportCase`+`SupportMessage` thread |
| CSV | `service/SpreadsheetParser.java`, `dto/BulkUploadResult.java`, `components/admin/ExcelUploadDialog.tsx` | parser+DTO+`BulkUploadDialog` |
| ag-grid | `components/admin/{AdminDataGrid,RowActionsCell}.tsx` | same |
| Toasts | `components/ui/sonner.tsx` (mounted in `App.tsx`), `api/client.ts` | mount in `AppProviders.tsx` + add interceptor + `lib/apiToast.ts` |
| 2FA | `components/ui/input-otp.tsx` only (flow NOT built) | build fresh per this spec |
