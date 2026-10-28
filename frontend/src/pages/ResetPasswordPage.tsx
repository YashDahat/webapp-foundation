import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { resetPassword } from '@/services/authService';
import { withToast } from '@/lib/apiToast';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { validatePassword } from '@/lib/passwordPolicy';

/** Set a new password using the token from the emailed reset link (/reset-password?token=...). */
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const pwdError = validatePassword(password);
    if (pwdError) { setError(pwdError); return; }
    if (password !== confirm) { setError('Passwords do not match.'); return; }
    setBusy(true);
    try {
      await withToast(resetPassword({ token: token as string, newPassword: password }), 'Password updated — please log in.');
      navigate('/login', { replace: true });
    } catch {
      // interceptor already toasted (e.g. expired link)
    } finally {
      setBusy(false);
    }
  };

  if (!token) {
    return (
      <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12">
        <Card>
          <CardHeader>
            <CardTitle>Invalid reset link</CardTitle>
            <CardDescription>This link is missing its token. Request a new reset link.</CardDescription>
          </CardHeader>
          <CardFooter>
            <Link to="/forgot-password" className="text-sm font-medium text-primary hover:underline">Request a new link</Link>
          </CardFooter>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12">
      <Card data-testid="reset-card">
        <CardHeader>
          <CardTitle>Choose a new password</CardTitle>
          <CardDescription>Enter a new password for your account.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="password">New password</Label>
              <Input id="password" type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} data-testid="reset-password" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirm">Confirm new password</Label>
              <Input id="confirm" type="password" placeholder="••••••••" value={confirm} onChange={(e) => setConfirm(e.target.value)} data-testid="reset-confirm" />
            </div>
            {error && <p className="text-sm font-medium text-destructive" data-testid="reset-error">{error}</p>}
            <Button type="submit" className="w-full" disabled={busy} data-testid="reset-submit">
              {busy ? 'Updating…' : 'Update password'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
