import { useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '@/services/authService';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';

/** Request a password-reset link. Always shows the same confirmation (never reveals if the email exists). */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) return;
    setBusy(true);
    try {
      await forgotPassword(email.trim());
      setSent(true);
    } catch {
      // even on error we show the neutral confirmation; interceptor already toasts real failures
      setSent(true);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12">
      <Card data-testid="forgot-card">
        <CardHeader>
          <CardTitle>Reset your password</CardTitle>
          <CardDescription>Enter your account email and we'll send you a reset link.</CardDescription>
        </CardHeader>
        <CardContent>
          {sent ? (
            <p className="text-sm text-muted-foreground" data-testid="forgot-sent">
              If an account exists for <strong>{email}</strong>, we've emailed a link to reset your password.
              Check your inbox (and spam).
            </p>
          ) : (
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input id="email" type="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} data-testid="forgot-email" />
              </div>
              <Button type="submit" className="w-full" disabled={busy || !email.trim()} data-testid="forgot-submit">
                {busy ? 'Sending…' : 'Send reset link'}
              </Button>
            </form>
          )}
        </CardContent>
        <CardFooter className="justify-center text-sm text-muted-foreground">
          <Link to="/login" className="font-medium text-primary hover:underline">Back to login</Link>
        </CardFooter>
      </Card>
    </div>
  );
}
