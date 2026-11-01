// FOUNDATION-owned analytics SDK (com.webappfoundation AdminAnalyticsController).
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type {
  AnalyticsPeriod,
  AnalyticsSummary,
  DailyBusinessLogDto,
  UpsertDailyBusinessLogRequest,
} from '@/types/analytics';

export const analyticsService = {
  summary: async (period: AnalyticsPeriod, branchId?: number | null): Promise<AnalyticsSummary> => {
    const { data } = await apiClient.get<AnalyticsSummary>('/api/v1/admin/analytics', {
      params: { period, branchId: branchId ?? undefined },
    });
    return data;
  },

  listLogs: async (): Promise<DailyBusinessLogDto[]> => {
    const { data } = await apiClient.get<DailyBusinessLogDto[]>('/api/v1/admin/analytics/daily-log');
    return data;
  },
  upsertLog: async (request: UpsertDailyBusinessLogRequest): Promise<DailyBusinessLogDto> => {
    const { data } = await apiClient.post<DailyBusinessLogDto>('/api/v1/admin/analytics/daily-log', request);
    return data;
  },
  removeLog: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/admin/analytics/daily-log/${id}`);
  },
  bulkUpload: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>(
      '/api/v1/admin/analytics/daily-log/bulk-upload',
      form,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
  },
};
