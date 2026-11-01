// React Query hooks over the analytics SDK. Mutations wrap withToast; failures auto-toast via the interceptor.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { analyticsService } from '@/services/analyticsService';
import { withToast } from '@/lib/apiToast';
import type {
  AnalyticsPeriod,
  AnalyticsSummary,
  DailyBusinessLogDto,
  UpsertDailyBusinessLogRequest,
} from '@/types/analytics';

export function useAnalyticsSummary(period: AnalyticsPeriod, branchId: number | null) {
  return useQuery<AnalyticsSummary>({
    queryKey: ['admin-analytics', period, branchId],
    queryFn: () => analyticsService.summary(period, branchId),
  });
}

export function useDailyLogs() {
  return useQuery<DailyBusinessLogDto[]>({
    queryKey: ['admin-daily-logs'],
    queryFn: analyticsService.listLogs,
  });
}

export function useUpsertDailyLog() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertDailyBusinessLogRequest) =>
      withToast(analyticsService.upsertLog(req), 'Day saved'),
    onSuccess: () => invalidate(qc),
  });
}

export function useDeleteDailyLog() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(analyticsService.removeLog(id), 'Entry deleted'),
    onSuccess: () => invalidate(qc),
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['admin-daily-logs'] });
  qc.invalidateQueries({ queryKey: ['admin-analytics'] });
}
