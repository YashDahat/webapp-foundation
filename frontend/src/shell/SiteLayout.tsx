// GENERATED foundation scaffold — do not edit by hand.
// Wraps public page content between SiteHeader and SiteFooter.
// Admin routes use <AdminLayout> instead — do not nest them inside SiteLayout.
import type { ReactNode } from 'react';
import SiteHeader from './SiteHeader';
import SiteFooter from './SiteFooter';
import type { SiteConfig } from './types';

interface SiteLayoutProps {
  config: SiteConfig;
  children: ReactNode;
}

export default function SiteLayout({ config, children }: SiteLayoutProps) {
  return (
    <div className="flex flex-col min-h-screen">
      <SiteHeader {...config.header} />
      <main className="flex-1">
        {children}
      </main>
      <SiteFooter {...config.footer} />
    </div>
  );
}
