# Foundation Frontend Contract (FENCED — ground truth)

These modules already exist in the workspace and are **fenced** — never re-declare, re-implement,
or edit them. Import from the exact paths below and match every signature, field, param, and
`| null` vs optional `?` **exactly**. Guessing these shapes is the #1 source of build failures.

---

## Prebuilt pages & routes (foundation-owned — do NOT rebuild)

These pages already exist and are already wired into the router. Never re-create the page file,
re-add its `<Route>`, or build a duplicate (e.g. your own login form or header cart button). Link to
them and, where noted, add your business content on top.

| Route | Page file | Gate | Notes |
|---|---|---|---|
| `/login` | `pages/LoginPage.tsx` | public | Prebuilt auth form. Link via `/login`; header `showAuth` already does. |
| `/signup` | `pages/SignupPage.tsx` | public | Prebuilt register form (role `USER`, auto-login). Link via `/signup`. |
| `/cart` | `pages/CartPage.tsx` | public | Cart view; header cart button links here. Build add-to-cart controls on your product pages. |
| `/checkout` | `pages/CheckoutPage.tsx` | `RequireAuth` | Working final-step checkout (see Checkout section) — usable **as-is**. |
| `/gallery` | `pages/GalleryPage.tsx` | public | Public gallery view over the media library. |
| `/admin/media` | `pages/admin/AdminMediaPage.tsx` | `RequireAdmin` | Media library admin. |

`LoginPage`/`SignupPage`/`CartPage`/`GalleryPage`/`AdminMediaPage` are **fenced** — do not edit them.
`CheckoutPage` is foundation-provided but **replaceable**: keep it as-is for a simple flow, or replace
that one file with a richer checkout that still uses `useCart()`/`useCheckout()` (see Checkout section).

## App shell — `src/App.tsx`, `src/AppRoutes.tsx`, `src/AppProviders.tsx` (FENCED — auto-derived)

**Never write, edit, or plan `App.tsx`, `AppRoutes.tsx`, or `AppProviders.tsx`.** `App.tsx` is a frozen
shell that mounts the provider tree (`QueryClientProvider` → `AuthProvider` → `CartProvider` →
`BrowserRouter`) around `<AppRoutes/>`. The **route table lives in `AppRoutes.tsx`, which the pipeline
DERIVES from your page plan at build time** — every page you plan with a route is added automatically,
public pages are wrapped in `SiteLayout`, admin pages in `AdminLayout`, and `/login` `/signup` `/cart`
`/checkout` `/gallery` `/admin/media` are always present. Providers you discover are injected into the
derived `AppProviders.tsx`. Just build your page components and give each a route in the plan — do NOT
hand-write routing, `<BrowserRouter>`, or the provider tree anywhere.

## Auth — `@/hooks/useAuth`, `@/context/AuthContext`

```ts
useAuth(): {
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;                                     // NOT `loading`
  user: AuthUser | null;                                  // guard for null
  login: (username: string, password: string) => Promise<void>;   // TWO string args; username = email OR phone
  register: (details: RegisterRequest) => Promise<void>;          // ONE object arg; creates a USER + auto-logs in
  logout: () => void;
}

type AuthUser = { username: string; role: string };       // role is "ADMIN" | "USER" (a string)
// RegisterRequest is imported from '@/types/auth':
type RegisterRequest = { firstName: string; lastName: string; email: string; phone: string; password: string };
```

