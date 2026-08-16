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

// Login identifier is email OR phone (India-first) — one field for both.
const schema = z.object({
  identifier: z.string().min(1, 'Enter your email or phone'),
  password: z.string().min(1, 'Enter your password'),
});
type Values = z.infer<typeof schema>;

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const redirectTo = searchParams.get('redirect') || '/';
  const signupHref = redirectTo === '/' ? '/signup' : `/signup?redirect=${encodeURIComponent(redirectTo)}`;
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { identifier: '', password: '' },
  });

  const onSubmit = async (values: Values) => {
    setFormError(null);
    try {
      await login(values.identifier, values.password);
      navigate(redirectTo, { replace: true }); // back to where the guest was (e.g. /checkout)
    } catch {
      setFormError('Invalid email/phone or password.');
    }
  };

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
                  <FormLabel>Password</FormLabel>
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
