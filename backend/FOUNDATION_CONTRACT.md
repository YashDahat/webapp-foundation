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
  boolean twoFactorEnabled;   // email-OTP 2FA opt-in (otpCodeHash/otpExpiresAt/otpAttempts are server-internal)
}
enum Role { SUPER_ADMIN, ADMIN, USER }   // authority is the bare name; SUPER_ADMIN = single-holder, all ADMIN rights + can delete users

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
- `POST /api/v1/auth/register` · body `RegisterRequest { firstName; lastName; email; phone; password }` → 201; always role USER, created **unverified**, emails an OTP and returns `LoginResponse { verificationRequired:true, challengeId }` (**no token**). 409 on duplicate email/phone; 400 on weak password (`PasswordPolicy`: ≥ 8 chars incl. a letter and a digit).
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
// PasswordPolicy: min 8 chars incl. a letter + a digit; throws 400 on register/change/reset.
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

Endpoints (already implemented): `POST /api/v1/payments/create-order`, `/verify`, `/webhook`.
`PaymentGateway` is the provider adapter — call payments through `PaymentService`, never the gateway directly.

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

## Shared exceptions

```java
class ResourceNotFoundException extends RuntimeException {   // throw for 404s; mapped by GlobalExceptionHandler
  ResourceNotFoundException(String message);
  ResourceNotFoundException(String message, Throwable cause);
}
class PaymentGatewayException extends RuntimeException { PaymentGatewayException(String message); }
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
`StorageService`, `MinioStorageService`, `S3Config`), or any auth/payment/media/support DTO listed above —
all fenced and wired.
