import { useDraggable } from '@dnd-kit/core';
import { AlertTriangle, Ban, Clock, CornerUpRight } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { cn } from '@/lib/utils';
import type { OrderCardDto } from '@/types/order';
import { blockReasonLabel, formatMinutes, initials } from './boardUtils';

/**
 * One order rendered as a board card. Draggable (unless the sprint is closed or the card is terminal); clicking
 * opens the detail sheet. Shows the stuck/blocked/unpaid flags, time-in-stage, channel and assignee avatar.
 */
export default function OrderCard({
  card, draggable, onOpen, dragging,
}: {
  card: OrderCardDto;
  draggable: boolean;
  onOpen: (card: OrderCardDto) => void;
  dragging?: boolean;
}) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: card.id,
    data: { card },
    disabled: !draggable,
  });

  const unpaid = card.paymentState === 'AWAITING_PAYMENT';

  return (
    <Card
      ref={setNodeRef}
      {...(draggable ? listeners : {})}
      {...attributes}
      onClick={() => onOpen(card)}
      data-testid={`order-card-${card.id}`}
      className={cn(
        'cursor-pointer space-y-2 p-3 text-sm shadow-sm transition-colors hover:border-primary/50',
        draggable && 'cursor-grab active:cursor-grabbing',
        (isDragging || dragging) && 'opacity-50',
        card.stuck && 'border-destructive/60',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <span className="font-medium">{card.orderNumber}</span>
        <span className="shrink-0 font-semibold">
          {card.currency === 'INR' ? '₹' : ''}{card.total}
        </span>
      </div>

      <div className="truncate text-muted-foreground">{card.customerName ?? 'Customer'}</div>

      <div className="flex flex-wrap items-center gap-1">
        {card.blocked && (
          <Badge variant="destructive" className="gap-1">
            <Ban className="h-3 w-3" />{blockReasonLabel(card.blockReason)}
          </Badge>
        )}
        {card.stuck && (
          <Badge variant="destructive" className="gap-1">
            <AlertTriangle className="h-3 w-3" />Stuck
          </Badge>
        )}
        {unpaid && <Badge variant="outline">Unpaid</Badge>}
        {card.carriedForward && (
          <Badge variant="secondary" className="gap-1">
            <CornerUpRight className="h-3 w-3" />Carried
          </Badge>
        )}
        {card.channel && <Badge variant="secondary">{card.channel}</Badge>}
      </div>

      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span className="flex items-center gap-1">
          <Clock className="h-3 w-3" />{formatMinutes(card.minutesInStage)}
          {card.targetMinutes != null && ` / ${formatMinutes(card.targetMinutes)}`}
        </span>
        <span className="flex items-center gap-1">
          <span>{card.itemCount} item{card.itemCount === 1 ? '' : 's'}</span>
          <Avatar className="h-6 w-6">
            <AvatarFallback className="text-[10px]">{initials(card.assignedStaffName)}</AvatarFallback>
          </Avatar>
        </span>
      </div>
    </Card>
  );
}
