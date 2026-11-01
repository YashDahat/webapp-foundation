// GENERATED foundation scaffold — minimal admin shell. Admin pages render inside this layout (NOT the
// public SiteLayout). Generated sites add their own admin links here; the gallery link ships by default.
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  Image, Package, CalendarDays, Sparkles, ShoppingCart, Workflow, Plug, Store, Star,
  MessageSquare, LifeBuoy, Users, ScrollText, BarChart3, type LucideIcon,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { ThemeToggle } from '@/components/theme/ThemeToggle';

// Admin left-nav entries. Generated sites add their own links here; the icon is a lucide component.
const NAV: { to: string; label: string; icon: LucideIcon; testId: string }[] = [
  { to: '/admin/media', label: 'Media library', icon: Image, testId: 'admin-nav-media' },
  { to: '/admin/products', label: 'Products', icon: Package, testId: 'admin-nav-products' },
  { to: '/admin/events', label: 'Events', icon: CalendarDays, testId: 'admin-nav-events' },
  { to: '/admin/recommendations', label: 'Recommendations', icon: Sparkles, testId: 'admin-nav-recommendations' },
  { to: '/admin/orders', label: 'Orders', icon: ShoppingCart, testId: 'admin-nav-orders' },
  { to: '/admin/workflow', label: 'Workflow', icon: Workflow, testId: 'admin-nav-workflow' },
  { to: '/admin/connectors', label: 'Connectors', icon: Plug, testId: 'admin-nav-connectors' },
  { to: '/admin/branches', label: 'Branches', icon: Store, testId: 'admin-nav-branches' },
  { to: '/admin/reviews', label: 'Reviews', icon: Star, testId: 'admin-nav-reviews' },
  { to: '/admin/feedback', label: 'Feedback', icon: MessageSquare, testId: 'admin-nav-feedback' },
  { to: '/admin/support', label: 'Support', icon: LifeBuoy, testId: 'admin-nav-support' },
  { to: '/admin/users', label: 'Users', icon: Users, testId: 'admin-nav-users' },
  { to: '/admin/analytics', label: 'Analytics', icon: BarChart3, testId: 'admin-nav-analytics' },
  { to: '/admin/logs', label: 'Logs', icon: ScrollText, testId: 'admin-nav-logs' },
];

export default function AdminLayout() {
  const { logout, user } = useAuth();
  const navigate = useNavigate();

  // STAFF is the restricted order-board role: the board is their only screen, so collapse the nav to it.
  const isStaff = user?.role === 'STAFF';
  const nav = isStaff ? NAV.filter((n) => n.to === '/admin/orders') : NAV;

  // The admin area follows the app-wide light/dark theme (chosen via the ThemeToggle in the header
  // and persisted by the root ThemeProvider). The `dark` class lives on <html>, so Radix portals —
  // dialogs, sheets, select dropdowns, toasts — that mount on document.body inherit the same tokens.

  const onLogout = () => {
    logout();
    navigate('/');
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'}`;

  // Fixed-viewport shell: the header and left nav stay put; only <main> scrolls. The outer column is
  // locked to the viewport height (overflow-hidden) and the content row grows into the remaining space
  // (flex-1 + min-h-0), so each inner region owns its own scrollbar instead of the whole page scrolling.
  return (
    <div className="flex h-screen flex-col overflow-hidden bg-background text-foreground">
      <header className="flex shrink-0 items-center justify-between border-b px-4 py-3">
        <Link to="/admin/gallery" className="font-bold">Admin</Link>
        <div className="flex items-center gap-3">
          <Link to="/" className="text-sm text-muted-foreground hover:underline">View site</Link>
          <ThemeToggle />
          <Button variant="outline" size="sm" onClick={onLogout} data-testid="admin-logout">Log out</Button>
        </div>
      </header>
      <div className="mx-auto flex w-full min-h-0 max-w-[90rem] flex-1 gap-6 px-4 py-6">
        <aside className="w-48 shrink-0 space-y-1 overflow-y-auto">
          {nav.map(({ to, label, icon: Icon, testId }) => (
            <NavLink key={to} to={to} className={linkClass} data-testid={testId}>
              <Icon className="h-4 w-4 shrink-0" />
              <span>{label}</span>
            </NavLink>
          ))}
        </aside>
        <main className="min-w-0 flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
