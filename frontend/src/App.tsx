import { BrowserRouter, Routes, Route, Outlet } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/context/AuthContext'
import { CartProvider } from '@/cart/CartContext'
import { Toaster } from '@/components/ui/sonner'
import { SiteLayout } from '@/shell'
import siteConfig from '@/config/siteConfig'
import HomePage from '@/pages/HomePage'
import LoginPage from '@/pages/LoginPage'
import SignupPage from '@/pages/SignupPage'
import CartPage from '@/pages/CartPage'
import CheckoutPage from '@/pages/CheckoutPage'
import GalleryPage from '@/pages/GalleryPage'
import SupportPage from '@/pages/SupportPage'
import AccountPage from '@/pages/AccountPage'
import ForgotPasswordPage from '@/pages/ForgotPasswordPage'
import ResetPasswordPage from '@/pages/ResetPasswordPage'
import AdminMediaPage from '@/pages/admin/AdminMediaPage'
import AdminSupportPage from '@/pages/admin/AdminSupportPage'
import AdminUsersPage from '@/pages/admin/AdminUsersPage'
import AdminLogsPage from '@/pages/admin/AdminLogsPage'
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
          {/* Global toasts — mounted once at the app root. Failed API calls auto-toast via the
              apiClient response interceptor; wrap mutations in withToast() for success feedback. */}
          <Toaster richColors position="top-right" />
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
                <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                <Route path="/reset-password" element={<ResetPasswordPage />} />
                <Route path="/cart" element={<CartPage />} />
                <Route path="/gallery" element={<GalleryPage />} />
                <Route path="/support" element={<RequireAuth><SupportPage /></RequireAuth>} />
                <Route path="/account" element={<RequireAuth><AccountPage /></RequireAuth>} />
                <Route path="/checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
                <Route path="*" element={<HomePage />} />
              </Route>

              {/* Admin area — own AdminLayout, ADMIN-gated, NOT wrapped in SiteLayout */}
              <Route
                path="/admin"
                element={<RequireAdmin><AdminLayout /></RequireAdmin>}
              >
                <Route path="media" element={<AdminMediaPage />} />
                <Route path="support" element={<AdminSupportPage />} />
                <Route path="users" element={<AdminUsersPage />} />
                <Route path="logs" element={<AdminLogsPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </CartProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
