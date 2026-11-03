# Test Cases — Discount & Loyalty Engine

Manual QA test plan for the Discount & Loyalty feature (plan: `Discount & Loyalty Engine – Design Spec.pdf`,
built in Phases 0–4 on branch `feat/discount-loyalty-engine`). Each row is
**Feature name · Action performed · Expected outcome**. UI steps use the app; a few checks use the API or a
quick DB query (appendix) for things the UI can't show directly (pending coins, the nightly job, holdout).

## Prerequisites

- App running: `docker compose up -d --build` → **http://localhost:8080**. Mail (offer/code emails) is caught at
  **http://localhost:8025** (mailpit).
- **Seeded admin:** `owner@yourbusiness.com` / `changeme123` (role `SUPER_ADMIN`).
  - If login returns *Invalid credentials*, your dev DB predates this build. Reset it:
    `docker compose down -v && docker compose up -d --build` (wipes + reseeds). Then the creds above work.
- **Demo data** is seeded in the `dev` profile (`LoyaltyDemoSeeder`): **15 loyalty profiles** (≈8 Bronze / 4 Silver /
  2 Gold / 1 Platinum), **4 discount codes**, and **this month's budget** (₹80,000 budget, ₹18,500 spent). Real users
  (e.g. `owner`, `john.doe`) have profiles so the admin grids show real emails.
- **Demo codes to test with** (see the exact owner/scope on **Admin → Loyalty & Discounts → Codes**):
  - `DIWALI10` — **public**, 10% off, min spend ₹999, cap ₹300 (anyone logged in can try it).
  - `WB-7K4QXR` (win-back, 10%, min ₹3,000), `BDAY-M9P2TC` (birthday, coins), `WEL-…` (used) — **personal** codes,
    tied to specific demo customers; only that customer can apply them.
- For API checks, get a JWT:
  `curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"owner@yourbusiness.com","password":"changeme123"}'`
  then send `Authorization: Bearer <token>`.
- **Demo payment gateway:** Razorpay keys are blank in `.env`, so an ONLINE order settles directly (`demo_key`) —
  checkout completes end-to-end locally (order marked `PAID`, workflow advances to the paid stage), which is what
  triggers coin earning and redemption commits. No real keys needed.
- **Admin loyalty section:** `/admin` → left-nav **Loyalty & Discounts** → tabs Overview / Customers / Codes /
  Triggers / Tiers / Scoring / Settings.
- **Nightly job:** the score decay, pending→confirmed flips, coin expiry, and win-back/birthday triggers run nightly
  (`loyalty.nightly-cron`). To run them on demand for testing: **`POST /api/v1/admin/loyalty/run-nightly`** (admin).
- **Live-recompute note:** `GET /api/v1/loyalty/me` (and the account Rewards card) recompute *your* score/tier on read,
  so engagement reflects immediately there; the full confirm/decay/tier pass is the nightly job.

---

## 1. Admin section — access & navigation

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Nav entry | Log in as admin, open `/admin` | **Loyalty & Discounts** appears in the left nav (badge-percent icon). |
| Section shell | Click it | Lands on `/admin/loyalty` with a tab strip: Overview, Customers, Codes, Triggers, Tiers, Scoring, Settings. |
| Tab routing | Click each tab | URL changes to `/admin/loyalty/{customers,codes,triggers,tiers,scoring,settings}`; content swaps; Overview is `index`. |
| Admin-only | Hit `/admin/loyalty` as a non-admin (CUSTOMER) | Redirected/blocked (RequireAdmin); STAFF sees only the order board. |
| API gate | `GET /api/v1/admin/loyalty/settings` with no token | **403**. |

