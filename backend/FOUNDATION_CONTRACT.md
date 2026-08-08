# Foundation Backend Contract (FENCED Java spine — ground truth)

These classes already exist and are **fenced** — never re-declare, re-implement, or edit them, and
never create your own `User` / `Role` / `SecurityConfig` / `PasswordEncoder` / auth or payment classes.
The base package is renamed per project, so **reference every type by its simple name** — import
resolution is automatic. Match every method signature and field exactly.

---

## Auth & users

```java
// Entity — table "_user"; implements UserDetails. getUsername() returns email (login identity = email).
class User {
  Integer id; String firstName; String lastName; String email; String password; Role role;
}
enum Role { ADMIN, USER }        // authority is the bare name ("ADMIN"), no ROLE_ prefix

interface UserRepository extends JpaRepository<User, Integer> {
  Optional<User> findByEmail(String email);
}

// UserService implements UserDetailsService — this is its ONLY public method.
class UserService {
  UserDetails loadUserByUsername(String email);
}
// For registration/lookup, inject UserRepository + PasswordEncoder directly; do not add methods to UserService.

class JwtUtil {
  String  generateToken(UserDetails userDetails);
  String  generateToken(Map<String,Object> extraClaims, String subject);
  String  extractUsername(String token);
  Date    extractExpiration(String token);
  boolean validateToken(String token, UserDetails userDetails);
}
```

Login endpoint (already implemented — do not rebuild):
`POST /api/v1/auth/login`  ·  body `AuthRequest { String username; String password }` (username = email)
→ `AuthResponse { String token }`.

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
or any auth/payment DTO listed above — they are all fenced and already wired.
