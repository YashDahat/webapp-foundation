# Foundation Backend Contract (FENCED Java spine — ground truth)

These classes already exist and are **fenced** — never re-declare, re-implement, or edit them, and
never create your own `User` / `Role` / `SecurityConfig` / `PasswordEncoder` / auth or payment classes.
The base package is renamed per project, so **reference every type by its simple name** — import
resolution is automatic. Match every method signature and field exactly.

---

## Auth & users

```java
// Entity — table "_user"; implements UserDetails. email AND phone are both required + unique.
// getUsername() returns email (JWT subject), but login accepts email OR phone (see below).
class User {
  Integer id; String firstName; String lastName; String email; String phone; String password; Role role;
  boolean emailVerified;      // set true after the signup OTP is confirmed; login is blocked until then
  boolean twoFactorEnabled;   // email-OTP 2FA opt-in (otpCodeHash/otpExpiresAt/otpAttempts + tokenVersion are server-internal)
}
enum Role { SUPER_ADMIN, ADMIN, STAFF, USER }   // authority is the bare name; SUPER_ADMIN = single-holder, all ADMIN rights + can delete users
// STAFF = operational role below ADMIN. SecurityConfig grants it a NARROW /admin/** allowlist — only the order board:
//   GET /orders/board(+/stream), GET /orders/{id}, GET /orders/{id}/history, PUT /orders/{id}/stage, POST /orders/{id}/block|resume.
// STAFF may view the board and change ticket status (advance/block/resume) only — no cancel/refund/send-payment-link/create,
// no sprints/workflow/users, no other admin area. Order-board assignees are STAFF users.

interface UserRepository extends JpaRepository<User, Integer> {
  Optional<User> findByEmail(String email);
  Optional<User> findByPhone(String phone);
}

// UserService implements UserDetailsService — this is its ONLY public method.
// The argument is the login identifier: email OR phone (resolved in that order).
class UserService {
  UserDetails loadUserByUsername(String emailOrPhone);
}
// Signup is already provided by the /register endpoint below (creates USER + auto-login) — use it,
// don't build your own. For any other lookup, inject UserRepository directly; do not add methods to UserService.

// Signup DTO (fenced). email + phone are both required and unique.
class RegisterRequest { String firstName; String lastName; String email; String phone; String password; }

class JwtUtil {
  String  generateToken(UserDetails userDetails);
  String  generateToken(Map<String,Object> extraClaims, String subject);
  String  extractUsername(String token);
  Date    extractExpiration(String token);
  boolean validateToken(String token, UserDetails userDetails);
}

// @CurrentUser — resolves to the authenticated user's id (User.id, Integer), or null for a guest.
// This is the ONLY way a controller learns "who is the current user".
@interface CurrentUser {}   // usage: (@CurrentUser Integer userId)
```

**Identity / cross-reference convention (IMPORTANT):**
- Every user-related record (order, payment, booking, …) links to the user by **`Integer userId`**
  (= `User.id`, our logical unique key) — **never a UUID, never email/phone stored as the reference.**
- A domain controller obtains it with `@CurrentUser Integer userId` — never read the principal by
  hand, never `UUID.fromString(...)`, never a hardcoded id. For a guest it is null (but guest checkout
  requires login first, so user-owned writes always have a real `userId`).
- For "the current user's own rows", filter your repository by `userId`; do not expose an id path param.
- Email/phone are the user's **login + lookup keys only** — when you genuinely need to find a user by
  email or phone, use `UserRepository.findByEmail(...)` / `findByPhone(...)`; do not denormalize them
  onto domain rows as the link.

Auth endpoints (already implemented — do not rebuild). `LoginResponse` is returned by both `/login` and
`/register`: `LoginResponse { String token; boolean twoFactorRequired; boolean verificationRequired; String challengeId }`.
`token` set = logged in; `twoFactorRequired` = finish via /verify-2fa; `verificationRequired` = email
not verified, finish via /verify-signup. A 6-digit OTP is emailed for either challenge.

