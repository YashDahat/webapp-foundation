// GENERATED foundation scaffold — minimal admin shell. Admin pages render inside this layout (NOT the
// public SiteLayout). Generated sites add their own admin links here; the gallery link ships by default.
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';

export default function AdminLayout() {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const onLogout = () => {
    logout();
    navigate('/');
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `block rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'}`;

  return (
    <div className="min-h-screen bg-background">
      <header className="flex items-center justify-between border-b px-4 py-3">
        <Link to="/admin/gallery" className="font-bold">Admin</Link>
        <div className="flex items-center gap-3">
          <Link to="/" className="text-sm text-muted-foreground hover:underline">View site</Link>
          <Button variant="outline" size="sm" onClick={onLogout} data-testid="admin-logout">Log out</Button>
        </div>
      </header>
      <div className="mx-auto flex max-w-6xl gap-6 px-4 py-6">
        <aside className="w-48 shrink-0 space-y-1">
          <NavLink to="/admin/media" className={linkClass} data-testid="admin-nav-media">Media library</NavLink>
        </aside>
        <main className="min-w-0 flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
