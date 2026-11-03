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
import { loyaltyService } from '@/services/loyaltyService';
import { loadRazorpay, openRazorpay } from '@/lib/razorpay';
import type { CartLineInput, FulfillmentType, PlaceOrderRequest, QuoteResponse } from '@/types/order';
import type { OfferValidationResult } from '@/types/loyalty';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
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

// The loyalty coin-redemption quote connector id (foundation CoinRedemptionConnector); its quote option
// is a DISCOUNT, surfaced as a "use coins" toggle rather than a delivery choice.
const COIN_CONNECTOR = 'loyalty.coins';
// The discount-code quote connector id (foundation LoyaltyDiscountConnector).
const DISCOUNT_CONNECTOR = 'loyalty.discount';

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
  const [useCoins, setUseCoins] = useState(false);
  const [codeInput, setCodeInput] = useState('');
  const [codeResult, setCodeResult] = useState<OfferValidationResult | null>(null);
  const [applyingCode, setApplyingCode] = useState(false);
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

  // The checkout quote mixes shipping options with the loyalty coin-redemption option (category DISCOUNT);
  // split them so coins render as their own toggle, not a delivery choice.
  const deliveryOptions = (quote?.options ?? []).filter((o) => o.connectorId !== COIN_CONNECTOR);
  const coinOption = quote?.options.find((o) => o.connectorId === COIN_CONNECTOR) ?? null;
  const selectedDelivery = deliveryOptions.find((o) => o.code === deliveryCode) ?? null;
  const deliveryFee = fulfillment === 'DELIVERY' && selectedDelivery ? selectedDelivery.amount : 0;
  const coinDiscount = useCoins && coinOption ? coinOption.amount : 0;   // negative amount
  const codeDiscount = codeResult?.valid ? -codeResult.amount : 0;       // negative amount
  const grandTotal = totals.total + deliveryFee + coinDiscount + codeDiscount;

  // A changed cart invalidates a previously-applied code (its amount/eligibility may differ).
  useEffect(() => { setCodeResult(null); }, [totals.total]);

  const applyCode = async () => {
    const code = codeInput.trim().toUpperCase();
    if (!code) return;
    setApplyingCode(true);
    try {
      setCodeResult(await loyaltyService.validateOffer(code, totals.total));
    } finally {
      setApplyingCode(false);
    }
  };

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
        const firstDelivery = q.options.find((o) => o.connectorId !== COIN_CONNECTOR);
        setDeliveryCode((prev) => prev ?? firstDelivery?.code ?? null);
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
    if (useCoins && coinOption) {
      connectorSelections[coinOption.connectorId] = coinOption.code;   // redeem coins (priced server-side)
    }
    if (codeResult?.valid && codeResult.code) {
      connectorSelections[DISCOUNT_CONNECTOR] = codeResult.code;       // discount code (re-validated server-side)
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
              ) : deliveryOptions.length > 0 ? (
                <RadioGroup value={deliveryCode ?? ''} onValueChange={setDeliveryCode}>
                  {deliveryOptions.map((o) => (
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

              {coinOption && (
                <label className="mt-2 flex items-center justify-between rounded-md border p-3" htmlFor="use-coins">
                  <span className="text-sm font-medium">
                    {coinOption.label}
                    {coinOption.detail ? <span className="text-muted-foreground"> · {coinOption.detail}</span> : null}
                  </span>
                  <input
                    id="use-coins"
                    data-testid="checkout-use-coins"
                    type="checkbox"
                    checked={useCoins}
                    onChange={(e) => setUseCoins(e.target.checked)}
                  />
                </label>
              )}
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
        <CardHeader><CardTitle className="text-base">Discount code</CardTitle></CardHeader>
        <CardContent>
          <Label htmlFor="offer-code" className="sr-only">Discount code</Label>
          <div className="flex gap-2">
            <Input
              id="offer-code"
              data-testid="checkout-code-input"
              placeholder="Enter a coupon / discount code"
              value={codeInput}
              onChange={(e) => setCodeInput(e.target.value)}
              className="uppercase"
            />
            <Button type="button" variant="outline" onClick={applyCode} disabled={applyingCode || !codeInput.trim()}>
              {applyingCode ? '…' : 'Apply'}
            </Button>
          </div>
          {codeResult && (
            <p className={`mt-1 text-xs ${codeResult.valid ? 'text-green-600' : 'text-destructive'}`} data-testid="checkout-code-msg">
              {codeResult.message}
            </p>
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
          {coinDiscount < 0 && (
            <div className="flex justify-between text-muted-foreground">
              <span>{coinOption?.label}</span><span>−₹{Math.abs(coinDiscount)}</span>
            </div>
          )}
          {codeDiscount < 0 && (
            <div className="flex justify-between text-muted-foreground">
              <span>Code {codeResult?.code}</span><span>−₹{Math.abs(codeDiscount)}</span>
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