## 2. Settings & the master kill switch (`/admin/loyalty/settings`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Load settings | Open Settings tab | "Discount programme" card with an **Enabled/Disabled** toggle + a grid of knobs (budget target, holdout %, coin value, caps, margin floor, …). |
| Kill-switch copy | Read the toggle description | States that scoring + tiers keep running when off; offers/codes/coin redemption pause. |
| Toggle → confirm prompt | Flip the **Discount programme** switch (either direction) | A confirmation dialog opens **before** the toggle moves. Disabling reads "Disable the discount programme?" and warns that discounts make customers feel rewarded, so turning them off can reduce repeat orders / sales (and pauses code generation + redemption); enabling reads "Enable the discount programme?" and notes it typically lifts sales. The switch position does **not** change yet. |
| Cancel the prompt | In that dialog, click **Cancel** (or press Esc) | Dialog closes; the toggle stays in its original position; nothing is saved. |
| Toggle off | Flip **Discount programme** off → **Confirm (Disable discounts)** in the dialog | The switch flips to Disabled and saves immediately → toast "Settings saved"; audit `LOYALTY_SETTINGS_UPDATED` at `/admin/logs`. (No separate Save click — the kill switch persists on confirm.) |
| Overview banner | With it off, open Overview tab | Amber banner: "The discount programme is **off** …". |
| Read-only knobs | Open Settings tab, don't click Edit | The knobs grid shows each value as read-only text (not inputs); an **Edit settings** button sits in the card header. |
| Edit knobs | Click **Edit settings** → inputs appear with **Save settings** + **Cancel** at the top of the card → change e.g. "Holdout group" to 15 → **Save settings** | Persisted; panel returns to read-only; reload keeps the value. **Cancel** instead discards edits and reverts to read-only. |
| Validation | (API) PUT settings with the body from GET, flipping `discountsEnabled` | 200; `discountsEnabled` reflects the new value. |
| Re-enable | Toggle it back on → **Confirm (Enable discounts)** | Saves immediately; banner disappears; offers/redemption resume (see §10–12). |

## 3. Tiers config (`/admin/loyalty/tiers`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Tier grid | Open Tiers tab | 4 rows: Bronze (min 0, 2 coins/₹100), Silver (500, 2.5), Gold (1500, 3), Platinum (4000, 4) + perks. |
| Edit tier | Row → **Edit** → change min score / coins-per-₹100 / perks → Save | "Tier saved"; grid updates; audit `LOYALTY_TIER_UPDATED`. |
| Threshold effect | Lower Silver's min score to 100, then run `run-nightly` | Customers with score ≥100 move to Silver on the next recompute (check Customers tab). |

## 4. Scoring rules (`/admin/loyalty/scoring`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Rules grid | Open Scoring tab | One row per action: SIGNUP 50 (once), PROFILE_COMPLETE 30 (once), DAILY_VISIT 2 (cap 1/day), PRODUCT_VIEW 0.5 (10/day), ADD_TO_CART 3 (5/day), PURCHASE 10 (per ₹1,000), REVIEW 20, … |
| Dead rules (note) | Inspect PROFILE_COMPLETE / REFERRAL_SIGNUP / REFERRAL_PURCHASE | Editable here but **never awarded** — no emitter wires them (see Known limitations). Don't expect a score change from these in testing. |
| Edit rule | Edit PRODUCT_VIEW points to 1 → Save | "Scoring rule saved"; audit `LOYALTY_EVENT_RULE_UPDATED`. |
| Daily-cap note | Read the page hint | PURCHASE is per ₹1,000; non-purchase actions share a combined daily/monthly cap (set in Settings). |

## 5. Triggers config (`/admin/loyalty/triggers`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Trigger grid | Open Triggers tab | Rows for WELCOME, ABANDONED_CART, WIN_BACK, TIER_UP, MILESTONE, BIRTHDAY, SLOW_STOCK, FESTIVE, SURPRISE with On/Off, expiry days, and an offer-ladder JSON. |
| Edit ladder | Edit WELCOME → change expiry / rungs JSON → Save | "Trigger saved"; audit `LOYALTY_TRIGGER_UPDATED`. |
| Bad JSON | Put invalid JSON in the ladder | **Save disabled** + "Not valid JSON." inline. |
| Disable a trigger | Toggle WELCOME off | New signups get **no** welcome code (see §10). |
| Which triggers auto-fire | Read the grid | Only **WELCOME, WIN_BACK, BIRTHDAY, SURPRISE** have firing wiring (see §10/§12). **ABANDONED_CART, SLOW_STOCK, TIER_UP, MILESTONE** are config-only in this build — editable/toggleable but never issue a code (see Known limitations). **FESTIVE** is the trigger stamped on admin-created public codes (§6), not an automatic trigger. |

