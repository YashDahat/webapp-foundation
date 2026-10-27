import { useState } from 'react';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import type { BlockReason } from '@/types/order';
import { BLOCK_REASONS } from './boardUtils';

/** Pick a reason (+ optional note) when blocking an order (drag-to-Blocked or the detail-sheet action). */
export default function BlockDialog({
  open, orderNumber, pending, onOpenChange, onConfirm,
}: {
  open: boolean;
  orderNumber?: string;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (reason: BlockReason, note: string) => void;
}) {
  const [reason, setReason] = useState<BlockReason>('OUT_OF_STOCK');
  const [note, setNote] = useState('');

  return (
    <Dialog open={open} onOpenChange={(next) => !next && !pending && onOpenChange(false)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Block {orderNumber}</DialogTitle>
          <DialogDescription>Pauses the time-in-stage timer until the order is resumed.</DialogDescription>
        </DialogHeader>
        <div className="space-y-3 py-2">
          <div className="space-y-1.5">
            <Label>Reason</Label>
            <Select value={reason} onValueChange={(v) => setReason(v as BlockReason)}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                {BLOCK_REASONS.map((r) => <SelectItem key={r.value} value={r.value}>{r.label}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label>Note (optional)</Label>
            <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Add context…" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button disabled={pending} onClick={() => onConfirm(reason, note)}>
            {pending ? 'Blocking…' : 'Block order'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
