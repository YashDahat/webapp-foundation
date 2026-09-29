import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Form, FormControl, FormField, FormItem, FormLabel, FormMessage,
} from '@/components/ui/form';
import {
  Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter,
} from '@/components/ui/card';
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp';

// Login identifier is email OR phone (India-first) — one field for both.
const schema = z.object({
  identifier: z.string().min(1, 'Enter your email or phone'),
  password: z.string().min(1, 'Enter your password'),
});
type Values = z.infer<typeof schema>;

export default function LoginPage() {
  const { login, verifyTwoFactor, verifySignup } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const redirectTo = searchParams.get('redirect') || '/';
  const signupHref = redirectTo === '/' ? '/signup' : `/signup?redirect=${encodeURIComponent(redirectTo)}`;
  const [formError, setFormError] = useState<string | null>(null);

  // OTP second step: set once login reports a challenge. `kind` picks which verify call to make.
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [challengeKind, setChallengeKind] = useState<'2fa' | 'verify'>('2fa');
  const [otp, setOtp] = useState('');
  const [verifying, setVerifying] = useState(false);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { identifier: '', password: '' },
  });

  const onSubmit = async (values: Values) => {
    setFormError(null);
    try {
      const result = await login(values.identifier, values.password);
      if ((result.twoFactorRequired || result.verificationRequired) && result.challengeId) {
        setChallengeKind(result.verificationRequired ? 'verify' : '2fa');
        setChallengeId(result.challengeId); // show the OTP step
        return;
      }
      navigate(redirectTo, { replace: true }); // back to where the guest was (e.g. /checkout)
    } catch {
      setFormError('Invalid email/phone or password.');
    }
  };

  const onVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    if (otp.length < 6) return;
    setFormError(null);
    setVerifying(true);
    try {
      if (challengeKind === 'verify') {
        await verifySignup(challengeId as string, otp);
      } else {
        await verifyTwoFactor(challengeId as string, otp);
      }
      navigate(redirectTo, { replace: true });
    } catch {
      setFormError('Invalid or expired code. Please try again.');
      setOtp('');
    } finally {
      setVerifying(false);
    }
  };

  if (challengeId) {
    return (
      <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12">
        <Card data-testid="login-2fa-card">
          <CardHeader>
            <CardTitle>{challengeKind === 'verify' ? 'Verify your email' : 'Enter your code'}</CardTitle>
            <CardDescription>
              {challengeKind === 'verify'
                ? "Your email isn't verified yet — we emailed a 6-digit code. Enter it to finish."
                : 'We emailed you a 6-digit verification code. Enter it to finish signing in.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={onVerify} className="space-y-4">
              <div className="flex justify-center">
                <InputOTP maxLength={6} value={otp} onChange={setOtp} data-testid="login-otp">
                  <InputOTPGroup>
                    {Array.from({ length: 6 }).map((_, i) => <InputOTPSlot key={i} index={i} />)}
                  </InputOTPGroup>
                </InputOTP>
              </div>
              {formError && <p className="text-center text-sm font-medium text-destructive" data-testid="login-2fa-error">{formError}</p>}
              <Button type="submit" className="w-full" disabled={verifying || otp.length < 6} data-testid="login-2fa-submit">
                {verifying ? 'Verifying…' : 'Verify'}
              </Button>
              <Button type="button" variant="ghost" className="w-full" onClick={() => { setChallengeId(null); setOtp(''); setFormError(null); }}>
                Back to login
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12">
      <Card data-testid="login-card">
        <CardHeader>
          <CardTitle>Welcome back</CardTitle>
          <CardDescription>Log in with your email or phone number.</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <FormField control={form.control} name="identifier" render={({ field }) => (
                <FormItem>
                  <FormLabel>Email or phone</FormLabel>
                  <FormControl><Input placeholder="you@example.com or +91…" data-testid="login-identifier" {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="password" render={({ field }) => (
                <FormItem>
                  <div className="flex items-center justify-between">
                    <FormLabel>Password</FormLabel>
                    <Link to="/forgot-password" className="text-xs font-medium text-primary hover:underline" data-testid="link-forgot">Forgot password?</Link>
                  </div>
                  <FormControl><Input type="password" placeholder="••••••••" data-testid="login-password" {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              {formError && <p className="text-sm font-medium text-destructive" data-testid="login-error">{formError}</p>}
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting} data-testid="login-submit">
                {form.formState.isSubmitting ? 'Logging in…' : 'Log in'}
              </Button>
            </form>
          </Form>
        </CardContent>
        <CardFooter className="justify-center text-sm text-muted-foreground">
          New here?&nbsp;
          <Link to={signupHref} className="font-medium text-primary hover:underline" data-testid="link-signup">Create an account</Link>
        </CardFooter>
      </Card>
    </div>
  );
}
