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

  // Design — supply Tailwind class strings. Defaults use semantic theme tokens
  // (bg-background / text-foreground / primary accent) so the header follows the
  // app-wide light/dark theme. Override with fixed brand colours only if you want a
  // header that does NOT flip with the theme (e.g. 'bg-gray-900', 'text-white').
  /** Header background. Default 'bg-background'. e.g. 'bg-gray-900', 'bg-amber-900' */
  bgClass?: string;
  /** Primary text colour. Default 'text-foreground'. e.g. 'text-white' */
  textClass?: string;
  /** Hover / accent colour for nav links. Default 'hover:text-primary'. */
  hoverClass?: string;
  /** CTA button classes. Default 'bg-primary text-primary-foreground hover:bg-primary/90'. */
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

  // Design — defaults use semantic theme tokens so the footer follows the light/dark theme.
  /** Footer background. Default 'bg-card'. e.g. 'bg-gray-900', 'bg-slate-800' */
  bgClass?: string;
  /** Body text colour. Default 'text-muted-foreground'. e.g. 'text-gray-400' */
  textClass?: string;
  /** Accent / hover colour for links. Default 'hover:text-primary'. */
  accentClass?: string;
}

// ── Combined config ──────────────────────────────────────────────────────────

export interface SiteConfig {
  header: SiteHeaderProps;
  footer: SiteFooterProps;
}
