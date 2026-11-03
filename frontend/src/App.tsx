import { BrowserRouter, Routes, Route, Outlet, Navigate } from 'react-router-dom'
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
import EventsPage from '@/pages/EventsPage'
import EventDetailPage from '@/pages/EventDetailPage'
import OrdersPage from '@/pages/OrdersPage'
import OrderDetailPage from '@/pages/OrderDetailPage'
import BranchesPage from '@/pages/BranchesPage'
import SupportPage from '@/pages/SupportPage'
import AccountPage from '@/pages/AccountPage'
import LoyaltyTermsPage from '@/pages/LoyaltyTermsPage'
import ForgotPasswordPage from '@/pages/ForgotPasswordPage'
import ResetPasswordPage from '@/pages/ResetPasswordPage'
import AdminMediaPage from '@/pages/admin/AdminMediaPage'
import AdminSupportPage from '@/pages/admin/AdminSupportPage'
import AdminUsersPage from '@/pages/admin/AdminUsersPage'
import AdminProductsPage from '@/pages/admin/AdminProductsPage'
import AdminEventsPage from '@/pages/admin/AdminEventsPage'
import AdminRecommendationsPage from '@/pages/admin/AdminRecommendationsPage'
import OrderBoardPage from '@/pages/admin/OrderBoardPage'
import AdminOrdersArchivePage from '@/pages/admin/AdminOrdersArchivePage'
import AdminWorkflowPage from '@/pages/admin/AdminWorkflowPage'
import AdminConnectorsPage from '@/pages/admin/AdminConnectorsPage'
import AdminBranchesPage from '@/pages/admin/AdminBranchesPage'
import AdminReviewsPage from '@/pages/admin/AdminReviewsPage'
import AdminFeedbackPage from '@/pages/admin/AdminFeedbackPage'
import AdminAnalyticsPage from '@/pages/admin/AdminAnalyticsPage'
import AdminLogsPage from '@/pages/admin/AdminLogsPage'
import LoyaltyAdminLayout from '@/components/admin/LoyaltyAdminLayout'
import LoyaltyOverviewPage from '@/pages/admin/loyalty/OverviewPage'
import LoyaltyCustomersPage from '@/pages/admin/loyalty/CustomersPage'
import LoyaltyCodesPage from '@/pages/admin/loyalty/CodesPage'
import LoyaltyTriggersPage from '@/pages/admin/loyalty/TriggersPage'
import LoyaltyTiersPage from '@/pages/admin/loyalty/TiersPage'
import LoyaltyScoringPage from '@/pages/admin/loyalty/ScoringPage'
import LoyaltySettingsPage from '@/pages/admin/loyalty/SettingsPage'
import RequireAuth from '@/components/RequireAuth'
import RequireAdmin from '@/components/RequireAdmin'
import RequireBoardAccess from '@/components/RequireBoardAccess'
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
                <Route path="/events" element={<EventsPage />} />
                <Route path="/events/:slug" element={<EventDetailPage />} />
                <Route path="/branches" element={<BranchesPage />} />
                <Route path="/gallery" element={<GalleryPage />} />
                <Route path="/loyalty-terms" element={<LoyaltyTermsPage />} />
                <Route path="/support" element={<RequireAuth><SupportPage /></RequireAuth>} />
                <Route path="/orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
                <Route path="/orders/:id" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
                <Route path="/account" element={<RequireAuth><AccountPage /></RequireAuth>} />
                <Route path="/checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
                <Route path="*" element={<HomePage />} />
              </Route>

              {/* Admin area — own AdminLayout, NOT wrapped in SiteLayout. The shell admits STAFF
                  (RequireBoardAccess) so they can reach the order board; every other page is
                  independently RequireAdmin-gated (STAFF is bounced to /admin/orders). */}
              <Route
                path="/admin"
                element={<RequireBoardAccess><AdminLayout /></RequireBoardAccess>}
              >
                <Route index element={<Navigate to="orders" replace />} />
                {/* The order board replaces the old /admin/orders grid; STAFF + ADMIN. */}
                <Route path="orders" element={<OrderBoardPage />} />
                {/* Flat cross-sprint order archive (ADMIN-only record view). */}
                <Route path="order-archive" element={<RequireAdmin><AdminOrdersArchivePage /></RequireAdmin>} />
                <Route path="media" element={<RequireAdmin><AdminMediaPage /></RequireAdmin>} />
                <Route path="support" element={<RequireAdmin><AdminSupportPage /></RequireAdmin>} />
                <Route path="users" element={<RequireAdmin><AdminUsersPage /></RequireAdmin>} />
                <Route path="products" element={<RequireAdmin><AdminProductsPage /></RequireAdmin>} />
                <Route path="events" element={<RequireAdmin><AdminEventsPage /></RequireAdmin>} />
                <Route path="recommendations" element={<RequireAdmin><AdminRecommendationsPage /></RequireAdmin>} />
                <Route path="workflow" element={<RequireAdmin><AdminWorkflowPage /></RequireAdmin>} />
                <Route path="connectors" element={<RequireAdmin><AdminConnectorsPage /></RequireAdmin>} />
                <Route path="branches" element={<RequireAdmin><AdminBranchesPage /></RequireAdmin>} />
                <Route path="reviews" element={<RequireAdmin><AdminReviewsPage /></RequireAdmin>} />
                <Route path="feedback" element={<RequireAdmin><AdminFeedbackPage /></RequireAdmin>} />
                {/* Discount & Loyalty section — tabbed sub-pages under one admin nav entry. */}
                <Route path="loyalty" element={<RequireAdmin><LoyaltyAdminLayout /></RequireAdmin>}>
                  <Route index element={<LoyaltyOverviewPage />} />
                  <Route path="customers" element={<LoyaltyCustomersPage />} />
                  <Route path="codes" element={<LoyaltyCodesPage />} />
                  <Route path="triggers" element={<LoyaltyTriggersPage />} />
                  <Route path="tiers" element={<LoyaltyTiersPage />} />
                  <Route path="scoring" element={<LoyaltyScoringPage />} />
                  <Route path="settings" element={<LoyaltySettingsPage />} />
                </Route>
                <Route path="analytics" element={<RequireAdmin><AdminAnalyticsPage /></RequireAdmin>} />
                <Route path="logs" element={<RequireAdmin><AdminLogsPage /></RequireAdmin>} />
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
