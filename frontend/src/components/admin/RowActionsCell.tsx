import type { CustomCellRendererProps } from 'ag-grid-react';
import { MoreHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface RowAction<T = any> {
  label: string;
  onClick: (row: T) => void;
  /** Render in a destructive (red) style. */
  danger?: boolean;
  /** Hide this action for rows where it returns true. */
  hidden?: (row: T) => boolean;
}

interface RowActionsParams extends CustomCellRendererProps {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  actions: RowAction<any>[];
}

/** AG Grid cell renderer: a single kebab (⋮) menu holding all row actions. */
export function RowActionsCell(props: RowActionsParams): React.JSX.Element | null {
  const { data, actions } = props;
  if (!data) return null;
  const visible = actions.filter((a) => !a.hidden || !a.hidden(data));
  if (visible.length === 0) return null;

  return (
    <div className="flex h-full items-center justify-end">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Row actions" data-testid="row-actions-trigger">
            <MoreHorizontal className="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {visible.map((a, i) => (
            <DropdownMenuItem
              key={i}
              onClick={() => a.onClick(data)}
              className={a.danger ? 'text-red-600 focus:text-red-600' : ''}
            >
              {a.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
