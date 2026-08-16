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

// India-first signup: phone is required (a primary contact + alternate login identity).
const schema = z.object({
  firstName: z.string().min(1, 'First name is required'),
  lastName: z.string().min(1, 'Last name is required'),
  email: z.string().email('Enter a valid email'),
  phone: z.string().min(10, 'Enter a valid phone number'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});
type Values = z.infer<typeof schema>;

export default function SignupPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const redirectTo = searchParams.get('redirect') || '/';
  const loginHref = redirectTo === '/' ? '/login' : `/login?redirect=${encodeURIComponent(redirectTo)}`;
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', email: '', phone: '', password: '' },
  });

  const onSubmit = async (values: Values) => {
    setFormError(null);
    try {
      await register(values); // auto-logs in on success
      navigate(redirectTo, { replace: true }); // back to where the guest was (e.g. /checkout)
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string; detail?: string } }; message?: string };
      setFormError(e.response?.data?.message ?? e.response?.data?.detail ?? 'Could not create your account. Please try again.');
    }
  };

  return (
    <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12">
      <Card data-testid="signup-card">
        <CardHeader>
          <CardTitle>Create your account</CardTitle>
          <CardDescription>Sign up with your details — you'll be logged in automatically.</CardDescription>
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
