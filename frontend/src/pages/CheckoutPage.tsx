import { useCart } from '@/cart';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle,
} from '@/components/ui/card';

// Minimal foundation checkout — mounted behind <RequireAuth>, so a guest is sent to /login first
// and returns here with their cart intact. A generated business replaces this with its real
// checkout (which sends only the cart to the server; the current user is derived from the token —
// orders link by Integer userId, never email/phone).
export default function CheckoutPage() {
  const { cartItems, totals, clearCart } = useCart();
  const { user } = useAuth();

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <Card data-testid="checkout-card">
        <CardHeader>
          <CardTitle>Checkout</CardTitle>
          <CardDescription>
            Logged in as <span data-testid="checkout-user">{user?.username}</span>.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {cartItems.length === 0 ? (
            <p data-testid="checkout-empty" className="text-muted-foreground">Your cart is empty.</p>
          ) : (
            <ul className="space-y-1" data-testid="checkout-items">
              {cartItems.map((item) => (
                <li key={`${item.id}:${item.variantKey ?? ''}`} className="flex justify-between text-sm">
                  <span>{item.name} × {item.quantity}</span>
                  <span>₹{item.unitPrice * item.quantity}</span>
                </li>
              ))}
            </ul>
          )}
          <div className="flex items-center justify-between border-t pt-3 font-semibold">
            <span>Total</span>
            <span data-testid="checkout-total">₹{totals.total}</span>
          </div>
          <Button
            className="w-full"
            disabled={cartItems.length === 0}
            data-testid="place-order"
            onClick={clearCart}
          >
            Place order
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
