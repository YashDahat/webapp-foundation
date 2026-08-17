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
}
enum Role { ADMIN, USER }        // authority is the bare name ("ADMIN"), no ROLE_ prefix

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

Auth endpoints (already implemented — do not rebuild), both public and both return `AuthResponse { String token }`:
`POST /api/v1/auth/login`     ·  body `AuthRequest { String username; String password }` — **username = email OR phone** (India-first).
`POST /api/v1/auth/register`  ·  body `RegisterRequest { firstName; lastName; email; phone; password }` → 201, always role USER, then auto-login (returns a JWT); 409 if the email OR phone already exists.

Beans available to inject anywhere: `PasswordEncoder`, `AuthenticationManager`, `AuthenticationProvider`.
The single ADMIN user is seeded from the `admin.email` / `admin.password` properties.

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
`PaymentController`, `SpaController`, `User`, `Role`, `Payment`, `UserRepository`, `PaymentRepository`,
the media/gallery/storage spine (`MediaAsset`, `MediaAssetRepository`, `MediaAssetService`,
`AdminMediaController`, `GalleryController`, `MediaController`, `GallerySection`, `GalleryItemDto`,
`StorageService`, `MinioStorageService`, `S3Config`), or any auth/payment/media DTO listed above —
all fenced and wired.
