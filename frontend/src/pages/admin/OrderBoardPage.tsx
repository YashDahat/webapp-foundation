import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  DndContext, DragOverlay, PointerSensor, useSensor, useSensors,
  type DragStartEvent, type DragEndEvent,
} from '@dnd-kit/core';
import { MapPin, Plus, Pencil, CheckCircle2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useBranch } from '@/context/BranchContext';
import { useWorkflows } from '@/hooks/workflowHooks';
import {
  useOrderBoard, useOrderBoardDetail, useOrderBoardStream, useStaffUsers,
  useAdvanceStage, useAdminCancelOrder, useMarkPaid,
  useBlockOrder, useResumeOrder, useAssignOrder, useSendPaymentLink, useRefundOrder,
} from '@/hooks/orderHooks';
import { useSprints, useCreateSprint, useUpdateSprint, useCompleteSprint } from '@/hooks/sprintHooks';
import { orderService } from '@/services/orderService';
import type { BlockReason, OrderCardDto } from '@/types/order';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import BoardColumn from '@/components/admin/board/BoardColumn';
import OrderCard from '@/components/admin/board/OrderCard';
import OrderDetailSheet from '@/components/admin/board/OrderDetailSheet';
import BlockDialog from '@/components/admin/board/BlockDialog';
import CollectPaymentDialog from '@/components/admin/board/CollectPaymentDialog';
import CompleteSprintDialog from '@/components/admin/board/CompleteSprintDialog';
import EditSprintDialog from '@/components/admin/board/EditSprintDialog';
import { columnKey, initials, validTargetKeys } from '@/components/admin/board/boardUtils';

const ALL_BRANCHES = '__all__';

function daysBetween(a: string, b: string): number {
  return Math.round((new Date(b).getTime() - new Date(a).getTime()) / 86_400_000);
}

/** Browser-print a simple receipt for an order (no backend). */
function printReceipt(orderNumber: string, lines: string[]) {
  const w = window.open('', '_blank', 'width=400,height=600');
  if (!w) return;
  w.document.write(`<pre style="font:13px monospace;padding:16px">${orderNumber}\n\n${lines.join('\n')}</pre>`);
  w.document.close();
  w.focus();
  w.print();
}

/**
 * The Order Board — an admin kanban over the fenced order spine (replaces the old /admin/orders grid). Columns
 * are the selected workflow's stages + synthetic Blocked / Cancelled. Drag a card to advance (next-stage only),
 * block, resume or cancel; click to open the detail sheet. Scoped by workflow + sprint + branch, live via SSE
 * (polling fallback). STAFF see a read-constrained view (advance/block/resume only, no secondary actions).
 */
