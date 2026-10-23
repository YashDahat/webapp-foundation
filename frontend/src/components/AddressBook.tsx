import { useState } from 'react';
import {
  useAddresses, useAddAddress, useUpdateAddress, useDeleteAddress, useSetDefaultAddress,
} from '@/hooks/addressHooks';
import type { UpsertAddressRequest, UserAddressDto } from '@/types/address';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog';
import { ConfirmDialog } from '@/components/ConfirmDialog';

interface AddressForm {
  id: number | null;
  label: string;
  contactName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  pincode: string;
}

const emptyForm = (): AddressForm => ({
  id: null, label: '', contactName: '', phone: '', line1: '', line2: '', city: '', state: '', pincode: '',
});

const toForm = (a: UserAddressDto): AddressForm => ({
  id: a.id, label: a.label ?? '', contactName: a.contactName ?? '', phone: a.phone ?? '',
  line1: a.line1 ?? '', line2: a.line2 ?? '', city: a.city ?? '', state: a.state ?? '', pincode: a.pincode ?? '',
});

const toRequest = (f: AddressForm): UpsertAddressRequest => ({
  label: f.label.trim() || null,
  contactName: f.contactName.trim() || null,
  phone: f.phone.trim() || null,
  line1: f.line1.trim() || null,
  line2: f.line2.trim() || null,
  city: f.city.trim() || null,
  state: f.state.trim() || null,
  pincode: f.pincode.trim() || null,
});

/** The user's saved-address book: add / edit / delete / set-default. Feeds the checkout address picker. */
export default function AddressBook() {
  const { data: addresses, isLoading } = useAddresses();
  const add = useAddAddress();
  const update = useUpdateAddress();
  const remove = useDeleteAddress();
  const setDefault = useSetDefaultAddress();

  const [form, setForm] = useState<AddressForm | null>(null);
  const [toDelete, setToDelete] = useState<UserAddressDto | null>(null);
  const patch = (p: Partial<AddressForm>) => setForm((f) => (f ? { ...f, ...p } : f));
  const saving = add.isPending || update.isPending;
  const canSave = !!form && form.line1.trim() !== '' && form.city.trim() !== '';

  const save = () => {
    if (!form) return;
    const req = toRequest(form);
    const after = () => setForm(null);
    if (form.id == null) add.mutate(req, { onSuccess: after });
    else update.mutate({ id: form.id, req }, { onSuccess: after });
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0">
        <CardTitle className="text-base">Saved addresses</CardTitle>
        <Button size="sm" onClick={() => setForm(emptyForm())}>Add address</Button>
      </CardHeader>
      <CardContent className="space-y-3">
        {isLoading ? (
          <p className="text-sm text-muted-foreground">Loading…</p>
        ) : !addresses || addresses.length === 0 ? (
          <p className="text-sm text-muted-foreground">No saved addresses yet.</p>
        ) : (
          addresses.map((a) => (
            <div key={a.id} className="flex items-start justify-between gap-3 rounded-md border p-3">
              <div className="text-sm">
                <p className="font-medium">
                  {a.label ?? 'Address'} {a.isDefault && <Badge variant="secondary" className="ml-1">Default</Badge>}
                </p>
                <p className="text-muted-foreground">
                  {[a.line1, a.line2, a.city, a.state, a.pincode].filter(Boolean).join(', ')}
                </p>
                {a.phone && <p className="text-muted-foreground">☎ {a.phone}</p>}
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1">
                {!a.isDefault && (
                  <Button variant="ghost" size="sm" onClick={() => setDefault.mutate(a.id)}>Set default</Button>
                )}
                <Button variant="ghost" size="sm" onClick={() => setForm(toForm(a))}>Edit</Button>
                <Button variant="ghost" size="sm" className="text-destructive" onClick={() => setToDelete(a)}>Remove</Button>
              </div>
            </div>
          ))
        )}
      </CardContent>

      <Dialog open={form != null} onOpenChange={(next) => !next && setForm(null)}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[440px]">
          <DialogHeader>
            <DialogTitle>{form?.id == null ? 'Add address' : 'Edit address'}</DialogTitle>
          </DialogHeader>
          {form && (
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div className="grid gap-2"><Label htmlFor="a-label">Label</Label><Input id="a-label" placeholder="Home / Work" value={form.label} onChange={(e) => patch({ label: e.target.value })} /></div>
                <div className="grid gap-2"><Label htmlFor="a-phone">Phone</Label><Input id="a-phone" value={form.phone} onChange={(e) => patch({ phone: e.target.value })} /></div>
              </div>
              <div className="grid gap-2"><Label htmlFor="a-name">Contact name</Label><Input id="a-name" value={form.contactName} onChange={(e) => patch({ contactName: e.target.value })} /></div>
              <div className="grid gap-2"><Label htmlFor="a-line1">Address line 1</Label><Input id="a-line1" value={form.line1} onChange={(e) => patch({ line1: e.target.value })} /></div>
              <div className="grid gap-2"><Label htmlFor="a-line2">Address line 2</Label><Input id="a-line2" value={form.line2} onChange={(e) => patch({ line2: e.target.value })} /></div>
              <div className="grid grid-cols-3 gap-3">
                <div className="grid gap-2"><Label htmlFor="a-city">City</Label><Input id="a-city" value={form.city} onChange={(e) => patch({ city: e.target.value })} /></div>
                <div className="grid gap-2"><Label htmlFor="a-state">State</Label><Input id="a-state" value={form.state} onChange={(e) => patch({ state: e.target.value })} /></div>
                <div className="grid gap-2"><Label htmlFor="a-pin">Pincode</Label><Input id="a-pin" value={form.pincode} onChange={(e) => patch({ pincode: e.target.value })} /></div>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setForm(null)}>Cancel</Button>
            <Button disabled={!canSave || saving} onClick={save}>{saving ? 'Saving…' : 'Save'}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={() => setToDelete(null)}
        title="Remove address?"
        description={<>This will remove “{toDelete?.label ?? 'this address'}” from your address book. This action cannot be undone.</>}
        confirmLabel="Remove"
        pendingLabel="Removing…"
        pending={remove.isPending}
        destructive
        onConfirm={() => toDelete && remove.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}
      />
    </Card>
  );
}