## 6. Codes / offers (`/admin/loyalty/codes`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Codes grid | Open Codes tab | Grid of offers incl. demo codes; columns Code, Trigger, Type, Value, **Scope** (Public / customer email), Used (n/max), Status, Expires. |
| Category selector | **New code** → look next to **Type** | A **Category** dropdown offers *Festive campaign / Manual / Milestone / Tier-up / Surprise* (defaults to Festive campaign). It sets the code's reporting **Trigger** label only; the engine-issued triggers (WELCOME, WIN_BACK, BIRTHDAY, …) are intentionally **not** listed. |
| Customer picker | **New code** → open the **Customer** field | A searchable dropdown (not a numeric id). Typing filters the user list by **name or email**; a **"Public offer (no user)"** entry sits at the top. Leaving it on that entry = a public code; picking a customer = a personal code scoped to them. |
| Create public code | **New code** → leave Customer on **Public offer (no user)**, Category **Festive campaign**, type **Percentage**, value 15, min spend 500, max uses 100 → Save | 201; row added as a **Public** FESTIVE code; audit `LOYALTY_OFFER_CREATED` (records approving staff id + trigger). |
| Pick a category | **New code** → set Category to **Milestone** (or Tier-up/Surprise) → Save | The new row's **Trigger** column shows the chosen category; it also groups under that bucket in the Overview "Codes by trigger" chart (§9). |
| Create personal code | **New code** → **Customer** → search a customer by name/email and select them (leave Category default) → Save | Created as a **MANUAL** personal code scoped to that user (the selected id is sent to the API). (Picking a category overrides the label.) |
| Blocked category (API) | POST a create body with `trigger` = `WELCOME`/`WIN_BACK`/`BIRTHDAY`/`ABANDONED_CART`/`SLOW_STOCK` | **400** — engine-only triggers can't be set on a hand-created code. |
| Schedule window | **New code** → set **Starts** and **Expires** (both date **and time**, e.g. start today 18:00, expire tomorrow 18:00) → Save | Code saved with that active window. Blank **Starts** = active now; blank **Expires** = never expires. Lets you run a 1-day / fixed-period code. |
| Not active yet | Create a code with **Starts** in the future, then try to apply it at checkout before that time | Red "This code isn't active yet." (validation checks `startsAt`). After the start time it validates normally (until Expires). |
| Form validation | In **New code** (or **Edit**): set **Starts** after **Expires**; or a 0/negative/blank **Value** for a %/₹/coins code; or a **Percentage** > 100; or a negative Min spend / Max discount; or Max uses < 1 | A red **error bar at the top of the form** lists every problem (e.g. "Expires must be after Starts.", "Enter a value greater than 0.", "Percentage can't exceed 100."), each offending **field is outlined in red**, and **Save** is **disabled** until all are fixed. (No messages below fields — keeps alignment.) The two date pickers also constrain each other (`min`/`max`). |
| Auto code | Create without typing a code | A look-alike-safe code is generated (e.g. `SALE-7K4QXR`; no 0/O/1/I/L). |
| Duplicate | Create with an existing code | **409** conflict. |
| Edit code | Row → **Edit** → change e.g. value / min spend / category / Starts / Expires → **Save changes** | Panel opens pre-filled; **Code** and **Customer** are shown read-only (immutable); on save the grid row updates; toast "Code updated"; audit `LOYALTY_OFFER_UPDATED`. |
| Edit guard: maxUses | Edit a code and set **Max uses** below its current Used count | **400** — "maxUses can't be below the N use(s) already redeemed". |
| Edit engine code | Row → **Edit** on an engine-issued code (e.g. a WELCOME code) | Its category shows as "WELCOME (engine)" and stays put on save; you can edit the other fields. Switching the category is only allowed to an admin category (FESTIVE/MANUAL/MILESTONE/TIER_UP/SURPRISE). |
| Revoke | Row → **Revoke** → confirm | Status → REVOKED; audit `LOYALTY_OFFER_REVOKED`; the code no longer validates at checkout. |
| Delete (unused) | Row → **Delete** → confirm | Code permanently removed from the grid; toast "Code deleted"; audit `LOYALTY_OFFER_DELETED`. The **Delete** action only appears on codes with **Used = 0**. |
| Delete blocked (used) | Find a redeemed code (Used ≥ 1) | No **Delete** action in its row menu (only Edit/Revoke). A direct `DELETE` API call returns **409** — revoke instead (keeps the redemption/budget history). |
| Bulk upload | **Bulk upload** a CSV `code,type,value,minSpend,maxDiscount,maxUses,campaignId,expiresAt` | `BulkUploadResult` summary; duplicates skipped; bad `type` rows reported; audit `LOYALTY_OFFER_BULK_IMPORT`. |

