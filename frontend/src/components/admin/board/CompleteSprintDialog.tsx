import { useEffect, useMemo, useState } from 'react';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import type { OrderCardDto } from '@/types/order';
import type { CompleteSprintRequest } from '@/types/sprint';

/**
 * Complete the active sprint: closes it, opens the next (name/dates below), and carries forward the ticked
 * open orders. Open orders are everything still on the board except the Cancelled column; all are ticked by
 * default so nothing is dropped by accident.
 */
export default function CompleteSprintDialog({
  open, openOrders, pending, onOpenChange, onConfirm,
}: {
  open: boolean;
  openOrders: OrderCardDto[];
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (req: CompleteSprintRequest) => void;
}) {
  const [nextName, setNextName] = useState('');
  const [nextStartsOn, setNextStartsOn] = useState('');
  const [nextEndsOn, setNextEndsOn] = useState('');
  const [carry, setCarry] = useState<Set<number>>(new Set());

  const allIds = useMemo(() => openOrders.map((o) => o.id), [openOrders]);

  useEffect(() => {
    if (!open) return;
    setNextName('');
    setNextStartsOn('');
    setNextEndsOn('');
    setCarry(new Set(allIds)); // default: carry everything forward
  }, [open, allIds]);

  const toggle = (id: number) => setCarry((prev) => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  return (
    <Dialog open={open} onOpenChange={(next) => !next && !pending && onOpenChange(false)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Complete sprint</DialogTitle>
          <DialogDescription>
            Closes the current sprint and opens the next. Ticked open orders carry forward (keeping their
            stage, history and blocked state); unticked ones stay in the closed sprint.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3 py-2">
          <div className="space-y-1.5">
            <Label>Next sprint name (optional)</Label>
            <Input value={nextName} onChange={(e) => setNextName(e.target.value)} placeholder="Auto: next number" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Starts on</Label>
              <Input type="date" value={nextStartsOn} onChange={(e) => setNextStartsOn(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>Ends on</Label>
              <Input type="date" value={nextEndsOn} onChange={(e) => setNextEndsOn(e.target.value)} />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label>Carry forward ({carry.size} of {openOrders.length})</Label>
            {openOrders.length === 0 ? (
              <p className="text-sm text-muted-foreground">No open orders to carry forward.</p>
            ) : (
              <ScrollArea className="h-48 rounded-md border">
                <div className="space-y-1 p-2">
                  {openOrders.map((o) => (
                    <label key={o.id} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-muted">
                      <Checkbox checked={carry.has(o.id)} onCheckedChange={() => toggle(o.id)} />
                      <span className="font-medium">{o.orderNumber}</span>
                      <span className="truncate text-muted-foreground">{o.customerName ?? 'Customer'}</span>
                    </label>
                  ))}
                </div>
              </ScrollArea>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button
            disabled={pending}
            onClick={() => onConfirm({
              nextName: nextName.trim() || null,
              nextStartsOn: nextStartsOn || null,
              nextEndsOn: nextEndsOn || null,
              carryForwardOrderIds: [...carry],
            })}
          >
            {pending ? 'Completing…' : 'Complete sprint'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
