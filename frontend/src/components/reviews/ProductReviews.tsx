import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Star } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useDeleteMyReview, useProductReviews, useSubmitReview } from '@/hooks/reviewHooks';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { cn } from '@/lib/utils';

/** Read-only star row (1–5). `size` is the icon size in px. */
function Stars({ value, size = 16 }: { value: number; size?: number }) {
  return (
    <div className="flex items-center" aria-label={`${value} out of 5 stars`}>
      {[1, 2, 3, 4, 5].map((n) => (
        <Star
          key={n}
          style={{ width: size, height: size }}
          className={cn(n <= Math.round(value) ? 'fill-amber-400 text-amber-400' : 'fill-transparent text-muted-foreground/40')}
        />
      ))}
    </div>
  );
}

/** Clickable star input used in the write-a-review form. */
function StarInput({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  const [hover, setHover] = useState(0);
  const shown = hover || value;
  return (
    <div className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          aria-label={`${n} star${n > 1 ? 's' : ''}`}
          onMouseEnter={() => setHover(n)}
          onMouseLeave={() => setHover(0)}
          onClick={() => onChange(n)}
          className="p-0.5"
        >
          <Star className={cn('h-6 w-6', n <= shown ? 'fill-amber-400 text-amber-400' : 'fill-transparent text-muted-foreground/40')} />
        </button>
      ))}
    </div>
  );
}

/**
 * Product reviews block for the detail page (Decathlon/Zomato-style): the average rating + star
 * distribution, a write/edit form for signed-in users (one review per user, editable), and the list of
 * reviews newest first. Guests see a prompt to sign in. Mounted at the bottom of ProductDetailPage.
 */
export default function ProductReviews({ productId }: { productId: number }) {
  const { isAuthenticated } = useAuth();
  const { data, isLoading } = useProductReviews(productId);
  const submit = useSubmitReview(productId);
  const remove = useDeleteMyReview(productId);

  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Prefill the form from the caller's existing review (so "submit" edits it).
  const mine = data?.myReview ?? null;
  useEffect(() => {
    setRating(mine?.rating ?? 0);
    setComment(mine?.comment ?? '');
  }, [mine]);

  if (isLoading || !data) {
    return null;
  }

  const total = data.count;
  const save = () => {
    if (rating < 1) return;
    submit.mutate({ rating, comment: comment.trim() || null });
  };

  return (
    <section className="space-y-6 border-t pt-8" data-testid="product-reviews">
      <h2 className="text-lg font-semibold">Ratings &amp; reviews</h2>

      {/* Summary */}
      <div className="flex flex-col gap-6 sm:flex-row sm:items-center">
        <div className="flex flex-col items-center justify-center rounded-lg bg-muted/40 px-6 py-4">
          <span className="text-4xl font-bold">{total > 0 ? data.average.toFixed(1) : '—'}</span>
          <Stars value={data.average} />
          <span className="mt-1 text-xs text-muted-foreground">
            {total} {total === 1 ? 'review' : 'reviews'}
          </span>
        </div>
        {total > 0 && (
          <div className="flex-1 space-y-1">
            {[5, 4, 3, 2, 1].map((star) => {
              const count = data.distribution?.[star] ?? 0;
              const pct = total > 0 ? (count / total) * 100 : 0;
              return (
                <div key={star} className="flex items-center gap-2 text-xs">
                  <span className="w-3 text-muted-foreground">{star}</span>
                  <Star className="h-3 w-3 fill-amber-400 text-amber-400" />
                  <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                    <div className="h-full bg-amber-400" style={{ width: `${pct}%` }} />
                  </div>
                  <span className="w-6 text-right text-muted-foreground">{count}</span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Write / edit a review */}
      {isAuthenticated ? (
        <div className="space-y-3 rounded-lg border p-4">
          <p className="text-sm font-medium">{mine ? 'Edit your review' : 'Write a review'}</p>
          <StarInput value={rating} onChange={setRating} />
          <div className="grid gap-2">
            <Label htmlFor="review-comment" className="sr-only">Your review</Label>
            <Textarea
              id="review-comment"
              placeholder="Share what you liked or didn't…"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
          </div>
          <div className="flex items-center gap-2">
            <Button onClick={save} disabled={rating < 1 || submit.isPending}>
              {submit.isPending ? 'Saving…' : mine ? 'Update review' : 'Submit review'}
            </Button>
            {mine && (
              <Button variant="ghost" className="text-destructive" onClick={() => setConfirmDelete(true)}>
                Delete
              </Button>
            )}
          </div>
        </div>
      ) : (
        <p className="rounded-lg border p-4 text-sm text-muted-foreground">
          <Link to="/login" className="font-medium text-primary hover:underline">Sign in</Link> to write a review.
        </p>
      )}

      {/* Review list */}
      <div className="space-y-4">
        {data.reviews.length === 0 ? (
          <p className="text-sm text-muted-foreground">No reviews yet — be the first to review this product.</p>
        ) : (
          data.reviews.map((r) => (
            <div key={r.id} className="space-y-1 border-b pb-4 last:border-b-0">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">
                  {r.userName}
                  {r.mine && <span className="ml-2 text-xs text-muted-foreground">(You)</span>}
                </span>
                <span className="text-xs text-muted-foreground">{new Date(r.createdAt).toLocaleDateString()}</span>
              </div>
              <Stars value={r.rating} size={14} />
              {r.comment && <p className="whitespace-pre-line text-sm text-muted-foreground">{r.comment}</p>}
            </div>
          ))
        )}
      </div>

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete your review?"
        description="This will permanently remove your review for this product."
        confirmLabel="Delete"
        pendingLabel="Deleting…"
        pending={remove.isPending}
        destructive
        onConfirm={() => remove.mutate(undefined, { onSuccess: () => setConfirmDelete(false) })}
      />
    </section>
  );
}
