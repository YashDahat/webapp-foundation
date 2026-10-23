import { MapPin, Check } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useBranch } from '@/context/BranchContext';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
  DropdownMenuLabel, DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu';

/**
 * Header outlet picker. Shows the selected branch (or "Select outlet") and lets the user switch; the
 * choice drives the catalog/recommendation branch filter and is recorded on orders. Renders nothing when
 * no branches exist.
 */
export default function BranchPicker({ className }: { className?: string }) {
  const { branches, selectedBranch, selectBranch } = useBranch();
  if (branches.length === 0) return null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className={className} data-testid="branch-picker">
          <MapPin size={16} className="mr-1" />
          {selectedBranch ? selectedBranch.name : 'Select outlet'}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel>Choose outlet</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {branches.map((b) => (
          <DropdownMenuItem key={b.id} onClick={() => selectBranch(b.id)} className="flex items-center gap-2">
            <div className="flex flex-col">
              <span>{b.name}</span>
              {(b.city || b.distanceKm != null) && (
                <span className="text-xs text-muted-foreground">
                  {b.city}{b.distanceKm != null ? ` · ${b.distanceKm} km` : ''}
                </span>
              )}
            </div>
            {selectedBranch?.id === b.id && <Check size={14} className="ml-auto" />}
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/branches">View all outlets</Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
