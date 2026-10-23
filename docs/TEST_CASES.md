# Test Cases — Cross-Sell/Upsell + Catalog + Orders spine (this session)

Manual QA test plan for everything built in this session (plan: `cross_sell_upsell_products_plan.md`,
steps 1–13). Each row is **Feature name · Action performed · Expected outcome**.

## Prerequisites

- App running: `docker compose up -d --build` → **http://localhost:8080**.
- Seeded admin: **`owner@yourbusiness.com` / `changeme123`** (role `SUPER_ADMIN`).
- For API checks, get a JWT:
  `curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"owner@yourbusiness.com","password":"changeme123"}'`
  then send `Authorization: Bearer <token>`.
- Admin UI lives under `/admin/**`; public pages under `/`.
- **Note on the demo gateway:** Razorpay keys are blank in `.env`, so the backend uses `DemoPaymentGateway`
  (public key `demo_key`), which accepts any signature. In this mode checkout **bypasses the hosted Razorpay
  modal** and settles the payment directly, so an **ONLINE** order completes end-to-end locally (marked `PAID`,
  workflow advances) — no real keys needed. Add real `RAZORPAY_KEY_ID`/`RAZORPAY_KEY_SECRET` to exercise the
  actual hosted-modal path.

---

## 1. Catalog — public products (`/products`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Product grid | Visit `/products` | Grid of active products with image/name/category/price; search box + category chips. |
| Search | Type part of a product name | Grid filters to matching products (client-side). |
| Category filter | Click a category chip | Grid shows only that category; "All" resets. |
| Add to cart (simple) | Click **Add to cart** on an in-stock, variant-less product | Item added; header cart badge count increases; "Added to cart" toast. |
| Variant product | A product with variants shows **View** (not Add) | Button is "View"; clicking opens the detail page to pick a size. |
| Out of stock | A tracked product with 0 available | Button shows **Out of stock**, disabled. |
| Detail page | Visit `/products/:slug` | Gallery, description, price; variant selector if variants; Add to cart. |
| Variant selection | Pick a size on the detail page | Price + availability update; Add enabled only after a size is chosen. |
| Out-of-stock variant | A variant with 0 tracked stock | That size is disabled ("— out of stock"). |
| Branch filter | Select an outlet, then view `/products` | Only products offered at that branch (GLOBAL or that branch) appear. |
| Not found | Visit `/products/does-not-exist` | "Product not found" with a back link (API returns 404). |

## 2. Catalog — admin products (`/admin/products`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Admin grid | Visit `/admin/products` | `AdminDataGrid` of all products (incl. inactive); Stock column shows "Unlimited / N left / N variant(s) / Per-branch". |
| Create product | **New product** → fill name+price → Save | Product created (201); appears in grid; audit `PRODUCT_CREATED`. |
| Validation | Save with blank name or negative price | Rejected (400); error toast. |
| Duplicate | Create a product with an existing name/slug | Rejected (409). |
| Edit | Row menu → Edit → change fields → Save | Updated (200); audit `PRODUCT_UPDATED`. |
| Delete | Row menu → Delete → confirm | Removed; audit `PRODUCT_DELETED`. |
| Images | Set thumbnail via `ImagePicker`, gallery via `MultiImagePicker` | Images picked from media library (no raw file input); stored as URLs. |
| Variants editor | Add variants with label/price/stock | Variants saved; product becomes non-cartable on the public grid. |
| Partial payload | POST create omitting `stockPerBranch` | Succeeds (201) — absent primitive defaults, no 400. |
| Bulk upload | **Bulk upload** with CSV (`name,price,category,stockTracked,stock,workflowCode,branchSlugs`) | `BulkUploadResult` summary; dup slug/name skipped; bad rows reported; `branchSlugs` resolves to SELECTED branches. |

## 3. Inventory / reservations (opt-in stock)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Unlimited stock | Order a `stockTracked=false` product (e.g. food) | Never blocks on stock; reserve/commit/release are no-ops. |
| Reserve on order | Place an order for a tracked product | `available` drops by the quantity (reserved increases). |
| Oversell guard | Order more than available for a tracked product | **409** (CONFLICT); whole order aborted, nothing reserved. |
| Release on cancel | Cancel an order with a tracked product | `available` returns to its prior value. |
| Commit on payment | Pay/settle an order | Reservation becomes a sale (available unchanged post-commit). |
| TTL sweep | Leave an online order `AWAITING_PAYMENT` past `inventory.reservation-ttl-minutes` | Scheduled sweep releases the hold, sets `FAILED`, advances to `CANCELLED`. |

## 4. Recommendations

