import { useEffect, useState, type ReactNode } from 'react';
import {
  Sheet, SheetContent, SheetHeader, SheetFooter, SheetTitle, SheetDescription,
} from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Stepper, type Step } from '@/components/admin/Stepper';

export interface WizardStep extends Step {
  /** Fields rendered on the right when this step is active. */
  content: ReactNode;
}

interface StepperSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  steps: WizardStep[];
  /** Enables the Save button once all required fields (across all steps) are valid. */
  canSave: boolean;
  saving?: boolean;
  saveLabel?: string;
  onSave: () => void;
  /** Max-width class for the panel (defaults to `sm:max-w-2xl`); widen for field-heavy forms. */
  widthClassName?: string;
}

/**
 * Right-side form panel with a vertical stepper. Replaces the crowded admin create/edit Dialog: each
 * step shows one logical group of fields on the right. Navigation is free (any step is clickable, plus
 * Back/Next); Save is gated by `canSave`, not by step order. Form state lives in the parent page, so
 * only the active step's content is mounted and values persist across steps.
 */
export function StepperSheet({
  open, onOpenChange, title, description, steps,
  canSave, saving = false, saveLabel = 'Save', onSave, widthClassName = 'sm:max-w-2xl',
}: StepperSheetProps) {
  const [active, setActive] = useState(0);

  // Reset to the first step each time the panel opens.
  useEffect(() => { if (open) setActive(0); }, [open]);

  const clampedActive = Math.min(active, steps.length - 1);
  const current = steps[clampedActive];
  const isFirst = clampedActive === 0;
  const isLast = clampedActive === steps.length - 1;

  return (
    <Sheet open={open} onOpenChange={(next) => !saving && onOpenChange(next)}>
      <SheetContent
        side="right"
        className={`flex w-full flex-col gap-0 p-0 ${widthClassName}`}
        onInteractOutside={(e) => saving && e.preventDefault()}
      >
        <SheetHeader className="border-b p-6">
          <SheetTitle>{title}</SheetTitle>
          {description && <SheetDescription>{description}</SheetDescription>}
        </SheetHeader>

        <div className="flex min-h-0 flex-1 flex-col md:flex-row">
          <div className="shrink-0 border-b p-4 md:w-56 md:border-b-0 md:border-r">
            <Stepper steps={steps} active={clampedActive} onStepClick={setActive} />
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-6">
            <div className="space-y-4">{current?.content}</div>
          </div>
        </div>

        <SheetFooter className="flex-row items-center justify-between gap-2 border-t p-4 sm:justify-between">
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={saving}>Cancel</Button>
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => setActive((i) => Math.max(0, i - 1))}
              disabled={isFirst || saving}
            >
              Back
            </Button>
            {!isLast && (
              <Button variant="outline" onClick={() => setActive((i) => Math.min(steps.length - 1, i + 1))}>
                Next
              </Button>
            )}
            <Button disabled={!canSave || saving} onClick={onSave}>
              {saving ? 'Saving…' : saveLabel}
            </Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
