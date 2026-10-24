import { useEffect, useState } from 'react';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogDescription,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Button } from '@/components/ui/button';
import type { SprintDto } from '@/types/sprint';

/** Create a new sprint, or edit the current sprint's name/dates. */
export default function EditSprintDialog({
  open, sprint, pending, onOpenChange, onSave,
}: {
  open: boolean;
  /** The sprint being edited; null = create a new sprint. */
  sprint: SprintDto | null;
  pending: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (values: { name: string; startsOn: string | null; endsOn: string | null }) => void;
}) {
  const [name, setName] = useState('');
  const [startsOn, setStartsOn] = useState('');
  const [endsOn, setEndsOn] = useState('');

  useEffect(() => {
    if (!open) return;
    setName(sprint?.name ?? '');
    setStartsOn(sprint?.startsOn ?? '');
    setEndsOn(sprint?.endsOn ?? '');
  }, [open, sprint]);

  return (
    <Dialog open={open} onOpenChange={(next) => !next && !pending && onOpenChange(false)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{sprint ? `Edit ${sprint.name}` : 'New sprint'}</DialogTitle>
          <DialogDescription>
            {sprint ? 'Update this sprint’s name and dates.' : 'Start a new active sprint for this workflow.'}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3 py-2">
          <div className="space-y-1.5">
            <Label>Name</Label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Sprint 1" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label>Starts on</Label>
              <Input type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label>Ends on</Label>
              <Input type="date" value={endsOn} onChange={(e) => setEndsOn(e.target.value)} />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button
            disabled={pending || !name.trim()}
            onClick={() => onSave({ name: name.trim(), startsOn: startsOn || null, endsOn: endsOn || null })}
          >
            {pending ? 'Saving…' : sprint ? 'Save' : 'Create sprint'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
