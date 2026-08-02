// GENERATED foundation scaffold — do not edit by hand.
// Configurable site footer. All content and design comes from props (see SiteFooterProps).
// Business-specific values live in src/config/siteConfig.ts, which the LLM generates.
import { Link } from 'react-router-dom';
import type { SiteFooterProps } from './types';

const SOCIAL_LABELS: Record<string, string> = {
  facebook: 'Facebook',
  instagram: 'Instagram',
  twitter: 'Twitter / X',
  youtube: 'YouTube',
  whatsapp: 'WhatsApp',
  linkedin: 'LinkedIn',
};

export default function SiteFooter({
  brandName,
  tagline,
  address,
  phone,
  email,
  openingHours,
  quickLinks = [],
  socialLinks = [],
  bgClass = 'bg-gray-900',
  textClass = 'text-gray-400',
  accentClass = 'hover:text-yellow-400',
}: SiteFooterProps) {
  return (
    <footer className={`${bgClass} ${textClass} pt-12 pb-6 px-4`}>
      <div className="max-w-7xl mx-auto">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8 mb-10">

          {/* Brand */}
          <div className="lg:col-span-2">
            <h3 className="text-white text-xl font-bold mb-3">{brandName}</h3>
            {tagline && <p className="text-sm mb-4">{tagline}</p>}
            {address && <p className="text-sm mb-1">{address}</p>}
            {phone && (
              <p className="text-sm mb-1">
                Phone:{' '}
                <a href={`tel:${phone}`} className={`transition-colors duration-200 ${accentClass}`}>
                  {phone}
                </a>
              </p>
            )}
            {email && (
              <p className="text-sm mb-1">
                Email:{' '}
                <a href={`mailto:${email}`} className={`transition-colors duration-200 ${accentClass}`}>
                  {email}
                </a>
              </p>
            )}
            {openingHours && <p className="text-sm mt-2">Hours: {openingHours}</p>}
          </div>

          {/* Quick Links */}
          {quickLinks.length > 0 && (
            <div>
              <h4 className="text-white font-semibold mb-4">Quick Links</h4>
              <ul className="space-y-2 text-sm">
                {quickLinks.map(link => (
                  <li key={link.href}>
                    {link.external ? (
                      <a
                        href={link.href}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`transition-colors duration-200 ${accentClass}`}
                      >
                        {link.label}
                      </a>
                    ) : (
                      <Link
                        to={link.href}
                        className={`transition-colors duration-200 ${accentClass}`}
                      >
                        {link.label}
                      </Link>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Social */}
          {socialLinks.length > 0 && (
            <div>
              <h4 className="text-white font-semibold mb-4">Follow Us</h4>
              <ul className="space-y-2 text-sm">
                {socialLinks.map(s => (
                  <li key={s.platform}>
                    <a
                      href={s.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={`transition-colors duration-200 ${accentClass}`}
                    >
                      {SOCIAL_LABELS[s.platform] ?? s.platform}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        <div className="border-t border-white/10 pt-6 text-center text-xs">
          © {new Date().getFullYear()} {brandName}. All rights reserved.
        </div>
      </div>
    </footer>
  );
}