**Auth hardening (enforced):** repeated failed logins lock the account out (429) via `LoginRateLimiter`;
Login (access) tokens last 7 days by default (`jwt.expiration-ms` / `JWT_EXPIRATION_MS`, 604800000), so users stay signed in across
browser restarts. challenge / password-reset links are short-lived (`jwt.challenge-expiration-ms`, 15 min) and a reset
link is **single-use** (it carries the user's `tokenVersion`, which is bumped on reset). Every access
token embeds `tokenVersion`; a password change or reset bumps it and thereby **revokes all existing
tokens** (so a password change logs the user out everywhere).
- `POST /api/v1/auth/register` · body `RegisterRequest { firstName; lastName; email; phone; password }` → 201; always role USER, created **unverified**, emails an OTP and returns `LoginResponse { verificationRequired:true, challengeId }` (**no token**). 409 on duplicate email/phone; 400 on weak password (`PasswordPolicy`: ≥ 10 chars incl. a letter and a digit, and not a common password).
- `POST /api/v1/auth/verify-signup` · body `VerifyTwoFactorRequest { challengeId; code }` → verifies OTP, marks account verified, returns `AuthResponse { token }`.
- `POST /api/v1/auth/login` · body `AuthRequest { username; password }` — **username = email OR phone**. Unverified → `verificationRequired`; else 2FA on → `twoFactorRequired`; else `token`.
- `POST /api/v1/auth/verify-2fa` · body `VerifyTwoFactorRequest { challengeId; code }` → `AuthResponse { token }`.
- `POST /api/v1/auth/2fa/enable` · `POST /api/v1/auth/2fa/disable` — authenticated; toggle the current user's 2FA.
- `POST /api/v1/auth/change-password` · authenticated · body `ChangePasswordRequest { currentPassword; newPassword }` → 200 (400 if current wrong / new weak).
- `POST /api/v1/auth/forgot-password` · body `ForgotPasswordRequest { email }` → always 200; emails a reset link `<app.base-url>/reset-password?token=…` if the account exists.
- `POST /api/v1/auth/reset-password` · body `ResetPasswordRequest { token; newPassword }` → 200 (401 if link invalid/expired, 400 if weak).

```java
// OTP: issues/verifies the emailed 6-digit code (BCrypt-hashed, TTL + attempt-locked) used by both
// signup verification and 2FA login. Internal to the auth flow. OTP_TTL_MINUTES controls validity.
class OtpService { void issue(User u); boolean verify(User u, String code); }
// PasswordPolicy: min 10 chars incl. a letter + a digit, and not a common password; throws 400 on register/change/reset.
class PasswordPolicy { void validate(String password); }
```

### Admin users (ADMIN + SUPER_ADMIN)

```java
class UserDto { Integer id; String firstName; String lastName; String email; String phone;
               Role role; boolean emailVerified; boolean twoFactorEnabled; }   // never exposes the password
```
- `GET /api/v1/admin/users` — list all users.
- `PUT /api/v1/admin/users/{id}/role` · body `UpdateUserRoleRequest { Role role }` → 409 if assigning a 2nd SUPER_ADMIN.
- `DELETE /api/v1/admin/users/{id}` — **SUPER_ADMIN only** (403 otherwise); cannot delete self or a SUPER_ADMIN.
- `POST /api/v1/admin/users/bulk-upload` — multipart `file`; columns firstName,lastName,email,phone,password,role → `BulkUploadResult`.

Beans available to inject anywhere: `PasswordEncoder`, `AuthenticationManager`, `AuthenticationProvider`.
The single SUPER_ADMIN owner is seeded from the `admin.email` / `admin.password` properties (email-verified).

## Payments

Inject `PaymentService` into your domain service. Call `createOrder` to open a payment, `verify`
after client checkout, and update **your own** entity from the returned result. For async
reconciliation (webhooks), listen with `@EventListener` for `PaymentCapturedEvent`.

```java
class PaymentService {
  PaymentOrderResponse        createOrder(CreatePaymentRequest request);
  PaymentVerificationResponse verify(VerifyPaymentRequest request);
  void                        handleWebhook(String payload, String signature);
}

class PaymentCapturedEvent { String referenceId; String gatewayPaymentId; BigDecimal amount; }  // @EventListener target

// DTOs
class CreatePaymentRequest        { BigDecimal amount; String currency; String referenceId; }   // amount in MAJOR units; referenceId opaque, e.g. "order_42"
class PaymentOrderResponse        { String gatewayOrderId; String gatewayKeyId; int amount; String currency; Long paymentRecordId; }
class VerifyPaymentRequest        { String gatewayOrderId; String gatewayPaymentId; String signature; }
class PaymentVerificationResponse { boolean verified; String status; String referenceId; }

// Entity (owned by the payment spine — NO foreign key to domain entities; link via referenceId string)
class Payment { Long id; String referenceId; String gatewayOrderId; String gatewayPaymentId;
                BigDecimal amount; String currency; PaymentStatus status; Instant createdAt; }
enum PaymentStatus { CREATED, CAPTURED, FAILED }

interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
  Optional<Payment> findByReferenceId(String referenceId);
}
```

Endpoints (already implemented): `POST /api/v1/payments/verify`, `/webhook`. There is **no public
create-order endpoint** — open a payment order server-side via `PaymentService.createOrder` (the order
spine does this in `OrderService.placeOrder`), so the amount is never client-chosen.
`PaymentGateway` is the provider adapter — call payments through `PaymentService`, never the gateway directly.

**Security (enforced — do not bypass):** `verify` requires an authenticated user; `/webhook` is public
but verifies its own signature and **fails closed** (no secret ⇒ rejected). Set a
payment's `amount` **server-side** from your entity's authoritative total — never from the client. A
consumer reacting to `PaymentCapturedEvent` MUST confirm `event.amount` equals its own expected total
before settling; the fenced order spine refuses to mark an order paid on a mismatch (see
`OrderService.onPaymentCaptured`, which audits `ORDER_PAYMENT_MISMATCH`).

## Media library + gallery (image storage)

ONE media library is already implemented and wired — admins upload/manage ALL images at `/admin/media`;
bytes live in object storage (MinIO/S3 via `StorageService`). The public gallery is a VIEW over it: an
image is published to the gallery by an admin flag (`showInGallery` + section/event), not a separate
store. Do NOT rebuild any of this and do NOT add your own upload/storage.

```java
class MediaAsset    { Long id; String objectKey; String filename; String contentType; Long sizeBytes;
                      String label; Boolean showInGallery; GallerySection section; String eventName;
                      LocalDate eventDate; Integer sortOrder; Instant uploadedAt; }
class MediaAssetDto { Long id; String url; String filename; String contentType; Long sizeBytes;
                      String label; Boolean showInGallery; GallerySection section; String eventName;
                      LocalDate eventDate; Integer sortOrder; Instant uploadedAt; }   // url = /api/v1/media/<key>
enum GallerySection { WEBSITE, EVENT }
class GalleryItemDto { Long id; String url; String caption; GallerySection section;
                       String eventName; LocalDate eventDate; Integer sortOrder; }   // public gallery view
interface StorageService { String store(MultipartFile file, String prefix); StoredObject load(String key); void delete(String key); }
```

Endpoints (already implemented):
- `GET /api/v1/media/{key}` (public, streams the image)
- `GET /api/v1/gallery(?section=WEBSITE|EVENT)` (public — the media flagged showInGallery)
- `GET/POST/PUT/DELETE /api/v1/admin/media` (ADMIN — POST/PUT are multipart: file + label +
  showInGallery/section/eventName/eventDate/sortOrder)

**Entity-image pattern** (a Trainer's photo, a product image): give your entity a plain `String photoUrl`
(or `imageUrl`) field, have the admin upload in the media library, store the returned `/api/v1/media/<key>`
url on the entity, and render `<img src={entity.photoUrl}>`. Never store bytes on the entity and never
add your own upload endpoint.

**Multiple images per entity** (a product gallery, event photos): store an ordered list of media URLs —
```java
@ElementCollection
@CollectionTable(name = "<entity>_images", joinColumns = @JoinColumn(name = "<entity>_id"))
@OrderColumn(name = "image_order")
@Column(name = "image_url")
private List<String> images = new ArrayList<>();
```
plus an optional `String imageUrl` primary/thumbnail field. The admin manages these via the
`MultiImagePicker` component (frontend contract). Still just URLs into the one media library — no new
storage.

## Email

A reusable, non-blocking transactional-email spine. `EmailService` is auto-configured from
`spring.mail.*` / `SMTP_*` env; inject it anywhere. `@Async` is enabled (`AsyncConfig`), so domain
notifications run off the request thread after the transaction commits.

```java
class EmailService { boolean sendHtml(String to, String subject, String html); }   // NEVER throws — logs + returns false on failure
```

**Notification pattern (how to email on a domain change):**
1. Publish a Spring event from your service (e.g. `applicationEventPublisher.publishEvent(new FooHappenedEvent(dto))`).
2. Add an `@Async @TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` handler
   in the `notification/` package that builds inline HTML and calls `emailService.sendHtml(...)`.

The listener depends only on `EmailService` (+ repositories), so the direction is always
domain → notification — no bean cycle. Config is ready; **do not** re-declare `EmailService`,
`AsyncConfig`, or add a second `JavaMailSender`.

## Support (threaded customer tickets)

Threaded customer support: a customer opens a case (subject + first message); customer and STAFF
exchange messages. Email notifications (via `EmailNotificationListener`): every STAFF reply publishes
`SupportRepliedEvent` → emails the **customer**; every CUSTOMER message — case creation and every reply —
publishes `SupportCustomerMessageEvent` → emails the **admin** (`admin.email`). Owned by
`SupportService` — inject it; do NOT re-declare any of these.

```java
class SupportCase    { Long id; Integer userId; String subject; SupportStatus status; Instant createdAt; Instant updatedAt; }
class SupportMessage { Long id; Long caseId; SupportAuthor author; String body; List<String> attachments; Instant createdAt; }   // one row per message; attachments = media-library image URLs
enum SupportStatus { OPEN, IN_PROGRESS, RESOLVED, CLOSED }
enum SupportAuthor { CUSTOMER, STAFF }

interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {
  List<SupportCase> findByUserIdOrderByUpdatedAtDesc(Integer userId);
  List<SupportCase> findAllByOrderByUpdatedAtDesc();
}
interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
  List<SupportMessage> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}

class SupportService {
  SupportCaseDto        open(Integer userId, CreateSupportCaseRequest req);   // creates case + first CUSTOMER message
  SupportCaseDto        reply(Integer userId, Long caseId, PostMessageRequest req);   // customer message (ownership-checked)
  List<SupportCaseDto>  myCases(Integer userId);
  SupportThreadDto      thread(Integer userId, Long caseId);                  // ownership-checked
  List<SupportCaseDto>  all();                                               // admin
  SupportThreadDto      adminThread(Long caseId);                            // admin
  SupportThreadDto      adminReply(Long caseId, AdminReplyRequest req);      // STAFF message + optional status; publishes SupportRepliedEvent
}
class SupportRepliedEvent         { Long caseId; Integer userId; String subject; String replyBody; SupportStatus status; }  // STAFF reply → email customer
class SupportCustomerMessageEvent { Long caseId; Integer userId; String subject; String messageBody; boolean newCase; }     // CUSTOMER msg → email admin

// DTOs
class SupportCaseDto    { Long id; Integer userId; String customerName; String customerEmail;
                          String subject; SupportStatus status; Instant createdAt; Instant updatedAt; }
class SupportMessageDto { Long id; Long caseId; SupportAuthor author; String body; List<String> attachments; Instant createdAt; }
class SupportThreadDto  { SupportCaseDto supportCase; List<SupportMessageDto> messages; }
class CreateSupportCaseRequest { String subject; String message; List<String> attachments; }   // attachments optional
class PostMessageRequest       { String body; List<String> attachments; }                        // attachments optional
class AdminReplyRequest        { String body; SupportStatus status; List<String> attachments; }  // status + attachments optional
```

**Image attachments:** a message can carry `attachments` — media-library image URLs. Upload each file first
(returns a `MediaAssetDto` whose `url` you put in `attachments`). Customers can't use `/admin/media`, so
support has its own authenticated upload endpoint.

Endpoints (already implemented):
- Customer (`@CurrentUser Integer userId`, requires login): `POST /api/v1/support`, `GET /api/v1/support`,
  `GET /api/v1/support/{id}`, `POST /api/v1/support/{id}/messages`,
  `POST /api/v1/support/attachments` (multipart `file` → `MediaAssetDto`; any logged-in user).
- Admin (ADMIN): `GET /api/v1/admin/support`, `GET /api/v1/admin/support/{id}`, `POST /api/v1/admin/support/{id}/reply`.

## Bulk upload (admin CSV/XLSX import)

**COMPULSORY:** every admin entity MUST expose `POST /api/v1/admin/<resource>/bulk-upload` (multipart
`file`) returning `BulkUploadResult`. Parse the file with the fenced `SpreadsheetParser`, map each row
to your entity in the entity's own service (`bulkImport(MultipartFile) -> BulkUploadResult`), and
accumulate a per-row error via `result.addError(rowNumber, message)`.

```java
class SpreadsheetParser {
  // Parses .csv (Commons CSV) and .xlsx (Apache POI) into rows keyed by trimmed, lower-cased headers.
  List<Map<String,String>> parse(MultipartFile file);
}
class BulkUploadResult {                       // @Builder; returned by every bulk-upload endpoint
  int created; int skipped; int failed; List<RowError> errors;
  void addError(int row, String message);      // appends a RowError and increments failed
  class RowError { int row; String message; }  // static nested; row = 1-based file row (data starts at 2)
}
```

`SpreadsheetParser` and `BulkUploadResult` are fenced — do NOT re-declare them. The per-entity
`bulkImport` method + `/bulk-upload` endpoint are generated per project (entities are per-project).

## Audit logging (user activity)

Every request is auto-logged (an access line with a correlation `requestId`, IP, method, path, status,
duration — `AuditLogFilter`). For **security-sensitive actions**, also record a domain audit event via
`AuditService`: it writes to the durable `audit_log` table (the SQL source of truth) AND to structured
JSON logs shipped to Loki/Grafana. `AuditService` **never throws** (an audit failure can't break the flow).

```java
class AuditService {
  // Actor resolved from the security context (or guest):
  void record(String action, String outcome, String targetType, String targetId, String detail);
  // Explicit actor (flows where the principal isn't set, e.g. a failed login):
  void recordActor(Integer userId, String actorEmail, String actorRole,
                   String action, String outcome, String targetType, String targetId, String detail);
}
class AuditActions { /* LOGIN_SUCCESS, LOGIN_FAILURE, USER_DELETED, …; SUCCESS / FAILURE */ }
class AuditEvent { Long id; Instant createdAt; Integer userId; String actorEmail; String actorRole;
                   String action; String outcome; String targetType; String targetId;
                   String ipAddress; String requestId; String detail; }   // table "audit_log"
```

**Rules:** inject `AuditService` and record an event for any privileged/sensitive action your code adds
(create/update/delete of important entities, permission changes, exports). Use your own action string if
needed. **NEVER put secrets in an audit event** — no passwords, tokens, OTP codes, or full card/PII data.
Admins read the log at `GET /api/v1/admin/audit(?action=&actorEmail=&limit=)` (→ `AuditEventDto`).
Do NOT re-declare `AuditService`, `AuditEvent`, `AuditLogFilter`, or `AdminAuditController`.

## Catalog & recommendations

The commerce spine is **polymorphic**: everything sellable maps to one `CatalogItemDto` contract, referenced
by `(itemType, itemId)`. The **framework** (`CatalogItemDto`, `CatalogItemResolver`, `CatalogService`,
`RecommendationService`) is fenced; the concrete default `Product` / `CatalogRecommendation` entities are
**batteries-included defaults** a project may extend. A new sellable type = ONE resolver bean, zero engine edits.

```java
// Universal "sellable" exchange shape — shared by cart, recommendations, and orders.
class CatalogItemDto { String itemType; Long itemId; String name; String description; BigDecimal price;
  String priceUnit; String imageUrl; List<String> images; String slug; String detailPath; String category;
  boolean active; boolean cartable; String workflowCode; Map<String,Object> attributes; }

// Per-business adapter — ONE @Component per sellable type. ProductCatalogResolver (itemType="PRODUCT") ships.
interface CatalogItemResolver { String itemType(); List<CatalogItemDto> resolve(Collection<Long> ids); }  // active items only

// Collects all resolver beans; unknown itemType → skipped (logged), never throws.
class CatalogService {
  boolean supports(String itemType);
  List<CatalogItemDto> resolve(String itemType, Collection<Long> ids);
  CatalogItemDto resolveOne(String itemType, Long itemId);          // null if no active match
  List<CatalogItemDto> resolveMixed(Collection<ItemRef> refs);      // groups by type, fans out
  record ItemRef(String itemType, Long itemId) {}                   // nested — the shared polymorphic ref
}

// Default sellable (extend/replace per project). branchScope GLOBAL (default) or SELECTED availableBranchIds;
// stockTracked default false = unlimited; variants make it cartable=false (pick a size on the detail page).
class Product { Long id; String name; String slug; String description; BigDecimal price;
  BranchScope branchScope; Set<Long> availableBranchIds; boolean stockTracked; boolean stockPerBranch;
  Integer stock; Integer reserved; List<ProductVariant> variants; String imageUrl; List<String> images;
  List<String> features; String category; boolean active; String workflowCode; Instant createdAt; Instant updatedAt; }  // features = highlight bullets shown on the detail page
enum BranchScope { GLOBAL, SELECTED }
class ProductVariant { Long id; String label; String sku; BigDecimal price; boolean stockTracked; Integer stock; Integer reserved; }
class BranchStock   { Long id; Long productId; Long variantId; Long branchId; Integer stock; Integer reserved; }  // unique (productId,variantId,branchId)
class ProductDto    { /* admin/detail shape + computed `available`; variants each with computed `available` */ }
class ProductService {
  List<ProductDto> listActive(String category, String q, Long branchId);   // branchId → only items offered there
  ProductDto getById(Long id); ProductDto getBySlug(String slug);          // ResourceNotFoundException on miss
  ProductDto create(UpsertProductRequest); ProductDto update(Long, UpsertProductRequest); void delete(Long);
  BulkUploadResult bulkImport(MultipartFile);   // columns: name,price,category,stockTracked,stock,workflowCode,branchSlugs
}

// Recommendations — referenced polymorphically by (itemType,itemId); score orders the results (desc).
class CatalogRecommendation { Long id; String sourceItemType; Long sourceItemId; String targetItemType;
  Long targetItemId; RecommendationType relationType; Double score; boolean active; Instant createdAt; Instant updatedAt; }  // unique (source*,target*,relationType)
enum RecommendationType { UPSELL, CROSS_SELL }
class RecommendationDto { /* admin view + resolved sourceItemName / targetItemName */ }
class RecommendationService {
  List<CatalogItemDto> getRecommendations(String itemType, Long itemId, RecommendationType type, Long branchId);  // score-ordered, branch-filtered
  // + admin CRUD + bulkImport
}
```
Endpoints — public: `GET /api/v1/products(?category=&q=&branchId=)`, `/products/{id}`, `/products/slug/{slug}`,
`GET /api/v1/recommendations?itemType=&itemId=&type=&branchId=` (→ `CatalogItemDto[]`). Admin:
`GET/POST/PUT/DELETE /api/v1/admin/products` + `/bulk-upload`; `/api/v1/admin/recommendations` CRUD + `/bulk-upload`.

## Events

A dated, finite-capacity sellable (`itemType="EVENT"`) — a `Product` with a date + seats. Registration/
ticketing runs on the normal order spine: `EventCatalogResolver` maps it to `CatalogItemDto` and
`EventInventoryProvider` tracks seats via `InventoryService` (no new engine). Capacity is a shared pool
(`capacity` null = unlimited) or per `EventTier` (GA/VIP/Early-bird); sales stop at `salesCloseAt`
(defaults to `startsAt`). A free (₹0) event signs up directly through the ₹0 order path; paid/tiered events
go through cart → checkout. Binds to the **`default`** workflow unless the business generates a dedicated
`events` workflow (`REGISTERED → CONFIRMED(onPaidEntry) → ATTENDED` + `CANCELLED`) and points events at it.

```java
class Event { Long id; String name; String slug; String description; BigDecimal price;
  Instant startsAt; Instant endsAt; Instant salesCloseAt; Long venueBranchId;
  Integer capacity; Integer reserved; List<EventTier> tiers;                 // capacity null = unlimited; tiered = seats per tier
  String imageUrl; List<String> images; List<String> features; String category;
  boolean active; String workflowCode; Instant createdAt; Instant updatedAt; }  // blank workflowCode → "default"
class EventTier { Long id; String label; String sku; BigDecimal price; Integer stock; Integer reserved; }  // price null → inherit event price
class EventDto  { /* detail/admin shape + computed available, seatsLeft, onSale, soldOut, venueName; tiers each with computed available */ }
class EventTierDto { Long id; String label; String sku; BigDecimal price; Integer stock; Integer reserved; Integer available; }

// EVENT itemType key on CatalogConstants.EVENT. ONE resolver + ONE inventory provider, zero engine edits.
@Component class EventCatalogResolver implements CatalogItemResolver { }   // itemType "EVENT"; detailPath "/events/<slug>"; cartable = tier-less, on-sale, seats left; schedule/soldOut ride CatalogItemDto.attributes
@Component class EventInventoryProvider implements InventoryProvider { }   // seats-as-stock; variantKey = tier id; atomic reserve, 409 on shortfall; branchId ignored (seats global to the event)

class EventService {
  List<EventDto> listActive(String category, String q, Long branchId);     // active, soonest first; branchId → that venue (+ unscoped)
  EventDto getById(Long id); EventDto getBySlug(String slug);              // ResourceNotFoundException on miss
  EventDto create(UpsertEventRequest); EventDto update(Long, UpsertEventRequest); void delete(Long);
  BulkUploadResult bulkImport(MultipartFile);   // columns: name,price,category,startsAt,endsAt,capacity,venueBranchSlug,workflowCode
}
class UpsertEventRequest { String name; String slug; String description; BigDecimal price; String category;
  Boolean active; String workflowCode; Instant startsAt; Instant endsAt; Instant salesCloseAt; Long venueBranchId;
  Integer capacity; String imageUrl; List<String> images; List<String> features; List<TierInput> tiers;
  record TierInput { Long id; String label; String sku; BigDecimal price; Integer stock; } }
```
Endpoints — public: `GET /api/v1/events(?category=&q=&branchId=)`, `/events/{id}`, `/events/slug/{slug}`.
Admin: `GET/POST/PUT/DELETE /api/v1/admin/events` + `/bulk-upload`.

## Inventory

Overselling is prevented by a per-type SPI; unlimited items are a no-op. Reserve/commit/release **only** via
`InventoryService` — never decrement stock by hand. `ProductInventoryProvider` ships (`"PRODUCT"`),
`EventInventoryProvider` (`"EVENT"`).

```java
interface InventoryProvider {           // ONE @Component per itemType
  String itemType();
  boolean availableAt(Long itemId, Long branchId);
  void reserve(Long itemId, String variantKey, Long branchId, int qty);   // atomic; 409 (CONFLICT) on shortfall — no oversell
  void commit(Long itemId, String variantKey, Long branchId, int qty);
  void release(Long itemId, String variantKey, Long branchId, int qty);
}
class InventoryService {                 // collects providers by itemType
  boolean availableAt(String itemType, Long itemId, Long branchId);
  void reserve/commit/release(String itemType, Long itemId, String variantKey, Long branchId, int qty);
  boolean availableAll(List<OrderItem> items, Long branchId);              // the order spine's batch overloads
  void reserveAll/commitAll/releaseAll(List<OrderItem> items, Long branchId);
}
```

## Orders & checkout

A persisted, batteries-included order spine — the connector anchor. Inject `OrderService`; never hardcode
lifecycle steps or write your own order/checkout persistence. Re-priced server-side via `CatalogService`;
one workflow per order (mixed-workflow carts → 409). Links to the payment spine by `referenceId` string.

```java
class Order { Long id; Integer userId; String orderNumber; String referenceId; Long branchId; String branchName;
  FulfillmentType fulfillmentType; PaymentMode paymentMode; String workflowCode; String currentStageCode;
  PaymentState paymentState; BigDecimal subtotal; BigDecimal total; BigDecimal refundAmount; String cancelReason;
  String currency; Address shippingAddress; List<OrderItem> items; List<OrderAdjustment> adjustments;
  // order-board fields (admin kanban; NEVER leaked into the customer OrderDto — board DTOs carry them):
  Integer assignedUserId; String assignedStaffName; boolean blocked; BlockReason blockReason; String blockNote;
  Instant blockedAt; Instant stageEnteredAt; boolean carriedForward; Long sprintId; String channel;
  Long version;                                                            // @Version — optimistic lock; stale board move → 409
  Instant createdAt; Instant updatedAt; }                                  // table "orders"
class OrderItem { Long id; String itemType; Long itemId; String variantKey; String variantLabel; String name; BigDecimal unitPrice; int quantity; String imageUrl; }
class OrderAdjustment { String code; String label; BigDecimal amount; String connectorId; }   // embeddable
class Address { String contactName; String phone; String line1; String line2; String city; String state; String pincode; Double latitude; Double longitude; }  // embeddable snapshot (NOT a link)
enum FulfillmentType { DELIVERY, SELF_PICKUP }
enum PaymentMode { ONLINE, PAY_AT_COUNTER }          // PAY_AT_COUNTER only valid with SELF_PICKUP
enum PaymentState { AWAITING_PAYMENT, PAID, FAILED, REFUND_PENDING, REFUNDED }

class OrderService {
  OrderDto placeOrder(Integer userId, PlaceOrderRequest req);    // re-price, reserve, one-workflow, branch+address snapshot
  QuoteResponse quote(Integer userId, QuoteRequest req);
  List<OrderDto> myOrders(Integer userId); OrderDto get(Integer userId, Long id);   // ownership-checked
  List<OrderDto> all(); OrderDto getAdmin(Long id);
  OrderDto advanceStage(Long id, String stageCode);              // admin, trigger=ADMIN. Payment guard: unpaid → terminal non-CANCELLED stage = 409
  OrderDto markPaid(Long id);                                    // settle a pay-at-counter pickup
  OrderDto cancel(Long orderId, Integer actingUserId, String reason, boolean adminOverride);  // stage-aware refund
  // board mutations — guarded by Order.@Version (concurrent/stale move → 409 Conflict):
  OrderDto block(Long id, BlockReason reason, String note);      // keep stage, freeze timer; terminal order → 409
  OrderDto resume(Long id);                                      // shift stageEnteredAt by blocked duration (timer resumes)
  OrderDto assign(Long id, Integer userId);                      // assign STAFF user (null = unassign); snapshots name
  OrderDto sendPaymentLink(Long id);                             // email the owner a pay link for an AWAITING_PAYMENT order
  OrderDto refund(Long id, BigDecimal amount, String reason);    // standalone goodwill refund (NOT a cancel) → REFUND_PENDING via refund connector; null amount = full
  BulkUploadResult bulkImport(MultipartFile);
  // @TransactionalEventListener(AFTER_COMMIT) onPaymentCaptured(PaymentCapturedEvent): mark PAID, commit stock, advance to onPaidEntry (idempotent)
  // @Scheduled sweepAbandonedReservations(): release AWAITING_PAYMENT holds past `inventory.reservation-ttl-minutes`
}
// DTOs
class PlaceOrderRequest { List<CartLineInput> items; Long branchId; FulfillmentType fulfillmentType; PaymentMode paymentMode; Long addressId; Map<String,String> connectorSelections; }
class CartLineInput    { String itemType; Long itemId; String variantKey; int quantity; }   // NO prices — re-priced server-side
class QuoteRequest     { List<CartLineInput> items; Long branchId; FulfillmentType fulfillmentType; Long addressId; }
class QuoteResponse    { BigDecimal subtotal; List<Option> options; class Option { String connectorId; String code; String label; BigDecimal amount; String detail; } }
class OrderDto         { /* Order fields as DTOs + boolean cancellable; BigDecimal refundPreview; boolean feedbackSubmitted; PaymentOrderResponse payment (online awaiting payment only) */ }
```
Endpoints — customer (`@CurrentUser`): `POST /api/v1/orders`, `GET /api/v1/orders`, `GET /api/v1/orders/{id}`,
`POST /api/v1/orders/{id}/cancel`, `POST /api/v1/checkout/quote`. Admin: `GET /api/v1/admin/orders` (→ `List<OrderDto>`),
`GET /api/v1/admin/orders/{id}` (→ `OrderBoardDetailDto` — the board detail sheet),
`PUT /{id}/stage`, `POST /{id}/cancel`, `POST /{id}/mark-paid`, `POST /bulk-upload`, and board actions
`POST /{id}/block` · `POST /{id}/resume` · `PUT /{id}/assignee` · `POST /{id}/send-payment-link` · `POST /{id}/refund`.
`block`/`resume` are also permitted to `STAFF`; the rest stay ADMIN-only (see SecurityConfig). Board read endpoints
(`GET /{id}` · `GET /{id}/history` · `GET /board` · `GET /board/stream`) are detailed in the **Order board & sprints** section.

## Order workflow & connectors

The order lifecycle is a **DB-configured workflow** of stages, each with 0..N **connector actions** bound to
it — all editable at `/admin/workflow`, no engine edits. Attach external services (delivery, tax, refunds,
notifications) as **connector beans**; never call a third-party API inline from domain code.

```java
class Workflow      { Long id; String code; String label; boolean isDefault; String staffRole; }   // staffRole = board label (e.g. "Rider"); seeded: "default" ONLY — business workflows are generated per project (see CLAUDE.md "Generating the business workflow")
class WorkflowStage { Long id; String workflowCode; String code; String label; int ordinal;
  boolean initial; boolean onPaidEntry; boolean terminal;
  boolean cancellable; RefundPolicy refundPolicy; Integer refundPercent;
  Integer targetMinutes; }                                                         // board SLA: "stuck" = time-in-stage > targetMinutes; unique (workflowCode, code)
enum RefundPolicy { FULL, PARTIAL, NONE }
class WorkflowBinding   { Long id; Long stageId; String connectorId; String actionCode; int ordinal; boolean enabled; Map<String,String> params; }  // keyed by stage ID, not code
class WorkflowExecution { Long id; Long orderId; Long stageId; Long bindingId; String connectorId; String actionCode; ExecutionStatus status; String detail; Instant createdAt; Instant updatedAt; }  // dedupe ledger; unique (orderId,stageId,bindingId)
enum ExecutionStatus { PENDING, SUCCESS, FAILED }
enum TriggerType { SYSTEM, PAYMENT, WEBHOOK, ADMIN, RETRY }

class WorkflowEngine { boolean advance(Order order, String toStageCode, TriggerType trigger); }  // idempotent for SYSTEM/PAYMENT/WEBHOOK
class StageActionDispatcher { /* @Async @TransactionalEventListener(AFTER_COMMIT): fans a stage's enabled bindings to connectors; dedupes SUCCESS via WorkflowExecution */ }
class WorkflowService { /* seeds the neutral "default" workflow, enforces invariants, stage lookups, admin getWorkflows()/replaceWorkflow(), defineWorkflow(WorkflowDto) */ }
// defineWorkflow(WorkflowDto): the sanctioned path for GENERATED per-business workflow seeders (never write the
//   workflow/stage/binding tables directly). Idempotent — no-ops if the code already exists (safe every boot,
//   preserves admin edits); else inserts the workflow + stages + bindings and re-checks invariants. isDefault=true
//   makes it the new order default (clears the previous). See CLAUDE.md "Generating the business workflow".
// replaceWorkflow(code, WorkflowDto): WorkflowDto now carries staffRole + per-stage targetMinutes, and an optional
//   stageMapping (WorkflowStageMappingRequest{ Map<String,String> mapping }). Removing a stage CODE that live
//   (non-terminal) orders occupy REQUIRES mapping[removedCode]=survivingCode or the save is rejected 409; the
//   remap is a migration (bulk re-stamp, NO connectors fired, migration-flagged OrderStageEvent, audited).

// Connector SPI — connector/
enum ConnectorCategory { SHIPPING, TAX, DISCOUNT, PAYMENT, FULFILLMENT, NOTIFICATION, OTHER }
interface OrderConnector   { String id(); String displayName(); ConnectorCategory category(); boolean enabled(); Set<ConnectorAction> actions(); }
interface ActionConnector  extends OrderConnector { ConnectorResult execute(String actionCode, OrderStageContext ctx); }      // async, on stage entry
interface QuoteConnector   extends OrderConnector { List<QuoteOption> quote(CheckoutContext ctx); }                            // checkout-time
interface WebhookConnector extends OrderConnector { void handleWebhook(String payload, Map<String,String> headers); }         // inbound → may advance the stage
class ConnectorRegistry { OrderConnector byId(String id); List<OrderConnector> all(); List<QuoteConnector> quoteConnectors(); List<ActionConnector> actionConnectors(); }
// Context/result: OrderStageContext, CheckoutContext, OrderItemSnapshot, QuoteOption, ConnectorResult, ConnectorAction, ConnectorConfig.
// Shipped connectors: EmailConnector (id="email": email.customer/email.store), DemoDeliveryConnector (id="delivery.demo": quote + delivery.initiate + webhook), DemoRefundConnector (id="refund.demo": refund.issue).
```
A new connector = ONE `@Component` implementing the relevant SPI(s) + declaring `actions()`; it then appears in
`/admin/connectors` and the workflow builder automatically. Inbound: `POST /api/v1/connectors/{connectorId}/webhook`
(public, per-connector verified). Admin: `GET/PUT /api/v1/admin/workflow(/{code})`, `GET/PUT /api/v1/admin/connectors(/{id})`.

## Order board & sprints

An admin kanban over the order spine (board = `/admin/orders`). Columns are the workflow stages (+ synthetic
**Blocked** and **Cancelled**); moves drive `OrderService`/`WorkflowEngine` — never a hardcoded lifecycle.
The board fields live on `Order` (above) and are **never** exposed on the customer `OrderDto`.

```java
enum BlockReason { OUT_OF_STOCK, CUSTOMER_UNREACHABLE, PAYMENT_ISSUE, STAFF_UNAVAILABLE, ADDRESS_ISSUE, OTHER }

// Stage-history timeline (board detail panel). Appended by WorkflowEngine on every transition + at placement.
// Block/resume/assign are NOT recorded here — they live in audit_log. migration=true marks a workflow-edit remap.
class OrderStageEvent { Long id; Long orderId; String stageCode; Instant enteredAt; TriggerType trigger; Integer byUserId; boolean migration; }  // table "order_stage_event"
interface OrderStageEventRepository extends JpaRepository<OrderStageEvent,Long> { List<OrderStageEvent> findByOrderIdOrderByEnteredAtAsc(Long orderId); }

// WorkflowEngine.advance(...) now also: sets Order.stageEnteredAt = now (resets the time-in-stage/"stuck" timer)
// and appends an OrderStageEvent. placeOrder seeds the timeline with the initial stage.

// Board-mutation request DTOs (see OrderService.block/assign/refund):
class BlockOrderRequest  { BlockReason reason; String note; }
class AssignOrderRequest { Integer userId; }                       // null = unassign
class RefundOrderRequest { BigDecimal amount; String reason; }     // null amount = full total
```
A concurrent/stale board move (someone already moved the card) fails the `Order.@Version` check and surfaces as
**409 Conflict** (`GlobalExceptionHandler` maps `OptimisticLockingFailureException`) so the UI can refresh.

**Board read model** — `OrderBoardService` assembles the kanban over the order spine (read-only; moves still go
through `OrderService`/`WorkflowEngine`). Columns are the workflow stages in ordinal order (excluding the terminal
`CANCELLED` stage) + a synthetic **Blocked** column + the **Cancelled** column. A blocked order shows in Blocked
regardless of its stage; a cancelled one in Cancelled. `minutesInStage` freezes while blocked; `stuck` = past the
stage's `targetMinutes` (never while blocked/terminal). Scope is `(workflowCode, sprintId?, branchId?)` — null
`sprintId` = the active sprint, null `branchId` = **all branches** (and `branches[]` always carries the per-branch
split for the whole sprint).

```java
class OrderStageEventDto { Long id; String stageCode; String stageLabel; Instant enteredAt; TriggerType trigger; Integer byUserId; boolean migration; }
class OrderCardDto { Long id; String orderNumber; String customerName; BigDecimal total; String currency; int itemCount;
  FulfillmentType fulfillmentType; PaymentMode paymentMode; PaymentState paymentState; Long branchId; String branchName;
  Integer assignedUserId; String assignedStaffName; boolean blocked; BlockReason blockReason; String channel;
  String stageCode; Instant stageEnteredAt; Instant blockedAt; Integer targetMinutes; long minutesInStage; boolean stuck;
  boolean carriedForward; Instant createdAt; }
class OrderBoardDto { String workflowCode; String workflowLabel; String staffRole; SprintDto sprint; Long branchId;
  List<Column> columns; FlagCounts flags; List<BranchCount> branches; Instant generatedAt;
  class Column { String kind; /* STAGE|BLOCKED|CANCELLED */ String stageCode; String label; Integer ordinal; Integer targetMinutes; int count; int stuckCount; List<OrderCardDto> cards; }
  class FlagCounts { int blocked; int stuck; int unpaid; int unassigned; }
  class BranchCount { Long branchId; String branchName; int count; } }
// detail-sheet model: the customer OrderDto + the board-only overlay + the stage-history timeline (one fetch).
class OrderBoardDetailDto { OrderDto order; String customerName; String stageLabel; Integer assignedUserId; String assignedStaffName;
  boolean blocked; BlockReason blockReason; String blockNote; Instant blockedAt; Instant stageEnteredAt; Integer targetMinutes;
  long minutesInStage; boolean stuck; boolean carriedForward; Long sprintId; String channel; List<OrderStageEventDto> timeline; }

// Flat cross-sprint / cross-workflow record row for the admin "All orders" archive (ADMIN-only).
class OrderArchiveRowDto { Long id; String orderNumber; String customerName; String workflowCode; String workflowLabel;
  String stageCode; String stageLabel; boolean blocked; Long sprintId; String sprintName; PaymentState paymentState;
  FulfillmentType fulfillmentType; Long branchId; String branchName; String assignedStaffName; BigDecimal total;
  String currency; boolean carriedForward; Instant createdAt; Instant updatedAt; }

class OrderBoardService {
  OrderBoardDto board(String workflowCode, Long sprintId, Long branchId);
  OrderBoardDetailDto detail(Long id);                 // admin GET /orders/{id}
  List<OrderStageEventDto> history(Long id);
  List<OrderArchiveRowDto> archive(String workflowCode, Long sprintId, Long branchId);  // all orders, newest first
}
// OrderBoardStream (@Component): holds per-workflow SseEmitters and, AFTER_COMMIT, pushes a "board" refresh event
//   on OrderStageEnteredEvent (stage move) or OrderBoardChangedEvent (block/resume/assign/refund/mark-paid/placement).
```
**Board read endpoints** (`GET`, STAFF-permitted — see SecurityConfig): `GET /api/v1/admin/orders/board?workflowCode=&sprintId=&branchId=`
→ `OrderBoardDto`; `GET /orders/board/stream?workflowCode=` → SSE (`SseEmitter`; `board` events = refetch nudge, polling
is the fallback); `GET /orders/{id}` → `OrderBoardDetailDto`; `GET /orders/{id}/history` → `List<OrderStageEventDto>`.
**All-orders archive** (`GET /api/v1/admin/orders/archive?workflowCode=&sprintId=&branchId=` → `List<OrderArchiveRowDto>`,
**ADMIN-only** — pinned ahead of the STAFF `/orders/*` rule in SecurityConfig so STAFF can't read the full record):
the flat cross-sprint record view behind the admin "All orders" page.

**Sprints** — time-boxed batches the board groups cards by, **per workflow** (branch is a filter within a sprint,
not its own sprint). Orders join the workflow's `ACTIVE` sprint at `placeOrder` (`Order.sprintId`). Invariant:
**at most one `ACTIVE` sprint per workflow**. On boot `SprintService.bootstrap()` (runs after workflow seeding)
ensures each workflow has one active sprint and adopts orphan (null-`sprintId`) orders into it.

```java
enum SprintStatus { ACTIVE, CLOSED }

class OrderSprint { Long id; String workflowCode; String name; LocalDate startsOn; LocalDate endsOn;
  SprintStatus status; Instant createdAt; }                          // table "order_sprint"
interface OrderSprintRepository extends JpaRepository<OrderSprint,Long> {
  List<OrderSprint> findByWorkflowCodeOrderByCreatedAtDesc(String workflowCode);
  Optional<OrderSprint> findFirstByWorkflowCodeAndStatusOrderByCreatedAtDesc(String workflowCode, SprintStatus status);
  List<OrderSprint> findByWorkflowCodeAndStatus(String workflowCode, SprintStatus status); }

class SprintService {
  List<SprintDto> list(String workflowCode);
  OrderSprint activeSprint(String workflowCode);                     // null if none
  Long activeSprintId(String workflowCode);                          // for OrderService.placeOrder
  SprintDto upsert(UpsertSprintRequest req);                         // id==null creates ACTIVE (409 if one already active); else edits name/dates
  SprintDto complete(Long id, CompleteSprintRequest req);            // close current, open next, carry forward ticked open orders
}

// DTOs
class SprintDto            { Long id; String workflowCode; String name; LocalDate startsOn; LocalDate endsOn; SprintStatus status; Instant createdAt; }
class UpsertSprintRequest  { Long id; String workflowCode; String name; LocalDate startsOn; LocalDate endsOn; }
class CompleteSprintRequest{ String nextName; LocalDate nextStartsOn; LocalDate nextEndsOn; List<Long> carryForwardOrderIds; }
```
Completing a sprint closes it (`CLOSED`), opens the next `ACTIVE` sprint, and re-points the ticked open (non-terminal)
orders onto it (`Order.carriedForward=true`), keeping their stage/history/blocked state. Audited
(`SPRINT_COMPLETED`, `ORDER_CARRIED_FORWARD`).

**Admin sprint endpoints** (`/api/v1/admin/sprints`, ADMIN-only — not in the STAFF allowlist):
`GET /sprints?workflowCode=`, `POST /sprints` (create), `PUT /sprints/{id}` (edit), `POST /sprints/{id}/complete`.

## Branches & address book

```java
class Branch { Long id; String name; String slug; String phone; String addressLine; String city; String state;
  String pincode; Double latitude; Double longitude; String googleMapsUrl; String openingHours; boolean active; Instant createdAt; Instant updatedAt; }
class BranchDto { /* Branch fields + Double distanceKm (set only via /nearest) */ }
class BranchService { List<BranchDto> listActive(); List<BranchDto> nearest(double lat, double lng); BranchDto getBySlug(String); /* admin CRUD + bulkImport */ }

// User address book — user-owned (Integer userId), NOT an admin entity (no bulk). Snapshotted onto Order.shippingAddress.
class UserAddress { Long id; Integer userId; String label; String contactName; String phone; String line1; String line2;
  String city; String state; String pincode; Double latitude; Double longitude; boolean isDefault; Instant createdAt; }
class AddressService {
  List<UserAddressDto> list(Integer userId);
  UserAddressDto add/update/setDefault(...); void remove(Integer userId, Long id);
  UserAddress requireOwned(Integer userId, Long id);     // ownership-checked; used by the order spine
}
```
Endpoints — public: `GET /api/v1/branches`, `GET /api/v1/branches/nearest?lat=&lng=`, `GET /api/v1/branches/slug/{slug}`;
admin `/api/v1/admin/branches` CRUD + `/bulk-upload`. Address book (auth, `@CurrentUser`): `GET/POST /api/v1/addresses`,
`PUT/DELETE /api/v1/addresses/{id}`, `PUT /api/v1/addresses/{id}/default`.

## Feedback

A tiny post-checkout rating, one per order (submit-once). Admins read it at `/admin/feedback` (read-only, no bulk).

```java
class OrderFeedback { Long id; Long orderId; Integer userId; int rating; String comment; Instant createdAt; }  // unique orderId
class OrderFeedbackDto { Long id; Long orderId; String orderNumber; int rating; String comment; Instant createdAt; }
class CreateFeedbackRequest { int rating; String comment; }    // rating 1–5; comment optional
class FeedbackService { OrderFeedbackDto submit(Integer userId, Long orderId, CreateFeedbackRequest req); List<OrderFeedbackDto> all(); }
// submit() also records a FEEDBACK loyalty event → score points + reward coins (see Discount & Loyalty).
```
Endpoints: `POST /api/v1/orders/{id}/feedback` (auth, ownership, submit-once → 409 if already submitted); admin
read-only `GET /api/v1/admin/feedback`. `OrderDto.feedbackSubmitted` tells the UI when to show the form.

## Product reviews

A customer review of a product: 1–5 stars + optional comment. Any signed-in user may leave ONE review per
product (submit is an **upsert** — they edit their own), delete their own, and read the public summary. Admins
moderate (delete) at `/admin/reviews`. Distinct from post-checkout `OrderFeedback` (order-level, submit-once).

```java
class ProductReview { Long id; Long productId; Integer userId; String userName; int rating; String comment;
  Instant createdAt; Instant updatedAt; }                                 // unique (productId, userId)
class ProductReviewDto { Long id; Long productId; String productName; Integer userId; String userName;
  int rating; String comment; Instant createdAt; boolean mine; }          // productName set only in the admin list; mine = caller's own
class ProductReviewSummaryDto { double average; long count; Map<Integer,Long> distribution;    // star 1–5 → count
  List<ProductReviewDto> reviews; ProductReviewDto myReview; }            // reviews newest-first; myReview null for guests / not-yet-reviewed
class CreateProductReviewRequest { int rating; String comment; }          // rating 1–5; comment optional
class ProductReviewService {
  ProductReviewSummaryDto summary(Long productId, Integer userId);        // userId nullable (marks caller's own)
  ProductReviewDto submit(Integer userId, Long productId, CreateProductReviewRequest req);   // upsert
  void deleteOwn(Integer userId, Long productId);
  List<ProductReviewDto> adminList(); void adminDelete(Long id);
}
```
Endpoints — public read / auth write (`@CurrentUser`): `GET /api/v1/products/{productId}/reviews` (→ summary),
`POST /api/v1/products/{productId}/reviews` (auth upsert), `DELETE /api/v1/products/{productId}/reviews/mine`
(auth). Admin: `GET /api/v1/admin/reviews`, `DELETE /api/v1/admin/reviews/{id}` (read-only + delete, no bulk).

## Discount & Loyalty

A behaviour-driven loyalty + discount engine. A **Loyalty Score** (engagement, never spent) sets a **Tier**;
**Reward Coins** are a redeemable ledger; discount **Offers** (codes) are issued by triggers and redeemed at
checkout as a negative `OrderAdjustment` via a `DISCOUNT` connector (never a new order path). The whole
discount/promotion side has a client-controllable master kill switch (`LoyaltySettings.discountsEnabled`): when
off, scoring + tiers keep running but no offers/codes/coin-redemption happen. Loyalty state is kept 1:1 with the
user in `LoyaltyProfile` (never widens the fenced `User`). Config (tiers, event rules, triggers, settings) is
seeded on startup by `LoyaltySeedInitializer` (no Flyway; `ddl-auto`).

```java
enum Tier { BRONZE, SILVER, GOLD, PLATINUM }
enum LoyaltyEventType { SIGNUP, PROFILE_COMPLETE, DAILY_VISIT, PRODUCT_VIEW, WISHLIST, ADD_TO_CART, PURCHASE,
  REVIEW, REVIEW_MEDIA, SHARE_CLICK, REFERRAL_SIGNUP, REFERRAL_PURCHASE, NOTIF_OPT_IN, FEEDBACK, SUGGESTION }
// FEEDBACK is recorded by the fenced FeedbackService on submit; SUGGESTION is recorded server-side by a site's own
// suggestion/ideas feature: loyaltyEventService.record(userId, LoyaltyEventType.SUGGESTION, null, meta).
enum LoyaltyEventStatus { PENDING, CONFIRMED, REVERSED }
enum CoinStatus { PENDING, CONFIRMED, REVERSED, EXPIRED }
enum OfferType { PCT, FLAT, FREE_SHIP, GIFT, COINS }
enum OfferStatus { ACTIVE, USED, EXPIRED, REVOKED }
enum OfferTrigger { WELCOME, ABANDONED_CART, WIN_BACK, TIER_UP, MILESTONE, BIRTHDAY, SLOW_STOCK, FESTIVE, SURPRISE, MANUAL }

class LoyaltyProfile { Integer userId /*PK = User.id*/; Tier tier; BigDecimal score; Instant scoreUpdatedAt;
  BigDecimal coinBalance; BigDecimal avgOrderValue; Integer usualOrderGapDays; Instant lastOrderAt;
  BigDecimal returnRate; boolean inHoldout; LocalDate birthday; Instant profileCompletedAt;
  Tier pendingDowngradeTier; Instant downgradeEffectiveAt; Instant trackingConsentAt; Instant createdAt, updatedAt; }
class LoyaltyEvent { Long id; Integer userId; LoyaltyEventType type; BigDecimal points; LoyaltyEventStatus status;
  Long orderId; String metaJson; Instant createdAt; Instant confirmAt; }
class CoinLedgerEntry { Long id; Integer userId; BigDecimal coins /*signed*/; String reason; Long orderId;
  CoinStatus status; Instant earnedAt; Instant expiresAt; }
class Offer { Long id; String code /*unique*/; Integer userId /*null=public*/; OfferTrigger trigger; Integer rung;
  OfferType type; BigDecimal value; BigDecimal minSpend; BigDecimal maxDiscount; Integer maxUses; Integer usedCount;
  Instant startsAt, expiresAt; String campaignId; OfferStatus status; Integer manualApprovedBy; Instant createdAt; }
class Redemption { Long id; String code; Long orderId; Integer userId; BigDecimal discountValue;
  BigDecimal grossMarginAfter; Instant createdAt; }
class DiscountBudget { Long id; String month /*yyyy-MM, unique*/; BigDecimal budgetAmount; BigDecimal spentToDate; }
class LoyaltyTier { Long id; Tier tier /*unique*/; String name; Integer minScore; BigDecimal coinsPer100; String perks; }
class LoyaltyEventRule { Long id; LoyaltyEventType eventType /*unique*/; BigDecimal points; Integer perDayCap;
  boolean onceOnly; boolean earnsCoins; }   // earnsCoins on a non-PURCHASE event → also credits coins = points awarded (CONFIRMED, spendable at once)
// Seeded earnsCoins: REVIEW (20), REVIEW_MEDIA (35), REFERRAL_PURCHASE (200), FEEDBACK (15, one per order), SUGGESTION (10, 1/day).
class LoyaltyTriggerConfig { Long id; OfferTrigger trigger /*unique*/; boolean enabled; String rungsJson; Integer expiryDays; }
class LoyaltySettings { Long id /*=1*/; boolean discountsEnabled; BigDecimal coinValue; Integer maxCoinPctOfOrder;
  BigDecimal minOrderForCoins; Integer coinExpiryMonths; Integer scoreHalfLifeDays; Integer dailyCap; Integer monthlyCap;
  Integer holdoutPct; Integer discountPctTarget; Integer returnRateBlockPct; Integer newCodeCooldownDays;
  Integer returnWindowDays; Integer marginFloorPct; }

// Loyalty Score engine (Phase 1)
class LoyaltyEventService {                                   // best-effort, never throws to the caller
  void ensureBootstrap(Integer userId);                      // materialise profile (+ one-time SIGNUP points)
  void record(Integer userId, LoyaltyEventType type, Long orderId, Map<String,Object> meta);  // applies caps/once-only/pending
  void recordActivity(Integer userId, LoyaltyEventType type, String itemType, Long itemId);   // beacon; requires tracking consent
}
class LoyaltyScoreService { int recomputeAll(); LoyaltyProfile recompute(Integer userId); }   // Σ points·½^(age/halfLife); tier up instant, down after 30d grace
class LoyaltyNightlyJob { int runNow(); }                    // @Scheduled(loyalty.nightly-cron): confirm events + coins, expire coins, recomputeAll

// Reward coins (Phase 2) — ledger + redemption via the fenced order/connector spine
class CoinService {
  BigDecimal spendable(Integer userId); List<CoinLedgerDto> history(Integer userId);
  BigDecimal earnForOrder(Integer userId, BigDecimal orderTotal, Long orderId);   // pending; floor(total/100 × tier.coinsPer100)
  BigDecimal redeemForOrder(Integer userId, BigDecimal rupeesOff, Long orderId);  // atomic, never overdraws
  BigDecimal earnForAction(Integer userId, BigDecimal coins, String reason);    // engagement coins (CONFIRMED); called by LoyaltyEventService for earnsCoins rules
  void reverseForOrder(Long orderId); int confirmDueEarnings(); int expireDueCoins();
}
// Connectors (auto-bound into every workflow by LoyaltyWorkflowBindingInitializer; admin may re-order/disable at /admin/workflow):
//   LoyaltyEarnConnector   (ActionConnector id "loyalty.earn", on the onPaidEntry stage) — award coins + PURCHASE points + commit coin redemption
//   LoyaltyReversalConnector (ActionConnector id "loyalty.reverse", on the CANCELLED stage) — reverse coins + purchase points
//   CoinRedemptionConnector (QuoteConnector id "loyalty.coins", category DISCOUNT) — offers "use N coins" (≤maxCoinPctOfOrder%, ≥minOrderForCoins); negative OrderAdjustment, decremented on the paid stage; suppressed when discountsEnabled=false
// DTO: CoinLedgerDto { Long id; BigDecimal coins /*signed*/; String reason; CoinStatus status; Instant earnedAt, expiresAt; }

// Discount offer engine (Phase 3) — gated by discountsEnabled; holdout never gets codes
class OfferService {
  Offer decideOffer(Integer userId, OfferTrigger trigger, BigDecimal cartSubtotal);  // guards → cheapest rung → budget → size → issue + email
  void checkTimeTriggers(LoyaltyProfile p);                                           // nightly: win-back + birthday
  OfferValidationResult validate(Integer userId, String code, BigDecimal subtotal);   // checkout preview / connector
  BigDecimal discountAmountFor(Offer offer, BigDecimal subtotal);
  void commitRedemption(Order order, OrderAdjustment adj);                            // paid stage: atomic usedCount + Redemption + budget spend
}
class BudgetPacingService { BigDecimal paceFactor(); boolean budgetExhausted(); void recordSpend(BigDecimal amount); }
// Connector: LoyaltyDiscountConnector (QuoteConnector id "loyalty.discount", category DISCOUNT) — reads the typed code
//   from CheckoutContext.selections, validates, returns a negative OrderAdjustment; committed on the paid stage by LoyaltyEarnConnector.
// Triggers fire from: WELCOME (first /me bootstrap), SURPRISE (post-paid, Silver+ 10%), WIN_BACK/BIRTHDAY (nightly). Festive = admin campaign codes.
// Sizing: d = min(base × tierFactor(1.0–1.3) × churn(1.0–1.5) × pace(0.5–1.2), 20%, rung.maxDiscount); minSpend ≈ round(1.25×AOV), ≥₹500.
// DTOs: OfferValidationResult { boolean valid; String code; BigDecimal amount; String message; }, ValidateOfferRequest { String code; BigDecimal subtotal; }

// Metrics dashboard (Phase 4) — judged against the holdout, feeds the growth tracker (Discount %, Repeat %, incremental revenue)
class LoyaltyMetricsService { LoyaltyMetricsDto metrics(); }
// LoyaltyMetricsDto { long members, holdout; BudgetMetrics budget; BigDecimal coinLiability; List<TierCount> tierMix;
//   List<TriggerStat> redemptionByTrigger; HoldoutComparison holdoutComparison; }  (nested: BudgetMetrics, TierCount, TriggerStat, HoldoutComparison)
// Admin endpoint: GET /api/v1/admin/loyalty/metrics. Abuse controls: holdout + return-rate block + one-code/cooldown guards
// (OfferService), manual/public codes carry the approving staff id + audit (OfferAdminService). Compliance: public /loyalty-terms
// page (coins are a discount not cash; real expiry only), DPDP tracking consent + opt-out on the account rewards card.
// LoyaltyProfileService also exposes: LoyaltyMeDto me(Integer userId); LoyaltyMeDto setTrackingConsent(Integer userId, boolean consent);
// DTOs: LoyaltyMeDto, RecordActivityRequest { LoyaltyEventType type; String itemType; Long itemId; }, TrackingConsentRequest { boolean consent; }

// Admin-facing services (all writes audited with LOYALTY_* actions)
class LoyaltySettingsService { LoyaltySettings get(); boolean discountsEnabled(); LoyaltySettingsDto getDto();
  LoyaltySettingsDto update(LoyaltySettingsDto req); }
class LoyaltyConfigService { List<LoyaltyTierDto> listTiers(); LoyaltyTierDto updateTier(Long id, UpdateLoyaltyTierRequest);
  List<LoyaltyEventRuleDto> listEventRules(); LoyaltyEventRuleDto updateEventRule(Long id, UpdateLoyaltyEventRuleRequest);
  List<LoyaltyTriggerConfigDto> listTriggers(); LoyaltyTriggerConfigDto updateTrigger(Long id, UpdateLoyaltyTriggerConfigRequest); }
class DiscountBudgetService { List<DiscountBudgetDto> adminList(); DiscountBudgetDto upsert(UpsertDiscountBudgetRequest); }
class LoyaltyProfileService { List<LoyaltyProfileDto> adminList(); LoyaltyProfile getOrCreate(Integer userId); }  // getOrCreate assigns the fixed holdout bucket once
class OfferAdminService { List<OfferDto> adminList(); OfferDto create(CreateOfferRequest, Integer staffId);
  OfferDto revoke(Long id); BulkUploadResult bulkImport(MultipartFile file); }
// DTOs: LoyaltySettingsDto, LoyaltyTierDto/UpdateLoyaltyTierRequest, LoyaltyEventRuleDto/UpdateLoyaltyEventRuleRequest,
// LoyaltyTriggerConfigDto/UpdateLoyaltyTriggerConfigRequest, DiscountBudgetDto/UpsertDiscountBudgetRequest,
// OfferDto/CreateOfferRequest, LoyaltyProfileDto.
```
Endpoints — admin (`/api/v1/admin/**`, ADMIN-gated): `GET/PUT /api/v1/admin/loyalty/settings`;
`GET /tiers`, `PUT /tiers/{id}`; `GET /event-rules`, `PUT /event-rules/{id}`; `GET /triggers`, `PUT /triggers/{id}`;
`GET /budgets`, `POST /budgets` (upsert); `GET /customers` (read-only); and codes under
`GET /api/v1/admin/loyalty/offers`, `POST /api/v1/admin/loyalty/offers` (manual/public, `@CurrentUser` staff id),
`POST /api/v1/admin/loyalty/offers/{id}/revoke`, `POST /api/v1/admin/loyalty/offers/bulk-upload` (campaign codes),
and `POST /api/v1/admin/loyalty/run-nightly` (run the maintenance job now).
Customer (auth, `@CurrentUser`): `GET /api/v1/loyalty/me` (→ LoyaltyMeDto), `GET /api/v1/loyalty/coins`
(→ coin ledger history), `POST /api/v1/loyalty/consent` (set DPDP tracking consent), `POST /api/v1/loyalty/activity`
(behavioural beacon: PRODUCT_VIEW / WISHLIST / ADD_TO_CART / DAILY_VISIT / SHARE_CLICK; requires consent; high-value
events are server-recorded, not posted here). `POST /api/v1/loyalty/offers/validate` (checkout "have a code?" preview →
OfferValidationResult). Coin redemption rides `connectorSelections["loyalty.coins"]`; a discount code rides
`connectorSelections["loyalty.discount"]` (both re-validated + committed server-side on the paid stage).
Signup points are awarded on first `/me`; review points are recorded server-side when a first review is submitted.

## Analytics (admin dashboard)

An admin-only business-analytics screen at `/admin/analytics`. Sales / bills / customers / average-bill
are **derived from paid orders** (read-only over the order spine — the spine is never touched); profit,
margins and complaints fold in the owner's end-of-day `DailyBusinessLog`. Targets are **weekday-aware** —
a day's target is the trailing average of its own recent weekdays × an optional growth factor, so Monday
is judged against Mondays. Comparisons: Today → last same weekday, This week → last week, This month →
last month, This year → last year. Inject `AnalyticsService`; do NOT re-declare it.

```java
// The owner's end-of-day entry (the only figures orders can't know). One row per day (unique logDate).
class DailyBusinessLog { Long id; LocalDate logDate; BigDecimal cogs; BigDecimal operatingExpenses;
                         int complaints; String note; Instant createdAt; Instant updatedAt; }  // table "daily_business_log"
interface DailyBusinessLogRepository extends JpaRepository<DailyBusinessLog, Long> {
  Optional<DailyBusinessLog> findByLogDate(LocalDate logDate);
  List<DailyBusinessLog> findByLogDateBetween(LocalDate from, LocalDate to);
  boolean existsByLogDate(LocalDate logDate);
  List<DailyBusinessLog> findAllByOrderByLogDateDesc();
}
// Read-only aggregation over Order for the dashboard — a SEPARATE repo from the fenced OrderRepository,
// so the order spine is untouched. "A sale" = PAID | REFUND_PENDING | REFUNDED; net = total − refundAmount.
interface AnalyticsOrderRepository extends Repository<Order, Long> {
  List<Order> findByPaymentStateInAndCreatedAtBetween(Collection<PaymentState> states, Instant from, Instant to);
  BigDecimal sumNetSales(Collection<PaymentState> states, Instant from, Instant to);     // @Query scalar
  List<UserFirstOrder> findFirstOrderInstants(Collection<PaymentState> states);           // new-vs-repeat
  Optional<Order> findFirstByPaymentStateInOrderByCreatedAtAsc(Collection<PaymentState> states);
  interface UserFirstOrder { Integer getUserId(); Instant getFirstAt(); }
}

class AnalyticsService {
  AnalyticsSummaryDto summary(String period, Long branchId);          // period = TODAY|WEEK|MONTH|YEAR; branchId null = all outlets
  List<DailyBusinessLogDto> listLogs();
  DailyBusinessLogDto getLog(LocalDate date);
  DailyBusinessLogDto upsert(UpsertDailyBusinessLogRequest req);      // upsert by logDate
  void deleteLog(Long id);
  BulkUploadResult bulkImport(MultipartFile file);                   // columns: date,cogs,operatingExpenses,complaints,note
}

// DTOs
class DailyBusinessLogDto           { Long id; LocalDate logDate; BigDecimal cogs; BigDecimal operatingExpenses; int complaints; String note; Instant createdAt; Instant updatedAt; }
class UpsertDailyBusinessLogRequest { LocalDate logDate; BigDecimal cogs; BigDecimal operatingExpenses; Integer complaints; String note; }
class AnalyticsSummaryDto { String period; Long branchId; LocalDate asOf; String currency; int outletCount;
  Banner banner; List<KpiCard> kpis; TargetProgress targetProgress; Projection projection; DayByDay dayByDay;
  List<AttentionItem> attention; List<WeekdayStat> weekdays; List<YearlyStat> yearly; List<BranchStat> branches; }
// nested static classes: Banner, KpiCard, TargetProgress, Projection, CumulativePoint, DayByDay, DayBar,
// AttentionItem, WeekdayStat, YearlyStat, BranchStat. Percentage fields are FRACTIONS (0.19 = +19%);
// KpiCard.display is pre-formatted ₹ L/Cr; AttentionItem.severity = ACT_NOW | WATCH.
```
Endpoints (ADMIN): `GET /api/v1/admin/analytics?period=&branchId=` → `AnalyticsSummaryDto`. Daily log:
`GET /api/v1/admin/analytics/daily-log`, `GET /daily-log/{date}`, `POST|PUT /daily-log` (upsert by date),
`DELETE /daily-log/{id}`, `POST /daily-log/bulk-upload` → `BulkUploadResult`. Config
(`application.properties`): `analytics.zone`, `analytics.fy-start-month`, `analytics.target-growth-pct`,
`analytics.annual-revenue-target` (annual revenue goal in absolute ₹; 0 = no goal).

## Shared exceptions

```java
class ResourceNotFoundException extends RuntimeException {   // throw for 404s; mapped by GlobalExceptionHandler
  ResourceNotFoundException(String message);
  ResourceNotFoundException(String message, Throwable cause);
}
class PaymentGatewayException extends RuntimeException { PaymentGatewayException(String message); }
// @RestControllerAdvice GlobalExceptionHandler → stable JSON { status, error, message, timestamp } so the frontend
// apiClient toasts data.message. Maps ResourceNotFoundException→404, ResponseStatusException→its status+reason,
// bean-validation→400; unexpected errors fall through to the default 500 (no message leak).
```

---

**Do not** generate: `SecurityConfig`, `JwtAuthFilter`, `PasswordEncoder`, `AuthController`,
`OtpService`, `PasswordPolicy`, `AdminUserController`, `UserAdminService`, `UserDto`,
`PaymentController`, `SpaController`, `User`, `Role`, `Payment`, `UserRepository`, `PaymentRepository`,
the email spine (`EmailService`, `AsyncConfig`, `EmailNotificationListener`, `OtpService`, `PasswordPolicy`),
the support spine (`SupportController`, `AdminSupportController`, `SupportService`, and the Support entities/
repositories/DTOs above), the bulk-upload helpers (`SpreadsheetParser`, `BulkUploadResult`),
the media/gallery/storage spine (`MediaAsset`, `MediaAssetRepository`, `MediaAssetService`,
`AdminMediaController`, `GalleryController`, `MediaController`, `GallerySection`, `GalleryItemDto`,
`StorageService`, `MinioStorageService`, `S3Config`), the `GlobalExceptionHandler` (the shared
`@RestControllerAdvice` error-body mapper), or any auth/payment/media/support DTO listed above —
all fenced and wired.

**Also do not re-declare the commerce spine** (all fenced): `CatalogItemDto`, `CatalogItemResolver`,
`CatalogService`, `RecommendationService`; `InventoryProvider`, `InventoryService`; `OrderService`,
`WorkflowEngine`, `StageActionDispatcher`, `WorkflowService`, `ConnectorRegistry` and the connector SPIs
(`OrderConnector`/`ActionConnector`/`QuoteConnector`/`WebhookConnector`) + context/result types +
`EmailConnector`/`DemoDeliveryConnector`/`DemoRefundConnector`; `BranchService`, `AddressService`,
`FeedbackService`, `ProductReviewService`; and their controllers (`ProductController`, `AdminProductController`,
`RecommendationController`, `AdminRecommendationController`, `OrderController`, `CheckoutController`,
`AdminOrderController`, `AdminWorkflowController`, `AdminConnectorController`, `ConnectorWebhookController`,
`BranchController`, `AdminBranchController`, `AddressController`, `AdminFeedbackController`,
`ProductReviewController`, `AdminReviewController`). The concrete
default entities — `Product`/`ProductVariant`/`BranchStock`, `CatalogRecommendation`, `Order`/`OrderItem`,
`Branch`, `UserAddress`, `OrderFeedback`, `ProductReview`, and the workflow/connector config tables — are **batteries-included
defaults** a project may extend; the framework above around them is not.
