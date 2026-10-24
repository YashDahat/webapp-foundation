// React Query hooks over the order SDK. Mutations wrap withToast; failures auto-toast via the interceptor.
import { useEffect, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { orderService } from '@/services/orderService';
import { userService } from '@/services/userService';
import { withToast } from '@/lib/apiToast';
import type { BlockReason, OrderBoardDetailDto, OrderBoardDto, OrderDto, OrderStageEventDto } from '@/types/order';
import type { UserDto } from '@/types/user';

// ── Customer ──
export function useMyOrders() {
  return useQuery<OrderDto[]>({ queryKey: ['orders', 'mine'], queryFn: orderService.myOrders });
}

export function useOrder(id: number | null) {
  return useQuery<OrderDto>({
    queryKey: ['order', id],
    queryFn: () => orderService.get(id as number),
    enabled: id != null,
  });
}

export function useCancelOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      withToast(orderService.cancel(id, reason), 'Order cancelled'),
    onSuccess: (o) => {
      qc.invalidateQueries({ queryKey: ['orders', 'mine'] });
      qc.invalidateQueries({ queryKey: ['order', o.id] });
    },
  });
}

// ── Admin ──
export function useAdminOrders() {
  return useQuery<OrderDto[]>({ queryKey: ['admin-orders'], queryFn: orderService.adminList });
}

export function useAdvanceStage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, stageCode }: { id: number; stageCode: string }) =>
      withToast(orderService.advanceStage(id, stageCode), 'Stage updated'),
    onSuccess: () => invalidateBoard(qc),
  });
}

export function useAdminCancelOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      withToast(orderService.adminCancel(id, reason), 'Order cancelled'),
    onSuccess: () => invalidateBoard(qc),
  });
}

export function useMarkPaid() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(orderService.markPaid(id), 'Marked paid'),
    onSuccess: () => invalidateBoard(qc),
  });
}

// ── Order board ──
// All board writes invalidate the (prefix-matched) board query + any open detail/history so the kanban,
// the detail sheet, and the old admin grid re-sync from one place.
function invalidateBoard(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['order-board'] });
  qc.invalidateQueries({ queryKey: ['order-detail'] });
  qc.invalidateQueries({ queryKey: ['order-history'] });
  qc.invalidateQueries({ queryKey: ['admin-orders'] });
}

export function useOrderBoard(
  workflowCode: string | null,
  sprintId: number | null,
  branchId: number | null,
) {
  return useQuery<OrderBoardDto>({
    queryKey: ['order-board', workflowCode, sprintId, branchId],
    queryFn: () => orderService.board(workflowCode as string, sprintId, branchId),
    enabled: !!workflowCode,
    // Polling fallback that keeps the board live if the SSE stream drops (see useOrderBoardStream).
    refetchInterval: 20_000,
  });
}

export function useOrderBoardDetail(id: number | null) {
  return useQuery<OrderBoardDetailDto>({
    queryKey: ['order-detail', id],
    queryFn: () => orderService.adminGet(id as number),
    enabled: id != null,
  });
}

export function useOrderHistory(id: number | null) {
  return useQuery<OrderStageEventDto[]>({
    queryKey: ['order-history', id],
    queryFn: () => orderService.history(id as number),
    enabled: id != null,
  });
}

/** STAFF users — the pool the board assigns orders to (assignee dropdown + filter toggles). */
export function useStaffUsers() {
  return useQuery<UserDto[]>({
    queryKey: ['staff-users'],
    queryFn: async () => (await userService.list()).filter((u) => u.role === 'STAFF'),
  });
}

export function useBlockOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason, note }: { id: number; reason: BlockReason; note?: string | null }) =>
      withToast(orderService.block(id, reason, note), 'Order blocked'),
    onSuccess: () => invalidateBoard(qc),
  });
}

export function useResumeOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(orderService.resume(id), 'Order resumed'),
    onSuccess: () => invalidateBoard(qc),
  });
}

export function useAssignOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, userId }: { id: number; userId: number | null }) =>
      withToast(orderService.assign(id, userId), userId == null ? 'Unassigned' : 'Assigned'),
    onSuccess: () => invalidateBoard(qc),
  });
}

export function useSendPaymentLink() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(orderService.sendPaymentLink(id), 'Payment link sent'),
    onSuccess: () => invalidateBoard(qc),
  });
}

export function useRefundOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, amount, reason }: { id: number; amount?: number | null; reason?: string | null }) =>
      withToast(orderService.refund(id, amount, reason), 'Refund issued'),
    onSuccess: () => invalidateBoard(qc),
  });
}

/**
 * Live board updates via Server-Sent Events. `EventSource` cannot attach the JWT Authorization header, so
 * this reads the stream with `fetch` (bearer token) and nudges the caller on each push. The board's polling
 * `refetchInterval` is the fallback if the stream drops; this reconnects automatically after a short delay.
 */
export function useOrderBoardStream(workflowCode: string | null, onPush: () => void) {
  const onPushRef = useRef(onPush);
  onPushRef.current = onPush;

  useEffect(() => {
    if (!workflowCode) return;
    const controller = new AbortController();
    let retry: ReturnType<typeof setTimeout> | undefined;
    let closed = false;

    const connect = async () => {
      const token = localStorage.getItem('token');
      try {
        const res = await fetch(
          `/api/v1/admin/orders/board/stream?workflowCode=${encodeURIComponent(workflowCode)}`,
          {
            headers: { Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
            signal: controller.signal,
          },
        );
        if (!res.ok || !res.body) throw new Error(`stream ${res.status}`);
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        // Read SSE frames (events are separated by a blank line); any data frame = a refetch nudge.
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let sep: number;
          while ((sep = buffer.indexOf('\n\n')) !== -1) {
            const frame = buffer.slice(0, sep);
            buffer = buffer.slice(sep + 2);
            if (frame.split('\n').some((line) => line.startsWith('data:'))) onPushRef.current();
          }
        }
      } catch {
        // Network error or non-OK response — swallow (polling covers it) and retry below.
      }
      if (!closed) retry = setTimeout(connect, 5_000);
    };

    connect();
    return () => {
      closed = true;
      if (retry) clearTimeout(retry);
      controller.abort();
    };
  }, [workflowCode]);
}
