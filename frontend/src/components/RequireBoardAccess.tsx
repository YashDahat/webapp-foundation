import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

/**
 * Gate for the admin shell when STAFF must be let in: admits ADMIN, SUPER_ADMIN and STAFF (the order-board
 * role). Guests go to /login; any other role goes home. Individual ADMIN-only pages inside the shell stay
 * wrapped in {@link RequireAdmin}, which bounces STAFF to /admin/orders — so STAFF only ever reaches the board.
 * This is the UI guard; `/api/v1/admin/**` is independently role-gated server-side (SecurityConfig).
 */
export default function RequireBoardAccess({ children }: { children: ReactNode }) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  if (user?.role !== 'ADMIN' && user?.role !== 'SUPER_ADMIN' && user?.role !== 'STAFF') {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
