import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/cart';
import { Button } from '@/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/components/ui/card';

// Minimal foundation home page — exists mainly to make the auth + guest-checkout flow visible/testable.
// A generated business replaces this with its real landing page.
export default function HomePage() {
  const { isAuthenticated, user, logout } = useAuth();
  const { cartCount, addItem } = useCart();

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-12">
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

      {/* Cart demo — a guest can add items, then checkout requires login (cart is preserved). */}
      <Card data-testid="cart-card">
        <CardHeader>
          <CardTitle>Your cart</CardTitle>
          <CardDescription><span data-testid="cart-count">{cartCount}</span> item(s) in cart.</CardDescription>
        </CardHeader>
        <CardContent className="flex gap-3">
          <Button
            variant="outline"
            data-testid="add-to-cart"
            onClick={() => addItem({ id: 'demo-1', name: 'Sample item', unitPrice: 499 })}
          >
            Add sample item
          </Button>
          <Button asChild data-testid="go-checkout"><Link to="/checkout">Checkout</Link></Button>
        </CardContent>
      </Card>
    </div>
  );
}
