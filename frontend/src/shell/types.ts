// GENERATED foundation scaffold — do not edit by hand.
// Design configuration interfaces for the site shell (Header + Footer).
// Every value is a prop — no hardcoded business data. The LLM generates
// src/config/siteConfig.ts with the real business values; the shell reads them.

export interface NavLink {
  label: string;
  href: string;
  external?: boolean;
}

export interface SocialLink {
  platform: 'facebook' | 'instagram' | 'twitter' | 'youtube' | 'whatsapp' | 'linkedin';
  url: string;
}

// ── Header ──────────────────────────────────────────────────────────────────

export interface SiteHeaderProps {
  // Content
  brandName: string;
  logoUrl?: string | null;
  navLinks: NavLink[];
  /** Primary call-to-action shown prominently (e.g. "Book a Table", "Order Now", "Join Now") */
  ctaButton?: { label: string; href: string } | null;
  /** Show Login / Logout button. Default true. */
  showAuth?: boolean;
  /** Show the cart button (icon + item-count badge, links to /cart). Default true. */
  showCart?: boolean;

  // Design — supply Tailwind class strings; defaults to a dark theme
  /** Header background. e.g. 'bg-gray-900', 'bg-white', 'bg-amber-900' */
  bgClass?: string;
  /** Primary text colour. e.g. 'text-white', 'text-gray-900' */
  textClass?: string;
  /** Hover / accent colour for nav links. e.g. 'hover:text-yellow-400' */
  hoverClass?: string;
  /** CTA button classes. e.g. 'bg-yellow-400 text-black hover:bg-yellow-300' */
  ctaClass?: string;
}

// ── Footer ──────────────────────────────────────────────────────────────────

export interface SiteFooterProps {
  // Content
  brandName: string;
  tagline?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  openingHours?: string | null;
  quickLinks?: NavLink[];
  socialLinks?: SocialLink[];

  // Design
  /** Footer background. e.g. 'bg-gray-900', 'bg-slate-800' */
  bgClass?: string;
  /** Body text colour. e.g. 'text-gray-400' */
  textClass?: string;
  /** Accent / hover colour for links. e.g. 'hover:text-yellow-400' */
  accentClass?: string;
}

// ── Combined config ──────────────────────────────────────────────────────────

export interface SiteConfig {
  header: SiteHeaderProps;
  footer: SiteFooterProps;
}
