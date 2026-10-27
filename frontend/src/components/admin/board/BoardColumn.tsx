import { useDroppable } from '@dnd-kit/core';
import { Ban } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { BoardColumnDto, OrderCardDto } from '@/types/order';
import OrderCard from './OrderCard';
import { columnKey } from './boardUtils';

/**
 * One kanban column (a workflow stage, or the synthetic Blocked / Cancelled column). A droppable target; while
 * a card is dragging it highlights when it's a valid target and fades when it isn't.
 */
export default function BoardColumn({
  column, draggable, dragActive, isValidTarget, onOpen,
}: {
  column: BoardColumnDto;
  /** Whether cards in this column can be dragged (false for Cancelled / closed sprints). */
  draggable: boolean;
  /** True while any card is being dragged (drives valid/invalid highlighting). */
  dragActive: boolean;
  isValidTarget: boolean;
  onOpen: (card: OrderCardDto) => void;
}) {
  const key = columnKey(column);
  const { setNodeRef, isOver } = useDroppable({ id: key, data: { column } });

  return (
    <div
      ref={setNodeRef}
      data-testid={`board-column-${key}`}
      className={cn(
        'flex h-full w-72 shrink-0 flex-col rounded-lg border bg-muted/30 transition-colors',
        dragActive && isValidTarget && 'border-primary/70 bg-primary/5',
        dragActive && !isValidTarget && 'opacity-40',
        isOver && isValidTarget && 'ring-2 ring-primary',
      )}
    >
      <div className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <span className="flex items-center gap-1.5 text-sm font-semibold">
          {column.kind === 'BLOCKED' && <Ban className="h-4 w-4 text-destructive" />}
          {column.label}
        </span>
        <span className="flex items-center gap-1">
          {column.stuckCount > 0 && (
            <Badge variant="destructive" className="h-5 px-1.5 text-[10px]">{column.stuckCount} stuck</Badge>
          )}
          <Badge variant="secondary" className="h-5 px-1.5 text-[10px]">{column.count}</Badge>
        </span>
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto p-2">
        {column.cards.length === 0 ? (
          <p className="px-1 py-6 text-center text-xs text-muted-foreground">No orders.</p>
        ) : (
          column.cards.map((c) => (
            <OrderCard key={c.id} card={c} draggable={draggable} onOpen={onOpen} />
          ))
        )}
      </div>
    </div>
  );
}
