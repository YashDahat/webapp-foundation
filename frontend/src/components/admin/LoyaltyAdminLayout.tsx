// GENERATED foundation scaffold — the Discount & Loyalty admin section shell. One admin nav entry
// (/admin/loyalty) opens here; an inner tab strip switches between the loyalty sub-pages. Generated
// sites do not rebuild this; they add business-specific loyalty screens as new tabs if needed.
import { NavLink, Outlet } from 'react-router-dom';

const TABS: { to: string; label: string; end?: boolean }[] = [
  { to: '/admin/loyalty', label: 'Overview', end: true },
  { to: '/admin/loyalty/customers', label: 'Customers' },
  { to: '/admin/loyalty/codes', label: 'Codes' },
  { to: '/admin/loyalty/triggers', label: 'Triggers' },
  { to: '/admin/loyalty/tiers', label: 'Tiers' },
  { to: '/admin/loyalty/scoring', label: 'Scoring' },
  { to: '/admin/loyalty/settings', label: 'Settings' },
];

export default function LoyaltyAdminLayout() {
  const tabClass = ({ isActive }: { isActive: boolean }) =>
    `whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium ${
      isActive ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-muted'
    }`;

  // Bounded-height panel: the heading + tab strip stay pinned while only the active tab's content scrolls.
  // Fills the admin shell's scrollable <main> (h-full), so no viewport magic numbers are needed.
  return (
    <div className="flex h-full flex-col">
      <div className="shrink-0 space-y-4">
        <div>
          <h1 className="text-2xl font-bold">Loyalty &amp; Discounts</h1>
          <p className="text-sm text-muted-foreground">
            Reward engagement with a Loyalty Score and tiers, and send trigger-driven discount codes — all
            inside a monthly budget. The discount side can be switched off entirely under Settings while
            scoring keeps running.
          </p>
        </div>
        <nav className="flex flex-wrap gap-1 border-b pb-2" data-testid="loyalty-tabs">
          {TABS.map(({ to, label, end }) => (
            <NavLink key={to} to={to} end={end} className={tabClass} data-testid={`loyalty-tab-${label.toLowerCase()}`}>
              {label}
            </NavLink>
          ))}
        </nav>
      </div>
      {/* The scroll region — only this moves; grids/forms inside scroll here, not the page. */}
      <div className="min-h-0 flex-1 overflow-y-auto pt-4 pr-1 pb-2" data-testid="loyalty-scroll">
        <Outlet />
      </div>
    </div>
  );
}
