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
hand-write routing, `<BrowserRouter>`, or the provider tree anywhere. The global `<Toaster/>` is already
mounted in this shell (see "Toasts & API feedback") — do not mount another.

## Auth — `@/hooks/useAuth`, `@/context/AuthContext`

```ts
useAuth(): {
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;                                     // NOT `loading`
  user: AuthUser | null;                                  // guard for null
  login: (username: string, password: string) => Promise<LoginResult>;  // TWO string args; username = email OR phone
  verifyTwoFactor: (challengeId: string, code: string) => Promise<void>;  // finishes 2FA login, persists JWT
  verifySignup: (challengeId: string, code: string) => Promise<void>;     // finishes signup email-verification, persists JWT
  enableTwoFactor: () => Promise<void>;                   // current user opts into email-OTP 2FA
  disableTwoFactor: () => Promise<void>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;  // authenticated
  register: (details: RegisterRequest) => Promise<LoginResult>;   // ONE object arg; creates an UNVERIFIED USER (returns a verification challenge)
  logout: () => void;
}

export interface AuthUser { username: string; role: string }  // role is "SUPER_ADMIN" | "ADMIN" | "USER" (a string); import it from '@/context/AuthContext' — do NOT redeclare
export interface LoginResult { twoFactorRequired: boolean; verificationRequired: boolean; challengeId?: string }  // from '@/context/AuthContext'
// RegisterRequest is imported from '@/types/auth':
type RegisterRequest = { firstName: string; lastName: string; email: string; phone: string; password: string };
```

**Signup + 2FA + reset (already wired in the PREBUILT Login/Signup/Account pages):**
- `register()` creates an UNVERIFIED account and returns `{ verificationRequired: true, challengeId }` (a
  6-digit OTP is emailed). Collect the code and call `verifySignup(challengeId, code)` to activate + persist the JWT.
- `login()` returns `LoginResult`: `twoFactorRequired` → `verifyTwoFactor(challengeId, code)`;
  `verificationRequired` (unverified account) → `verifySignup(...)`; otherwise already logged in.
- Password: `changePassword(current, next)` (Account page); forgot/reset via `authService.forgotPassword(email)`
  and `authService.resetPassword({ token, newPassword })` — prebuilt pages `/forgot-password`, `/reset-password`.
- **Admin users** (SUPER_ADMIN/ADMIN): `import { userService } from '@/services/userService'` — `list()`,
  `changeRole(id, role)`, `remove(id)` (SUPER_ADMIN only), `bulkUpload(file)`; page `/admin/users`. `RequireAdmin`
  admits both ADMIN and SUPER_ADMIN.

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
// A response interceptor auto-toasts (toast.error) on ANY failed request — see "Toasts & API feedback".
```
Do not create another axios instance and do not manually set the Authorization header.

## Toasts & API feedback

Toasts are **global** and mounted once at the app root (`<Toaster/>` in the fenced `App.tsx`) — never
mount another `<Toaster/>`. `import { toast } from 'sonner'` for ad-hoc toasts.

```ts
import { withToast } from '@/lib/apiToast';
// Success feedback on a mutation — wraps the call, shows ONE toast.success on resolve:
await withToast(fooService.save(payload), 'Saved');   // errors already toasted by the interceptor
```

**Rule — every API call surfaces a toast.**
- **Failures auto-toast.** The `apiClient` response interceptor shows a single `toast.error` for any
  failed request. Do **NOT** add manual `toast.error` after a caught request error — it double-toasts.
- **Successes on mutations** use `withToast(promise, 'message')`. Reads (list/detail queries) don't toast.

## Support — `@/services/supportService`, `@/hooks/supportHooks`

Threaded customer support ships PREBUILT (customer page `/support` gated `AUTH`; admin helpdesk
`/admin/support` gated `ADMIN`). Do NOT rebuild the service/hooks/types or re-declare the endpoints.

```ts
import { supportService } from '@/services/supportService';
import { useMyCases, useSupportThread, useOpenCase, usePostMessage,
         useAllSupportCases, useAdminSupportThread, useAdminReply } from '@/hooks/supportHooks';
import type { SupportCaseDto, SupportThreadDto, SupportMessageDto,
              SupportStatus, SupportAuthor } from '@/types/support';

// Customer: useMyCases() · useSupportThread(id) · useOpenCase() · usePostMessage(caseId)
// Admin:    useAllSupportCases() · useAdminSupportThread(id) · useAdminReply(caseId)
// Mutations already wrap withToast; failures auto-toast via the interceptor.

type SupportStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
type SupportAuthor = 'CUSTOMER' | 'STAFF';
interface SupportCaseDto   { id: number; userId: number; customerName: string | null; customerEmail: string | null;
                             subject: string; status: SupportStatus; createdAt: string; updatedAt: string }
