import { useState } from 'react';
import { Star } from 'lucide-react';
import { useSubmitFeedback } from '@/hooks/feedbackHooks';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

/**
 * Minimal post-checkout feedback: pick 1–5 stars (friction near zero), optionally add a one-line comment,
 * submit. One per order — the caller renders this only while feedback hasn't been submitted.
 */
export default function FeedbackForm({ orderId }: { orderId: number }) {
  const submit = useSubmitFeedback(orderId);
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState(0);
  const [comment, setComment] = useState('');

  const send = () => {
    if (rating === 0) return;
    submit.mutate({ rating, comment: comment.trim() || null });
  };

  return (
    <Card data-testid="feedback-form">
      <CardHeader>
        <CardTitle className="text-base">How was your experience?</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex gap-1" onMouseLeave={() => setHover(0)}>
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              aria-label={`${n} star${n > 1 ? 's' : ''}`}
              onMouseEnter={() => setHover(n)}
              onClick={() => setRating(n)}
              className="p-0.5"
            >
              <Star
                size={28}
                className={(hover || rating) >= n ? 'fill-yellow-400 text-yellow-400' : 'text-muted-foreground'}
              />
            </button>
          ))}
        </div>
        <Input
          placeholder="Add a comment (optional)"
          value={comment}
          onChange={(e) => setComment(e.target.value)}
        />
        <Button disabled={rating === 0 || submit.isPending} onClick={send}>
          {submit.isPending ? 'Submitting…' : 'Submit feedback'}
        </Button>
      </CardContent>
    </Card>
  );
}