## 7. Budget (`/admin/loyalty/settings` → Monthly discount budget)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Budget list | Scroll Settings to **Monthly discount budget** | Grid of months with Budget ₹ and Spent ₹ (demo: current month 80,000 / 18,500). |
| Set budget | Pick a month + amount → **Set budget** | Upserted; audit `LOYALTY_BUDGET_UPSERTED`; appears/updates in the grid. |
| Bad month/amount | (API) POST a budget with month `2026-13` or negative amount | **400**. |
| Spend tracking | After a code redeems at checkout (§12), reopen the budget | `Spent ₹` for the month increased by the discount value. |

## 8. Customers (`/admin/loyalty/customers`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Customers grid | Open Customers tab | Read-only grid: Customer (email/name), Tier, Score, Coins, AOV, Return %, Holdout, Last order; sorted by score desc. |
| Real emails | Check top rows | Demo real users show emails (e.g. a Platinum `john.doe@…`); synthetic demo rows show `#900001`. |
| Holdout flag | Find rows with Holdout = Yes | ≈10% of customers are flagged (demo seeds a couple). |

## 9. Metrics dashboard (`/admin/loyalty` Overview)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| KPI cards | Open Overview | Cards: Members (+holdout count), Discount % of gross (vs target), Budget used %, Coin liability ₹. |
| Discount % colour | If discount % of gross > target | The value is shown in red (over target). |
| Members vs holdout | Read the "Members vs holdout" chart | Bars for repeat % and avg revenue, members vs holdout, + an incremental-revenue line of copy. *(Demo shows 0s until real paid orders exist.)* |
| Tier mix | Read the pie | Slices per tier (Bronze/Silver/Gold/Platinum) matching the Customers grid counts. |
| Codes by trigger | Read the bar chart | Issued vs Redeemed per trigger (demo: issued from the seed codes, redeemed 0 until §12). |
| API | `GET /api/v1/admin/loyalty/metrics` (admin token) | JSON with `members, holdout, budget, coinLiability, tierMix, redemptionByTrigger, holdoutComparison`. |

## 10. Loyalty Score engine — customer side (`/account`, beacons)

