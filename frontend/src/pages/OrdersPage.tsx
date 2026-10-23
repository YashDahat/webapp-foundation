import { Link } from 'react-router-dom';
import { useMyOrders } from '@/hooks/orderHooks';
import type { PaymentState } from '@/types/order';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

const PAYMENT_LABEL: Record<PaymentState, string> = {
  AWAITING_PAYMENT: 'Awaiting payment',
  PAID: 'Paid',
  FAILED: 'Failed',
  REFUND_PENDING: 'Refund pending',
  REFUNDED: 'Refunded',
};

/** Customer order history (RequireAuth). Public page → shadcn cards, never ag-grid. */
export default function OrdersPage() {
  const { data: orders, isLoading } = useMyOrders();

  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10">
      <h1 className="text-2xl font-bold">My orders</h1>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : !orders || orders.length === 0 ? (
        <p className="text-sm text-muted-foreground">You have no orders yet.</p>
      ) : (
        <div className="space-y-3">
          {orders.map((o) => (
            <Link key={o.id} to={`/orders/${o.id}`} className="block">
              <Card className="transition-colors hover:bg-muted/50">
                <CardContent className="flex items-center justify-between gap-4 p-4">
                  <div className="space-y-1">
                    <p className="font-medium">{o.orderNumber}</p>
                    <p className="text-xs text-muted-foreground">
                      {new Date(o.createdAt).toLocaleDateString()} · {o.items.length} item(s)
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant="outline">{o.currentStageCode ?? '—'}</Badge>
                    <Badge variant="secondary">{PAYMENT_LABEL[o.paymentState]}</Badge>
                    <span className="font-semibold">₹{o.total}</span>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
