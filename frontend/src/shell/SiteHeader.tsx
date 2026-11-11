// GENERATED foundation scaffold — do not edit by hand.
// Configurable site header. All content and design comes from props (see SiteHeaderProps).
// Business-specific values live in src/config/siteConfig.ts, which the LLM generates.
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Menu, X, ShoppingCart, LayoutDashboard } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/cart';
import BranchPicker from '@/components/BranchPicker';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import type { SiteHeaderProps } from './types';

export default function SiteHeader({
  brandName,
  logoUrl,
  navLinks,
  ctaButton,
  showAuth = true,
  showCart = true,
  // Defaults use semantic tokens so the header follows the light/dark theme. Pass brand colours
  // to override (see SiteHeaderProps) — fixed colours won't flip with the theme.
  bgClass = 'bg-background',
  textClass = 'text-foreground',
  hoverClass = 'hover:text-primary',
  ctaClass = 'bg-primary text-primary-foreground hover:bg-primary/90',
}: SiteHeaderProps) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { isAuthenticated, logout, user } = useAuth();
  const { cartCount } = useCart();
  const navigate = useNavigate();
  const close = () => setMobileOpen(false);

  // Globally-accessible cart button (icon + item-count badge) — rendered in the header, which
  // appears on every public page via SiteLayout. Links to the public /cart view.
  const cartButton = (
    <Link to="/cart" onClick={close} aria-label="Cart" data-testid="cart-button">
      <Button variant="ghost" size="icon" className={`relative ${textClass} ${hoverClass}`}>
        <ShoppingCart size={20} />
        {cartCount > 0 && (
          <span
            data-testid="cart-badge"
            className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-bold text-primary-foreground"
          >
            {cartCount}
          </span>
        )}
      </Button>
    </Link>
  );

  // Back-office entry for signed-in staff: ADMIN / SUPER_ADMIN go to the admin dashboard; STAFF (order board
  // only) go straight to the board. Customers never see it.
  const role = isAuthenticated ? user?.role : undefined;
  const adminLink =
    role === 'ADMIN' || role === 'SUPER_ADMIN'
      ? { href: '/admin', label: 'Admin dashboard' }
      : role === 'STAFF'
        ? { href: '/admin/orders', label: 'Order board' }
        : null;
  const adminButton = (fullWidth: boolean) =>
    adminLink && (
      <Link to={adminLink.href} onClick={close} className={fullWidth ? 'flex-1' : undefined} data-testid="admin-dashboard-link">
        <Button
          variant="outline"
          className={`${fullWidth ? 'w-full ' : ''}gap-2 border-current ${textClass} ${hoverClass} transition-colors duration-200`}
        >
          <LayoutDashboard size={16} aria-hidden="true" />
          {adminLink.label}
        </Button>
      </Link>
    );

  const handleLogout = () => {
    logout();
    navigate('/');
    close();
  };

  return (
    <header className={`${bgClass} ${textClass} py-4 px-4 sticky top-0 z-50 border-b border-border shadow-md`}>
      <div className="max-w-7xl mx-auto flex items-center justify-between">

        {/* Brand */}
        <Link to="/" onClick={close} className="flex items-center gap-2 font-bold text-xl">
          {logoUrl && <img src={logoUrl} alt={brandName} className="h-8 w-auto object-contain" />}
          <span>{brandName}</span>
        </Link>

        {/* Desktop nav */}
        <nav className="hidden md:flex items-center gap-6 text-sm font-medium">
          {navLinks.map(link =>
            link.external ? (
              <a
                key={link.href}
                href={link.href}
                target="_blank"
                rel="noopener noreferrer"
                className={`transition-colors duration-200 ${hoverClass}`}
              >
                {link.label}
              </a>
            ) : (
              <Link
                key={link.href}
                to={link.href}
                className={`transition-colors duration-200 ${hoverClass}`}
              >
                {link.label}
              </Link>
            )
          )}
        </nav>

        {/* Desktop right actions */}
        <div className="hidden md:flex items-center gap-3">
          <ThemeToggle className={`${textClass} ${hoverClass}`} />
          <BranchPicker className={`${textClass} ${hoverClass}`} />
          {ctaButton && (
            <Link to={ctaButton.href}>
              <Button className={`rounded-full px-6 font-semibold transition-colors duration-200 ${ctaClass}`}>
                {ctaButton.label}
              </Button>
            </Link>
          )}
          {showCart && cartButton}
          {adminButton(false)}
          {showAuth && (
            isAuthenticated ? (
              <Button
                variant="ghost"
                onClick={handleLogout}
                className={`${textClass} ${hoverClass} transition-colors duration-200`}
              >
                Logout
              </Button>
            ) : (
              <>
                <Link to="/login">
                  <Button
                    variant="ghost"
                    className={`${textClass} ${hoverClass} transition-colors duration-200`}
                  >
                    Login
                  </Button>
                </Link>
                <Link to="/signup">
                  <Button
                    variant="ghost"
                    className={`${textClass} ${hoverClass} border border-current transition-colors duration-200`}
                  >
                    Sign up
                  </Button>
                </Link>
              </>
            )
          )}
        </div>

        {/* Mobile right actions: cart stays visible in the top bar; hamburger opens the menu */}
        <div className="md:hidden flex items-center gap-1">
          <ThemeToggle className={`${textClass} ${hoverClass}`} />
          {showCart && cartButton}
          <button
            type="button"
            onClick={() => setMobileOpen(o => !o)}
            className={`p-2 rounded-md transition-colors duration-200 ${hoverClass}`}
            aria-label="Toggle navigation menu"
            aria-expanded={mobileOpen}
            data-testid="nav-mobile-toggle"
          >
            {mobileOpen ? <X size={24} /> : <Menu size={24} />}
          </button>
        </div>
      </div>

      {/* Mobile menu */}
      {mobileOpen && (
        <nav className={`md:hidden mt-3 border-t border-border pt-4 flex flex-col gap-4 px-4`}>
          {navLinks.map(link =>
            link.external ? (
              <a
                key={link.href}
                href={link.href}
                target="_blank"
                rel="noopener noreferrer"
                onClick={close}
                className={`text-sm font-medium transition-colors duration-200 ${hoverClass}`}
              >
                {link.label}
              </a>
            ) : (
              <Link
                key={link.href}
                to={link.href}
                onClick={close}
                className={`text-sm font-medium transition-colors duration-200 ${hoverClass}`}
              >
                {link.label}
              </Link>
            )
          )}
          {adminLink && (
            <div className="flex items-center gap-3 pt-2 border-t border-border">{adminButton(true)}</div>
          )}
          <div className="flex items-center gap-3 pt-2 border-t border-border">
            {ctaButton && (
              <Link to={ctaButton.href} onClick={close} className="flex-1">
                <Button className={`w-full rounded-full font-semibold ${ctaClass}`}>
                  {ctaButton.label}
                </Button>
              </Link>
            )}
            {showAuth && (
              isAuthenticated ? (
                <Button variant="ghost" onClick={handleLogout} className={`${textClass} ${hoverClass}`}>
                  Logout
                </Button>
              ) : (
                <>
                  <Link to="/login" onClick={close}>
                    <Button variant="ghost" className={`${textClass} ${hoverClass}`}>Login</Button>
                  </Link>
                  <Link to="/signup" onClick={close}>
                    <Button variant="ghost" className={`${textClass} ${hoverClass} border border-current`}>Sign up</Button>
                  </Link>
                </>
              )
            )}
          </div>
        </nav>
      )}
    </header>
  );
}
