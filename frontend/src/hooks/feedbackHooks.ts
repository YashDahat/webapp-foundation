// React Query hooks over the feedback SDK. Submit wraps withToast; failures auto-toast via the interceptor.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { feedbackService } from '@/services/feedbackService';
import { withToast } from '@/lib/apiToast';
import type { CreateFeedbackRequest, OrderFeedbackDto } from '@/types/feedback';

export function useSubmitFeedback(orderId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateFeedbackRequest) =>
      withToast(feedbackService.submit(orderId, req), 'Thanks for your feedback'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order', orderId] });
      qc.invalidateQueries({ queryKey: ['orders', 'mine'] });
    },
  });
}

export function useAdminFeedback() {
  return useQuery<OrderFeedbackDto[]>({ queryKey: ['admin-feedback'], queryFn: feedbackService.adminList });
}