Gotchas (each was a real failure this run):
- Use `isLoading`, **never** `loading`.
- `user.role` is a **single string**, **not** `user.roles` (no array).
- There is **no** `user.email` and **no** `user.name`; the only fields are `username` and `role`.
- `login` takes `(username, password)` — the first arg is the login identifier, **email OR phone**. Label the login field "Email or phone".
- `register` takes a **single object** `{ firstName, lastName, email, phone, password }` — NOT positional args. `phone` is REQUIRED (India-first primary contact) and must be unique; email + phone are both unique. Signup always yields role `USER` and auto-logs in; do not build your own `/register` call.
- **Login and Signup pages are PREBUILT** at `/login` and `/signup` (foundation-owned `pages/LoginPage.tsx` + `pages/SignupPage.tsx`, auto-routed by the route registry). Do NOT build your own — link to them (`ROUTES.LOGIN` / `ROUTES.SIGNUP`, or `/login` / `/signup`). The site header's `showAuth` already links to `/login`.
- **Guest checkout = login-first, cart preserved.** Wrap any page that requires a logged-in user in the foundation `RequireAuth` component (`@/components/RequireAuth`): `<Route path="/checkout" element={<RequireAuth><CheckoutPage/></RequireAuth>} />`. A guest is sent to `/login?redirect=<path>` and returns to that page after logging in/signing up. The cart persists to localStorage, so nothing is lost across the round-trip — do not try to stash the cart yourself.
- **Identity is server-derived — never send a user id/email from the client.** For "the current user's" data, the frontend sends only the payload (e.g. the cart); the backend resolves the user from the JWT and links rows by `Integer userId`. Don't put `userId`/`userEmail` in request bodies or paths, and don't read the user from `localStorage`/token — use `useAuth()`.
- `<AuthProvider>` is already mounted at the app root — do not add another.

## Cart — `@/cart` (also re-exported from `@/context/CartContext`)

```ts
useCart(): {
  cartItems: CartItem[];
  addItem: (item: Omit<CartItem, 'quantity'>, quantity?: number) => void;
  removeItem: (id: CartItem['id'], variantKey?: string) => void;
  setItemQuantity: (id: CartItem['id'], quantity: number, variantKey?: string) => void;
  clearCart: () => void;
  totals: CartTotals;
  cartCount: number;
}

interface CartItem   { id: string | number; name: string; unitPrice: number; quantity: number;
                       imageUrl?: string | null; variantKey?: string; metadata?: Record<string, unknown> }
interface CartTotals { subtotal: number; adjustments: { id: string; label: string; amount: number }[]; total: number }
```
The cart is **headless** for product/add-to-cart UI — build add-to-cart buttons on your pages with `useCart()`.
But the **cart entry point is PREBUILT and global**: the site header shows a cart button (icon + item-count
badge, `SiteHeaderProps.showCart`, default true) that links to the prebuilt **`/cart`** page (`pages/CartPage.tsx`,
public — a guest can view it). "Proceed to checkout" there goes to `/checkout` (RequireAuth-gated). Do NOT add
your own header cart button or `/cart` page; do build the add-to-cart controls on product/menu pages.

## Checkout — `@/cart/useCheckout` (also re-exported from `@/context/CheckoutContext`)

```ts
useCheckout(steps: CheckoutStep[]): CheckoutController     // REQUIRES a steps[] argument

interface CheckoutStep       { id: string; label: string; validate?: () => boolean | string }
interface CheckoutController  { steps: CheckoutStep[]; current: CheckoutStep | undefined; index: number;
                                isFirst: boolean; isLast: boolean; error: string | null; progress: number;
                                next: () => boolean; back: () => void; goTo: (id: string) => void }
```
Gotcha: `useCheckout()` with no argument is a type error — always pass the ordered `steps` array.

**A working checkout page is PREBUILT** at `/checkout` (`pages/CheckoutPage.tsx`, `RequireAuth`-gated,
auto-routed). It renders the cart line items, the total, and a "Place order" button (clears the cart),
and shows the logged-in user from `useAuth()`. Use it **as-is** as the final step of a simple flow —
"Proceed to checkout" on `/cart` already links here. Only if the business needs a richer flow (address,
delivery slot, payment) do you replace that one page file with your own, still driving it via
`useCheckout(steps)` and sending **only the cart** to the server (the user is derived from the JWT —
never send a user id/email; orders link by `Integer userId`).

## API client — `@/api/client`

