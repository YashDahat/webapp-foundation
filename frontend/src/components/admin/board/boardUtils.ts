// Shared helpers for the order board: block-reason labels, time formatting, assignee initials, and the
// drag-and-drop valid-target rules (next-stage-only forward moves + Blocked/Cancelled), enforced in the UI
// (the backend stays permissive — admin override). See order_board_plan.md §7.
import type { BlockReason, BoardColumnDto, OrderCardDto } from '@/types/order';

export const BLOCK_REASONS: { value: BlockReason; label: string }[] = [
  { value: 'OUT_OF_STOCK', label: 'Out of stock' },
  { value: 'CUSTOMER_UNREACHABLE', label: 'Customer unreachable' },
  { value: 'PAYMENT_ISSUE', label: 'Payment issue' },
  { value: 'STAFF_UNAVAILABLE', label: 'Staff unavailable' },
  { value: 'ADDRESS_ISSUE', label: 'Address issue' },
  { value: 'OTHER', label: 'Other' },
];

export function blockReasonLabel(reason?: BlockReason | null): string {
  return BLOCK_REASONS.find((r) => r.value === reason)?.label ?? 'Blocked';
}

/** A stable droppable id for a column (stage code for STAGE columns, the kind otherwise). */
export function columnKey(col: Pick<BoardColumnDto, 'kind' | 'stageCode'>): string {
  return col.kind === 'STAGE' ? (col.stageCode as string) : col.kind;
}

/** "1h 20m" / "45m" from a minute count. */
export function formatMinutes(mins: number): string {
  if (mins < 60) return `${mins}m`;
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return m === 0 ? `${h}h` : `${h}h ${m}m`;
}

/** Up to two uppercase initials from a name (falls back to "?"). */
export function initials(name?: string | null): string {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  return (parts[0][0] + (parts[1]?.[0] ?? '')).toUpperCase();
}

/**
 * The set of droppable column keys a card may be dragged to. Forward moves are next-stage-only; a card may
 * always be Blocked (unless terminal) and — for admins only — Cancelled. A blocked card may be dropped back
 * on its own stage (resume) or Cancelled (admin).
 */
export function validTargetKeys(
  card: OrderCardDto,
  columns: BoardColumnDto[],
  isAdmin: boolean,
  terminalCodes: Set<string>,
): Set<string> {
  const stageCols = columns.filter((c) => c.kind === 'STAGE');
  const idx = stageCols.findIndex((c) => c.stageCode === card.stageCode);
  const targets = new Set<string>();
  const terminal = terminalCodes.has(card.stageCode);

  if (card.blocked) {
    if (idx >= 0) targets.add(card.stageCode); // resume → back to its own stage
  } else {
    const next = idx >= 0 ? stageCols[idx + 1] : undefined;
    if (next?.stageCode) targets.add(next.stageCode);
    if (!terminal) targets.add('BLOCKED');
  }
  if (isAdmin) targets.add('CANCELLED');
  return targets;
}
