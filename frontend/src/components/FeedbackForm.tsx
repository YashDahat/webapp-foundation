import { useState } from 'react';
import { ArrowRight } from 'lucide-react';
import { useSubmitFeedback } from '@/hooks/feedbackHooks';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

const LEVELS = [
  { n: 1, label: 'Poor' },
  { n: 2, label: 'Fair' },
  { n: 3, label: 'Okay' },
  { n: 4, label: 'Good' },
  { n: 5, label: 'Excellent' },
] as const;

/**
 * Post-checkout feedback as a big, non-ignorable takeover dialog: one tap on a 1–5 rating card, optionally
 * a one-line comment, submit. One per order — the caller renders this only while feedback hasn't been
 * submitted, so the dialog auto-opens on the order page and stays until rated. It is intentionally
 * NOT dismissible (no close button, Esc/backdrop blocked): submitting a rating is the only way out, so
 * no feedback is lost. It closes on a successful submit (and the order then reads feedbackSubmitted=true).
 */
export default function FeedbackForm({ orderId }: { orderId: number }) {
  const submit = useSubmitFeedback(orderId);
  const [open, setOpen] = useState(true);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');

  const send = () => {
    if (rating === 0) return;
    submit.mutate({ rating, comment: comment.trim() || null }, { onSuccess: () => setOpen(false) });
  };

  return (
    <Dialog open={open}>
      <DialogContent
        data-testid="feedback-form"
        className="max-w-4xl gap-8 p-8 [&>button]:hidden sm:p-12"
        onEscapeKeyDown={(e) => e.preventDefault()}
        onInteractOutside={(e) => e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle className="text-4xl font-black tracking-tight text-foreground/90 sm:text-6xl">
            How did we do?
          </DialogTitle>
          <DialogDescription className="sr-only">
            Rate your order experience from 1 (Poor) to 5 (Excellent).
          </DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-5 gap-2 sm:gap-4">
          {LEVELS.map(({ n, label }) => (
            <button
              key={n}
              type="button"
              aria-label={`${n} — ${label}`}
              aria-pressed={rating === n}
              onClick={() => setRating(n)}
              className={cn(
                'group flex min-h-[112px] flex-col justify-between rounded-lg border p-3 text-left transition-colors sm:min-h-[180px] sm:p-5',
                rating === n
                  ? 'border-primary bg-primary/5 ring-2 ring-primary'
                  : 'border-border hover:border-foreground/40 hover:bg-muted/50',
              )}
            >
              <span
                className={cn(
                  'text-4xl font-black leading-none sm:text-7xl',
                  rating === n
                    ? 'text-primary'
                    : 'text-muted-foreground/30 group-hover:text-muted-foreground/60',
                )}
              >
                {n}
              </span>
              <span
                className={cn(
                  'text-xs sm:text-sm',
                  rating === n ? 'font-medium text-foreground' : 'text-muted-foreground',
                )}
              >
                {label}
              </span>
            </button>
          ))}
        </div>

        {rating > 0 && (
          <Input
            placeholder="Tell us more (optional)"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
        )}

        <DialogFooter className="flex-row items-center justify-between gap-4 sm:justify-between">
          <p className="text-sm text-muted-foreground">
            {rating === 0 ? 'Pick a number to continue.' : `You rated us "${LEVELS[rating - 1].label}".`}
          </p>
          <Button disabled={rating === 0 || submit.isPending} onClick={send}>
            {submit.isPending ? 'Submitting…' : 'Submit rating'}
            <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
