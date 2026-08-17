import { BrowserRouter, Routes, Route, Outlet } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/context/AuthContext'
import { CartProvider } from '@/cart/CartContext'
import { SiteLayout } from '@/shell'
import siteConfig from '@/config/siteConfig'
import HomePage from '@/pages/HomePage'
import LoginPage from '@/pages/LoginPage'
import SignupPage from '@/pages/SignupPage'
import CartPage from '@/pages/CartPage'
import CheckoutPage from '@/pages/CheckoutPage'
import GalleryPage from '@/pages/GalleryPage'
import AdminMediaPage from '@/pages/admin/AdminMediaPage'
import RequireAuth from '@/components/RequireAuth'
import RequireAdmin from '@/components/RequireAdmin'
import AdminLayout from '@/components/AdminLayout'

const queryClient = new QueryClient()

// Public and admin routes are injected here by the pipeline.
// Public pages are wrapped in SiteLayout (Header + Footer from foundation).
// Admin pages use their own AdminLayout — do not wrap them in SiteLayout.
// /login and /signup are foundation auth routes (always present).
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CartProvider>
          <BrowserRouter>
            <Routes>
              {/* Public routes rendered inside SiteLayout shell */}
              <Route
                element={
                  <SiteLayout config={siteConfig}>
                    <Outlet />
                  </SiteLayout>
                }
              >
                <Route path="/" element={<HomePage />} />
                <Route path="/login" element={<LoginPage />} />
                <Route path="/signup" element={<SignupPage />} />
                <Route path="/cart" element={<CartPage />} />
                <Route path="/gallery" element={<GalleryPage />} />
                <Route path="/checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
                <Route path="*" element={<HomePage />} />
              </Route>

              {/* Admin area — own AdminLayout, ADMIN-gated, NOT wrapped in SiteLayout */}
              <Route
                path="/admin"
                element={<RequireAdmin><AdminLayout /></RequireAdmin>}
              >
                <Route path="media" element={<AdminMediaPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </CartProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