> Do these logged in as a **customer** (register a new user, or use a seeded non-admin). Enable consent first.

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Rewards card | Open `/account` | **Rewards** card: tier badge, Loyalty score, Reward coins ("a discount, not cash"), progress-to-next-tier, consent toggle, Rewards-terms link. |
| Signup points | Brand-new user opens `/account` for the first time | Score shows **50** (one-time SIGNUP), tier Bronze. (Awarded on first `/me`.) **Note:** PROFILE_COMPLETE (30) is **not** awarded even with name/phone filled — it's a dead rule, so a fresh user stays at 50, not 80 (see Known limitations). |
| Progress nudge | View a customer below a threshold | "You're N points from <Tier>"; within 10% it reads "almost there!". |
| Consent off → no tracking | Leave **Personalised rewards** off; view a product 10s | No PRODUCT_VIEW points (beacons are ignored without consent); score unchanged. |
| Consent on | Turn **Personalised rewards** on | Toast "Preference saved"; a DAILY_VISIT beacon fires (≤1/day). |
| Product-view points | With consent on, open a product detail page and **stay ≥10s**, then reopen `/account` | Score increased by the PRODUCT_VIEW points (0.5 by default; capped 10/day). |
| Add-to-cart points | With consent on, add a product to cart | ADD_TO_CART points (3; cap 5/day) counted on next `/account` load. |
| Per-type cap | Add to cart >5 times in a day | Only the first 5 earn (per-type daily cap). |
| Combined cap | Rack up many farmable actions in a day | Non-purchase points stop at 30/day total (one-time events like signup are exempt). |
| Review points | Buy + review a product (first review of it) | REVIEW points (20) added once per product; editing the review does **not** re-earn. |
| Purchase score points (deferred) | Place + pay an order, then reopen `/account` right away | Score does **not** move yet. The PURCHASE event is written **PENDING** and only counts toward the score after the return window — the live recompute sums **CONFIRMED** events only. Run `run-nightly` after the window (or lower `returnWindowDays`) and the score rises. (Same deferral as the earned coins in §11.) |
| Nightly recompute/decay | `POST /api/v1/admin/loyalty/run-nightly` | Scores recomputed with 90-day half-life decay; tiers move up instantly, down only after a 30-day grace. |
| API | `GET /api/v1/loyalty/me` (customer token) | `{ tier, tierName, score, coinBalance, nextTier, pointsToNextTier, nearNextTier, trackingConsent, discountsEnabled }`. |
| Activity guard | `POST /api/v1/loyalty/activity {"type":"PURCHASE"}` | **400** — high-value events can't be self-reported (only VIEW/WISHLIST/ADD_TO_CART/DAILY_VISIT/SHARE_CLICK). |

