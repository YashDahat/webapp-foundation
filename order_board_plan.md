# Order Board — Implementation Plan

> Admin-side order management board (kanban per workflow + sprint). Source spec (vendored into the repo
> for reference): [`docs/order-board/Order Board Features.md`](docs/order-board/Order%20Board%20Features.md)
> + screenshots in `docs/order-board/screenshots/`.
> **Confirmed decisions:** use `@dnd-kit/core` for drag-and-drop; the board **replaces** the
> current `/admin/orders` grid; a new **`STAFF`** role (separate from ADMIN/SUPER_ADMIN) can view the
> board and change ticket status only; admin **"New order"** redirects to the menu/products (catalog)
> flow; the board gets **live updates** (SSE push + optimistic locking). See §9 for the full decisions log.

## 1. Approach

The Order Board is **a new admin front-end over the existing fenced order spine**, plus a small set of
genuinely new backend concepts. Guiding principle (per `CLAUDE.md`): **drive the existing `OrderService` /
`WorkflowEngine`, never hardcode a lifecycle or add ad-hoc cancel/refund paths.** Columns come from
`WorkflowStage` ordinals; moves call `advanceStage`; cancel calls `OrderService.cancel`; refund/cancel
policy stays a property of the stage.

### Reuse as-is (no changes)

| Board feature | Existing foundation piece |
|---|---|
| Columns = workflow stages | `WorkflowStage.ordinal/label`, `WorkflowService.stages()` |
| Next-stage move | `OrderService.advanceStage()` → `WorkflowEngine.advance()` (already fires connectors + audits) |
| Cancelled column / cancel action | `OrderService.cancel()` (stage-aware refund, already exists) |
| Record payment / "Awaiting payment" flag | `OrderService.markPaid()`, `Order.paymentState` |
| Refund action | stage `refundPolicy` / `refundPreview` already on `OrderDto` |
| Customer, items, total, fulfilment | `OrderDto` fields |
| Multi-workflow board + staff-role label | `Workflow` (+ picker) |
| Branch scoping / filtering | `Branch`, `BranchService`, `Order.branchId/branchName`, fenced `BranchPicker` / `useBranch` |
| Bulk upload / New order | existing `bulk-upload`, checkout |

### Genuinely new (needs same-commit card + manifest sync)

1. **Per-stage time target** → `WorkflowStage.targetMinutes` (Integer, nullable). "Stuck" = time-in-stage > target.
2. **Time-in-stage + stage history** → `Order.stageEnteredAt`; new `OrderStageEvent` entity (timeline in detail panel), appended by `WorkflowEngine`.
3. **Blocked** (orthogonal to stage, pauses timer) → `Order.blocked/blockReason/blockNote/blockedAt`; `BlockReason` enum.
4. **Sprints** (time-boxed batches, complete → carry-forward) → new `OrderSprint` entity + `SprintStatus` enum; `Order.sprintId`, `Order.carriedForward`.
5. **Staff assignment** → `Order.assignedUserId` (+ name snapshot); `Workflow.staffRole` (Rider / Packer / Technician).
6. **Channel** (card shows "Marketplace") → `Order.channel` (String, nullable, cosmetic).
7. **`STAFF` role** → new `Role.STAFF` (below ADMIN): board-read + status-change only (no cancel/refund/create, no other admin area). Assignees are STAFF users.
8. **Live board** → `Order` gets `@Version` (optimistic lock, stale-move rejection) + an **SSE stream** for push updates.

## 2. Backend changes