interface SupportMessageDto{ id: number; caseId: number; author: SupportAuthor; body: string; attachments: string[]; createdAt: string }
interface SupportThreadDto { supportCase: SupportCaseDto; messages: SupportMessageDto[] }
// Requests accept optional attachments (image URLs): open({subject,message,attachments?}),
// reply({body,attachments?}), adminReply({body,status?,attachments?}).
```

**Image attachments:** messages can include images. Upload files with
`supportService.uploadAttachment(file) → MediaAssetDto` (works for customers too — NOT the admin
`/admin/media`), collect the returned `url`s, and pass them as `attachments`. The prebuilt UI uses
`@/components/support/SupportAttachmentInput` (`{ SupportAttachmentInput, SupportAttachmentThumbs }`) —
customers can't use the admin `MultiImagePicker`, so use this upload-only control on support pages.

The admin detail panel `@/components/admin/support/SupportThreadPanel` renders a thread + status dropdown
+ reply box; `pages/admin/AdminSupportPage` lists cases in an `AdminDataGrid`.

## Bulk upload — `@/components/admin/BulkUploadDialog` (ADMIN ONLY, COMPULSORY)

**Every admin entity list page MUST include a `BulkUploadDialog`** (paired with the backend
`POST /api/v1/admin/<resource>/bulk-upload`). The dialog downloads a CSV template, accepts a
`.csv`/`.xlsx` file, calls the injected `onUpload` fn, and renders the `BulkUploadResult` summary.

```tsx
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import type { BulkUploadResult } from '@/types/bulkUpload';

<BulkUploadDialog
  title="Bulk upload products"
  columns={['name', 'price', 'stock']}         // template headers (match backend expected keys)
  sampleRow={['Widget', '199', '10']}          // optional example row in the template
  templateName="products-template.csv"
  onUpload={(file) => productService.bulkUpload(file)}   // returns Promise<BulkUploadResult>
/>

interface BulkUploadResult { created: number; skipped: number; failed: number;
                             errors: { row: number; message: string }[] }
```

Failed uploads auto-toast via the apiClient interceptor; the dialog toasts the success/partial summary
(don't add your own `toast.error`). Send the file as multipart `file` (`FormData`) in your service call.

## Admin data grid — `@/components/admin/AdminDataGrid` (ADMIN ONLY)

**All admin tables use `AdminDataGrid` (ag-grid).** ag-grid is **admin-only** — do NOT use it on public
pages (keep shadcn `Table` / native there). Pass columns as ag-grid `ColDef[]`; row actions collapse
into a right-pinned kebab (⋮) menu. Floating text filters, sorting, and pagination (`[10,20,50]`) are on
by default.

```tsx
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import type { ColDef } from 'ag-grid-community';   // column defs
// import type { CustomCellRendererProps } from 'ag-grid-react';   // for custom cellRenderer components

<AdminDataGrid<Foo>
  rowData={foos}
  columnDefs={[{ field: 'name', headerName: 'Name' }, { field: 'price' }] as ColDef<Foo>[]}
  actions={[
    { label: 'Edit', onClick: (row) => openEdit(row) },
    { label: 'Delete', danger: true, onClick: (row) => remove(row), hidden: (row) => row.locked },
  ] as RowAction<Foo>[]}
  pagination pageSize={10} emptyMessage="No records found."
/>

interface RowAction<T> { label: string; onClick: (row: T) => void; danger?: boolean; hidden?: (row: T) => boolean }
// Props: rowData: T[]; columnDefs: ColDef<T>[]; actions?; pagination?=true; pageSize?=10;
//        rowHeight?; height?='calc(100vh - 220px)'; emptyMessage?; testId?
```

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

**Admin image-picker components** (ADMIN forms only — never on public pages): don't hand-roll a file
input; use these. They upload via the media library and hand back URL(s) for the form field.
```tsx
import ImagePicker from '@/components/admin/ImagePicker';           // single URL — replaces an "image URL" text field
  <ImagePicker value={imageUrl} onChange={(url) => setImageUrl(url)} />   // value: string|null; onChange('' clears)
import MultiImagePicker from '@/components/admin/MultiImagePicker';  // ordered string[] — multiple images per entity
  <MultiImagePicker value={images} onChange={(next) => setImages(next)} />  // upload / pick from library / reorder / remove
```
Entity storing MANY images: keep an ordered `List<String> images` on the backend entity —
`@ElementCollection @CollectionTable(name = "<entity>_images", joinColumns = @JoinColumn(name = "<entity>_id"))`
`@OrderColumn(name = "image_order") @Column(name = "image_url")` — plus an optional `imageUrl` primary/thumbnail.

---

## Pinned-library gotchas

- **`react-day-picker` is v10** (used by the shadcn `<Calendar>` at `@/components/ui/calendar`).
  The legacy **`initialFocus` prop was removed** — do not pass it. Use `autoFocus` if you need it.
- Toasts use **`sonner`**: `import { toast } from 'sonner'`.
