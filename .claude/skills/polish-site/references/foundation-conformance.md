# Foundation conformance checklist (Phase 2)

Audit the **generated domain code** against these rules. Each is a foundation invariant from
`CLAUDE.md` + the two `FOUNDATION_CONTRACT.md` cards. A violation is a bug in the generated
code — fix it by binding to the fenced API, not by changing the foundation.

How to use: for each item, grep/read the generated code, confirm compliance, fix if not,
re-check. Note any item you can't satisfy without touching fenced code in the Phase 6 report.

## Toasts & API feedback
- [ ] No manual `toast.error(...)` after a caught API error (the `apiClient` interceptor already
      toasts failures — a manual one double-toasts). Grep generated code for `toast.error`.
- [ ] Mutations show success via `withToast(promise, 'message')`, not ad-hoc toasts.
- [ ] `<Toaster/>` is NOT re-mounted in generated code (it's mounted once in the fenced `App.tsx`).

## Admin tables & forms
- [ ] Every `/admin/**` data table renders via the shared `AdminDataGrid` (ag-grid) — not a
      hand-rolled table and not ag-grid on public pages.
- [ ] Public pages use shadcn `Table`/native markup (no ag-grid).
- [ ] Admin create/edit uses the foundation form panel (`StepperSheet`) pattern where applicable.

## Bulk upload (compulsory on admin entity tables)
- [ ] Every admin entity list page ships a `BulkUploadDialog`.
- [ ] Every admin entity exposes `POST /api/v1/admin/<resource>/bulk-upload` returning
      `BulkUploadResult`, parsing with the foundation `SpreadsheetParser`.

## Audit logging
- [ ] Privileged/security-sensitive actions added by generated code (create/update/**delete**,
      permission changes, exports) inject `AuditService` and call `record(...)`.
- [ ] No secrets/PII (passwords, tokens, OTPs) in any audit event or log line.

## Catalog, inventory, orders (the engines — never reimplemented)
- [ ] New sellable types keep a typed entity + ONE `@Component implements CatalogItemResolver`;
      recommendations/orders reference items polymorphically by `(itemType, itemId)`.
- [ ] No reimplementation of the catalog / recommendation / order / inventory engines.
- [ ] Stock changes go **only** through `InventoryService` (reserve/commit/release) — never a
      manual stock decrement. Stock is opt-in per product (`stockTracked`, default false).
- [ ] Orders/checkout/fulfillment go through the fenced order spine (`OrderService` +
      DB-configured workflow stages). No hardcoded lifecycle steps, no custom order persistence.
- [ ] Cancellation/refund is read from the current workflow stage (`cancellable` + `refundPolicy`)
      — no ad-hoc cancel path.
- [ ] External services (delivery, tax, loyalty, refunds, notifications) are **connector beans**
      (`ActionConnector`/`QuoteConnector`/`WebhookConnector`) bound at `/admin/workflow` — never a
      third-party API called inline from domain code. Built-in email is the `email` connector.

## Branches / address book / feedback
- [ ] Orders fulfil through a **branch**; checkout uses the user address book.
- [ ] Shipping address is **snapshotted** onto the order (`Order.shippingAddress`) — never a link.
- [ ] Post-checkout feedback is 1–5 stars + optional comment, one per order.
- [ ] No reimplementation / re-declaration of `BranchService`, `AddressService`, `FeedbackService`,
      `OrderService`, `WorkflowEngine`, `StageActionDispatcher`, `ConnectorRegistry`/connector SPIs,
      `CatalogService`, `InventoryService`.

## Contract-card + manifest integrity
- [ ] If generated code legitimately extended a fenced surface, the matching
      `FOUNDATION_CONTRACT.md` section AND `foundation.manifest.json` entry were updated in the
      same change (see `CLAUDE.md` → "same-commit card sync").
