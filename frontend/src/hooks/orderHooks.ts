// React Query hooks over the order SDK. Mutations wrap withToast; failures auto-toast via the interceptor.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { orderService } from '@/services/orderService';
import { withToast } from '@/lib/apiToast';
import type { OrderDto } from '@/types/order';

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
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-orders'] }),
  });
}

export function useAdminCancelOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      withToast(orderService.adminCancel(id, reason), 'Order cancelled'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-orders'] }),
  });
}

export function useMarkPaid() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(orderService.markPaid(id), 'Marked paid'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-orders'] }),
  });
}
