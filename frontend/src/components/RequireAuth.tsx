import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

/**
 * Gate for pages that require a logged-in user (e.g. checkout). A guest is sent to /login with a
 * `redirect` back to where they were; after logging in (or signing up) they return to exactly that
 * page. Their cart/selection is preserved across the round-trip because the cart persists to
 * localStorage — nothing is lost by logging in mid-flow.
 *
 * Usage (public route that must be authenticated):
 *   <Route path="/checkout" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
 */
export default function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  return <>{children}</>;
}
