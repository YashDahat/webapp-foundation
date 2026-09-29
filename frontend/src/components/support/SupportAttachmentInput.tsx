import { useRef, useState } from 'react';
import { supportService } from '@/services/supportService';
import { Button } from '@/components/ui/button';
import { Paperclip, X } from 'lucide-react';

interface SupportAttachmentInputProps {
  /** Current ordered list of uploaded image URLs. */
  value: string[];
  onChange: (next: string[]) => void;
}

/**
 * Upload-only image attachment control for support tickets. Unlike the admin MultiImagePicker it does
 * NOT read the admin media library (customers can't), so it just uploads files via
 * supportService.uploadAttachment and collects the returned URLs. Upload failures auto-toast via the
 * apiClient interceptor.
 */
export function SupportAttachmentInput({ value, onChange }: SupportAttachmentInputProps) {
  const [busy, setBusy] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFiles = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setBusy(true);
    try {
      const urls: string[] = [];
      for (const file of Array.from(files)) {
        const asset = await supportService.uploadAttachment(file);
        urls.push(asset.url);
      }
      onChange([...value, ...urls]);
    } catch {
      // failure already toasted by the interceptor
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const removeAt = (i: number) => onChange(value.filter((_, idx) => idx !== i));

  return (
    <div className="space-y-2">
      {value.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {value.map((url, i) => (
            <div key={`${url}:${i}`} className="relative h-16 w-16 overflow-hidden rounded-md border">
              <img src={url} alt={`Attachment ${i + 1}`} className="h-full w-full object-cover" />
              <button
                type="button"
                aria-label="Remove attachment"
                onClick={() => removeAt(i)}
                className="absolute right-0.5 top-0.5 rounded-full bg-black/60 p-0.5 text-white hover:bg-black/80"
              >
                <X className="h-3 w-3" />
              </button>
            </div>
          ))}
        </div>
      )}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        className="hidden"
        onChange={(e) => handleFiles(e.target.files)}
        data-testid="support-attachment-input"
      />
      <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => inputRef.current?.click()}>
        <Paperclip className="mr-2 h-4 w-4" />
        {busy ? 'Uploading…' : 'Attach images'}
      </Button>
    </div>
  );
}

/** Renders a message's image attachments as clickable thumbnails (opens full image in a new tab). */
export function SupportAttachmentThumbs({ attachments }: { attachments: string[] }) {
  if (!attachments || attachments.length === 0) return null;
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {attachments.map((url, i) => (
        <a key={`${url}:${i}`} href={url} target="_blank" rel="noreferrer" className="block h-20 w-20 overflow-hidden rounded-md border">
          <img src={url} alt={`Attachment ${i + 1}`} className="h-full w-full object-cover" />
        </a>
      ))}
    </div>
  );
}
