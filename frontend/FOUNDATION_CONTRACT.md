# Foundation Frontend Contract (FENCED — ground truth)

These modules already exist in the workspace and are **fenced** — never re-declare, re-implement,
or edit them. Import from the exact paths below and match every signature, field, param, and
`| null` vs optional `?` **exactly**. Guessing these shapes is the #1 source of build failures.

---

## Auth — `@/hooks/useAuth`, `@/context/AuthContext`

```ts
useAuth(): {
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;                                     // NOT `loading`
  user: AuthUser | null;                                  // guard for null
  login: (username: string, password: string) => Promise<void>;   // TWO string args, not an object
  logout: () => void;
}

type AuthUser = { username: string; role: string };       // role is "ADMIN" | "USER" (a string)
```

Gotchas (each was a real failure this run):
- Use `isLoading`, **never** `loading`.
- `user.role` is a **single string**, **not** `user.roles` (no array).
- There is **no** `user.email` and **no** `user.name`; the only fields are `username` and `role`.
- `login` takes `(username, password)` — call `login(values.username, values.password)`, not `login(values)`.
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
The cart is **headless** — build the cart UI (add-to-cart, quantity, cart view) yourself using `useCart()`.

## Checkout — `@/cart/useCheckout` (also re-exported from `@/context/CheckoutContext`)

```ts
useCheckout(steps: CheckoutStep[]): CheckoutController     // REQUIRES a steps[] argument

interface CheckoutStep       { id: string; label: string; validate?: () => boolean | string }
interface CheckoutController  { steps: CheckoutStep[]; current: CheckoutStep | undefined; index: number;
                                isFirst: boolean; isLast: boolean; error: string | null; progress: number;
                                next: () => boolean; back: () => void; goTo: (id: string) => void }
```
Gotcha: `useCheckout()` with no argument is a type error — always pass the ordered `steps` array.

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

---

## Pinned-library gotchas

- **`react-day-picker` is v10** (used by the shadcn `<Calendar>` at `@/components/ui/calendar`).
  The legacy **`initialFocus` prop was removed** — do not pass it. Use `autoFocus` if you need it.
- Toasts use **`sonner`**: `import { toast } from 'sonner'`.