export default function OrderBoardPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
  const navigate = useNavigate();
  const qc = useQueryClient();

  const { data: workflows } = useWorkflows();
  const { branches, selectedBranchId, selectBranch } = useBranch();
  const { data: staffUsers = [] } = useStaffUsers();

  const [workflowCode, setWorkflowCode] = useState<string>('');
  const [sprintId, setSprintId] = useState<number | null>(null); // null = active sprint

  // Default to the default workflow (or the first) once workflows load.
  useEffect(() => {
    if (!workflowCode && workflows && workflows.length > 0) {
      setWorkflowCode((workflows.find((w) => w.default) ?? workflows[0]).code);
    }
  }, [workflows, workflowCode]);

  const branchId = selectedBranchId;
  const { data: board, isLoading } = useOrderBoard(workflowCode || null, sprintId, branchId);
  const { data: sprints = [] } = useSprints(workflowCode || null);

  // Live updates: an SSE push nudges a board refetch (polling in useOrderBoard is the fallback).
  useOrderBoardStream(workflowCode || null, () => qc.invalidateQueries({ queryKey: ['order-board'] }));

  const selectedWorkflow = useMemo(
    () => workflows?.find((w) => w.code === workflowCode),
    [workflows, workflowCode],
  );
  const terminalCodes = useMemo(
    () => new Set((selectedWorkflow?.stages ?? []).filter((s) => s.terminal).map((s) => s.code)),
    [selectedWorkflow],
  );

  const sprint = board?.sprint ?? null;
  const readOnly = sprint?.status === 'CLOSED';

  // ── Filters ──
  const [search, setSearch] = useState('');
  const [flagFilters, setFlagFilters] = useState<string[]>([]);
  const [staffFilters, setStaffFilters] = useState<string[]>([]);

  const matchesFilters = (c: OrderCardDto): boolean => {
    const q = search.trim().toLowerCase();
    if (q && !c.orderNumber.toLowerCase().includes(q) && !(c.customerName ?? '').toLowerCase().includes(q)) {
      return false;
    }
    if (flagFilters.length > 0) {
      const hit = flagFilters.some((f) =>
        (f === 'blocked' && c.blocked)
        || (f === 'stuck' && c.stuck)
        || (f === 'unpaid' && c.paymentState === 'AWAITING_PAYMENT')
        || (f === 'unassigned' && c.assignedUserId == null));
      if (!hit) return false;
    }
    if (staffFilters.length > 0 && !(c.assignedUserId != null && staffFilters.includes(String(c.assignedUserId)))) {
      return false;
    }
    return true;
  };

  const columns = useMemo(
    () => (board?.columns ?? []).map((col) => ({ ...col, cards: col.cards.filter(matchesFilters) })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [board, search, flagFilters, staffFilters],
  );

  // ── Detail sheet ──
  const [openOrderId, setOpenOrderId] = useState<number | null>(null);
  const { data: detail } = useOrderBoardDetail(openOrderId);

  // ── Mutations ──
  const advance = useAdvanceStage();
  const cancel = useAdminCancelOrder();
  const markPaid = useMarkPaid();
  const block = useBlockOrder();
  const resume = useResumeOrder();
  const assign = useAssignOrder();
  const sendLink = useSendPaymentLink();
  const refund = useRefundOrder();
  const createSprint = useCreateSprint();
  const updateSprint = useUpdateSprint();
  const completeSprint = useCompleteSprint();
  const busy = advance.isPending || cancel.isPending || markPaid.isPending || block.isPending
    || resume.isPending || assign.isPending || sendLink.isPending || refund.isPending;

  // ── Action-dialog targets ──
  const [blockTarget, setBlockTarget] = useState<{ id: number; orderNumber: string } | null>(null);
  const [cancelTarget, setCancelTarget] = useState<{ id: number; orderNumber: string } | null>(null);
  const [collectOpen, setCollectOpen] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);
  const [editSprintOpen, setEditSprintOpen] = useState(false);
  const [completeOpen, setCompleteOpen] = useState(false);

  // ── Drag & drop ──
  const [activeCard, setActiveCard] = useState<OrderCardDto | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 8 } }));
  const validTargets = useMemo(
    () => (activeCard ? validTargetKeys(activeCard, board?.columns ?? [], isAdmin, terminalCodes) : new Set<string>()),
    [activeCard, board, isAdmin, terminalCodes],
  );

  const onDragStart = (e: DragStartEvent) => setActiveCard((e.active.data.current?.card as OrderCardDto) ?? null);
  const onDragEnd = (e: DragEndEvent) => {
    const card = e.active.data.current?.card as OrderCardDto | undefined;
    const key = e.over?.id != null ? String(e.over.id) : null;
    setActiveCard(null);
    if (!card || !key || !validTargets.has(key)) return;
    if (key === 'BLOCKED') setBlockTarget({ id: card.id, orderNumber: card.orderNumber });
    else if (key === 'CANCELLED') setCancelTarget({ id: card.id, orderNumber: card.orderNumber });
    else if (card.blocked) resume.mutate(card.id);
    else advance.mutate({ id: card.id, stageCode: key });
  };

  // Next stage for the detail sheet (next STAGE column after the order's current stage; null if terminal).
  const nextStageForDetail = useMemo(() => {
    if (!detail || detail.blocked) return null;
    const code = detail.order.currentStageCode;
    const stageCols = (board?.columns ?? []).filter((c) => c.kind === 'STAGE');
    const idx = stageCols.findIndex((c) => c.stageCode === code);
    const next = idx >= 0 ? stageCols[idx + 1] : undefined;
    return next?.stageCode ? { code: next.stageCode, label: next.label } : null;
  }, [detail, board]);

  const openOrders = useMemo(
    () => (board?.columns ?? []).filter((c) => c.kind !== 'CANCELLED').flatMap((c) => c.cards),
    [board],
  );

  const closeDetail = () => setOpenOrderId(null);

  if (!workflowCode) return <p className="text-sm text-muted-foreground">Loading…</p>;

  return (
    <div className="space-y-4">
      {/* ── Header ── */}
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="mr-auto text-2xl font-bold">Order board</h1>

        <Select value={workflowCode} onValueChange={(c) => { setWorkflowCode(c); setSprintId(null); }}>
          <SelectTrigger className="w-44" data-testid="board-workflow-picker"><SelectValue /></SelectTrigger>
          <SelectContent>
            {(workflows ?? []).map((w) => <SelectItem key={w.code} value={w.code}>{w.label}</SelectItem>)}
          </SelectContent>
        </Select>

        {branches.length > 0 && (
          <Select
            value={selectedBranchId != null ? String(selectedBranchId) : ALL_BRANCHES}
            onValueChange={(v) => selectBranch(v === ALL_BRANCHES ? null : Number(v))}
          >
            <SelectTrigger className="w-44" data-testid="board-branch-picker">
              <MapPin className="mr-1 h-4 w-4" /><SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_BRANCHES}>All branches</SelectItem>
              {branches.map((b) => <SelectItem key={b.id} value={String(b.id)}>{b.name}</SelectItem>)}
            </SelectContent>
          </Select>
        )}

        {sprints.length > 0 && (
          <Select
            value={sprint ? String(sprint.id) : ''}
            onValueChange={(v) => setSprintId(Number(v))}
          >
            <SelectTrigger className="w-44" data-testid="board-sprint-picker"><SelectValue placeholder="Sprint" /></SelectTrigger>
            <SelectContent>
              {sprints.map((s) => (
                <SelectItem key={s.id} value={String(s.id)}>
                  {s.name}{s.status === 'CLOSED' ? ' (closed)' : ''}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        )}

        {isAdmin && (
          <>
            <Button variant="outline" size="sm" onClick={() => setEditSprintOpen(true)}>
              {sprint ? <><Pencil className="mr-1 h-4 w-4" />Edit</> : <><Plus className="mr-1 h-4 w-4" />New sprint</>}
            </Button>
            {sprint && !readOnly && (
              <Button variant="outline" size="sm" onClick={() => setCompleteOpen(true)}>
                <CheckCircle2 className="mr-1 h-4 w-4" />Complete
              </Button>
            )}
            <Button size="sm" onClick={() => navigate('/products')} data-testid="board-new-order">
              <Plus className="mr-1 h-4 w-4" />New order
            </Button>
            <BulkUploadDialog
              title="Bulk upload orders"
              columns={['userId', 'itemType', 'itemId', 'quantity']}
              sampleRow={['1', 'PRODUCT', '1', '2']}
              templateName="orders-template.csv"
              onUpload={(file) => orderService.bulkUpload(file)}
            />
          </>
        )}
      </div>

      {/* Sprint progress */}
      {sprint?.startsOn && sprint?.endsOn && (
        <div className="space-y-1">
          <div className="flex justify-between text-xs text-muted-foreground">
            <span>{sprint.name} · {sprint.startsOn} → {sprint.endsOn}{readOnly ? ' · closed' : ''}</span>
            <span>{Math.max(0, daysBetween(new Date().toISOString(), sprint.endsOn))} days left</span>
          </div>
          <Progress
            value={Math.min(100, Math.max(0,
              (daysBetween(sprint.startsOn, new Date().toISOString()) / Math.max(1, daysBetween(sprint.startsOn, sprint.endsOn))) * 100))}
          />
        </div>
      )}

      {/* ── Filter bar ── */}
      <div className="flex flex-wrap items-center gap-3">
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search order / customer…"
          className="w-56"
          data-testid="board-search"
        />
        <ToggleGroup type="multiple" value={flagFilters} onValueChange={setFlagFilters} variant="outline" size="sm">
          <ToggleGroupItem value="blocked">Blocked <Badge variant="secondary" className="ml-1">{board?.flags.blocked ?? 0}</Badge></ToggleGroupItem>
          <ToggleGroupItem value="stuck">Stuck <Badge variant="secondary" className="ml-1">{board?.flags.stuck ?? 0}</Badge></ToggleGroupItem>
          <ToggleGroupItem value="unpaid">Unpaid <Badge variant="secondary" className="ml-1">{board?.flags.unpaid ?? 0}</Badge></ToggleGroupItem>
          <ToggleGroupItem value="unassigned">Unassigned <Badge variant="secondary" className="ml-1">{board?.flags.unassigned ?? 0}</Badge></ToggleGroupItem>
        </ToggleGroup>
        {staffUsers.length > 0 && (
          <ToggleGroup type="multiple" value={staffFilters} onValueChange={setStaffFilters} variant="outline" size="sm">
            {staffUsers.map((u) => (
              <ToggleGroupItem key={u.id} value={String(u.id)} title={[u.firstName, u.lastName].filter(Boolean).join(' ') || u.email}>
                {initials([u.firstName, u.lastName].filter(Boolean).join(' ') || u.email)}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
        )}
        {board && branchId == null && board.branches.length > 1 && (
          <span className="text-xs text-muted-foreground">
            {board.branches.map((b) => `${b.branchName ?? 'Unknown'}: ${b.count}`).join(' · ')}
          </span>
        )}
      </div>

      {/* ── Board ── */}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd}>
          {/* Fixed viewport-relative height so columns stretch to the bottom of the page and scroll
              internally, rather than growing with their card count. Offset ≈ the admin header + the
              board header/sprint/filter rows above. */}
          <div className="flex h-[calc(100vh-16rem)] gap-3 overflow-x-auto pb-4">
            {columns.map((col) => (
              <BoardColumn
                key={columnKey(col)}
                column={col}
                draggable={!readOnly && col.kind !== 'CANCELLED'}
                dragActive={activeCard != null}
                isValidTarget={validTargets.has(columnKey(col))}
                onOpen={(c) => setOpenOrderId(c.id)}
              />
            ))}
          </div>
          <DragOverlay>
            {activeCard && <OrderCard card={activeCard} draggable={false} onOpen={() => {}} dragging />}
          </DragOverlay>
        </DndContext>
      )}

      {/* ── Detail sheet ── */}
      <OrderDetailSheet
        detail={detail ?? null}
        open={openOrderId != null}
        isAdmin={isAdmin && !readOnly}
        staffUsers={staffUsers}
        staffRole={board?.staffRole}
        nextStage={readOnly ? null : nextStageForDetail}
        busy={busy}
        onOpenChange={(next) => !next && closeDetail()}
        onAdvance={(stageCode) => openOrderId && advance.mutate({ id: openOrderId, stageCode })}
        onBlock={() => detail && setBlockTarget({ id: detail.order.id, orderNumber: detail.order.orderNumber })}
        onResume={() => openOrderId && resume.mutate(openOrderId)}
        onAssign={(userId) => openOrderId && assign.mutate({ id: openOrderId, userId })}
        onCollectPayment={() => setCollectOpen(true)}
        onSendPaymentLink={() => openOrderId && sendLink.mutate(openOrderId)}
        onRefund={() => setRefundOpen(true)}
        onCancel={() => detail && setCancelTarget({ id: detail.order.id, orderNumber: detail.order.orderNumber })}
        onPrint={() => detail && printReceipt(
          detail.order.orderNumber,
          detail.order.items.map((it) => `${it.quantity}x ${it.name}  ${it.unitPrice * it.quantity}`)
            .concat([`\nTotal: ${detail.order.total} ${detail.order.currency}`]),
        )}
      />

      {/* ── Action dialogs ── */}
      <BlockDialog
        open={blockTarget != null}
        orderNumber={blockTarget?.orderNumber}
        pending={block.isPending}
        onOpenChange={(next) => !next && setBlockTarget(null)}
        onConfirm={(reason: BlockReason, note: string) =>
          blockTarget && block.mutate({ id: blockTarget.id, reason, note }, { onSuccess: () => setBlockTarget(null) })}
      />

      <ConfirmDialog
        open={cancelTarget != null}
        onOpenChange={() => setCancelTarget(null)}
        title="Cancel order?"
        description={<>This cancels “{cancelTarget?.orderNumber}” (admin override, stage refund policy applies).</>}
        confirmLabel="Cancel order"
        pendingLabel="Cancelling…"
        cancelLabel="Keep order"
        pending={cancel.isPending}
        destructive
        onConfirm={() => cancelTarget && cancel.mutate(
          { id: cancelTarget.id, reason: 'Admin cancellation' },
          { onSuccess: () => setCancelTarget(null) },
        )}
      />

      <CollectPaymentDialog
        open={collectOpen}
        detail={detail ?? null}
        pending={markPaid.isPending}
        onOpenChange={setCollectOpen}
        onConfirm={() => openOrderId && markPaid.mutate(openOrderId, { onSuccess: () => setCollectOpen(false) })}
      />

      <ConfirmDialog
        open={refundOpen}
        onOpenChange={() => setRefundOpen(false)}
        title="Refund order?"
        description={<>Issues a full goodwill refund for “{detail?.order.orderNumber}” without cancelling it.</>}
        confirmLabel="Issue refund"
        pendingLabel="Refunding…"
        pending={refund.isPending}
        destructive
        onConfirm={() => openOrderId && refund.mutate(
          { id: openOrderId, amount: null, reason: 'Goodwill refund' },
          { onSuccess: () => setRefundOpen(false) },
        )}
      />

      <EditSprintDialog
        open={editSprintOpen}
        sprint={sprint}
        pending={createSprint.isPending || updateSprint.isPending}
        onOpenChange={setEditSprintOpen}
        onSave={(values) => {
          const done = { onSuccess: () => setEditSprintOpen(false) };
          if (sprint) updateSprint.mutate({ id: sprint.id, req: { id: sprint.id, workflowCode, ...values } }, done);
          else createSprint.mutate({ workflowCode, ...values }, done);
        }}
      />

      <CompleteSprintDialog
        open={completeOpen}
        openOrders={openOrders}
        pending={completeSprint.isPending}
        onOpenChange={setCompleteOpen}
        onConfirm={(req) => sprint && completeSprint.mutate(
          { id: sprint.id, req },
          { onSuccess: () => { setCompleteOpen(false); setSprintId(null); } },
        )}
      />
    </div>
  );
}
