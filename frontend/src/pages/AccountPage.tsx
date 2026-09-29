import { useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { withToast } from '@/lib/apiToast';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

const STRONG = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/; // matches backend PasswordPolicy

/**
 * Account settings: toggle email-OTP two-factor auth and change your password. (The backend exposes
 * enable/disable actions only, so the 2FA switch reflects the last action taken this session.)
 */
export default function AccountPage() {
  const { user, enableTwoFactor, disableTwoFactor, changePassword } = useAuth();
  const [twoFactor, setTwoFactor] = useState(false);
  const [busy, setBusy] = useState(false);

  const onToggle = async (next: boolean) => {
    setBusy(true);
    try {
      await withToast(
        next ? enableTwoFactor() : disableTwoFactor(),
        next ? 'Two-factor authentication enabled' : 'Two-factor authentication disabled',
      );
      setTwoFactor(next);
    } catch {
      // Failure already toasted by the apiClient interceptor; leave the switch unchanged.
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-8">
      <h1 className="text-2xl font-bold">Account</h1>
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Security</CardTitle>
          <CardDescription>Signed in as {user?.username}.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between gap-4">
            <div>
              <Label htmlFor="twofactor" className="text-sm font-medium">Two-factor authentication</Label>
              <p className="text-sm text-muted-foreground">
                Require a 6-digit code emailed to you each time you log in.
              </p>
            </div>
            <Switch id="twofactor" checked={twoFactor} disabled={busy} onCheckedChange={onToggle} data-testid="twofactor-switch" />
          </div>
        </CardContent>
      </Card>
      <ChangePasswordCard onChange={changePassword} />
    </div>
  );
}

function ChangePasswordCard({ onChange }: { onChange: (current: string, next: string) => Promise<void> }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!STRONG.test(next)) { setError('New password must be at least 8 characters and include a letter and a digit.'); return; }
    if (next !== confirm) { setError('New passwords do not match.'); return; }
    setBusy(true);
    try {
      await withToast(onChange(current, next), 'Password changed');
      setCurrent(''); setNext(''); setConfirm('');
    } catch {
      // interceptor toasts real failures (e.g. wrong current password)
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Change password</CardTitle>
        <CardDescription>Enter your current password and choose a new one.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit} className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="current">Current password</Label>
            <Input id="current" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} data-testid="cp-current" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="new">New password</Label>
            <Input id="new" type="password" value={next} onChange={(e) => setNext(e.target.value)} data-testid="cp-new" />
          </div>
          <div className="space-y-2">
            <Label htmlFor="confirm">Confirm new password</Label>
            <Input id="confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} data-testid="cp-confirm" />
          </div>
          {error && <p className="text-sm font-medium text-destructive" data-testid="cp-error">{error}</p>}
          <Button type="submit" disabled={busy || !current || !next} data-testid="cp-submit">
            {busy ? 'Saving…' : 'Change password'}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