**Model**
- `Role` enum (auth, fenced): add **`STAFF`** (between ADMIN and USER). Fenced-surface change → sync the auth section of `backend/FOUNDATION_CONTRACT.md` + manifest in the same commit.
- `WorkflowStage`: add `Integer targetMinutes`.
- `Workflow`: add `String staffRole`.
- `Order`: add `Integer assignedUserId`, `String assignedStaffName`, `boolean blocked`, `BlockReason blockReason`, `String blockNote`, `Instant blockedAt`, `Instant stageEnteredAt`, `boolean carriedForward`, `Long sprintId`, `String channel`, and `@Version Long version` (optimistic lock for concurrent board moves).
- New `enum BlockReason { OUT_OF_STOCK, CUSTOMER_UNREACHABLE, PAYMENT_ISSUE, STAFF_UNAVAILABLE, ADDRESS_ISSUE, OTHER }`.
- New `@Entity OrderSprint { id, workflowCode, name, LocalDate startsOn, endsOn, SprintStatus status, Instant createdAt }` + `enum SprintStatus { ACTIVE, CLOSED }`.
- New `@Entity OrderStageEvent { id, orderId, stageCode, Instant enteredAt, TriggerType trigger, Integer byUserId }` (stage-history timeline).

**Repositories**: `OrderSprintRepository`, `OrderStageEventRepository`; add `OrderRepository` finders scoped by sprint/workflow/branch (e.g. `findByWorkflowCodeAndSprintId(...)`, optional `branchId` filter), plus `findByWorkflowCodeAndCurrentStageCodeIn(...)` for the migration check (§6).

**Services**
- `WorkflowEngine.advance()`: set `stageEnteredAt = now` and append an `OrderStageEvent` on every transition (keeps history + timer source in one place).
- `OrderService` gains order mutations (audited, like `markPaid`): `block(id, reason, note)` (sets blocked, freezes timer via `blockedAt`), `resume(id)` (shifts `stageEnteredAt += now − blockedAt` so the paused timer resumes), `assign(id, userId)`. Plus two small touches in `placeOrder`: set `stageEnteredAt`, join the workflow's active sprint.
- **Payment guard** in `advanceStage`: advancing an `AWAITING_PAYMENT` / pay-at-counter order into a terminal non-cancelled stage → `409` (mirrors the UI "collect payment first" prompt). *Behavior change — flagged.*
- `OrderService` also gains: `sendPaymentLink(id)` (emails a pay URL for an unpaid order via the `email` connector + `PaymentService`), and a **standalone** `refund(id, amount?, reason)` that issues a refund **without** cancelling (goodwill on a delivered order) — distinct from `cancel()`'s stage-aware refund; routes through the refund connector and sets `REFUND_PENDING`. Both audited.
- **Optimistic concurrency:** board mutations (advance/block/resume/assign) are guarded by `Order.@Version`; a stale write (someone already moved the card) surfaces as `409 Conflict` so the UI can refresh — the backbone that makes the live multi-user board safe (Jira-style).
- New `SprintService`: list by workflow, create/edit (upsert), `complete(id, req)` → close current, create next, carry forward ticked open orders (keep stage/history/blocked, set `carriedForward=true`, re-point `sprintId`).
- `WorkflowService.replaceWorkflow()` gains **in-flight order migration** (§6): diff old→new stage codes, require a `stageMapping` for any removed code that live orders occupy, and re-stamp those orders without firing connectors.
- New `OrderBoardService`: assembles the board read model — columns (stage order + synthetic **Blocked** + **Cancelled**), cards, per-column counts + stuck counts, and flag-filter counts, scoped by `(workflowCode, sprintId, branchId?)`. `branchId` is optional: unset = **All branches** (for an owner monitoring every outlet at once), set = one outlet. Per-branch card counts included so a multi-outlet owner sees the split.

**Controllers / endpoints** (`/api/v1/admin`; ADMIN-gated except the staff-permitted subset below)
- `AdminOrderController` (extend): `POST /orders/{id}/block`, `POST /orders/{id}/resume`, `PUT /orders/{id}/assignee`, `POST /orders/{id}/send-payment-link`, `POST /orders/{id}/refund`, `GET /orders/board?workflowCode=&sprintId=&branchId=`, `GET /orders/{id}/history`, and `GET /orders/board/stream` (**SSE** — pushes board changes to connected viewers).
- New `AdminSprintController`: `GET /sprints?workflowCode=`, `POST /sprints`, `PUT /sprints/{id}`, `POST /sprints/{id}/complete`.
- `AdminWorkflowController` (extend `PUT /workflow/{code}`): accept an optional `stageMapping` for in-flight order migration — see §6.
- **Admin "New order" has no new backend** — the button redirects staff/admin to the menu/products (catalog) page and reuses the existing checkout flow (per decision §9.1). Offline specifics (pay-at-counter, on-behalf-of customer) are a follow-up noted in §9.1.
- **STAFF authorization:** `SecurityConfig` grants `STAFF` a **narrow allowlist** — `GET /orders/board(+/stream)`, `GET /orders/{id}`, `GET /orders/{id}/history`, `PUT /orders/{id}/stage`, `POST /orders/{id}/block|resume` — and nothing else under `/admin/**`. Cancel, refund, send-payment-link, sprints, workflow, users, etc. stay ADMIN-only.

