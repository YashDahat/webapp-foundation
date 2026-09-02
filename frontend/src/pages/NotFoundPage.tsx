import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';

// Foundation 404 page — mounted at the "*" catch-all route. AppRoutes renders it inside
// SiteLayout (header + footer), so this is only the inner content. Generic and business-agnostic:
// shared across every generated project so the catch-all route always resolves to a real file.
export default function NotFoundPage() {
  return (
    <div
      className="mx-auto flex max-w-2xl flex-col items-center gap-6 px-4 py-24 text-center"
      data-testid="not-found-page"
    >
      <p className="text-6xl font-bold text-primary" data-testid="not-found-code">
        404
      </p>
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">Page not found</h1>
        <p className="text-muted-foreground">
          The page you are looking for doesn&rsquo;t exist or may have moved.
        </p>
      </div>
      <Button asChild data-testid="not-found-home">
        <Link to="/">Back to home</Link>
      </Button>
    </div>
  );
}
