// FOUNDATION-owned Discount & Loyalty types (mirror the backend loyalty DTOs). A Loyalty Score measures
// engagement (never spent); Reward Coins are the redeemable ledger; discount codes are issued by triggers.

export type Tier = 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM';

export type LoyaltyEventType =
  | 'SIGNUP' | 'PROFILE_COMPLETE' | 'DAILY_VISIT' | 'PRODUCT_VIEW' | 'WISHLIST' | 'ADD_TO_CART'
  | 'PURCHASE' | 'REVIEW' | 'REVIEW_MEDIA' | 'SHARE_CLICK' | 'REFERRAL_SIGNUP' | 'REFERRAL_PURCHASE'
  | 'NOTIF_OPT_IN';

export type OfferTrigger =
  | 'WELCOME' | 'ABANDONED_CART' | 'WIN_BACK' | 'TIER_UP' | 'MILESTONE' | 'BIRTHDAY' | 'SLOW_STOCK'
  | 'FESTIVE' | 'SURPRISE' | 'MANUAL';

export type OfferType = 'PCT' | 'FLAT' | 'FREE_SHIP' | 'GIFT' | 'COINS';

export type OfferStatus = 'ACTIVE' | 'USED' | 'EXPIRED' | 'REVOKED';

/** Global programme settings. `discountsEnabled` is the client-controllable master kill switch. */
export interface LoyaltySettingsDto {
  discountsEnabled: boolean;
  coinValue: number;
  maxCoinPctOfOrder: number;
  minOrderForCoins: number;
  coinExpiryMonths: number;
  scoreHalfLifeDays: number;
  dailyCap: number;
  monthlyCap: number;
  holdoutPct: number;
  discountPctTarget: number;
  returnRateBlockPct: number;
  newCodeCooldownDays: number;
  returnWindowDays: number;
  marginFloorPct: number;
}

export interface LoyaltyTierDto {
  id: number;
  tier: Tier;
  name: string;
  minScore: number;
  coinsPer100: number;
  perks?: string | null;
}

export interface UpdateLoyaltyTierRequest {
  name?: string;
  minScore?: number;
  coinsPer100?: number;
  perks?: string | null;
}

export interface LoyaltyEventRuleDto {
  id: number;
  eventType: LoyaltyEventType;
  points: number;
  perDayCap?: number | null;
  onceOnly: boolean;
  earnsCoins: boolean;
}

export interface UpdateLoyaltyEventRuleRequest {
  points?: number;
  perDayCap?: number | null;
  onceOnly?: boolean;
  earnsCoins?: boolean;
}

export interface LoyaltyTriggerConfigDto {
  id: number;
  trigger: OfferTrigger;
  enabled: boolean;
  rungsJson?: string | null;
  expiryDays: number;
}

export interface UpdateLoyaltyTriggerConfigRequest {
  enabled?: boolean;
  rungsJson?: string | null;
  expiryDays?: number;
}

export interface DiscountBudgetDto {
  id: number;
  month: string;
  budgetAmount: number;
  spentToDate: number;
}

export interface UpsertDiscountBudgetRequest {
  month: string;
  budgetAmount: number;
}

export interface OfferDto {
  id: number;
  code: string;
  userId?: number | null;
  userEmail?: string | null;
  trigger: OfferTrigger;
  rung?: number | null;
  type: OfferType;
  value?: number | null;
  minSpend?: number | null;
  maxDiscount?: number | null;
  maxUses: number;
  usedCount: number;
  startsAt?: string | null;
  expiresAt?: string | null;
  campaignId?: string | null;
  status: OfferStatus;
  createdAt: string;
}

/** Edit payload for an existing code. `code` and `userId` are immutable, so they're omitted. */
export interface UpdateOfferRequest {
  trigger?: OfferTrigger | null;
  type: OfferType;
  value?: number | null;
  minSpend?: number | null;
  maxDiscount?: number | null;
  maxUses?: number | null;
  startsAt?: string | null;
  expiresAt?: string | null;
  campaignId?: string | null;
}

export interface CreateOfferRequest {
  code?: string | null;
  userId?: number | null;
  /** Reporting label; backend restricts hand-created codes to FESTIVE/MANUAL/MILESTONE/TIER_UP/SURPRISE. */
  trigger?: OfferTrigger | null;
  type: OfferType;
  value?: number | null;
  minSpend?: number | null;
  maxDiscount?: number | null;
  maxUses?: number | null;
  startsAt?: string | null;
  expiresAt?: string | null;
  campaignId?: string | null;
}

export interface LoyaltyProfileDto {
  userId: number;
  userEmail?: string | null;
  userName?: string | null;
  tier: Tier;
  score: number;
  coinBalance: number;
  avgOrderValue: number;
  usualOrderGapDays?: number | null;
  lastOrderAt?: string | null;
  returnRate: number;
  inHoldout: boolean;
  birthday?: string | null;
  scoreUpdatedAt?: string | null;
}

// ── Admin metrics dashboard ──

export interface LoyaltyBudgetMetrics {
  month: string;
  budgetAmount: number;
  spentToDate: number;
  grossSales: number;
  discountPctOfGross: number;
  pctOfBudgetUsed: number;
  targetPct: number;
}

export interface LoyaltyHoldoutComparison {
  memberRepeatRate: number;
  holdoutRepeatRate: number;
  memberAvgRevenue: number;
  holdoutAvgRevenue: number;
  incrementalRevenuePerCustomer: number;
}

export interface LoyaltyMetricsDto {
  members: number;
  holdout: number;
  budget: LoyaltyBudgetMetrics;
  coinLiability: number;
  tierMix: { tier: Tier; count: number }[];
  redemptionByTrigger: { trigger: OfferTrigger; issued: number; redeemed: number }[];
  holdoutComparison: LoyaltyHoldoutComparison;
}

// ── Customer-facing (my loyalty) ──

/** The signed-in customer's own loyalty snapshot (account page). */
export interface LoyaltyMeDto {
  userId: number;
  tier: Tier;
  tierName?: string | null;
  score: number;
  coinBalance: number;
  nextTier?: Tier | null;
  pointsToNextTier: number;
  nearNextTier: boolean;
  inHoldout: boolean;
  trackingConsent: boolean;
  discountsEnabled: boolean;
}

export type CoinStatus = 'PENDING' | 'CONFIRMED' | 'REVERSED' | 'EXPIRED';

/** A single reward-coin ledger line (customer coin history). */
export interface CoinLedgerDto {
  id: number;
  coins: number;      // signed: + earned, − redeemed/expired
  reason: string;
  status: CoinStatus;
  earnedAt: string;
  expiresAt?: string | null;
}

/** The result of validating a discount code against a cart (checkout "have a code?" preview). */
export interface OfferValidationResult {
  valid: boolean;
  code?: string | null;
  amount: number;       // positive rupees off (0 for non-cash / invalid)
  message?: string | null;
}

/** Low-value engagement types a client may self-report via the activity beacon. */
export type ActivityType = 'PRODUCT_VIEW' | 'WISHLIST' | 'ADD_TO_CART' | 'DAILY_VISIT' | 'SHARE_CLICK';

export interface RecordActivityRequest {
  type: ActivityType;
  itemType?: string | null;
  itemId?: number | null;
}
