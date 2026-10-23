// GENERATED foundation scaffold — do not edit by hand.
// App-wide light/dark theming. Wraps next-themes and toggles the `dark` class on <html>,
// which drives the CSS-variable palettes in src/index.css (:root = light, .dark = dark).
// Mounted once at the app root in App.tsx. Read/change the theme anywhere via `useTheme`.
import { ThemeProvider as NextThemesProvider } from 'next-themes';
import type { ComponentProps } from 'react';

export { useTheme } from 'next-themes';

type ThemeProviderProps = ComponentProps<typeof NextThemesProvider>;

export function ThemeProvider({ children, ...props }: ThemeProviderProps) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
      storageKey="theme"
      {...props}
    >
      {children}
    </NextThemesProvider>
  );
}
