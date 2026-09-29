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
import { Image as ImageIcon, X, ChevronLeft, ChevronRight } from 'lucide-react';
import { toast } from 'sonner';

interface MultiImagePickerProps {
  /** Current ordered list of image URLs (bound to the form field). */
  value: string[];
  /** Called with the next ordered list of image URLs. */
  onChange: (next: string[]) => void;
  testId?: string;
}

/**
 * Admin-only multi-image picker: attach an ordered list of images to an entity by uploading new files
 * or picking from the media library; remove or reorder them. Backed by the foundation media library
 * (uploadMedia / useMedia). Store the resulting string[] on your entity (see the "Media library"
 * entity-images pattern). Upload failures auto-toast via the apiClient interceptor.
 */
export default function MultiImagePicker({ value, onChange, testId }: MultiImagePickerProps): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const { data: assets, isLoading } = useMedia();
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);

  const append = (url: string): void => {
    if (!url || value.includes(url)) return;
    onChange([...value, url]);
  };
  const removeAt = (index: number): void => onChange(value.filter((_, i) => i !== index));
  const move = (index: number, delta: number): void => {
    const next = [...value];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  const handleUpload = async (file: File): Promise<void> => {
    setBusy(true);
    try {
      const asset = await uploadMedia({ file });
      await queryClient.invalidateQueries({ queryKey: ['media'] });
      append(asset.url);
      toast.success('Image uploaded');
    } catch {
      // Upload failure already surfaced by the apiClient response interceptor — do not double-toast.
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  return (
    <div className="space-y-2" data-testid={testId}>
      {value.length > 0 && (
        <div className="flex flex-wrap gap-2" data-testid="multi-image-thumbs">
          {value.map((url, index) => (
            <div key={`${url}:${index}`} className="relative h-20 w-20 overflow-hidden rounded-md border group">
              <img src={url} alt={`Angle ${index + 1}`} className="h-full w-full object-cover" />
              <button
                type="button"
                aria-label="Remove image"
                onClick={() => removeAt(index)}
                className="absolute right-0.5 top-0.5 rounded-full bg-black/60 p-0.5 text-white hover:bg-black/80"
                data-testid={`multi-image-remove-${index}`}
              >
                <X className="h-3 w-3" />
              </button>
              <div className="absolute inset-x-0 bottom-0 flex justify-between bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
                <button type="button" aria-label="Move left" disabled={index === 0} onClick={() => move(index, -1)} className="p-0.5 text-white disabled:opacity-30">
                  <ChevronLeft className="h-3 w-3" />
                </button>
                <button type="button" aria-label="Move right" disabled={index === value.length - 1} onClick={() => move(index, 1)} className="p-0.5 text-white disabled:opacity-30">
                  <ChevronRight className="h-3 w-3" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)} data-testid="multi-image-add">
        <ImageIcon className="mr-2 h-4 w-4" /> Add image{value.length > 0 ? 's' : ''}
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-[640px]">
          <DialogHeader>
            <DialogTitle>Add images</DialogTitle>
            <DialogDescription>Upload new images or pick from the library. Click to add more than one.</DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Input
                ref={inputRef}
                type="file"
                accept="image/*"
                disabled={busy}
                onChange={(e) => { const f = e.target.files?.[0]; if (f) handleUpload(f); }}
                data-testid="multi-image-file"
              />
              {busy && <span className="text-sm text-muted-foreground">Uploading…</span>}
            </div>

            <div className="max-h-72 overflow-y-auto">
              {isLoading ? (
                <p className="text-sm text-muted-foreground">Loading library…</p>
              ) : assets && assets.length > 0 ? (
                <div className="grid grid-cols-3 sm:grid-cols-4 gap-3">
                  {assets.map((a) => {
                    const selected = value.includes(a.url);
                    return (
                      <button
                        type="button"
                        key={a.id}
                        onClick={() => (selected ? onChange(value.filter((u) => u !== a.url)) : append(a.url))}
                        className={`relative aspect-square overflow-hidden rounded-md border hover:ring-2 hover:ring-primary ${selected ? 'ring-2 ring-primary' : ''}`}
                        title={a.label ?? a.filename ?? ''}
                        data-testid={`multi-image-asset-${a.id}`}
                      >
                        <img src={a.url} alt={a.label ?? a.filename ?? ''} className="h-full w-full object-cover" />
                        {selected && <span className="absolute right-1 top-1 rounded-full bg-primary px-1 text-[10px] text-primary-foreground">✓</span>}
                      </button>
                    );
                  })}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">No images in the library yet — upload one above.</p>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" onClick={() => setOpen(false)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
