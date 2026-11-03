import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { loyaltyService } from '@/services/loyaltyService';
import { useCoinHistory, useLoyaltyMe, useSetTrackingConsent } from '@/hooks/loyaltyHooks';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';

/**
 * The signed-in customer's loyalty panel: tier, Loyalty Score, progress-to-next-tier nudge, coin balance,
 * and the DPDP tracking-consent toggle. Also fires a once-per-day DAILY_VISIT beacon (server-capped).
 * Rendered on the account page.
 */
export default function LoyaltyCard() {
  const { data: me, isLoading } = useLoyaltyMe();
  const { data: coinHistory } = useCoinHistory();
  const setConsent = useSetTrackingConsent();

  // Daily-visit engagement signal (ignored unless consented; capped to 1/day server-side).
  useEffect(() => {
    if (me?.trackingConsent) loyaltyService.recordActivity({ type: 'DAILY_VISIT' });
  }, [me?.trackingConsent]);

  if (isLoading || !me) {
    return (
      <Card>
        <CardHeader><CardTitle className="text-base">Rewards</CardTitle></CardHeader>
        <CardContent><p className="text-sm text-muted-foreground">Loading…</p></CardContent>
      </Card>
    );
  }

  const nextMin = me.score + me.pointsToNextTier;
  const progress = me.nextTier && nextMin > 0 ? Math.min(100, (me.score / nextMin) * 100) : 100;

  return (
    <Card data-testid="loyalty-card">
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle className="text-base">Rewards</CardTitle>
          <Badge variant="secondary" data-testid="loyalty-tier">{me.tierName ?? me.tier}</Badge>
        </div>
        <CardDescription>Earn points for engagement and coins you can redeem.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <div className="text-2xl font-bold" data-testid="loyalty-score">{Math.round(me.score)}</div>
            <div className="text-xs text-muted-foreground">Loyalty score</div>
          </div>
          <div>
            <div className="text-2xl font-bold">{Math.round(me.coinBalance)}</div>
            <div className="text-xs text-muted-foreground">Reward coins · a discount, not cash</div>
          </div>
        </div>

        {me.nextTier ? (
          <div className="space-y-2">
            <Progress value={progress} />
            <p className={`text-sm ${me.nearNextTier ? 'font-medium text-foreground' : 'text-muted-foreground'}`}>
              You’re {me.pointsToNextTier} point{me.pointsToNextTier === 1 ? '' : 's'} from {me.nextTier}
              {me.nearNextTier ? ' — almost there!' : ''}
            </p>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">You’re at our top tier. Thank you!</p>
        )}

        <div className="flex items-center justify-between gap-4 border-t pt-4">
          <div>
            <Label htmlFor="loyalty-consent" className="text-sm font-medium">Personalised rewards</Label>
            <p className="text-sm text-muted-foreground">
              Let us track your activity to tailor offers. You can turn this off any time.{' '}
              <Link to="/loyalty-terms" className="underline">Rewards terms</Link>
            </p>
          </div>
          <Switch
            id="loyalty-consent"
            data-testid="loyalty-consent-switch"
            checked={me.trackingConsent}
            disabled={setConsent.isPending}
            onCheckedChange={(v) => setConsent.mutate(v)}
          />
        </div>

        {coinHistory && coinHistory.length > 0 && (
          <div className="border-t pt-4">
            <p className="mb-2 text-sm font-medium">Coin history</p>
            <ul className="space-y-1" data-testid="loyalty-coin-history">
              {coinHistory.slice(0, 6).map((c) => (
                <li key={c.id} className="flex items-center justify-between text-sm">
                  <span className="capitalize text-muted-foreground">
                    {c.reason}
                    {c.status !== 'CONFIRMED' ? <span className="ml-1 text-xs">({c.status.toLowerCase()})</span> : null}
                  </span>
                  <span className={c.coins >= 0 ? 'text-foreground' : 'text-muted-foreground'}>
                    {c.coins >= 0 ? '+' : ''}{Math.round(c.coins)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
