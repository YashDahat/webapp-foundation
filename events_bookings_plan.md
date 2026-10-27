# Events & Bookings — Implementation Plan

> Two new **sellable types** added on top of the existing fenced commerce spine (catalog + inventory +
> order workflow), to cover the discovery-platform listing capabilities **"events"** and
> **"reservations / booking"** (restaurants, clinics, gyms & studios, bookstores). Integration-style
> features (WhatsApp/Instagram/Delhivery) are **out of scope** here — those stay `*Connector` beans.
>
> **Guiding principle (per `CLAUDE.md`):** a new sellable type = **ONE `CatalogItemResolver`** (+ at most
> **ONE `InventoryProvider`**) and a **DB-configured order workflow** — never reimplement the catalog,
> order, checkout, inventory, or refund engines, and never hardcode a lifecycle.

## 1. Approach — one spine, two shapes

Both features are sellables referenced polymorphically by `(itemType, itemId)`; purchase/registration is an
`Order` that runs a DB-configured workflow. The **only** real difference between them is capacity modelling:

| | **Event** (`itemType="EVENT"`) | **Booking** (`itemType="BOOKING"`) |
|---|---|---|
| Nature | A **fixed dated** item with **finite capacity** | A service the customer books into a **chosen time slot** |
| Capacity | Seats-as-stock (a number) | Slot availability against a resource's calendar |
| New engine? | **None** — reuse `InventoryService` (seats = tracked stock) | A small **availability/slot layer** (the genuinely new part) |
| Examples | Workshop, author meet, class, gig, screening | Table reservation, clinic appointment, trainer session, chair |

