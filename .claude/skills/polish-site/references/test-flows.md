# Canonical test flows (Phase 3)

Exercise these journeys first in a **live browser**, then capture them as Playwright specs in
`frontend/e2e/<feature>.spec.ts`. Adapt to the generated domain — the sellable may be a product,
room, membership, service, etc., but the spine is the same.

**First, read `docs/`** for this project's own feature/test specs (e.g. a `TEST_CASES.md` or
feature notes) and fold any domain-specific journeys into the list below — `docs/` is the source
of truth for what this business actually does.

## Setup
- App runs single-origin at **http://localhost:8080** (backend serves the built frontend).
- Playwright is pre-configured (`frontend/playwright.config.ts`: `testDir: ./e2e`,
  `baseURL = $BASE_URL || http://localhost:8080`). Run:
  `cd frontend && BASE_URL=http://localhost:8080 npx playwright test`
- `frontend/uiverify.mjs` is a working reference for the driver style and selectors.
- **Selectors**: prefer `getByTestId(...)` and ARIA roles over text. Known auth testids:
  `login-identifier`, `login-password`, `login-submit`. Discover others by reading the page/
  component source rather than guessing.
- Admin login: seeded `ADMIN_EMAIL` / `ADMIN_PASSWORD` from `.env`. For customer flows, sign up a
  fresh user (OTP email lands in mailpit at http://localhost:8025 if 2FA/verification is on).

## Journeys
1. **Auth** — sign up → (verify/OTP via mailpit if enabled) → log in → log out → forgot/reset
   password happy path.
2. **Browse & detail** — home → listing page → open a detail page → images/content render.
3. **Cart** — add a sellable to cart → update qty → remove → cart total correct → persists across
   reload.
4. **Checkout** — select/enter a shipping address (address book) → pick a branch + fulfillment
   type → place order → order confirmation → order shows in "My Orders" with the snapshotted
   address.
5. **Order lifecycle** — order detail shows current workflow stage; cancel is offered only when the
   stage is `cancellable`.
6. **Feedback** — after a completed order, submit 1–5 stars + comment; only one per order.
7. **Admin CRUD** — log in as admin → open an admin entity list (renders via `AdminDataGrid`) →
   create → edit → delete an entity → audit entry appears at `/admin/logs`.
8. **Admin bulk upload** — open the entity's `BulkUploadDialog` → upload a small CSV/XLSX →
   `BulkUploadResult` reports created/failed rows.

## Spec hygiene
- One spec file per feature; independent tests (each sets up its own state, no ordering coupling).
- Assert on user-visible outcomes (URL, visible text/role, row counts), not implementation detail.
- Keep them deterministic: wait for `networkidle` / explicit locators, not fixed sleeps.
- If a journey can't pass because of a foundation defect, note it in the Phase 6 report rather than
  weakening the assertion.
