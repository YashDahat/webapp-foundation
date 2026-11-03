// React Query hooks over the loyalty admin SDK. Mutations wrap withToast; failures auto-toast via the interceptor.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { loyaltyService } from '@/services/loyaltyService';
import { withToast } from '@/lib/apiToast';
import type {
  CreateOfferRequest, LoyaltySettingsDto, UpdateLoyaltyEventRuleRequest, UpdateLoyaltyTierRequest,
  UpdateLoyaltyTriggerConfigRequest, UpdateOfferRequest, UpsertDiscountBudgetRequest,
} from '@/types/loyalty';

// ── Settings ──
export function useLoyaltySettings() {
  return useQuery({ queryKey: ['loyalty-settings'], queryFn: loyaltyService.getSettings });
}

export function useUpdateLoyaltySettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: LoyaltySettingsDto) => withToast(loyaltyService.updateSettings(req), 'Settings saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-settings'] }),
  });
}

// ── Tiers ──
export function useLoyaltyTiers() {
  return useQuery({ queryKey: ['loyalty-tiers'], queryFn: loyaltyService.listTiers });
}

export function useUpdateLoyaltyTier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpdateLoyaltyTierRequest }) =>
      withToast(loyaltyService.updateTier(id, req), 'Tier saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-tiers'] }),
  });
}

// ── Event scoring rules ──
export function useLoyaltyEventRules() {
  return useQuery({ queryKey: ['loyalty-event-rules'], queryFn: loyaltyService.listEventRules });
}

export function useUpdateLoyaltyEventRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpdateLoyaltyEventRuleRequest }) =>
      withToast(loyaltyService.updateEventRule(id, req), 'Scoring rule saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-event-rules'] }),
  });
}

// ── Triggers ──
export function useLoyaltyTriggers() {
  return useQuery({ queryKey: ['loyalty-triggers'], queryFn: loyaltyService.listTriggers });
}

export function useUpdateLoyaltyTrigger() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpdateLoyaltyTriggerConfigRequest }) =>
      withToast(loyaltyService.updateTrigger(id, req), 'Trigger saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-triggers'] }),
  });
}

// ── Budgets ──
export function useDiscountBudgets() {
  return useQuery({ queryKey: ['loyalty-budgets'], queryFn: loyaltyService.listBudgets });
}

export function useUpsertDiscountBudget() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertDiscountBudgetRequest) => withToast(loyaltyService.upsertBudget(req), 'Budget saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-budgets'] }),
  });
}

// ── Customers ──
export function useLoyaltyCustomers() {
  return useQuery({ queryKey: ['loyalty-customers'], queryFn: loyaltyService.listCustomers });
}

// ── Metrics ──
export function useLoyaltyMetrics() {
  return useQuery({ queryKey: ['loyalty-metrics'], queryFn: loyaltyService.getMetrics });
}

// ── Codes / offers ──
export function useLoyaltyOffers() {
  return useQuery({ queryKey: ['loyalty-offers'], queryFn: loyaltyService.listOffers });
}

export function useCreateOffer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateOfferRequest) => withToast(loyaltyService.createOffer(req), 'Code created'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-offers'] }),
  });
}

export function useUpdateOffer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpdateOfferRequest }) =>
      withToast(loyaltyService.updateOffer(id, req), 'Code updated'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-offers'] }),
  });
}

export function useRevokeOffer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(loyaltyService.revokeOffer(id), 'Code revoked'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-offers'] }),
  });
}

export function useDeleteOffer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(loyaltyService.deleteOffer(id), 'Code deleted'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-offers'] }),
  });
}

// ── Customer-facing (my loyalty) ──
export function useLoyaltyMe() {
  return useQuery({ queryKey: ['loyalty-me'], queryFn: loyaltyService.getMe });
}

export function useSetTrackingConsent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (consent: boolean) => withToast(loyaltyService.setTrackingConsent(consent), 'Preference saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['loyalty-me'] }),
  });
}

export function useCoinHistory() {
  return useQuery({ queryKey: ['loyalty-coins'], queryFn: loyaltyService.getCoinHistory });
}