**DTOs**: `OrderBoardDto` (columns + per-branch counts), `OrderCardDto` (incl. `branchName`), `OrderBoardDetailDto` (order + timeline + target/stuck), `SprintDto`, `OrderStageEventDto`, `BlockOrderRequest`, `AssignOrderRequest`, `UpsertSprintRequest`, `CompleteSprintRequest`, `WorkflowStageMappingRequest` (removed→surviving stage map for §6). (Board fields live in dedicated DTOs rather than bloating the shared customer `OrderDto`.)

**Seeds** (`WorkflowService.seedDefaults`): add `targetMinutes` to stages; add `staffRole` ("Rider" for food). Seed **retail** + **services** workflows to match the doc (so the multi-workflow board is demoable out of the box).

**Data evolution / backfill** (§9.4): the foundation uses Hibernate `ddl-auto` so new nullable columns are auto-added, but existing rows need sane values — a one-time boot backfill sets `stageEnteredAt ← updatedAt` (so time-in-stage isn't null), `blocked ← false`, `carriedForward ← false`. Sprints bootstrap: ensure **exactly one ACTIVE sprint per workflow** (invariant), auto-create a first sprint if none, and adopt **orphan (null-`sprintId`) orders** into the active sprint of their workflow.

**Audit**: new `AuditActions` constants for block / resume / assign / sprint-complete / carry-forward (no PII).

## 3. Frontend changes

The board **becomes `/admin/orders`** (kanban, not a data table — an intentional exception to the
AdminDataGrid rule, which governs tabular lists). Bulk upload + New order are retained.

**New page/components** (`src/pages/admin/OrderBoardPage.tsx` + `src/components/admin/board/`):
- `OrderBoardPage` — header with **workflow picker + branch picker (`BranchPicker`, incl. an "All branches" option) + sprint picker**, date range, days-left, two-segment `Progress`, Edit/Complete; filter bar (search `Input`, staff-initials toggles, flag toggles with count `Badge`); column row. The branch picker reuses the fenced `useBranch`/`BranchContext`, so selection persists with the rest of the app.
- `BoardColumn`, `OrderCard` (`Card` + `Avatar`), `OrderDetailSheet` (`Sheet`: status vs target, warnings, next-stage action, secondary actions **Mark blocked / Record payment / Send payment link / Refund / Print receipt / Cancel**, Call/WhatsApp, assignee `Select`, stage-history timeline, line items). *Send payment link* and *Refund* call the new endpoints; *Print receipt* is a browser-print view of a receipt component (no new backend). **Secondary actions are hidden for `STAFF`** (they only see the next-stage action + block/resume).
- Dialogs: `BlockDialog`, `CollectPaymentDialog`, `CompleteSprintDialog` (carry-forward `Checkbox` list), `EditSprintDialog`.
- **"+ New order"** button → navigates to the menu/products (catalog) page; ADMIN-only (hidden for STAFF).
- **Drag & drop** via `@dnd-kit/core`: valid-target highlighting (next stage / Blocked / Cancelled; blocked → own stage or Cancelled; invalid columns fade). Closed sprints render read-only (no drag/actions). A `409` from a stale move surfaces a "someone already moved this — refreshing" toast and re-syncs.
- **Live updates:** the board subscribes to `GET /orders/board/stream` (SSE via `EventSource`) and invalidates/patches the React Query cache on push; a polling `refetchInterval` is the fallback if the stream drops.

**Staff vs admin UI:** `STAFF` lands on the board as their home (no other admin nav), sees the same columns, and may drag to change status / block / resume only — create, cancel, refund, payment-link, sprint and workflow controls are hidden. Admin nav gets the board at `/admin/orders`; STAFF is routed straight to it.

**Types / services / hooks**: extend `types/order.ts` with board types + new `types/sprint.ts`; add board/block/resume/assign/send-payment-link/refund calls to `orderService` (board query takes `workflowCode`, `sprintId`, optional `branchId`) + new `sprintService`; add `useOrderBoard`, an `useOrderBoardStream` (SSE) hook, sprint hooks, and block/resume/assign/complete/refund mutations (wrapped in `withToast`; errors auto-toast via the interceptor — no manual `toast.error`). Assignee dropdown lists **STAFF users** via `userService.list()` (filtered by role); branch list/selection reuses the fenced `branchService` / `useBranch`.

## 4. Contract card + manifest sync (same commit — the critical rule)

- **`backend/FOUNDATION_CONTRACT.md`**: update `## Auth & users` (`Role` gains `STAFF` + its permission scope); update `## Orders & checkout` (new `Order` fields incl. `@Version`, new endpoints incl. send-payment-link / refund / SSE stream) and `## Order workflow & connectors` (`WorkflowStage.targetMinutes`, `Workflow.staffRole`, `replaceWorkflow` stage-mapping migration); add a new `## Order board & sprints` section (`OrderSprint`, `OrderStageEvent`, `BlockReason`, `SprintStatus`, `SprintService`, `OrderBoardService`, board DTOs incl. `WorkflowStageMappingRequest`, branch-scoped board endpoint).
- **`frontend/FOUNDATION_CONTRACT.md`**: add board page/components, `sprintService`, board types to the orders/shell sections.
- **`foundation.manifest.json`**: add a separate `order-board` feature (`requires: ["orders", "branches"]`) with the new fenced symbols, frontend modules/components, pages (the `ADMIN_ORDERS` page's component becomes the board), and any new guards.

## 5. Branches & remote monitoring

Branches already exist in the foundation; the board just adds **branch as a scope/filter axis** so a client can watch operations from home or anywhere — the board is a standard ADMIN-gated web page, so "remote monitoring" needs no extra infra beyond making it branch-aware.

- **Branch picker in the header** (reuses fenced `BranchPicker` / `useBranch`), with an **"All branches"** option. "All branches" = a single-screen view for an owner overseeing every outlet at once; selecting one outlet scopes the whole board (columns, counts, cards, sprint progress) to it.
- Orders already carry `branchId`/`branchName`, so `OrderBoardService` filters/groups by branch with no schema change; `OrderBoardDto` carries **per-branch card counts** for the all-branches view.
- `GET /orders/board` takes an optional `branchId` (unset = all).
- **Sprint scope decision:** sprints stay **per workflow** (not per branch); branch is a *filter* within a sprint. This keeps one operational cadence across outlets and matches the "watch all outlets from one screen" goal. (Per-branch sprints can be layered later if an outlet needs its own cadence — flagged, not built now.)
- **Live refresh (now in scope — §9.3):** the board subscribes to an **SSE** stream (`GET /orders/board/stream`) so remote viewers see moves in near-real-time; React Query `refetchInterval` polling is the fallback. Optimistic locking (`Order.@Version`) keeps concurrent moves safe.

## 6. Workflow edits & in-flight order migration (gap found)

**Problem:** `WorkflowService.replaceWorkflow()` currently **hard-deletes and recreates all stages with no order remapping** (`backend/.../service/WorkflowService.java:197`). Since `Order.currentStageCode` is a stored reference (same model as a Jira issue status), removing/renaming a stage **code** that live orders occupy strands them — their `currentStageCode` no longer resolves and the board can't place the card in any column.

**Fix (Jira-style "publish requires status mapping"):**
- On save, **diff** old→new stage codes and compute the **removed** set.
- Additive changes / label-only renames (code unchanged) → no mapping needed, live orders untouched.
- If any **live** (non-terminal) order sits in a removed code, **require** a `stageMapping` (`removedCode → survivingCode`) in the save request; reject with `409` otherwise. (Minimal first cut: refuse to drop a stage that has live orders until drained/remapped.)
- Apply the remap as a **migration, not a transition**: bulk re-stamp `currentStageCode`, **do not fire connector bindings**, set `stageEnteredAt` sensibly, append an `OrderStageEvent` flagged as a migration, and **audit** it.
- Optionally keep a backup copy of the prior stage set for rollback.

## 7. Behavior / guard notes

- Backend stays permissive on which stage an admin may move to (admin override); the **next-stage-only** rule is enforced in the board UI via valid drop targets. The payment guard (terminal + unpaid → 409) is the one new server-side constraint.
- Blocked pauses the timer by freezing `blockedAt`; resume shifts `stageEnteredAt` forward by the blocked duration so a single timestamp field models a pausable timer.
- New orders auto-join the active sprint of their workflow at `placeOrder` (null if none).

## 8. Additional gaps & decisions (resolved)

1. **Admin "New order" (offline creation)** — *Decision:* the button **redirects admin/staff to the menu/products (catalog) page** and reuses the existing checkout; no new create-order endpoint in this pass. *Follow-up (flagged, not built now):* a true offline path (choose an on-behalf-of customer, `PAY_AT_COUNTER`, counter settlement) if walk-in entry needs to skip the storefront checkout.
2. **Staff identity & authorization** — *Decision:* add a **`STAFF` role** distinct from ADMIN/SUPER_ADMIN. STAFF can **view the board and change ticket status** (advance stage, block/resume) and nothing else; `SecurityConfig` enforces a narrow allowlist, and the board is STAFF's only screen. Assignees are STAFF users.
3. **Concurrency + live updates** — *Jira model:* optimistic locking makes transitions atomic and rejects stale writes; boards refresh via polling on top. *Decision:* mirror it — `Order.@Version` + `409` on stale moves, **plus an SSE stream** for push so staff/owners don't wait for a refresh (polling fallback).
4. **Schema evolution & sprint bootstrap** — *Decision:* boot backfill (`stageEnteredAt`, `blocked`, `carriedForward`), enforce **≤1 ACTIVE sprint per workflow**, auto-create a first sprint, and adopt orphan (null-sprint) orders. (Details in §2 "Data evolution / backfill".)
5. **Missing spec actions** — *Decision:* add **Send payment link** and a **standalone Refund** (decoupled from cancel) as new `OrderService` methods + endpoints (ADMIN-only); **Print receipt** is a client-side print view (no backend).
6. **Smaller edge cases** — all included: sprint completion must handle **non-carried open orders** (force-carry or block completion — flagged as a product toggle); **never leak** board/internal fields (`blocked`, `assignedStaffName`, `sprintId`) into the customer `OrderDto`; **block guard** forbids blocking a terminal order; **timeline** shows stage events (block/assign live in `audit_log`); **board pagination/virtualization** for large sprints.

## 9. Sequencing (each commit keeps cards/manifest in sync)

1. **`STAFF` role** + `SecurityConfig` allowlist + auth card/manifest sync (§8.2).
2. Backend model changes (incl. `Order.@Version`) + `OrderStageEvent` / `stageEnteredAt` wiring + **backfill** + card/manifest (§8.4).
3. Block/resume/assign + payment guard + **send-payment-link + standalone refund** + optimistic-lock `409` + audit + card (§8.5).
4. **Workflow-edit migration** — `replaceWorkflow` stage-mapping + `WorkflowStageMappingRequest` + audit + card (§6).
5. Sprints (entity, service, controller, carry-forward, ≤1-active invariant, bootstrap) + card.
6. Board read model + endpoint (workflow/sprint/**branch** scope) + **SSE stream** + DTOs + card.
7. Frontend board page/components/DnD (`@dnd-kit/core`) + **branch picker** + **SSE live updates** + **STAFF-scoped UI** + "New order" redirect + types/services/hooks + frontend card + manifest.
8. Seed retail/services workflows + demo data.
