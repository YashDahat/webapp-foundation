import type { ReactNode } from 'react';
import {
  AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogFooter,
  AlertDialogTitle, AlertDialogDescription, AlertDialogAction, AlertDialogCancel,
} from '@/components/ui/alert-dialog';

interface ConfirmDialogProps {
  /** Controlled open state — render this once per page and drive it from a `target` state. */
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  confirmLabel?: string;
  /** Label shown while `pending` is true (defaults to "Working…"). */
  pendingLabel?: string;
  cancelLabel?: string;
  /** Disables both buttons and swaps the confirm label; keeps the dialog open until the caller closes it. */
  pending?: boolean;
  /** Styles the confirm button with the destructive variant (delete/cancel-style actions). */
  destructive?: boolean;
  onConfirm: () => void;
}

/**
 * Shared shadcn AlertDialog replacement for `window.confirm`. The confirm button suppresses the
 * default auto-close so the dialog stays open while `pending`; the caller closes it (set the target
 * to null) from its mutation's onSuccess.
 */
export function ConfirmDialog({
  open, onOpenChange, title, description,
  confirmLabel = 'Confirm', pendingLabel = 'Working…', cancelLabel = 'Cancel',
  pending = false, destructive = false, onConfirm,
}: ConfirmDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={(next) => !next && !pending && onOpenChange(next)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          {description != null && <AlertDialogDescription>{description}</AlertDialogDescription>}
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction
            className={destructive ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90' : undefined}
            disabled={pending}
            onClick={(e) => { e.preventDefault(); onConfirm(); }}
          >
            {pending ? pendingLabel : confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
