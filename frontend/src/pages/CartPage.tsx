import { Link } from 'react-router-dom';
import { useCart } from '@/cart';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

// Public cart view (a guest can see their cart). "Proceed to checkout" goes to /checkout, which is
// gated by RequireAuth — so a guest is asked to log in there, with the cart preserved.
export default function CartPage() {
  const { cartItems, totals, removeItem, clearCart } = useCart();

  return (
    <div className="mx-auto max-w-2xl px-4 py-12">
      <Card data-testid="cart-page">
        <CardHeader>
          <CardTitle>Your cart</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {cartItems.length === 0 ? (
            <p data-testid="cart-empty" className="text-muted-foreground">Your cart is empty.</p>
          ) : (
            <>
              <ul className="divide-y" data-testid="cart-items">
                {cartItems.map((item) => (
                  <li key={`${item.id}:${item.variantKey ?? ''}`} className="flex items-center justify-between py-3">
                    <div>
                      <p className="font-medium">{item.name}</p>
                      <p className="text-sm text-muted-foreground">₹{item.unitPrice} × {item.quantity}</p>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="font-semibold">₹{item.unitPrice * item.quantity}</span>
                      <Button
                        variant="ghost"
                        size="sm"
                        data-testid="cart-remove"
                        onClick={() => removeItem(item.id, item.variantKey)}
                      >
                        Remove
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
              <div className="flex items-center justify-between border-t pt-3 font-semibold">
                <span>Total</span>
                <span data-testid="cart-total">₹{totals.total}</span>
              </div>
              <div className="flex gap-3">
                <Button variant="outline" onClick={clearCart} data-testid="cart-clear">Clear cart</Button>
                <Button asChild className="flex-1" data-testid="cart-checkout">
                  <Link to="/checkout">Proceed to checkout</Link>
                </Button>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
