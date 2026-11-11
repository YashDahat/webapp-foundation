import { useState } from 'react';
import { Check, ChevronsUpDown } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { userService } from '@/services/userService';
import type { UserDto } from '@/types/user';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList,
} from '@/components/ui/command';

const userLabel = (u: UserDto): string => {
  const name = [u.firstName, u.lastName].filter(Boolean).join(' ').trim();
  return name ? `${name} · ${u.email}` : u.email;
};

interface UserComboboxProps {
  /** Selected user id, or null for "no user". */
  value: number | null;
  onChange: (userId: number | null) => void;
  /** Label shown (and selectable) when no user is picked. */
  placeholder?: string;
  id?: string;
}

/** Searchable user picker (name or email). Clearing the selection means "no user" — a public offer. */
export function UserCombobox({ value, onChange, placeholder = 'Public offer (no user)', id }: UserComboboxProps) {
  const [open, setOpen] = useState(false);
  const { data: users, isLoading } = useQuery<UserDto[]>({ queryKey: ['admin-users'], queryFn: userService.list });
  const selected = value == null ? null : users?.find((u) => u.id === value) ?? null;

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          data-testid="o-user-combobox"
          className="w-full justify-between font-normal"
        >
          <span className={cn('truncate', !selected && 'text-muted-foreground')}>
            {selected ? userLabel(selected) : placeholder}
          </span>
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
        <Command>
          <CommandInput placeholder="Search by name or email…" />
          <CommandList>
            <CommandEmpty>{isLoading ? 'Loading…' : 'No users found.'}</CommandEmpty>
            <CommandGroup>
              <CommandItem value="__public__ public offer no user" onSelect={() => { onChange(null); setOpen(false); }}>
                <Check className={cn('mr-2 h-4 w-4', value == null ? 'opacity-100' : 'opacity-0')} />
                {placeholder}
              </CommandItem>
              {(users ?? []).map((u) => (
                <CommandItem
                  key={u.id}
                  value={`${u.firstName ?? ''} ${u.lastName ?? ''} ${u.email} ${u.id}`}
                  onSelect={() => { onChange(u.id); setOpen(false); }}
                >
                  <Check className={cn('mr-2 h-4 w-4', value === u.id ? 'opacity-100' : 'opacity-0')} />
                  <span className="truncate">{userLabel(u)}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
