import { useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useMedia } from '@/hooks/useMedia';
import { uploadMedia } from '@/services/mediaService';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Image as ImageIcon } from 'lucide-react';
import { toast } from 'sonner';

interface ImagePickerProps {
  /** Current image URL (bound to the form field). */
  value?: string | null;
  /** Called with the selected/uploaded image URL ('' when cleared). */
  onChange: (url: string) => void;
  testId?: string;
}

/**
 * Admin-only single-image picker: replaces a raw "image URL" text field. Upload a new image (stored in
 * the foundation media library) or pick an existing one; the resulting URL is mapped back via onChange.
 * Store it on your entity as a plain `imageUrl` string. Upload failures auto-toast via the interceptor.
 */
export default function ImagePicker({ value, onChange, testId }: ImagePickerProps): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const { data: assets, isLoading } = useMedia();
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);

  const handleUpload = async (file: File) => {
    setBusy(true);
    try {
      const asset = await uploadMedia({ file });
      await queryClient.invalidateQueries({ queryKey: ['media'] });
      onChange(asset.url);
      toast.success('Image uploaded');
      setOpen(false);
    } catch {
      // Upload failure already surfaced by the apiClient response interceptor — do not double-toast.
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const pick = (url: string) => {
    onChange(url);
    setOpen(false);
  };

  return (
    <div className="space-y-2">
      {value ? (
        <div className="flex items-center gap-3">
          <img src={value} alt="Selected" className="h-16 w-16 rounded-md object-cover border" />
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)} data-testid={testId}>
              Change image
            </Button>
            <Button type="button" variant="ghost" size="sm" onClick={() => onChange('')}>
              Remove
            </Button>
          </div>
        </div>
      ) : (
        <Button type="button" variant="outline" onClick={() => setOpen(true)} data-testid={testId}>
          <ImageIcon className="mr-2 h-4 w-4" /> Select image
        </Button>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-[640px]">
          <DialogHeader>
            <DialogTitle>Select an image</DialogTitle>
            <DialogDescription>Upload a new image or pick one from the media library.</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Input
                ref={inputRef}
                type="file"
                accept="image/*"
                disabled={busy}
                onChange={(e) => { const f = e.target.files?.[0]; if (f) handleUpload(f); }}
                data-testid="image-picker-file"
              />
              {busy && <span className="text-sm text-muted-foreground">Uploading…</span>}
            </div>

            <div className="max-h-72 overflow-y-auto">
              {isLoading ? (
                <p className="text-sm text-muted-foreground">Loading library…</p>
              ) : assets && assets.length > 0 ? (
                <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
                  {assets.map((a) => (
                    <button
                      type="button"
                      key={a.id}
                      onClick={() => pick(a.url)}
                      className={`relative aspect-square overflow-hidden rounded-md border hover:ring-2 hover:ring-primary ${value === a.url ? 'ring-2 ring-primary' : ''}`}
                      title={a.label ?? a.filename ?? ''}
                      data-testid={`image-picker-asset-${a.id}`}
                    >
                      <img src={a.url} alt={a.label ?? a.filename ?? ''} className="h-full w-full object-cover" />
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No images in the library yet — upload one above.</p>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