| Feature name | Action performed | Expected outcome |
|---|---|---|
| UPSELL widget | On a product detail page with an UPSELL rule | "Upgrade options" shows the target with **View details** → its detail page. |
| CROSS_SELL widget | On a product detail page with a CROSS_SELL rule | "Frequently bought together" shows the target; a `cartable` item has a one-click **Add**. |
| Public endpoint | `GET /api/v1/recommendations?itemType=PRODUCT&itemId=1&type=UPSELL` | Returns resolved `CatalogItemDto[]`, highest score first. |
| Branch filter | Pass `branchId` with a recommendation read | Items not offered at that branch are dropped. |
| Admin grid | `/admin/recommendations` | Grid with Source/Target as **resolved names**, Type, Score, Active. |
| Create (pickers) | New recommendation → pick source + target products + type/score | Created; both appear with names. |
| Self-reference | Set source == target | Rejected (409) with "cannot recommend itself"; form shows inline error. |
| Duplicate | Create the same (source, target, type) twice | Second rejected (409). |
| Bad item | Reference a non-existent/inactive item | Rejected (400). |
| Bulk upload | CSV `sourceItemType,sourceItemId,targetItemType,targetItemId,relationType,score,active` | Imported; self-refs/dups skipped; invalid rows reported. |

## 5. Orders — placement & payment paths

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Pay-at-counter | Place `SELF_PICKUP` + `PAY_AT_COUNTER` | 201; stage advances to **CONFIRMED** (onPaidEntry), `paymentState=AWAITING_PAYMENT`, no gateway; CONFIRMED emails fire. |
| Online order | Place `DELIVERY`/`SELF_PICKUP` + `ONLINE` | 201 with a `payment` block (gateway order) for Razorpay; initial stage fires **no** actions until payment. |
| ₹0 order | Place a free order | Skips payment, marked `PAID`, advances to onPaidEntry. |
| Server re-pricing | Send client-tampered prices (via raw API) | Ignored — priced from the catalog server-side. |
| Mixed workflow | Cart mixes items whose products use different workflows | **409** ("order them separately"). |
| Pay-at-counter guard | `DELIVERY` + `PAY_AT_COUNTER` | **400** — pay-at-counter is self-pickup only. |
| Order number | After placing | `orderNumber` like `ORD-2026-000123`; `referenceId` `order_<id>`. |
| My orders | `/orders` (logged in) | History list with stage + payment badges + total; link to detail. |
| Ownership | `GET /api/v1/orders/{id}` for another user's order | **403**. |
| Mark paid | Admin → order row → Mark paid (pay-at-counter) | `paymentState=PAID`; audit `ORDER_MARK_PAID`. |
| Advance stage | Admin → Advance → pick a stage | Order moves to that stage; its bound actions re-fire. |

## 6. Orders — cancellation & stage-aware refund

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Cancellable stage | Cancel an order whose current stage is `cancellable` | Advances to **CANCELLED**; `refundAmount` computed per the stage's `refundPolicy`. |
| FULL refund | Cancel a paid order at a FULL stage | `refundAmount = total`; `paymentState=REFUND_PENDING`; `refund.issue` connector fires (demo records intent). |
| PARTIAL refund | Cancel at a PARTIAL stage (e.g. food PREPARING 50%) | `refundAmount = total × percent%`. |
| Non-cancellable | Cancel at a `cancellable=false` stage (e.g. food OUT_FOR_DELIVERY) | **409** "can no longer be cancelled". |
| Admin override | Admin cancel on a non-cancellable stage | Allowed (override); refund still per policy. |
| Unpaid cancel | Cancel an `AWAITING_PAYMENT` order | `paymentState=FAILED`; held stock released. |
| Refund preview | Open `/orders/:id` with a cancellable order | "Cancel order" button shows the refund the policy grants. |

