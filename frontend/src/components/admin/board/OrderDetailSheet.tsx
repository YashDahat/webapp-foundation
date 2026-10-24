import {
  AlertTriangle, Ban, BadgeCheck, Link2, Phone, MessageCircle,
  Printer, RotateCcw, Undo2, ChevronRight, X,
} from 'lucide-react';
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription,
} from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import type { OrderBoardDetailDto } from '@/types/order';
import type { UserDto } from '@/types/user';
import { blockReasonLabel, formatMinutes } from './boardUtils';

const UNASSIGNED = '__none__';

/**
 * Board detail panel. Shows status-vs-target, warnings, the next-stage action and (for admins) the secondary
 * actions. STAFF see only advance/resume + block. All actions are driven by the parent via callbacks; the
 * parent owns the mutations and the block/payment/refund/cancel dialogs.
 */
export default function OrderDetailSheet({
  detail, open, isAdmin, staffUsers, staffRole, nextStage, busy,
  onOpenChange, onAdvance, onBlock, onResume, onAssign,
  onCollectPayment, onSendPaymentLink, onRefund, onCancel, onPrint,
}: {
  detail: OrderBoardDetailDto | null;
  open: boolean;
  isAdmin: boolean;
  staffUsers: UserDto[];
  staffRole?: string | null;
  nextStage: { code: string; label: string } | null;
  busy: boolean;
  onOpenChange: (open: boolean) => void;
  onAdvance: (stageCode: string) => void;
  onBlock: () => void;
  onResume: () => void;
  onAssign: (userId: number | null) => void;
  onCollectPayment: () => void;
  onSendPaymentLink: () => void;
  onRefund: () => void;
  onCancel: () => void;
  onPrint: () => void;
}) {
  const order = detail?.order;
  const phone = order?.shippingAddress?.phone ?? null;
  const unpaid = order?.paymentState === 'AWAITING_PAYMENT';
  const paid = order?.paymentState === 'PAID';

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-full flex-col gap-0 overflow-y-auto sm:max-w-md">
        {detail && order && (
          <>
            <SheetHeader className="space-y-1">
              <SheetTitle className="flex items-center justify-between">
                <span>{order.orderNumber}</span>
                <span>{order.currency === 'INR' ? '₹' : ''}{order.total}</span>
              </SheetTitle>
              <SheetDescription>
                {detail.customerName ?? 'Customer'}
                {order.branchName ? ` · ${order.branchName}` : ''}
                {detail.channel ? ` · ${detail.channel}` : ''}
              </SheetDescription>
            </SheetHeader>

            <div className="space-y-4 py-4 text-sm">
              {/* Status vs target */}
              <div className="rounded-md border p-3">
                <div className="flex items-center justify-between">
                  <span className="font-medium">{detail.stageLabel ?? order.currentStageCode}</span>
                  <span className="text-muted-foreground">
                    {formatMinutes(detail.minutesInStage)}
                    {detail.targetMinutes != null && ` / ${formatMinutes(detail.targetMinutes)} target`}
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap gap-1">
                  <Badge variant="secondary">{order.paymentState}</Badge>
                  <Badge variant="outline">{order.fulfillmentType}</Badge>
                  {detail.carriedForward && <Badge variant="secondary">Carried forward</Badge>}
                </div>
              </div>

              {/* Warnings */}
              {detail.blocked && (
                <div className="flex items-start gap-2 rounded-md border border-destructive/50 bg-destructive/5 p-3 text-destructive">
                  <Ban className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-medium">Blocked · {blockReasonLabel(detail.blockReason)}</p>
                    {detail.blockNote && <p className="text-xs">{detail.blockNote}</p>}
                  </div>
                </div>
              )}
              {detail.stuck && (
                <div className="flex items-center gap-2 rounded-md border border-destructive/50 bg-destructive/5 p-3 text-destructive">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  <span className="text-xs">Past its time target for this stage.</span>
                </div>
              )}

              {/* Primary + status actions */}
              <div className="flex flex-wrap gap-2">
                {detail.blocked ? (
                  <Button size="sm" disabled={busy} onClick={onResume}>
                    <Undo2 className="mr-1 h-4 w-4" />Resume
                  </Button>
                ) : (
                  <>
                    {nextStage && (
                      <Button size="sm" disabled={busy} onClick={() => onAdvance(nextStage.code)}>
                        <ChevronRight className="mr-1 h-4 w-4" />{nextStage.label}
                      </Button>
                    )}
                    <Button size="sm" variant="outline" disabled={busy} onClick={onBlock}>
                      <Ban className="mr-1 h-4 w-4" />Block
                    </Button>
                  </>
                )}
              </div>

              {/* Secondary actions (ADMIN only) */}
              {isAdmin && (
                <>
                  <Separator />
                  <div className="flex flex-wrap gap-2">
                    {unpaid && (
                      <Button size="sm" variant="outline" disabled={busy} onClick={onCollectPayment}>
                        <BadgeCheck className="mr-1 h-4 w-4" />Record payment
                      </Button>
                    )}
                    {unpaid && (
                      <Button size="sm" variant="outline" disabled={busy} onClick={onSendPaymentLink}>
                        <Link2 className="mr-1 h-4 w-4" />Send payment link
                      </Button>
                    )}
                    {paid && (
                      <Button size="sm" variant="outline" disabled={busy} onClick={onRefund}>
                        <RotateCcw className="mr-1 h-4 w-4" />Refund
                      </Button>
                    )}
                    <Button size="sm" variant="outline" onClick={onPrint}>
                      <Printer className="mr-1 h-4 w-4" />Print receipt
                    </Button>
                    {order.cancellable && (
                      <Button size="sm" variant="outline" className="text-destructive" disabled={busy} onClick={onCancel}>
                        <X className="mr-1 h-4 w-4" />Cancel
                      </Button>
                    )}
                  </div>

                  {/* Assignee */}
                  <div className="space-y-1.5">
                    <p className="text-xs font-medium text-muted-foreground">{staffRole ?? 'Assignee'}</p>
                    <Select
                      value={detail.assignedUserId != null ? String(detail.assignedUserId) : UNASSIGNED}
                      disabled={busy}
                      onValueChange={(v) => onAssign(v === UNASSIGNED ? null : Number(v))}
                    >
                      <SelectTrigger><SelectValue placeholder="Unassigned" /></SelectTrigger>
                      <SelectContent>
                        <SelectItem value={UNASSIGNED}>Unassigned</SelectItem>
                        {staffUsers.map((u) => (
                          <SelectItem key={u.id} value={String(u.id)}>
                            {[u.firstName, u.lastName].filter(Boolean).join(' ') || u.email}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </>
              )}

              {/* Contact */}
              {phone && (
                <div className="flex gap-2">
                  <Button size="sm" variant="outline" asChild>
                    <a href={`tel:${phone}`}><Phone className="mr-1 h-4 w-4" />Call</a>
                  </Button>
                  <Button size="sm" variant="outline" asChild>
                    <a href={`https://wa.me/${phone.replace(/[^\d]/g, '')}`} target="_blank" rel="noreferrer">
                      <MessageCircle className="mr-1 h-4 w-4" />WhatsApp
                    </a>
                  </Button>
                </div>
              )}

              <Separator />

              {/* Line items */}
              <div className="space-y-1.5">
                <p className="text-xs font-medium text-muted-foreground">Items</p>
                {order.items.map((it, i) => (
                  <div key={i} className="flex justify-between">
                    <span className="truncate">{it.quantity}× {it.name}{it.variantLabel ? ` (${it.variantLabel})` : ''}</span>
                    <span className="shrink-0 text-muted-foreground">{order.currency === 'INR' ? '₹' : ''}{it.unitPrice * it.quantity}</span>
                  </div>
                ))}
              </div>

              <Separator />

              {/* Stage-history timeline */}
              <div className="space-y-1.5">
                <p className="text-xs font-medium text-muted-foreground">Timeline</p>
                {detail.timeline.length === 0 ? (
                  <p className="text-xs text-muted-foreground">No stage history.</p>
                ) : (
                  <ol className="space-y-2">
                    {detail.timeline.map((ev) => (
                      <li key={ev.id} className="flex items-start gap-2">
                        <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-primary" />
                        <div className="text-xs">
                          <p className="font-medium">
                            {ev.stageLabel}
                            {ev.migration && <span className="ml-1 text-muted-foreground">(migrated)</span>}
                          </p>
                          <p className="text-muted-foreground">
                            {new Date(ev.enteredAt).toLocaleString()} · {ev.trigger}
                          </p>
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
