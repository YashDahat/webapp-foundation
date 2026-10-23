import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface Step {
  id: string;
  title: string;
  description?: string;
  /** Show a check instead of the number (step's required fields are satisfied). */
  complete?: boolean;
  /** Mark the step's indicator as invalid (required fields missing/invalid). */
  error?: boolean;
}

interface StepperProps {
  steps: Step[];
  active: number;
  onStepClick: (index: number) => void;
}

/**
 * Presentational step rail for the admin form panels. Steps are freely clickable (navigation is not
 * gated). Stacks horizontally on small screens and vertically from `md` up. Used by StepperSheet and
 * the bulk-upload panel.
 */
export function Stepper({ steps, active, onStepClick }: StepperProps) {
  return (
    <nav
      aria-label="Form steps"
      className="flex flex-row gap-1 overflow-x-auto md:flex-col md:gap-0 md:overflow-visible"
    >
      {steps.map((step, i) => {
        const isActive = i === active;
        return (
          <button
            key={step.id}
            type="button"
            onClick={() => onStepClick(i)}
            aria-current={isActive ? 'step' : undefined}
            className={cn(
              'flex shrink-0 items-center gap-3 rounded-md px-3 py-2 text-left transition-colors md:w-full',
              isActive ? 'bg-muted' : 'hover:bg-muted/60',
            )}
          >
            <span
              className={cn(
                'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-xs font-medium',
                step.error
                  ? 'border-destructive text-destructive'
                  : step.complete
                    ? 'border-primary bg-primary text-primary-foreground'
                    : isActive
                      ? 'border-primary text-primary'
                      : 'border-muted-foreground/40 text-muted-foreground',
              )}
            >
              {step.complete && !step.error ? <Check className="h-4 w-4" /> : i + 1}
            </span>
            <span className="hidden min-w-0 md:block">
              <span className={cn('block truncate text-sm font-medium', !isActive && 'text-muted-foreground')}>
                {step.title}
              </span>
              {step.description && (
                <span className="block truncate text-xs text-muted-foreground">{step.description}</span>
              )}
            </span>
          </button>
        );
      })}
    </nav>
  );
}
