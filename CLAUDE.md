# webapp-foundation — Project Context for Claude Code

## What this repo is

`webapp-foundation` is the **fenced base project** every generated client site is built on top of.
The discovery pipeline (the `business-discovery` repo's `discovery-worker`) clones this repo into each
generated project's workspace, then the LLM generates the business domain **on top of** it. The
foundation ships the immutable spine — auth/users, payments (Razorpay behind a gateway strategy),
media library + gallery (MinIO/S3), the app shell, cart/checkout framework, and shared exceptions —
so generated code **imports and uses** these, never reimplements them.

Layout: `backend/` (Spring Boot 3, Java 17), `frontend/` (React 19 + Vite + TS), `docker-compose.yml`,
`Dockerfile`.

## The two contract cards are ground truth — keep them in sync (CRITICAL)

The generated-code pipeline reads foundation signatures **verbatim** from two hand-authored cards:

- `backend/FOUNDATION_CONTRACT.md` — the fenced Java spine (sections: `## Auth & users`,
  `## Payments`, `## Media library + gallery`, `## Shared exceptions`).
- `frontend/FOUNDATION_CONTRACT.md` — the fenced TS/React modules (auth, cart, checkout, API client,
  site shell, media/gallery, prebuilt pages & routes).

These cards are **hand-authored, not auto-extracted from the code**. The worker injects them into
every generation call as the FENCED FOUNDATION CONTRACT and the LLM binds to them exactly. So a
foundation change that isn't mirrored into the card ships **stale ground truth** to every generated
project — the single biggest integrity risk in the whole pipeline. It surfaces downstream as a
generated-code compile failure that looks like a codegen bug but is actually card drift.

> **RULE — same-commit card sync.** Any change to a **fenced module's public surface** — a
> new/renamed class, DTO, entity field, enum value, endpoint (method + path), hook, component prop, or
> exported function in **auth / user / payment / media-gallery / cart / checkout / shell /
> shared-exceptions** — MUST update the matching entry in `backend/FOUNDATION_CONTRACT.md` or
> `frontend/FOUNDATION_CONTRACT.md` **in the same commit**.
>
> - **Adding a fenced feature** = add its `##` section to the relevant card in that commit.
> - **Renaming/removing** a fenced type or changing a signature/field/enum = edit the card entry in
>   that commit.
> - Never merge a fenced-surface change with a stale card. If you touch a fenced file's public API and
>   the diff has no corresponding card edit, the change is incomplete.

Match the existing card style: reference types by **simple name** (the base package is renamed per
project, so import resolution is automatic), and show exact fields/signatures inside fenced code
blocks under the right `##` section.

### Cross-check on the pipeline side

The worker also runs a build-time drift check (`FoundationCardIntegrity` in `discovery-worker`): after
it clones this repo it warns loudly if a card is absent/empty, or if a fenced data type exists in the
source but has no matching symbol in its card. That warning is a **safety net, not a substitute** for
the same-commit rule here — fix drift at the source (this repo), not by silencing the warning.

### Feature manifest — `foundation.manifest.json` (this repo owns it)

`foundation.manifest.json` at the repo root **is** the pipeline's source of truth for the foundation's
features + fenced surface (SDK-skip controllers, guard names, fenced symbol names, fenced frontend
module paths, route-page gates, config env/compose). The `business-discovery` worker reads it at clone
time and projects every "what to skip / never strip / how to gate / which paths are fenced" seam from
it — so **onboarding a new foundation feature is an edit HERE, never a pipeline code change** (the
pipeline is critical; keep it untouched).

To add or change a foundation feature, in the **same commit**:
1. Ship the code (backend + frontend) as usual.
2. Add/update its `##` section in the relevant `FOUNDATION_CONTRACT.md` (shapes — rule above).
3. Add/update its entry in `foundation.manifest.json` (seam policy — `id`, `core`, `requires`,
   `backend.{controllers,packages,fenced}`, `frontend.{modules,guards,fenced,pages}`,
   `config.{env,compose}`).

The worker falls back to a built-in default only if this file is absent/malformed, so a drift between
this file and the shipped code silently ships stale seam policy — treat it like the contract cards.
Design of record: `docs/foundation-feature-manifest-plan.md` in `business-discovery`.

## Foundation rules generated code MUST follow

These are enforced conventions the foundation ships (details + signatures in the `FOUNDATION_CONTRACT.md`
cards). Generated code inherits them; do not reinvent or bypass:

- **Toasts are global.** `<Toaster/>` is mounted once in the fenced `App.tsx`. Failed API requests
  auto-`toast.error` via the `apiClient` response interceptor — never add a manual `toast.error` after a
  caught request error (double-toast). Wrap mutations in `withToast(promise, 'message')` for success.
- **Forms validate per the feature.** Every create/edit form MUST validate its inputs against the rules
  the feature implies (required fields, positive amounts, percentages ≤ 100, date ranges where start <
  end, values within the domain's bounds, …) and **gate the Save button** (`canSave`) until the form is
  valid — never rely on the backend alone to reject bad input. **Error display convention:** show all
  messages in a single error bar at the **top of the form** (a `role="alert"` block) and mark each invalid
  field with a red border (`border-destructive focus-visible:ring-destructive`). Do **not** put messages
  below individual fields — it shifts the layout and breaks row alignment. Keep server-side checks as the
  backstop for data-integrity/security rules.
- **Admin tables use `AdminDataGrid` (ag-grid), admin-only.** All `/admin/**` data tables render via the
  shared `AdminDataGrid`; public pages keep shadcn `Table`/native — do NOT use ag-grid on public pages.
- **Bulk upload is compulsory on admin entity tables.** Every admin entity list page ships a
  `BulkUploadDialog`, and every admin entity exposes `POST /api/v1/admin/<resource>/bulk-upload`
  returning `BulkUploadResult` (parse files with the foundation `SpreadsheetParser`).
- **Audit security-sensitive actions.** Inject `AuditService` and `record(...)` an event for privileged
  actions you add (create/update/**delete**, permission changes, exports). It persists to the `audit_log`
  table + structured logs (→ Loki/Grafana). **Never** put secrets (passwords, tokens, OTPs, PII) in an
  audit event or log line. Admins read activity at `/admin/logs`.
- **Sellables bind to the fenced `CatalogItemDto` / `CatalogItemResolver` contract.** Recommendations and
  orders reference items polymorphically by `(itemType, itemId)`. A new sellable type (`Room`, `Membership`,
  …) keeps its typed entity and adds ONE `@Component implements CatalogItemResolver` — never reimplement the
  catalog, recommendation, order, or inventory engines. A batteries-included default `Product`
  (`itemType="PRODUCT"`) already ships.
- **Stock is opt-in per product** (`stockTracked`, default false = unlimited). Reserve/commit/release **only**
  via the fenced `InventoryService` (a per-type `InventoryProvider` SPI) — never decrement stock by hand.
- **Orders/checkout/fulfillment run on the fenced order spine.** Inject `OrderService`; the order lifecycle is
  a DB-configured **workflow** of stages, each with 0..N **connector actions** — never hardcode lifecycle
  steps or write your own order/checkout persistence. Orders carry a `fulfillmentType` (delivery / self-pickup;
  pickup may be `PAY_AT_COUNTER`) and run through a selected **branch**. Cancellation/refund is a property of
  the current workflow **stage** (`cancellable` + `refundPolicy`) — never add an ad-hoc cancel path.
- **Attach external services via connectors.** Delivery, tax, loyalty, refunds, notifications, … are
  **connector beans** (`ActionConnector`/`QuoteConnector`/`WebhookConnector`) bound to a stage at
  `/admin/workflow`; never call a third-party API inline from domain code. Built-in email is the `email`
  connector.
- **Branches / address book / feedback.** Orders fulfil through a **branch** (outlet); the user **address
  book** feeds checkout and is **snapshotted** onto the order (`Order.shippingAddress`, never a link);
  post-checkout **feedback** is 1–5 stars + optional comment, one per order. Never reimplement `BranchService`,
  `AddressService`, `FeedbackService`, or re-declare `OrderService`, `WorkflowEngine`, `StageActionDispatcher`,
  `ConnectorRegistry`/the connector SPIs, `CatalogService`, or `InventoryService`.
