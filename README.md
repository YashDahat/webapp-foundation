# WebApp Foundation

A production-ready Spring Boot 4 + React + TypeScript foundation that every generated website clones.
The pipeline does a one-time package rename then generates only the business-specific layer on top.

## What's included (deterministic scaffold — no LLM cost)

**Backend**
- Spring Boot 4.x + JPA + PostgreSQL + Security
- JWT authentication (AuthController, JwtUtil, JwtAuthFilter, SecurityConfig)
- Admin user bootstrap via AdminInitializer (env: ADMIN_EMAIL / ADMIN_PASSWORD)
- Full Razorpay payment spine (PaymentGateway interface + RazorpayPaymentGateway + DemoPaymentGateway fallback)
- SPA fallback controller (serves React at all non-API routes, including nested paths)
- Base package: `com.webappfoundation` → replaced by pipeline to `com.<businessslug>`

**Frontend**
- Vite + React 19 + TypeScript (strict mode)
- Tailwind CSS + shadcn/ui (25 components pre-installed)
- Cart spine: CartContext, CartProvider, CartItem, useCheckout, pricing engine
- AuthContext + ProtectedRoute shims at `@/context/`
- Playwright config for e2e specs
- All canonical configs: vite.config.ts, tsconfig.app.json, playwright.config.ts

## Pipeline usage

```bash
# Pipeline clones this and renames packages
git clone git@github.com:YashDahat/webapp-foundation.git <business-slug>
find . -type f -name "*.java" -exec sed -i 's/com\.webappfoundation/com.<slug>/g' {} \;
find . -type f -name "*.java" -exec sed -i 's/WebAppFoundation/<BusinessName>/g' {} \;
# Then: LLM generates only entities, controllers, services, frontend pages/components
```

## Running locally

```bash
cp .env.example .env   # fill in DB_URL, JWT_SECRET, etc.
docker-compose up --build
# → http://localhost:8080
```
