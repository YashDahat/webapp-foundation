// FOUNDATION-owned Discount & Loyalty admin SDK (com.webappfoundation AdminLoyaltyController + AdminOfferController).
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type {
  CoinLedgerDto, CreateOfferRequest, DiscountBudgetDto, LoyaltyEventRuleDto, LoyaltyMeDto, LoyaltyMetricsDto,
  LoyaltyProfileDto, LoyaltySettingsDto, LoyaltyTierDto, LoyaltyTriggerConfigDto, OfferDto, OfferValidationResult,
  RecordActivityRequest, UpdateLoyaltyEventRuleRequest, UpdateLoyaltyTierRequest, UpdateLoyaltyTriggerConfigRequest,
  UpdateOfferRequest, UpsertDiscountBudgetRequest,
} from '@/types/loyalty';

const BASE = '/api/v1/admin/loyalty';

/**
 * Fire-and-forget behavioural beacon. Deliberately bypasses apiClient (whose interceptor would toast on
 * failure) — a silent beacon must never surface an error. Uses keepalive so it survives navigation.
 */
function fireActivity(req: RecordActivityRequest): void {
  try {
    const token = localStorage.getItem('token');
    void fetch('/api/v1/loyalty/activity', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify(req),
      keepalive: true,
    }).catch(() => { /* silent */ });
  } catch { /* silent */ }
}

export const loyaltyService = {
  // ── Settings (incl. discountsEnabled master kill switch) ──
  getSettings: async (): Promise<LoyaltySettingsDto> => {
    const { data } = await apiClient.get<LoyaltySettingsDto>(`${BASE}/settings`);
    return data;
  },
  updateSettings: async (req: LoyaltySettingsDto): Promise<LoyaltySettingsDto> => {
    const { data } = await apiClient.put<LoyaltySettingsDto>(`${BASE}/settings`, req);
    return data;
  },

  // ── Tiers ──
  listTiers: async (): Promise<LoyaltyTierDto[]> => {
    const { data } = await apiClient.get<LoyaltyTierDto[]>(`${BASE}/tiers`);
    return data;
  },
  updateTier: async (id: number, req: UpdateLoyaltyTierRequest): Promise<LoyaltyTierDto> => {
    const { data } = await apiClient.put<LoyaltyTierDto>(`${BASE}/tiers/${id}`, req);
    return data;
  },

  // ── Event scoring rules ──
  listEventRules: async (): Promise<LoyaltyEventRuleDto[]> => {
    const { data } = await apiClient.get<LoyaltyEventRuleDto[]>(`${BASE}/event-rules`);
    return data;
  },
  updateEventRule: async (id: number, req: UpdateLoyaltyEventRuleRequest): Promise<LoyaltyEventRuleDto> => {
    const { data } = await apiClient.put<LoyaltyEventRuleDto>(`${BASE}/event-rules/${id}`, req);
    return data;
  },

  // ── Triggers ──
  listTriggers: async (): Promise<LoyaltyTriggerConfigDto[]> => {
    const { data } = await apiClient.get<LoyaltyTriggerConfigDto[]>(`${BASE}/triggers`);
    return data;
  },
  updateTrigger: async (id: number, req: UpdateLoyaltyTriggerConfigRequest): Promise<LoyaltyTriggerConfigDto> => {
    const { data } = await apiClient.put<LoyaltyTriggerConfigDto>(`${BASE}/triggers/${id}`, req);
    return data;
  },

  // ── Monthly discount budget ──
  listBudgets: async (): Promise<DiscountBudgetDto[]> => {
    const { data } = await apiClient.get<DiscountBudgetDto[]>(`${BASE}/budgets`);
    return data;
  },
  upsertBudget: async (req: UpsertDiscountBudgetRequest): Promise<DiscountBudgetDto> => {
    const { data } = await apiClient.post<DiscountBudgetDto>(`${BASE}/budgets`, req);
    return data;
  },

  // ── Customers (read-only loyalty grid) ──
  listCustomers: async (): Promise<LoyaltyProfileDto[]> => {
    const { data } = await apiClient.get<LoyaltyProfileDto[]>(`${BASE}/customers`);
    return data;
  },

  // ── Metrics dashboard ──
  getMetrics: async (): Promise<LoyaltyMetricsDto> => {
    const { data } = await apiClient.get<LoyaltyMetricsDto>(`${BASE}/metrics`);
    return data;
  },

  // ── Codes / offers ──
  listOffers: async (): Promise<OfferDto[]> => {
    const { data } = await apiClient.get<OfferDto[]>(`${BASE}/offers`);
    return data;
  },
  createOffer: async (req: CreateOfferRequest): Promise<OfferDto> => {
    const { data } = await apiClient.post<OfferDto>(`${BASE}/offers`, req);
    return data;
  },
  updateOffer: async (id: number, req: UpdateOfferRequest): Promise<OfferDto> => {
    const { data } = await apiClient.put<OfferDto>(`${BASE}/offers/${id}`, req);
    return data;
  },
  revokeOffer: async (id: number): Promise<OfferDto> => {
    const { data } = await apiClient.post<OfferDto>(`${BASE}/offers/${id}/revoke`);
    return data;
  },
  deleteOffer: async (id: number): Promise<void> => {
    await apiClient.delete(`${BASE}/offers/${id}`);
  },
  bulkUploadOffers: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>(`${BASE}/offers/bulk-upload`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  // ── Customer-facing (my loyalty) ──
  getMe: async (): Promise<LoyaltyMeDto> => {
    const { data } = await apiClient.get<LoyaltyMeDto>('/api/v1/loyalty/me');
    return data;
  },
  setTrackingConsent: async (consent: boolean): Promise<LoyaltyMeDto> => {
    const { data } = await apiClient.post<LoyaltyMeDto>('/api/v1/loyalty/consent', { consent });
    return data;
  },
  getCoinHistory: async (): Promise<CoinLedgerDto[]> => {
    const { data } = await apiClient.get<CoinLedgerDto[]>('/api/v1/loyalty/coins');
    return data;
  },
  validateOffer: async (code: string, subtotal: number): Promise<OfferValidationResult> => {
    const { data } = await apiClient.post<OfferValidationResult>('/api/v1/loyalty/offers/validate', { code, subtotal });
    return data;
  },
  /** Fire-and-forget engagement beacon (ignored unless signed in + consented). */
  recordActivity: fireActivity,
};
