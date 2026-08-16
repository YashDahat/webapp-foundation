import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/components/ui/card';

// Minimal foundation home page — exists mainly to make the auth flow visible/testable.
// A generated business replaces this with its real landing page.
export default function HomePage() {
  const { isAuthenticated, user, logout } = useAuth();

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      {!isAuthenticated || !user ? (
        <Card data-testid="home-anon">
          <CardHeader>
            <CardTitle>Welcome</CardTitle>
            <CardDescription>You are not logged in.</CardDescription>
          </CardHeader>
          <CardContent className="flex gap-3">
            <Button asChild data-testid="nav-login"><Link to="/login">Log in</Link></Button>
            <Button asChild variant="outline" data-testid="nav-signup"><Link to="/signup">Sign up</Link></Button>
          </CardContent>
        </Card>
      ) : (
        <Card data-testid="home-authed">
          <CardHeader>
            <CardTitle data-testid="home-welcome">Hello, {user.username}</CardTitle>
            <CardDescription>
              You are logged in as <span data-testid="home-role" className="font-semibold">{user.role}</span>.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {user.role === 'ADMIN' && (
              <div className="rounded-md border border-primary/40 bg-primary/5 p-4" data-testid="admin-panel">
                <p className="font-medium">Admin area</p>
                <p className="text-sm text-muted-foreground">
                  Only ADMIN users see this. Admin-only APIs live under <code>/api/v1/admin/**</code>.
                </p>
              </div>
            )}
            <Button variant="outline" onClick={logout} data-testid="logout-button">Log out</Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
