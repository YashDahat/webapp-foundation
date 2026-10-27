import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import type { OrderBoardDetailDto } from '@/types/order';

/**
 * Record a counter/offline payment for an awaiting-payment order — confirms marking it paid (settles a
 * pay-at-counter pickup). Online payments are collected via the storefront Razorpay flow, not here.
 */
export default function CollectPaymentDialog({
  open, detail, pending, onOpenChange, onConfirm,
}: {
  open: boolean;
  detail: OrderBoardDetailDto | null;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
}) {
  const order = detail?.order;
  return (
    <Dialog open={open} onOpenChange={(next) => !next && !pending && onOpenChange(false)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Record payment for {order?.orderNumber}</DialogTitle>
          <DialogDescription>
            Marks this order as paid (counter / offline settlement). This cannot be undone.
          </DialogDescription>
        </DialogHeader>
        {order && (
          <p className="py-2 text-sm">
            Amount due: <span className="font-semibold">{order.currency === 'INR' ? '₹' : ''}{order.total}</span>
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={pending} onClick={onConfirm}>{pending ? 'Saving…' : 'Mark paid'}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
