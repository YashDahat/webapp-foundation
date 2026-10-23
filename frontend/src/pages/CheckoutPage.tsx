import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Link } from 'react-router-dom';
import { useCart } from '@/cart';
import type { CartItem } from '@/cart';
import { useBranch } from '@/context/BranchContext';
import { useAddresses } from '@/hooks/addressHooks';
import { orderService } from '@/services/orderService';
import { verifyPayment } from '@/services/paymentService';
import { loadRazorpay, openRazorpay } from '@/lib/razorpay';
import type { CartLineInput, FulfillmentType, PlaceOrderRequest, QuoteResponse } from '@/types/order';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Label } from '@/components/ui/label';

/** Map a cart line to the server's price-free CartLineInput (itemType/itemId from metadata or composite id). */
function toLine(item: CartItem): CartLineInput {
  const md = (item.metadata ?? {}) as { itemType?: string; itemId?: number };
  let itemType = md.itemType;
  let itemId = md.itemId;
  if (!itemType || itemId == null) {
    const parts = String(item.id).split(':');
    if (parts.length === 2) {
      itemType = itemType ?? parts[0];
      itemId = itemId ?? Number(parts[1]);
    } else {
      itemType = itemType ?? 'PRODUCT';
      itemId = itemId ?? Number(item.id);
    }
  }
  return { itemType: String(itemType), itemId: Number(itemId), variantKey: item.variantKey ?? null, quantity: item.quantity };
}

/**
 * The real checkout (replaces the fenced stub): pick fulfilment (delivery / self-pickup), for delivery pick
 * a priced option (POST /checkout/quote), or for pickup choose pay-now / pay-at-counter; then place the order
 * and run the Razorpay flow for online payment. Drives useCart; CartPage stays fenced/untouched. A
 * mixed-workflow cart is rejected server-side (409, auto-toasted).
 */