So **Events ship first** (they're ~a `Product` with a date + capacity), and **Bookings** reuse all of the
Events scaffolding plus an availability engine.

### Reuse as-is (no engine changes)

| Feature | Existing foundation piece |
|---|---|
| Register / buy a ticket / book = an order | `OrderService.placeOrder` → `WorkflowEngine` |
| Lifecycle (confirm, complete, cancel, refund) | DB `Workflow` of stages; stage `cancellable` + `refundPolicy`; `OrderService.cancel()` |
| Payment (UPI/card/netbanking, ₹0, pay-at-counter) | `PaymentService` + gateway strategy (Razorpay) |
| Capacity / no-oversell (sold-out) | `InventoryService` reserve/commit/release (409 on shortfall) |
| Ticket tiers / party size | variants (`ProductVariant`-shape: label + price + stock) |
| Venue / location | `Branch` (orders already run through a branch) |
| Confirmation + reminder emails | `email` connector bound to the stage |
| Post-event / post-visit feedback | `feedback` (1–5, one per order) |
| Type-specific public fields (`startsAt`, `venue`, `slot`) | `CatalogItemDto.attributes` (`Map<String,Object>`) — **no DTO/contract change** |
| Admin list + CRUD + bulk upload | `AdminDataGrid` + `BulkUploadDialog` + `SpreadsheetParser` |
| Recommendations ("you may also like this event") | `RecommendationService` (polymorphic, already) |

### Genuinely new (needs same-commit contract-card + manifest sync)

1. **`Event` entity + `EventCatalogResolver`** (`itemType="EVENT"`) — dated sellable, capacity = stock.
2. **`EventInventoryProvider`** (`itemType="EVENT"`) — seats-as-stock (near-copy of `ProductInventoryProvider`).
3. **`Bookable`/`BookableService` entity + `BookingCatalogResolver`** (`itemType="BOOKING"`).
4. **Availability layer** — `Resource`, `AvailabilityRule` (+ blackout exceptions), `Reservation`, and an
   **`AvailabilityService`** that computes free slots. This is the one non-trivial new engine.
5. **`BookingInventoryProvider`** (`itemType="BOOKING"`) — a **slot** is the inventory unit; `variantKey`
   encodes `(resourceId, startAt)`, so reserve/commit/release ride the existing SPI unchanged.
6. **Date/slot gating + auto-close sweeps** — stop sales after start/cutoff; a scheduled job advances past
   events/bookings to a terminal stage (same pattern as the existing reservation-TTL sweep).
7. Two seeded workflows: **`events`** and **`bookings`** (the latter may extend the already-seeded
   `services` workflow — confirm in §9).

---

## 2. Phase 1 — Events

### 2.1 Backend

**Model**
- `@Entity Event { Long id; String name; String slug; String description; BigDecimal price; Instant startsAt;
  Instant endsAt; Long venueBranchId; Integer capacity; Integer reserved; boolean stockTracked /*=true*/;
  List<EventTier> tiers; String imageUrl; List<String> images; List<String> features; String category;
  boolean active; String workflowCode /*="events"*/; Instant salesCloseAt; Instant createdAt; Instant updatedAt; }`
- `@Entity EventTier { Long id; String label; String sku; BigDecimal price; Integer stock; Integer reserved; }`
  (mirrors `ProductVariant` — GA / VIP / Early-bird, each with its own seat stock).
- Capacity reuses the stock fields (`capacity`/`reserved`, or per-tier `stock`/`reserved`).

**Resolver + inventory**
- `@Component EventCatalogResolver implements CatalogItemResolver` → `itemType()="EVENT"`; `resolve(ids)`
  returns `CatalogItemDto` with `detailPath="/events/{slug}"`, `cartable` = (no tiers && on sale && not full),
  and `attributes = { startsAt, endsAt, venue, soldOut }`.
- `@Component EventInventoryProvider implements InventoryProvider` → `itemType()="EVENT"`; seats-as-stock,
  atomic reserve with 409 on shortfall (near-copy of `ProductInventoryProvider`; `variantKey` = tier).

**Date gating + sweep**
- `availableAt` / `cartable` additionally gate on `now < salesCloseAt` (default `salesCloseAt = startsAt`).
- `@Scheduled EventCloseSweep`: advance orders whose event `endsAt < now` to the terminal stage
  (`ATTENDED`/`COMPLETED`) via `OrderService.advanceStage` — reuse the TTL-sweep pattern, no new lifecycle.

**Service / endpoints**
- `EventService` mirroring `ProductService`: `listActive(category,q,branchId)` (upcoming first, hide past
  unless asked), `getBySlug/getById`, admin `create/update/delete`, `bulkImport` (CSV:
  `name,price,category,startsAt,endsAt,capacity,venueBranchSlug,workflowCode`).
- Public: `GET /api/v1/events(?category=&q=&branchId=)`, `/events/{id}`, `/events/slug/{slug}`.
- Admin: `GET/POST/PUT/DELETE /api/v1/admin/events` + `/bulk-upload`.
- **Workflow seed** `events`: `REGISTERED(initial) → CONFIRMED(onPaidEntry, email.customer+email.store) →
  ATTENDED(terminal)` + `CANCELLED`; `REGISTERED`/`CONFIRMED` `cancellable` with a `refundPolicy`
  (e.g. FULL up to cutoff, then NONE). Seed only when the workflow table is empty (existing seed rule).

### 2.2 Frontend
- Public pages: `EventsPage` (`/events` — upcoming grid: date/venue/price/"Sold out") and
  `EventDetailPage` (`/events/:slug` — date/venue/tiers, "Get tickets"/"Register", capacity banner).
  Reuse the product grid/detail layout; tiers reuse the variant selector.
- Admin: `/admin/events` — `AdminDataGrid` (incl. inactive/past) + `BulkUploadDialog` + create/edit panel
  (date/venue/capacity/tiers via the shared stepper sheet). Audit `EVENT_CREATED/UPDATED/DELETED`.
- Types/services/hooks: `@/types/event`, `@/services/eventService`, `@/hooks/eventHooks` (mirror product).

### 2.3 Event detail / sign-up screen — visual design

`EventDetailPage` (`/events/:slug`) is an **editorial split**: a poster-style event panel on the left, a
"Reserve your seat" form on the right. (Reference mockup; brand colour = the project's `primary` token, so
it adapts per site and to light/dark — do **not** hardcode the red.)

```
┌───────────────────────────────────────────────┬───────────────────────────────┐
│  YOUR COMPANY · EVENTS          FREE · 40 SEATS │  Reserve your seat            │
│ ┌─────────────────────────────────────────────┐│  Sign-up closes Wed 12 Nov,   │
│ │  [ primary-colour hero band ]               ││  11:59 pm.                    │
│ │                                             ││ ───────────────────────────── │
│ │   Autumn                                    ││  Full name                    │
│ │   Tasting Night      ← giant display type   ││  [ Alex Morgan              ] │
│ └─────────────────────────────────────────────┘│  Email                        │
│  ██ 14  STARTS        →   ██ 14  ENDS           │  [ you@example.com          ] │
│  Fri, Nov 2026 6:30pm     Fri, Nov 2026 9:30pm  │  Number of guests             │
│                                                 │  [ 1 ][ 2 ][ 3 ][ 4 ]         │
│  An evening with our kitchen team… description  │  ┌───────────────────────────┐│
│ ─────────────────────────────────────────────── │  │  Sign up                → ││
│  01 Fill in    02 Confirm    03 Show up         │  └───────────────────────────┘│
│     the form      by email      (no ticket)     │                               │
└───────────────────────────────────────────────┴───────────────────────────────┘
```

**Left panel (event poster)**
- **Top meta row:** `"{brandName} · EVENTS"` (left) and a status chip `"{price-or-FREE} · {seatsLeft} SEATS"`
  (right). FREE when `price == 0`; `seatsLeft` = computed `available` from `InventoryService`.
- **Hero band** in `bg-primary` / `text-primary-foreground` with the **event title** in giant display type
  (`font-black`, very large, tight leading) — same bold register as the feedback takeover.
- **Date block:** big day-of-month numeral + `STARTS` / `ENDS` labels, full date + time, an arrow between
  them. Single-day events collapse to one block. Source: `startsAt` / `endsAt`.
- **Description** paragraph (`Event.description`).
- **"How it works" strip:** three numbered steps (`01 / 02 / 03`) with bold heading + one-line caption.
  Default copy for free events: *Fill in the form → Confirm by email → Show up*. Make the steps
  **config-driven** (`Event.signupSteps?`) with this default, since paid events differ (*Pick tickets → Pay →
  Get confirmation*).

**Right panel (sign-up / reserve)**
- Heading **"Reserve your seat"** + subline **"Sign-up closes {salesCloseAt}."** (hidden if no cutoff).
- **Full name** + **Email** — **prefilled from the auth profile** for a logged-in user (still editable; they
  land on the order as the contact). Logged-out: these double as the identity capture; on submit, route
  through the existing auth gate (`RequireAuth` on checkout) or create a lightweight guest contact — see
  decision §9.6.
- **Number of guests** — a segmented 1–4 selector = the order line **`qty`**, capped by `maxPerOrder`
  (default 4) **and** by `seatsLeft` (disable options above remaining capacity).
- **"Sign up"** — full-width primary button with a trailing arrow. Disabled until the form is valid /
  while submitting / when the event is sold out or past `salesCloseAt` (button then reads "Sold out" /
  "Sign-up closed").

**Behaviour mapping to the spine (no new engine)**
- **Free event** (`price == 0`): "Sign up" places a **₹0 order** for `qty` seats directly (reserve → the
  existing ₹0 path marks `PAID` and advances to the `onPaidEntry` stage → confirmation emails) and navigates
  to `/orders/:id`. This matches the mockup's "Confirm by email / Show up, no ticket to print."
- **Paid / multi-tier event:** the right panel instead shows **tier selection + qty**, and "Sign up" /
  "Get tickets" proceeds through the normal **cart → `/checkout` → payment** flow. Same page, two modes keyed
  off `price`/`tiers`.
- Oversell is still enforced by `EventInventoryProvider` (409 → "Only N seats left" toast).

**Responsive:** below `md`, the two panels **stack** (poster on top, form below); the Sign-up button becomes a
sticky bottom bar on mobile.

### 2.4 Paid / multi-tier sign-up variant (right panel)

Same page, same left poster — only the **right panel** changes when `price > 0` or the event has `tiers`.
Instead of name/email/guests it shows **tier cards + per-tier quantity**, a running subtotal, and a
"Get tickets" button that enters the normal cart → checkout → payment flow.

```
┌───────────────────────────────┐
│  Get your tickets             │
│  Sign-up closes Wed 12 Nov…   │
│ ───────────────────────────── │
│  ┌───────────────────────────┐│   ← one card per EventTier (variant)
│  │ Early bird          ₹499  ││     label · price (right)
│  │ 12 left          [ – 1 + ]││     remaining (= available) · qty stepper
│  └───────────────────────────┘│
│  ┌───────────────────────────┐│
│  │ General             ₹799  ││
│  │ 30 left          [ – 0 + ]││
│  └───────────────────────────┘│
│  ┌───────────────────────────┐│
│  │ VIP               ₹1,499  ││
│  │ Sold out        (disabled)││   ← tier at 0 available → disabled
│  └───────────────────────────┘│
│ ───────────────────────────── │
│  2 tickets             ₹1,298 │   ← running subtotal (sum of tier×qty)
│  ┌───────────────────────────┐│
│  │  Get tickets           →  ││   → cart → /checkout → Razorpay
│  └───────────────────────────┘│
└───────────────────────────────┘
```

- Each card = one `EventTier`; stepper capped by that tier's `available` **and** `maxPerOrder`.
- **Mixed tiers allowed** — one order, multiple lines (each line `(itemType="EVENT", itemId, variantKey=tierSku, qty)`).
- Name/email are collected at the **existing checkout** (prefilled for logged-in users), so this variant adds
  no identity capture of its own.
- Login-required event with free + paid tiers? Price-per-tier decides the line; the order total drives the
  ₹0-vs-payment branch. Single mode switch on the page keyed off `price`/`tiers`.

### 2.5 Admin — Events management & live load

Admins must see **demand in real time** — how full each event is, who's coming, and check people in. Most of
this **reuses the existing Order Board**: a registration/ticket **is an order on the `events` workflow**, so it
already appears on `/admin/orders` (kanban, SSE-live, branch-scoped, stuck/blocked flags). The event-specific
additions are a **capacity/occupancy** lens and an **attendee roster + check-in** the generic board doesn't have.

**`/admin/events` grid** (`AdminDataGrid`) — a **load column** per event:

```
Event                 Date          Venue       Load                 Left  Revenue   Status
Autumn Tasting Night  Fri 14 Nov    Main St     ▓▓▓▓▓▓▓▓░░ 32/40       8    —         On sale
Jazz Night            Sat 22 Nov    Downtown    ▓▓▓▓▓▓▓▓▓▓ 120/120     0    ₹95,880   Sold out
Kids Workshop         (past)        Main St     ▓▓▓▓▓░░░░░ 14/30       —    ₹11,186   Ended
```
Columns: Event · Date · Venue(branch) · **Load meter** (reserved/capacity) · Seats left · Revenue · Status
(`On sale` / `Sold out` / `Sales closed` / `Ended`). Row actions: Edit · **View roster** · Close sales ·
Cancel event (stage-aware refunds to all holders via the spine).

**Event roster drawer** (click an event) — the "current load" cockpit for one event:

```
Autumn Tasting Night · Fri 14 Nov, 6:30–9:30 pm · Main St
Reserved 32 / 40   ▓▓▓▓▓▓▓▓░░ 80%      8 seats left      Revenue —
Tiers:  Early bird 12/12 (full) · General 18/20 · VIP 2/8
Checked in: 0 / 32
──────────────────────────────────────────────────────────────
Attendee        Guests   Tier       Payment    Stage       Check-in
Alex Morgan        2      General    Paid       CONFIRMED    [  ]
Priya N.           1      VIP        Paid       CONFIRMED    [  ]
…
[ Export CSV ]   [ Message all ]   [ Close sales ]
```
- **Capacity meter + per-tier breakdown + revenue** computed from the event's orders (`InventoryService`
  reserved/available + order totals). Live via the same **SSE** the Order Board uses — the meter ticks up as
  people register.
