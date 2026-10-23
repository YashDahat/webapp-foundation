import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useOrder, useCancelOrder } from '@/hooks/orderHooks';
import FeedbackForm from '@/components/FeedbackForm';
import type { PaymentState } from '@/types/order';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription,
} from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';

const PAYMENT_LABEL: Record<PaymentState, string> = {
  AWAITING_PAYMENT: 'Awaiting payment',
  PAID: 'Paid',
  FAILED: 'Failed',
  REFUND_PENDING: 'Refund pending',
  REFUNDED: 'Refunded',
};

/** Customer order detail: items, totals, current stage + payment status, and a policy-aware cancel. */
export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const orderId = id ? Number(id) : null;
  const { data: order, isLoading, isError } = useOrder(orderId);
  const cancel = useCancelOrder();
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState('');

  if (isLoading) {
    return <p className="mx-auto max-w-3xl px-4 py-10 text-sm text-muted-foreground">Loading…</p>;
  }
  if (isError || !order) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <p className="text-sm text-muted-foreground">Order not found.</p>
        <Button asChild variant="link" className="px-0"><Link to="/orders">← Back to orders</Link></Button>
      </div>
    );
  }

  const doCancel = () => {
    cancel.mutate({ id: order.id, reason: reason.trim() }, { onSuccess: () => setConfirming(false) });
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Button asChild variant="link" className="px-0 text-muted-foreground"><Link to="/orders">← My orders</Link></Button>
          <h1 className="text-2xl font-bold">{order.orderNumber}</h1>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline">{order.currentStageCode ?? '—'}</Badge>
          <Badge variant="secondary">{PAYMENT_LABEL[order.paymentState]}</Badge>
        </div>
      </div>

      {order.paymentState === 'AWAITING_PAYMENT' && order.paymentMode === 'ONLINE' && (
        <p className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
          Payment is still pending for this order.
        </p>
      )}
      {order.refundAmount != null && order.refundAmount > 0 && (
        <p className="rounded-md border bg-muted p-3 text-sm">
          Refund of ₹{order.refundAmount} is {order.paymentState === 'REFUNDED' ? 'complete' : 'being processed'}.
        </p>
      )}

      <Card>
        <CardHeader><CardTitle className="text-base">Items</CardTitle></CardHeader>
        <CardContent className="space-y-2">
          {order.items.map((it, i) => (
            <div key={i} className="flex justify-between text-sm">
              <span>
                {it.name}
                {it.variantLabel ? ` (${it.variantLabel})` : ''} × {it.quantity}
              </span>
              <span>₹{it.unitPrice * it.quantity}</span>
            </div>
          ))}
          <div className="space-y-1 border-t pt-2 text-sm">
            <div className="flex justify-between text-muted-foreground">
              <span>Subtotal</span><span>₹{order.subtotal}</span>
            </div>
            {order.adjustments.map((a, i) => (
              <div key={i} className="flex justify-between text-muted-foreground">
                <span>{a.label}</span><span>₹{a.amount}</span>
              </div>
            ))}
            <div className="flex justify-between font-semibold">
              <span>Total</span><span>₹{order.total}</span>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="text-sm text-muted-foreground">
        Fulfilment: {order.fulfillmentType === 'SELF_PICKUP' ? 'Self-pickup' : 'Delivery'}
        {order.paymentMode === 'PAY_AT_COUNTER' ? ' · Pay at counter' : ''}
      </div>

      {!order.feedbackSubmitted && <FeedbackForm orderId={order.id} />}

      {order.cancellable && (
        <Button variant="destructive" onClick={() => setConfirming(true)}>
          Cancel order
        </Button>
      )}

      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cancel this order?</DialogTitle>
            <DialogDescription>
              {order.refundPreview != null && order.refundPreview > 0
                ? `You'll be refunded ₹${order.refundPreview} per the current stage's policy.`
                : 'No refund applies at the current stage.'}
            </DialogDescription>
          </DialogHeader>
          <Textarea
            placeholder="Reason (optional)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirming(false)}>Keep order</Button>
            <Button variant="destructive" disabled={cancel.isPending} onClick={doCancel}>
              {cancel.isPending ? 'Cancelling…' : 'Cancel order'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