export default function CheckoutPage() {
  const { cartItems, totals, clearCart } = useCart();
  const { selectedBranchId } = useBranch();
  const { data: addresses } = useAddresses();
  const navigate = useNavigate();

  const [fulfillment, setFulfillment] = useState<FulfillmentType>('DELIVERY');
  const [pickupPayAtCounter, setPickupPayAtCounter] = useState(false);
  const [deliveryCode, setDeliveryCode] = useState<string | null>(null);
  const [addressId, setAddressId] = useState<number | null>(null);
  const [quote, setQuote] = useState<QuoteResponse | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [placing, setPlacing] = useState(false);

  const lines = useMemo(() => cartItems.map(toLine), [cartItems]);
  const empty = cartItems.length === 0;

  // Default the address to the user's default (or first) saved address.
  useEffect(() => {
    if (addressId == null && addresses && addresses.length > 0) {
      setAddressId((addresses.find((a) => a.isDefault) ?? addresses[0]).id);
    }
  }, [addresses, addressId]);

  const selectedDelivery = quote?.options.find((o) => o.code === deliveryCode) ?? null;
  const deliveryFee = fulfillment === 'DELIVERY' && selectedDelivery ? selectedDelivery.amount : 0;
  const grandTotal = totals.total + deliveryFee;

  // Fetch delivery quotes whenever delivery is selected (incl. on first load) or the cart changes.
  useEffect(() => {
    if (fulfillment !== 'DELIVERY' || cartItems.length === 0) {
      setQuote(null);
      return;
    }
    let active = true;
    setQuoting(true);
    orderService.quote({ items: lines, fulfillmentType: 'DELIVERY' })
      .then((q) => {
        if (!active) return;
        setQuote(q);
        setDeliveryCode((prev) => prev ?? q.options[0]?.code ?? null);
      })
      .finally(() => { if (active) setQuoting(false); });
    return () => { active = false; };
  }, [fulfillment, cartItems.length, lines]);

  const placeOrder = async () => {
    const paymentMode = fulfillment === 'SELF_PICKUP' && pickupPayAtCounter ? 'PAY_AT_COUNTER' : 'ONLINE';
    const connectorSelections: Record<string, string> = {};
    if (fulfillment === 'DELIVERY' && selectedDelivery) {
      connectorSelections[selectedDelivery.connectorId] = selectedDelivery.code;
    }
    const req: PlaceOrderRequest = {
      items: lines,
      fulfillmentType: fulfillment,
      paymentMode,
      connectorSelections,
      branchId: selectedBranchId ?? undefined,
      addressId: fulfillment === 'DELIVERY' ? addressId ?? undefined : undefined,
    };

    setPlacing(true);
    try {
      const order = await orderService.place(req);
      if (order.payment) {
        // Demo mode (no real gateway keys configured): the backend DemoPaymentGateway accepts any
        // signature, so skip the hosted Razorpay modal — which can't open with the placeholder key —
        // and settle directly. This lets the full order/workflow run locally without real payment.
        if (order.payment.gatewayKeyId === 'demo_key') {
          await verifyPayment({
            gatewayOrderId: order.payment.gatewayOrderId,
            gatewayPaymentId: `demo_pay_${order.payment.gatewayOrderId}`,
            signature: 'demo_signature',
          });
          clearCart();
          toast.success('Payment successful (demo mode)');
          navigate(`/orders/${order.id}`);
          return;
        }
        // Online: drive the Razorpay modal, then verify server-side.
        const ok = await loadRazorpay();
        if (!ok) {
          toast.error('Could not load the payment SDK');
          navigate(`/orders/${order.id}`);
          return;
        }
        try {
          const result = await openRazorpay({
            keyId: order.payment.gatewayKeyId,
            orderId: order.payment.gatewayOrderId,
            amount: order.payment.amount,
            currency: order.payment.currency,
            name: 'Order payment',
            description: order.orderNumber,
          });
          await verifyPayment({
            gatewayOrderId: result.razorpay_order_id,
            gatewayPaymentId: result.razorpay_payment_id,
            signature: result.razorpay_signature,
          });
          clearCart();
          toast.success('Payment successful');
          navigate(`/orders/${order.id}`);
        } catch {
          // Dismissed/failed — the order exists awaiting payment; let them resume from the detail page.
          toast.message('Payment not completed — you can pay from your order');
          navigate(`/orders/${order.id}`);
        }
      } else {
        // Pay-at-counter / ₹0 — already placed and confirmed.
        clearCart();
        toast.success('Order placed');
        navigate(`/orders/${order.id}`);
      }
    } finally {
      setPlacing(false);
    }
  };

  if (empty) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-12">
        <Card><CardContent className="p-6 text-muted-foreground" data-testid="checkout-empty">Your cart is empty.</CardContent></Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-12">
      <h1 className="text-2xl font-bold">Checkout</h1>

      <Card>
        <CardHeader><CardTitle className="text-base">Fulfilment</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <RadioGroup value={fulfillment} onValueChange={(v) => setFulfillment(v as FulfillmentType)}>
            <div className="flex items-center gap-2">
              <RadioGroupItem value="DELIVERY" id="f-delivery" />
              <Label htmlFor="f-delivery">Delivery</Label>
            </div>
            <div className="flex items-center gap-2">
              <RadioGroupItem value="SELF_PICKUP" id="f-pickup" />
              <Label htmlFor="f-pickup">Self-pickup</Label>
            </div>
          </RadioGroup>

          {fulfillment === 'DELIVERY' && (
            <div className="space-y-2">
              <p className="text-sm font-medium">Delivery address</p>
              {addresses && addresses.length > 0 ? (
                <RadioGroup value={addressId != null ? String(addressId) : ''} onValueChange={(v) => setAddressId(Number(v))}>
                  {addresses.map((a) => (
                    <div key={a.id} className="flex items-start gap-2">
                      <RadioGroupItem value={String(a.id)} id={`addr-${a.id}`} className="mt-1" />
                      <Label htmlFor={`addr-${a.id}`} className="font-normal">
                        <span className="font-medium">{a.label ?? 'Address'}</span>
                        <span className="block text-xs text-muted-foreground">
                          {[a.line1, a.line2, a.city, a.pincode].filter(Boolean).join(', ')}
                        </span>
                      </Label>
                    </div>
                  ))}
                </RadioGroup>
              ) : (
                <p className="text-sm text-muted-foreground">
                  No saved addresses. <Link to="/account" className="underline">Add one in your account</Link> to get it delivered.
                </p>
              )}

              <p className="pt-2 text-sm font-medium">Delivery option</p>
              {quoting ? (
                <p className="text-sm text-muted-foreground">Fetching options…</p>
              ) : quote && quote.options.length > 0 ? (
                <RadioGroup value={deliveryCode ?? ''} onValueChange={setDeliveryCode}>
                  {quote.options.map((o) => (
                    <div key={`${o.connectorId}:${o.code}`} className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <RadioGroupItem value={o.code} id={`d-${o.code}`} />
                        <Label htmlFor={`d-${o.code}`}>
                          {o.label}{o.detail ? <span className="text-muted-foreground"> · {o.detail}</span> : null}
                        </Label>
                      </div>
                      <span className="text-sm">₹{o.amount}</span>
                    </div>
                  ))}
                </RadioGroup>
              ) : (
                <p className="text-sm text-muted-foreground">No delivery options available.</p>
              )}
              <p className="text-xs text-muted-foreground">
                A saved delivery address is selected here once the address book is available.
              </p>
            </div>
          )}

          {fulfillment === 'SELF_PICKUP' && (
            <div className="flex items-center gap-2">
              <input
                id="pay-at-counter"
                type="checkbox"
                checked={pickupPayAtCounter}
                onChange={(e) => setPickupPayAtCounter(e.target.checked)}
              />
              <Label htmlFor="pay-at-counter">Pay at counter on pickup</Label>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">Summary</CardTitle></CardHeader>
        <CardContent className="space-y-1 text-sm">
          {cartItems.map((item) => (
            <div key={`${item.id}:${item.variantKey ?? ''}`} className="flex justify-between">
              <span>{item.name} × {item.quantity}</span>
              <span>₹{item.unitPrice * item.quantity}</span>
            </div>
          ))}
          <div className="flex justify-between border-t pt-2 text-muted-foreground">
            <span>Subtotal</span><span>₹{totals.total}</span>
          </div>
          {deliveryFee > 0 && (
            <div className="flex justify-between text-muted-foreground">
              <span>{selectedDelivery?.label}</span><span>₹{deliveryFee}</span>
            </div>
          )}
          <div className="flex justify-between pt-1 font-semibold">
            <span>Total</span><span data-testid="checkout-total">₹{grandTotal}</span>
          </div>
        </CardContent>
      </Card>

      <Button
        className="w-full"
        disabled={placing || (fulfillment === 'DELIVERY' && addressId == null)}
        data-testid="place-order"
        onClick={placeOrder}
      >
        {placing
          ? 'Placing…'
          : fulfillment === 'SELF_PICKUP' && pickupPayAtCounter
            ? 'Place order (pay at counter)'
            : 'Place order & pay'}
      </Button>
    </div>
  );
}