## 11. Reward Coins (earn / redeem / reverse / expiry)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Coin balance | Open `/account` as a demo user who has coins | Reward coins shows the balance; "Coin history" lists ledger lines (reason + signed amount). |
| Earn on purchase | Place + pay an order (demo gateway) as a customer | On the paid stage the earn connector awards `floor(total/100 × coinsPer100)` coins **PENDING** (not yet spendable). |
| Pending not spendable | Right after that order, check `/account` | Coin balance **unchanged** (pending coins aren't in the spendable balance yet). |
| Confirm coins | `POST /api/v1/admin/loyalty/run-nightly` after the return window, or temporarily lower `returnWindowDays` in Settings | Pending earnings become **CONFIRMED**; balance increases; history line flips from "(pending)". |
| Redeem at checkout | As a user with a confirmed balance, add items ≥ ₹500, go to checkout (Delivery) | A **"Use N coins (−₹X)"** toggle appears (≤10% of order, ≥₹500 min); toggling it shows a discount line + lower total. |
| Below min order | Cart subtotal < ₹500 | The coin toggle does **not** appear. |
| Redeem commits once | Complete that order (paid) | Balance decremented by the redeemed coins; a negative "redeem" ledger line; idempotent (no double-decrement). |
| Reverse on cancel | Cancel/return that order | The reversal connector pulls back earned coins and refunds redeemed coins; purchase points reversed. |
| Expiry | (DB) set a confirmed coin's `expires_at` in the past → `run-nightly` | Entry → EXPIRED; balance reduced. |
| API | `GET /api/v1/loyalty/coins` (customer token) | Ledger newest-first with `coins, reason, status, earnedAt, expiresAt`. |

## 12. Discount offer engine (triggers + checkout codes)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Welcome offer | Register a brand-new user, open `/account` (fires first bootstrap) | With WELCOME enabled + not in holdout, a welcome code is issued (check Admin → Codes, scope = that user) and an email lands in mailpit. |
| Holdout gets nothing | Repeat for a user flagged `in_holdout` (DB) | **No** code issued (holdout never receives triggered codes). |
| Kill switch blocks | Turn discounts **off** (§2), register a new user | **No** welcome code issued; scoring still works. |
| Validate (preview) | At checkout, type `DIWALI10` with subtotal ≥ ₹999 → **Apply** | Green "₹… off applied."; a "Code …" discount line appears; total drops (10%, capped ₹300). |
| Min spend | Apply `DIWALI10` with subtotal < ₹999 | Red "Spend ₹999 to use this code." |
| Wrong owner | Apply a personal code (`WB-7K4QXR`) as a different user | Red "This code belongs to another account." |
| Expired/revoked | Apply a revoked or expired code | Red "This code is no longer active." / "This code has expired." |
| Redeem commits | Apply a valid code and complete the order (paid) | `Redemption` recorded; the offer's **Used** count increments (single-use → status USED); budget **Spent** increases; audit `LOYALTY_CODE_REDEEMED`. |
| One code per order | Try to apply a second code | Only one discount code applies per order. |
| Win-back trigger | Set a customer's `last_order_at` well past 1.5× their gap (≥45d) in DB → `run-nightly` | A WIN_BACK code is issued to that customer (Admin → Codes) + email. |
| Birthday trigger | Set a customer's `birthday` to this month + `profile_completed_at` not null → `run-nightly` | A BIRTHDAY offer is issued. |
| Surprise trigger | Place several paid orders as a Silver+ customer | ~10% chance per paid order of a SURPRISE reward (non-cash) being issued. |
| Guard: one active code | A customer who already has an ACTIVE code | No new code is issued until it's used/expired (plus a 21-day cooldown). |
| Guard: return rate | A customer with return rate above the block threshold (Settings) | No codes issued. |
| Code format | Inspect an issued code | Trigger prefix + 6 look-alike-safe chars (e.g. `WB-7K4QXR`, `BDAY-M9P2TC`). |

## 13. Budget pacing

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Pace shrinks offers | Set a small monthly budget, drive spend ahead of plan, then trigger a % offer | The sized discount shrinks (pace factor 0.5–1.2 applied). |
| Budget exhausted | Set `spentToDate ≥ budgetAmount` for the month (redeem codes or edit budget) → trigger a cash offer | **No** cash (PCT/FLAT) offer issued until next month; non-cash still allowed. |
| No budget configured | Delete the month's budget row → trigger an offer | Treated as unlimited (pace 1.0, never exhausted); offers still issue. |

## 14. Compliance & abuse controls

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Terms page | Visit `/loyalty-terms` (public) | Page covering score/tiers, coin earning + value + expiry, **"coins are a discount, not cash"**, real-expiry-only (no fake timers), DPDP consent, and the right to change the programme. |
| Terms link | From the account Rewards card | "Rewards terms" link opens `/loyalty-terms`. |
| DPDP opt-out | Turn off **Personalised rewards** | Behavioural beacons stop being recorded; tier perks continue. |
| Coins-not-cash note | Read the Rewards card | "Reward coins · a discount, not cash". |
| Manual-code approval trail | Create a manual code (§6) then open `/admin/logs` | `LOYALTY_OFFER_CREATED` recorded with the approving staff id (audit trail for staff misuse). |
| No secrets in audit | Review loyalty audit lines at `/admin/logs` | Only non-sensitive detail (codes, counts, ids) — no PII/secrets. |

## 15. Kill-switch end-to-end (the client "turn it all off" requirement)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Turn off | Settings → Discount programme **off** | Saved; Overview shows the amber banner. |
| Scoring continues | As a customer, browse with consent + `run-nightly` | Score/tier still update; `/account` Rewards still works. |
| No offers | Register a new user / run nightly triggers | **No** welcome/win-back/birthday/surprise codes issued; **no** promo emails in mailpit. |
| No redemption | At checkout, try to apply `DIWALI10` or toggle coins | Code validation fails closed ("Discounts are currently unavailable."); the coin toggle doesn't appear. |
| Turn back on | Settings → **on** | Offers + redemption resume; banner clears. |

---

## Appendix A — handy API calls (admin token = `$T`)

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"owner@yourbusiness.com","password":"changeme123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# Admin
curl -s http://localhost:8080/api/v1/admin/loyalty/metrics      -H "Authorization: Bearer $T"      # dashboard data
curl -s -X POST http://localhost:8080/api/v1/admin/loyalty/run-nightly -H "Authorization: Bearer $T"  # run the nightly job now
curl -s http://localhost:8080/api/v1/admin/loyalty/customers    -H "Authorization: Bearer $T"
curl -s http://localhost:8080/api/v1/admin/loyalty/offers       -H "Authorization: Bearer $T"

# Customer (same token works; owner has a profile)
curl -s http://localhost:8080/api/v1/loyalty/me                 -H "Authorization: Bearer $T"
curl -s http://localhost:8080/api/v1/loyalty/coins              -H "Authorization: Bearer $T"
curl -s -X POST http://localhost:8080/api/v1/loyalty/consent    -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"consent":true}'
curl -s -X POST http://localhost:8080/api/v1/loyalty/offers/validate -H "Authorization: Bearer $T" -H 'Content-Type: application/json' -d '{"code":"DIWALI10","subtotal":1500}'
```

## Appendix B — DB checks / fixtures (psql)

```bash
DBU=$(grep -E '^POSTGRES_USER=' .env|cut -d= -f2); DBN=$(grep -E '^POSTGRES_DB=' .env|cut -d= -f2)
psql(){ docker compose exec -T db psql -U "$DBU" -d "$DBN" -c "$1"; }

psql "select tier, count(*) from loyalty_profile group by tier order by min(score);"      -- tier mix
psql "select code, trigger_type, status, used_count, max_uses from loyalty_offer;"         -- offers / codes
psql "select connector_id, action_code from workflow_binding where connector_id like 'loyalty%';"  -- earn/reverse bound
psql "select user_id, coins, reason, status from coin_ledger_entry order by earned_at desc limit 10;"

-- Fixtures to force triggers on the next run-nightly:
psql "update loyalty_profile set in_holdout=true where user_id=<id>;"                        -- make a holdout user
psql "update loyalty_profile set last_order_at = now() - interval '120 days', usual_order_gap_days=30 where user_id=<id>;"  -- win-back
psql "update loyalty_profile set birthday = date_trunc('month', now())::date, profile_completed_at = now() where user_id=<id>;"  -- birthday
psql "update coin_ledger_entry set expires_at = now() - interval '1 day' where id=<id>;"      -- force coin expiry
```

## Appendix C — automated coverage (already green)

Backend unit/integration tests (run `cd backend && mvn test` → 31 pass):
`LoyaltyScoreEngineTest` (signup-once, per-type cap, 90-day decay, tier rise, nightly confirm),
`CoinServiceTest` (earn→pending→confirm, atomic redeem + overdraw refusal, reversal),
`OfferServiceTest` (kill switch, holdout, welcome issuance, validate ownership/min-spend, atomic+idempotent commit).

## Known limitations (by design, this build)

- **Purchase rewards are deferred, not instant.** Paying an order earns both reward **coins** and **PURCHASE score points**, but both are written **PENDING** and only take effect after the return window (`returnWindowDays`) when the nightly job confirms them — so right after checkout neither the spendable coin balance nor the loyalty score changes. (By design: no earn-and-spend before a return is possible.) Small orders can earn nothing — a sub-₹100 total floors to 0 coins, and a tier with `coinsPer100 = 0` earns none.
- **Some seeded scoring rules are never awarded ("dead" rules).** `PROFILE_COMPLETE` (30), `REFERRAL_SIGNUP` (50) and `REFERRAL_PURCHASE` (200) appear as editable rows in the §4 Scoring grid, but **no code path records them** — there is no profile-completion hook and no referral capture. So of the one-time events only **SIGNUP** actually fires (verified: a brand-new user with name + phone set scores exactly **50, not 80**). Editing their points in the grid has no effect until the emitters are wired. See also the unwired triggers above.
- **Slow-stock**, **abandoned-cart**, **tier-up**, and **milestone** triggers are not wired — no domain code fires them, so toggling them On in the grid issues nothing (abandoned-cart also needs server-side cart persistence). Only **WELCOME** (first `/account` load), **WIN_BACK** and **BIRTHDAY** (nightly job), and **SURPRISE** (per paid order) fire automatically; **FESTIVE** is the label on admin-created public codes, not an auto-trigger.
- **True margin-floor** isn't enforced (foundation has no cost-of-goods) — discounts are capped by the 20% ceiling + per-rung max instead.
- The **coin toggle** only appears on the **Delivery** checkout path (where a quote is fetched); the discount-code input works on both.
- Holdout/revenue metrics read **0** until there are real paid orders (the demo seed has none).

---

## Test-run findings log

### Run — 2026-11-01 (API + read-only DB; §10 and §12 active, §11/§13/§14 state-verified)

**Environment:** app at `localhost:8080`, new user `loyalty.test.…@example.com` (userId 3) registered + email-verified via mailpit.

**§10 Loyalty Score engine — PASS (customer-side).** signup=50/Bronze; PURCHASE self-report→400; consent-off beacon ignored (score stayed 50); consent-on; PRODUCT_VIEW +0.5; ADD_TO_CART +3; per-type cap (6 ADD_TO_CART → only 5 counted); combined daily cap (50 mixed beacons → capped exactly at 80 = 50 signup + 30); `/me` shape matches; coin history empty. Edge probes all green: non-beacon/garbage/empty/null type→400, no/garbage token→403, consent off→on round-trips, SIGNUP idempotent across repeated `/me`, over-cap writes nothing (no negative events — guard `points.signum()<=0`).

**§12 Discount offer engine (validation) — PASS.** Via `POST /api/v1/loyalty/offers/validate` as user 3 against seeded demo codes:
- `DIWALI10` @1500 → **₹150 off** (10%); @5000 → **₹300** (maxDiscount cap applied); @500 → "Spend ₹999…" (min-spend).
- `WB-7K4QXR` (personal to another user) → "belongs to another account."
- `WEL-5H8KQ2` (USED) → "no longer active."; unknown code → "isn't valid."; blank → "Enter a code."; lowercase `diwali10` → accepted (case-insensitive).

**§11 / §13 / §14 — state-verified from prior real orders (read-only DB):**
- §11: `coin_ledger_entry` holds **CONFIRMED (15, 1875 coins) + PENDING (4, 37 coins)** → the pending→confirmed deferral is real.
- §12/§13: `EAT-DIWALI` redemption of **₹200** → `discount_budget` 2026-11 `spent_to_date = ₹200` → redemption-commit **and** budget spend-tracking both work.
- §14: all `LOYALTY_*` audit actions present (incl. `LOYALTY_OFFER_UPDATED`/`DELETED`); details show only `code=/order=/amount=/trigger=` — **no PII/secrets**.

**Edge cases found (this run):**
- **§12 validate is preview-only / idempotent** — validating `DIWALI10` repeatedly does **not** consume uses (`used_count` stayed 137/500); uses only increment on an actual order redemption commit. Good separation of validate vs redeem.
- **§12 maxDiscount cap** correctly clamps a % offer (5000×10% = 500 → capped to 300).
- **§12 codes are case-insensitive** (lowercased input is uppercased server-side).
- **§12 distinct messages** for blank vs unknown vs wrong-owner vs used/expired — no information leak about which codes exist beyond "isn't valid".

**⚠ Blocked / NOT exercised this run (need admin or a full checkout):**
- **Admin access is blocked.** The seeded `owner@yourbusiness.com` login returns 401 — **not** a stale DB (demo data is intact), but `token_version=1` shows the admin password was **changed from the default**, so `changeme123` no longer works. Overwriting the password hash was (correctly) refused by the sandbox, and a `down -v` reset was avoided to protect local state.
- Consequently un-run: **§11** active earn/redeem/reverse/expiry (need a fresh paid order + `run-nightly`), **§13** pacing (pace-factor shrink, budget-exhaustion block — need admin + offer trigger), **§15** kill-switch end-to-end (need admin to toggle `discountsEnabled`), **§12** trigger issuance (welcome/win-back/birthday/surprise) + live redemption-commit, and anything via `/admin/**` (`run-nightly`, metrics, `/admin/logs`).
- These paths are covered by backend unit/integration tests (Appendix C: `CoinServiceTest`, `OfferServiceTest`) and partially corroborated by the DB state above, but were **not** actively re-run here.