## 7. Workflow engine & connectors

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Seeding | First boot (empty DB) | `default` + `food` workflows seeded with stages, policies, and bindings. |
| Stage dispatch | Order enters a stage with bindings | Each enabled binding runs its connector action; recorded as `WorkflowExecution` rows (SUCCESS/FAILED). |
| Email actions | onPaidEntry stage (CONFIRMED) entered | `email.customer` + `email.store` fire (visible in Mailpit at http://localhost:8025). |
| Inbound webhook | `POST /api/v1/connectors/delivery.demo/webhook` body `{"orderId":N,"stage":"OUT_FOR_DELIVERY"}` | Order advances; OUT_FOR_DELIVERY bindings fire (delivery.initiate + emails). |
| Idempotency / dedupe | Re-send the same webhook | No-op; `WorkflowExecution` count unchanged (no double email/ship/refund). |
| Isolation | One bound action fails | Others still run; failure recorded `FAILED` with detail. |
| New connector | Add a `@Component` implementing an SPI + `actions()` | Appears in `/admin/connectors` + the workflow builder with no engine edits. |

## 8. Admin — workflow builder & diagram (`/admin/workflow`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Diagram view | Open the **Diagram** tab | Stages as nodes, happy path left→right; dashed red "cancel" edges into CANCELLED from cancellable stages. |
| Policy legend | Read a node | Shows cancellable + refund policy (e.g. `Cancellable · PARTIAL 50%`) and bound action chips. |
| Workflow switch | Select "Food delivery" | Diagram shows tightening policy (FULL → PARTIAL 50 → not cancellable). |
| Node panel | Click a stage node | Right-side panel opens to edit that stage. |
| Edit policy | Toggle cancellable / change refund policy + percent | Live reflected in the diagram node. |
| Bindings | Add/remove/toggle a connector action in the panel | Binding list updates. |
| Save | Click **Save workflow** | 200; changes persist (verify by reloading). |
| Invariants | Save a workflow missing an initial or CANCELLED stage | **400** (invariant violation). |

## 9. Admin — connectors (`/admin/connectors`)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| List | Open the page | Cards for `email`, `delivery.demo`, `refund.demo` with category + declared action chips. |
| Toggle | Flip a connector's enable switch | Persisted; a disabled connector is skipped by dispatch/quotes. |
| Secrets | Inspect a card | Shows "Secrets configured via env" — no secret values rendered or editable. |

## 10. Branches & outlet selection

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Admin create | `/admin/branches` → New branch (name, address, lat/lng, Google Maps link) | Created; audit `BRANCH_CREATED`. |
| Google Maps field | Set the **Google Maps link** field | Stored; public page shows "📍 View on Google Maps" (falls back to coordinates/address when blank). |
| Public list | `/branches` | Active outlets as cards with address, hours, maps link, and "Order from here". |
| Nearest | `GET /api/v1/branches/nearest?lat=&lng=` | Active branches sorted by distance; `distanceKm` set. |
| Header picker | Open the header outlet dropdown | Lists outlets; selecting one updates the catalog filter + persists (localStorage). |
| Geolocation | First visit, grant location | Nearest active branch auto-selected. |
| Bulk upload | CSV `name,phone,addressLine,city,state,pincode,latitude,longitude,googleMapsUrl,openingHours` | Imported; dup name/slug skipped. |

## 11. User address book

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Add | Account → Saved addresses → Add address | Saved; first address becomes default automatically. |
| List order | Multiple addresses | Default first, then newest; `GET /api/v1/addresses`. |
| Set default | "Set default" on a non-default address | Previous default cleared; this one becomes default. |
| Edit / Remove | Edit or Remove an address | Updated/removed; ownership-checked. |
| Ownership | Access another user's address id | **403**. |
| No bulk | — | Address book is user-owned; no bulk-upload endpoint. |

## 12. Checkout (`/checkout`, replaces the stub)

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Fulfilment | Choose Delivery / Self-pickup | UI switches accordingly. |
| Delivery quote | Select Delivery | Options (Standard ₹49 / Express ₹99) load; selecting adds the fee to the total. |
| Address picker | Delivery with saved addresses | Pick a saved address; Place button disabled until one is chosen. |
| No address | Delivery with no saved address | Prompt to add one in the account; backend rejects (400) without `addressId`. |
| Pickup pay option | Self-pickup | Choose "Pay at counter" or pay now. |
| Branch + address on order | Place a delivery order | Order records `branchName` and a **snapshotted** `shippingAddress` (not a link). |
| Razorpay (real keys) | Online order with real keys | SDK loads, modal opens, `verifyPayment` on success → navigate to `/orders/:id`. |
| Empty cart | Open checkout with empty cart | "Your cart is empty." |

## 13. Post-checkout feedback

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Form shown | Open `/orders/:id` before submitting | "How was your experience?" star form appears. |
| Submit | Pick 1–5 stars (+ optional comment) → Submit | 201; "Thanks for your feedback" toast; form disappears (`feedbackSubmitted=true`). |
| Submit-once | Submit feedback again for the same order | **409** "already submitted". |
| Validation | Submit rating 0 or 6 | **400** (rating must be 1–5). |
| Admin view | `/admin/feedback` | Read-only grid: Order / Rating (★) / Comment / Submitted; no bulk-upload. |

## 14. Cross-cutting

| Feature name | Action performed | Expected outcome |
|---|---|---|
| Bulk upload (all admin entities) | Products / Recommendations / Orders / Branches pages | Each ships a `BulkUploadDialog` + `POST /api/v1/admin/<resource>/bulk-upload`. |
| Audit trail | Perform any admin write | Event recorded; visible at `/admin/logs` (no secrets/PII in the detail). |
| Toasts | Any failed request / successful mutation | Single `toast.error` on failure (interceptor); `withToast` success toast on mutations — no double-toast. |
| Admin dark theme | Open any admin dialog / sheet / dropdown | Renders in dark theme (portal content included), matching the admin shell. |
| ag-grid scope | Inspect public vs admin tables | Admin tables use `AdminDataGrid` (ag-grid); public pages use shadcn cards — never ag-grid. |
| Security | Public GET of products/recommendations/branches unauthenticated; any `/api/v1/admin/**` without ADMIN | Public reads 200; admin without role → 401/403. |
