import { BrowserRouter, Routes, Route, Outlet } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/context/AuthContext'
import { CartProvider } from '@/cart/CartContext'
import { SiteLayout } from '@/shell'
import siteConfig from '@/config/siteConfig'

const queryClient = new QueryClient()

// Public and admin routes are injected here by the pipeline.
// Public pages are wrapped in SiteLayout (Header + Footer from foundation).
// Admin pages use their own AdminLayout — do not wrap them in SiteLayout.
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CartProvider>
          <BrowserRouter>
            <Routes>
              {/* Admin routes rendered with their own AdminLayout — no SiteLayout */}
              {/* Public routes rendered inside SiteLayout shell */}
              <Route
                path="*"
                element={
                  <SiteLayout config={siteConfig}>
                    <Outlet />
                  </SiteLayout>
                }
              />
            </Routes>
          </BrowserRouter>
        </CartProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
