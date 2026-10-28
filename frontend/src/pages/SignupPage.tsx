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

// India-first signup: phone is required (a primary contact + alternate login identity).
const schema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  email: z.string().email('Enter a valid email'),
  phone: z.string().min(10, 'Enter a valid phone number'),
  // Backend PasswordPolicy: min 10 chars incl. a letter and a digit (and not a common password).
  password: z.string().regex(/^(?=.*[A-Za-z])(?=.*\d).{10,}$/, 'At least 10 chars, incl. a letter and a digit'),
});
type Values = z.infer<typeof schema>;

export default function SignupPage() {
  const { register, verifySignup } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const redirectTo = searchParams.get('redirect') || '/';
  const loginHref = redirectTo === '/' ? '/login' : `/login?redirect=${encodeURIComponent(redirectTo)}`;
  const [formError, setFormError] = useState<string | null>(null);

  // Email-verification second step: set once register returns a challenge.
  const [challengeId, setChallengeId] = useState<string | null>(null);
  const [otp, setOtp] = useState('');
  const [verifying, setVerifying] = useState(false);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '', phone: '', password: '' },
  });

  const onSubmit = async (values: Values) => {
    setFormError(null);
    try {
      const result = await register(values);
      if (result.verificationRequired && result.challengeId) {
        setChallengeId(result.challengeId); // show the OTP step
        return;
      }
      navigate(redirectTo, { replace: true });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string; detail?: string } }; message?: string };
      setFormError(e.response?.data?.message ?? e.response?.data?.detail ?? 'Could not create your account. Please try again.');
    }
  };

  const onVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    if (otp.length < 6) return;
    setFormError(null);
    setVerifying(true);
    try {
      await verifySignup(challengeId as string, otp);
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
        <Card data-testid="signup-verify-card">
          <CardHeader>
            <CardTitle>Verify your email</CardTitle>
            <CardDescription>We emailed you a 6-digit code. Enter it to activate your account.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={onVerify} className="space-y-4">
              <div className="flex justify-center">
                <InputOTP maxLength={6} value={otp} onChange={setOtp} data-testid="signup-otp">
                  <InputOTPGroup>
                    {Array.from({ length: 6 }).map((_, i) => <InputOTPSlot key={i} index={i} />)}
                  </InputOTPGroup>
                </InputOTP>
              </div>
              {formError && <p className="text-center text-sm font-medium text-destructive" data-testid="signup-verify-error">{formError}</p>}
              <Button type="submit" className="w-full" disabled={verifying || otp.length < 6} data-testid="signup-verify-submit">
                {verifying ? 'Verifying…' : 'Verify & continue'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12">
      <Card data-testid="signup-card">
        <CardHeader>
          <CardTitle>Create your account</CardTitle>
          <CardDescription>Sign up with your details — we'll email a code to verify your account.</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <FormField control={form.control} name="firstName" render={({ field }) => (
                  <FormItem>
                    <FormLabel>First name</FormLabel>
                    <FormControl><Input placeholder="Aarav" data-testid="signup-firstName" {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )} />
                <FormField control={form.control} name="lastName" render={({ field }) => (
                  <FormItem>
                    <FormLabel>Last name</FormLabel>
                    <FormControl><Input placeholder="Sharma" data-testid="signup-lastName" {...field} /></FormControl>
                    <FormMessage />
                  </FormItem>
                )} />
              </div>
              <FormField control={form.control} name="email" render={({ field }) => (
                <FormItem>
                  <FormLabel>Email</FormLabel>
                  <FormControl><Input type="email" placeholder="you@example.com" data-testid="signup-email" {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="phone" render={({ field }) => (
                <FormItem>
                  <FormLabel>Phone</FormLabel>
                  <FormControl><Input type="tel" placeholder="+91 98765 43210" data-testid="signup-phone" {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="password" render={({ field }) => (
                <FormItem>
                  <FormLabel>Password</FormLabel>
                  <FormControl><Input type="password" placeholder="••••••••" data-testid="signup-password" {...field} /></FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              {formError && <p className="text-sm font-medium text-destructive" data-testid="signup-error">{formError}</p>}
              <Button type="submit" className="w-full" disabled={form.formState.isSubmitting} data-testid="signup-submit">
                {form.formState.isSubmitting ? 'Creating account…' : 'Create account'}
              </Button>
            </form>
          </Form>
        </CardContent>
        <CardFooter className="justify-center text-sm text-muted-foreground">
          Already have an account?&nbsp;
          <Link to={loginHref} className="font-medium text-primary hover:underline" data-testid="link-login">Log in</Link>
        </CardFooter>
      </Card>
    </div>
  );
}