- **Attendee list** = `AdminDataGrid` of that event's orders (name, guests=qty, tier, payment, current stage,
  order link). **Check-in** toggles door arrival → advances that order to **`ATTENDED`** (door-scan friendly;
  the mockup's "give your name at the door, no ticket to print").
- Bulk: Export CSV (door list), "Message all" (an `email`-connector blast), "Close sales" (flip `salesCloseAt`
  to now → public page shows "Sign-up closed").

**How load is actually handled (no new engine):**
- **Overflow / sold-out** is automatic — `EventInventoryProvider` rejects the (N+1)th seat with 409; the public
  card flips to "Sold out", the admin meter hits 100%.
- **Multi-outlet owner** watches every event's load at once via the board's **All-branches** scope + the grid
  load column; drill into one via branch filter.
- **Waitlist** (optional) — capture interest when sold out, auto-notify on a cancellation. New decision §9.7.

### 2.6 Contract + manifest (same commit)
- `backend/FOUNDATION_CONTRACT.md` → under `## Catalog & recommendations` (or a new `## Events` block):
  `Event`, `EventTier`, `EventCatalogResolver`, `EventInventoryProvider`, `EventService`, endpoints.
- `frontend/FOUNDATION_CONTRACT.md` → `## Events` module + the `/events`·`/events/:slug`·`/admin/events` routes.
- `foundation.manifest.json` → new `events` feature entry (`requires: [catalog, inventory, orders]`,
  controllers, packages, fenced symbols, frontend modules/pages).

---

## 3. Phase 2 — Bookings (availability engine)

### 3.1 Backend

**Model**
- `@Entity BookableService { Long id; String name; String slug; String description; BigDecimal price;
  Integer durationMinutes; Set<Long> resourceIds; Long venueBranchId; String imageUrl; List<String> images;
  String category; boolean active; String workflowCode /*="bookings"|"services"*/; Instant createdAt/updatedAt; }`
- `@Entity Resource { Long id; String name; Long branchId; int slotCapacity /*=1*/; boolean active; }`
  (table/practitioner/chair/room; `slotCapacity` = concurrent bookings per slot).
- `@Entity AvailabilityRule { Long id; Long resourceId; DayOfWeek day; LocalTime startTime; LocalTime endTime;
  int slotMinutes; }` + `@Entity AvailabilityException { Long id; Long resourceId; LocalDate date; boolean closed;
  LocalTime startTime; LocalTime endTime; }` (holidays/blackouts/one-offs).
- `@Entity Reservation { Long id; Long orderId; Long serviceId; Long resourceId; Instant startAt; Instant endAt;
  int partySize; ReservationStatus status; }` + `enum ReservationStatus { HELD, CONFIRMED, RELEASED }`.

**Availability + inventory**
- `AvailabilityService.openSlots(serviceId, resourceId?, LocalDate from, LocalDate to)`:
  generate candidate slots from `AvailabilityRule` (minus `AvailabilityException`), subtract slots whose
  `Reservation` count ≥ `Resource.slotCapacity`. Returns free `(resourceId, startAt)` slots.
- `@Component BookingCatalogResolver implements CatalogItemResolver` → `itemType()="BOOKING"`;
  `attributes = { durationMinutes, venue, resourceIds }`; `cartable=false` (a slot must be chosen first).
- `@Component BookingInventoryProvider implements InventoryProvider` → `itemType()="BOOKING"`;
  **a slot is the inventory unit** — `variantKey` encodes `(resourceId|startAt)`. `reserve` checks
  `slotCapacity − existing HELD/CONFIRMED` for that resource+slot, creates a `HELD` `Reservation`
  (409 on conflict/no-oversell); `commit` → `CONFIRMED`; `release` → `RELEASED`. **No SPI change.**

**Gating + sweep**
- No booking past its `startAt` (gate in the quote/placeOrder path).
- `@Scheduled BookingCompleteSweep`: advance bookings whose `endAt < now` to the terminal stage; releases
  stale `HELD` holds via the existing reservation-TTL path.

**Service / endpoints**
- `BookableService`(svc) mirroring `ProductService` (list/get/CRUD/bulk).
- Public: `GET /api/v1/bookables(?category=&q=&branchId=)`, `/bookables/{slug}`,
  `GET /api/v1/bookables/{id}/availability?resourceId=&from=&to=` → free slots.
  Booking is placed via the **existing** `POST /api/v1/orders` carrying the chosen slot in the line's
  `variantKey`/attributes (no new order path).
- Admin: `GET/POST/PUT/DELETE /api/v1/admin/bookables` + `/bulk-upload`;
  `.../admin/resources` CRUD; `.../admin/resources/{id}/availability` (rules + exceptions).
- **Workflow:** reuse the already-seeded `services` workflow if its stages fit, else seed `bookings`:
  `REQUESTED(initial) → CONFIRMED(onPaidEntry) → COMPLETED(terminal)` + `CANCELLED`, with a
  time-sensitive `refundPolicy` on the cancellable stages. (Decision in §9.)

### 3.2 Frontend
- Public: `BookablesPage` (`/book` — service grid) and `BookableDetailPage` (`/book/:slug` — pick
  resource + date → **slot picker** calling the availability endpoint → add chosen slot to cart → checkout).
- Admin: `/admin/bookables` (`AdminDataGrid` + bulk + CRUD); `/admin/resources` (resources +
  availability-rule/blackout editor). Audit `BOOKABLE_*`, `RESOURCE_*`, `AVAILABILITY_*`.
- Types/services/hooks: `@/types/booking`, `@/services/bookingService`, `@/hooks/bookingHooks`
  (incl. `useAvailability`).

### 3.3 Admin — Bookings schedule & load

Bookings have a **time dimension** events don't, so the operational "load" view is a **resource × time grid**
(who/what is occupied when), on top of the same reused Order Board for lifecycle. Three admin surfaces:

**`/admin/bookables`** — `AdminDataGrid` of services (name, duration, price, resources, active) + bulk + CRUD.

**`/admin/resources`** — resources + their availability. Per resource: name, branch, `slotCapacity`, and the
**availability-rule editor** (weekly open windows + slot length) plus **blackout** exceptions:

```
Resource: Dr. A · Main St · capacity 1          [ Active ]
Weekly hours:  Mon–Fri 09:00–17:00 · 30-min slots     [ + add rule ]
Blackouts:     Thu 20 Nov (closed) · 25 Dec (closed)  [ + add blackout ]
```

**`/admin/bookings-schedule`** — the live-load grid (default to today, branch-scoped):

```
Branch: [ Main ▾ ]      ◂  Fri 14 Nov  ▸      View: [ Day | Week ]
            09:00   09:30   10:00   10:30   11:00   11:30   12:00
 Dr. A      │▓Alex▓│       │▓Priya│       │ free │ free │░lunch░│
 Dr. B      │ free │▓Ravi ▓│▓Ravi ▓│ free │ free │ free │░lunch░│
 Table 4    │▓Party▓▓▓▓▓▓▓▓│ free │ free │▓Booked│ free │ free │
            └ occupied (click → booking)   free    ░ blackout/closed
```
- Cells come from `Reservation` rows over the resource's generated slots; **occupied** = a `HELD`/`CONFIRMED`
  reservation (click → booking detail/order), **free** = open slot, **shaded** = blackout/outside hours.
- Occupancy per resource/day is the booking equivalent of the event capacity meter — at a glance, how loaded
  each table/doctor/chair is. Live via the Order Board **SSE**; branch filter reuses `useBranch`.
- Lifecycle (confirm / cancel / refund / no-show) stays on the Order Board — a booking is an order on the
  `bookings`/`services` workflow; this grid is the **time-slot lens**, not a second lifecycle path.

### 3.4 Contract + manifest (same commit)
- Backend card: new `## Bookings` block — `BookableService`, `Resource`, `AvailabilityRule`,
  `AvailabilityException`, `Reservation`, `ReservationStatus`, `BookingCatalogResolver`,
  `BookingInventoryProvider`, `AvailabilityService`, endpoints.
- Frontend card: `## Bookings` module + `/book`·`/book/:slug`·`/admin/bookables`·`/admin/resources` routes.
- `foundation.manifest.json` → new `bookings` feature (`requires: [catalog, inventory, orders, branches]`).

---

## 4. Frontend placement & navigation

Everything hooks into **existing extension points** — no new shell, no structural changes.

### Customer side — "sign up for an event" / "book"

**Public routes** — add alongside `/products` in the `SiteLayout` block of `App.tsx`:

| Route | Page | Notes |
|---|---|---|
| `/events` | `EventsPage` | upcoming grid (date / venue / price / "Sold out") |
| `/events/:slug` | `EventDetailPage` | tiers + "Get tickets" / "Register" |
| `/book` | `BookablesPage` | service grid |
| `/book/:slug` | `BookableDetailPage` | resource + date → **slot picker** |

**Where it shows in the UI** — the header nav is **not** hardcoded; it is driven by
`siteConfig.header.navLinks` (per-project `src/config/siteConfig.ts`, typed by `shell/types.ts`). Surfaces:
- Header **`navLinks`** → add "Events" / "Book".
- Header **`ctaButton`** → purpose-built for "Get Tickets" / "Book a Table".
- Footer `quickLinks`, and an optional "Upcoming events" section on `HomePage`.

**Sign-up flow reuses the existing cart → checkout → orders path — nothing new:**
`EventDetailPage` → pick tier → **Add to cart** → `/checkout` (already `RequireAuth`, so logged-out users
are bounced to login/signup) → payment (or ₹0 / pay-at-counter) → `/orders/:id` (the readback card).
Tickets/bookings then live in the **existing `/orders`** history. Bookings are identical except the detail
page first requires choosing a slot.

### Admin side — "manage events / bookings"

**Left-nav** — add entries to the `NAV` array in `components/AdminLayout.tsx` (the comment there says
"Generated sites add their own links here"):

```ts
{ to: '/admin/events',    label: 'Events',    icon: CalendarDays,  testId: 'admin-nav-events' },
{ to: '/admin/bookables', label: 'Bookings',  icon: CalendarClock, testId: 'admin-nav-bookables' },
{ to: '/admin/resources', label: 'Resources', icon: Armchair,      testId: 'admin-nav-resources' }, // bookings only
```

**Admin routes** — add to the `/admin` block in `App.tsx`, each `RequireAdmin`-gated like Products:

| Route | Page | Pattern |
|---|---|---|
| `/admin/events` | `AdminEventsPage` | `AdminDataGrid` + `BulkUploadDialog` + create/edit `StepperSheet` |
| `/admin/bookables` | `AdminBookablesPage` | same grid/bulk/stepper pattern |
| `/admin/resources` | `AdminResourcesPage` | resources + availability-rule / blackout editor (bookings only) |

All three mirror `AdminProductsPage` (grid + bulk + audited CRUD). Events need no `/admin/resources` page —
an event is a single dated item with a seat count, not a per-resource calendar.

### Summary
- **Customer:** new public routes + a `navLink`/CTA in `siteConfig`; sign-up = existing cart → checkout → orders.
- **Admin:** a `NAV` entry + a `RequireAdmin` route per page; management = the existing `AdminDataGrid` pattern.

## 5. Seeds / demo data
- Events: 2–3 upcoming demo events (one with tiers, one free ₹0, one near-capacity to show "Sold out").
- Bookings: 1 demo service (e.g. 30-min consultation), 2 resources, weekday availability rules, one blackout.
- Both only when the respective tables are empty (match existing seed-on-empty convention).

## 6. Test cases (append to `docs/TEST_CASES.md`)
- **§15 Events:** grid/detail, tier select, sold-out (oversell → 409), sales-close gating, buy (paid/₹0/
  pay-at-counter), cancel+refund per stage, auto-close sweep, admin CRUD + bulk, recommendations.
- **§16 Bookings:** service detail, availability slots render, double-book guard (409), place booking,
  cancel+refund, blackout hides slots, auto-complete sweep, admin resource/availability CRUD + bulk.

## 7. Phasing / commits
1. **E1** — Events backend (entity, resolver, inventory provider, `events` workflow seed, endpoints) + card/manifest sync.
2. **E2** — Events frontend (public + admin + bulk) + §15 test cases.
3. **B1** — Bookings backend (entities, `AvailabilityService`, `BookingInventoryProvider`, resolver, workflow) + card/manifest sync.
4. **B2** — Bookings frontend (service + slot picker + admin resources/availability) + §16 test cases.

Each commit is self-contained and obeys the same-commit contract rule; rebuild + health-check after E2/B2.

## 8. Risk / why this stays on-contract
- No new order, checkout, payment, refund, or inventory **engine** — only new **resolver/provider beans** and
  DB workflows, exactly the extension seams the foundation prescribes.
- `CatalogItemDto.attributes` carries all type-specific public fields → the fenced item/cart/order DTOs are
  **unchanged**, so no downstream card drift beyond the two new `##` blocks.
- Overselling/double-booking is enforced by the existing `InventoryService` 409 path, not by hand.

## 9. Open decisions (confirm before coding)
1. **Bookings workflow:** reuse the already-seeded **`services`** workflow, or seed a dedicated **`bookings`** one?
2. **Resource selection:** let the customer pick a specific resource (e.g. a named stylist), or auto-assign
   any free resource for the slot?
3. **Refund policy granularity:** fixed per-stage policy (as today) vs time-based (e.g. full refund >24h before
   the event/booking). Time-based would be a small stage-policy extension — flag if wanted.
4. **Party size vs capacity** (restaurant tables): model party size as `qty` against `slotCapacity`, or as a
   tier? (Lean: `qty` = party size, `slotCapacity` = seats.)
5. **Events as recurring:** one `Event` row per session (default) vs sessions-as-tiers.
6. **Free-event sign-up identity:** require login (reuse `RequireAuth`, name/email prefilled) vs allow a
   **guest reservation** (name + email only, no account). The mockup implies a low-friction guest flow;
   supporting it means a guest-contact path on the ₹0 order. Lean: require login but prefill, unless guest
   sign-up is explicitly wanted.
7. **Waitlist:** ship a sold-out waitlist (capture interest, auto-notify on cancellation) in v1, or defer?
   Applies to both events (seats) and bookings (a freed slot). Lean: defer to a fast-follow — the capacity
   meter + 409 sold-out handling cover v1.
