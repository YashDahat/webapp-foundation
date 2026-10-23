import { BrowserRouter, Routes, Route, Outlet } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/context/AuthContext'
import { CartProvider } from '@/cart/CartContext'
import { BranchProvider } from '@/context/BranchContext'
import { ThemeProvider } from '@/components/theme/ThemeProvider'
import { Toaster } from '@/components/ui/sonner'
import { SiteLayout } from '@/shell'
import siteConfig from '@/config/siteConfig'
import HomePage from '@/pages/HomePage'
import LoginPage from '@/pages/LoginPage'
import SignupPage from '@/pages/SignupPage'
import CartPage from '@/pages/CartPage'
import CheckoutPage from '@/pages/CheckoutPage'
import GalleryPage from '@/pages/GalleryPage'
import ProductsPage from '@/pages/ProductsPage'
import ProductDetailPage from '@/pages/ProductDetailPage'
import OrdersPage from '@/pages/OrdersPage'
import OrderDetailPage from '@/pages/OrderDetailPage'
import BranchesPage from '@/pages/BranchesPage'
import SupportPage from '@/pages/SupportPage'
import AccountPage from '@/pages/AccountPage'
import ForgotPasswordPage from '@/pages/ForgotPasswordPage'
import ResetPasswordPage from '@/pages/ResetPasswordPage'
import AdminMediaPage from '@/pages/admin/AdminMediaPage'
import AdminSupportPage from '@/pages/admin/AdminSupportPage'
import AdminUsersPage from '@/pages/admin/AdminUsersPage'
import AdminProductsPage from '@/pages/admin/AdminProductsPage'
import AdminRecommendationsPage from '@/pages/admin/AdminRecommendationsPage'
import AdminOrdersPage from '@/pages/admin/AdminOrdersPage'
import AdminWorkflowPage from '@/pages/admin/AdminWorkflowPage'
import AdminConnectorsPage from '@/pages/admin/AdminConnectorsPage'
import AdminBranchesPage from '@/pages/admin/AdminBranchesPage'
import AdminReviewsPage from '@/pages/admin/AdminReviewsPage'
import AdminFeedbackPage from '@/pages/admin/AdminFeedbackPage'
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
      <ThemeProvider>
      <AuthProvider>
        <BranchProvider>
        <CartProvider>
          {/* Global toasts — mounted once at the app root. Failed API calls auto-toast via the
              apiClient response interceptor; wrap mutations in withToast() for success feedback. */}
          <Toaster richColors position="bottom-left" />
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
                <Route path="/products" element={<ProductsPage />} />
                <Route path="/products/:slug" element={<ProductDetailPage />} />
                <Route path="/branches" element={<BranchesPage />} />
                <Route path="/gallery" element={<GalleryPage />} />
                <Route path="/support" element={<RequireAuth><SupportPage /></RequireAuth>} />
                <Route path="/orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
                <Route path="/orders/:id" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
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
                <Route path="products" element={<AdminProductsPage />} />
                <Route path="recommendations" element={<AdminRecommendationsPage />} />
                <Route path="orders" element={<AdminOrdersPage />} />
                <Route path="workflow" element={<AdminWorkflowPage />} />
                <Route path="connectors" element={<AdminConnectorsPage />} />
                <Route path="branches" element={<AdminBranchesPage />} />
                <Route path="reviews" element={<AdminReviewsPage />} />
                <Route path="feedback" element={<AdminFeedbackPage />} />
                <Route path="logs" element={<AdminLogsPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </CartProvider>
        </BranchProvider>
      </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
