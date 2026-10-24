import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

/**
 * Gate for ADMIN-only pages (e.g. the gallery manager). A guest is sent to /login with a redirect
 * back; a logged-in non-admin is sent home. Backend endpoints under /api/v1/admin/** are independently
 * ADMIN-gated — this is the UI guard, not the security boundary.
 *
 *   <Route path="/admin/media" element={<RequireAdmin><AdminMediaPage /></RequireAdmin>} />
 *
 * STAFF is a restricted role that may use only the order board: it is redirected to /admin/orders rather
 * than home, so STAFF landing on any ADMIN-only admin page ends up on their one permitted screen.
 */
export default function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  if (user?.role !== 'ADMIN' && user?.role !== 'SUPER_ADMIN') {
    return <Navigate to={user?.role === 'STAFF' ? '/admin/orders' : '/'} replace />;
  }
  return <>{children}</>;
}