```ts
import apiClient from '@/api/client';   // default export; an axios instance
// apiClient.get / .post / .put / .delete — baseURL is ''; the JWT Bearer token is attached automatically.
```
Do not create another axios instance and do not manually set the Authorization header.

## Site shell — `@/shell`

```ts
import { SiteLayout, SiteHeader, SiteFooter } from '@/shell';
import type { SiteConfig, SiteHeaderProps, SiteFooterProps, NavLink, SocialLink } from '@/shell';

// SiteLayout wraps PUBLIC pages; admin pages use <AdminLayout> (never nest the two).
<SiteLayout config={siteConfig}> ...page content... </SiteLayout>

interface SiteConfig      { header: SiteHeaderProps; footer: SiteFooterProps }
interface NavLink         { label: string; href: string; external?: boolean }
interface SocialLink      { platform: 'facebook'|'instagram'|'twitter'|'youtube'|'whatsapp'|'linkedin'; url: string }
interface SiteHeaderProps { brandName: string; logoUrl?: string | null; navLinks: NavLink[];
                            ctaButton?: { label: string; href: string } | null; showAuth?: boolean;
                            showCart?: boolean;  // cart icon + item-count badge, links to /cart; default true
                            bgClass?: string; textClass?: string; hoverClass?: string; ctaClass?: string }
interface SiteFooterProps { brandName: string; tagline?: string | null; address?: string | null;
                            phone?: string | null; email?: string | null; openingHours?: string | null;
                            quickLinks?: NavLink[]; socialLinks?: SocialLink[];
                            bgClass?: string; textClass?: string; accentClass?: string }
```
You must generate `src/config/siteConfig.ts` with `export const siteConfig: SiteConfig = { header, footer }`
(named export — import it as `import { siteConfig } from '@/config/siteConfig'`).

## Utils — `@/lib/utils`

```ts
import { cn } from '@/lib/utils';     // cn(...inputs: ClassValue[]): string — className merge helper
```

## Media library + gallery — `@/hooks/useMedia`, `@/components/gallery/GallerySection`

ONE media library ships PREBUILT. Admins manage ALL images at `/admin/media` (upload, edit, delete, and
tick "Show in public gallery"). The public gallery is a VIEW over it — there is NO separate admin gallery
page. Do NOT rebuild any of this, re-declare its types, or build your own upload UI/endpoint.

**Show gallery images on the site** (public, read-only):
```ts
import GallerySection from '@/components/gallery/GallerySection';
import { useGallery } from '@/hooks/useGallery';
import type { GalleryItemDto } from '@/types/gallery';

<GallerySection section="WEBSITE" title="Gallery" />   // main site gallery grid
<GallerySection section="EVENT" title="Events" />       // event photos, grouped by eventName
useGallery(section?): { data: GalleryItemDto[] | undefined; isLoading; isError }
type GalleryItemDto = { id: number; url: string; caption: string | null; section: 'WEBSITE'|'EVENT';
                        eventName: string | null; eventDate: string | null; sortOrder: number };
```

**Entity images** (a trainer's photo, a product image): give the entity a plain `photoUrl`/`imageUrl`
string. The admin uploads in the media library, copies the `/api/v1/media/<key>` link, and it's stored
on the entity; render `<img src={entity.photoUrl}>`.
```ts
import { uploadMedia } from '@/services/mediaService';   // { file, label?, showInGallery?, section?, ... } -> MediaAssetDto
type MediaAssetDto = { id: number; url: string; filename: string | null; contentType: string;
                       sizeBytes: number | null; label: string | null; showInGallery: boolean;
                       section: 'WEBSITE'|'EVENT'|null; eventName: string | null; eventDate: string | null;
                       sortOrder: number | null; uploadedAt: string };   // url ready for <img src>
```

---

## Pinned-library gotchas

- **`react-day-picker` is v10** (used by the shadcn `<Calendar>` at `@/components/ui/calendar`).
  The legacy **`initialFocus` prop was removed** — do not pass it. Use `autoFocus` if you need it.
- Toasts use **`sonner`**: `import { toast } from 'sonner'`.
