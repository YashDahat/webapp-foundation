import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useGallery } from '@/hooks/useGallery';
import { uploadGalleryItem, deleteGalleryItem } from '@/services/galleryService';
import type { GallerySection } from '@/types/gallery';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

export default function AdminGalleryPage() {
  const queryClient = useQueryClient();
  const { data: items, isLoading } = useGallery(); // all items
  const [file, setFile] = useState<File | null>(null);
  const [caption, setCaption] = useState('');
  const [section, setSection] = useState<GallerySection>('WEBSITE');
  const [eventName, setEventName] = useState('');
  const [eventDate, setEventDate] = useState('');
  const [busy, setBusy] = useState(false);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['gallery'] });

  const onUpload = async () => {
    if (!file) { toast.error('Choose an image first'); return; }
    setBusy(true);
    try {
      await uploadGalleryItem({
        file, caption: caption || undefined, section,
        eventName: section === 'EVENT' ? eventName || undefined : undefined,
        eventDate: section === 'EVENT' ? eventDate || undefined : undefined,
      });
      toast.success('Image uploaded');
      setFile(null); setCaption(''); setEventName(''); setEventDate('');
      refresh();
    } catch {
      toast.error('Upload failed');
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (id: number) => {
    try {
      await deleteGalleryItem(id);
      toast.success('Deleted');
      refresh();
    } catch {
      toast.error('Delete failed');
    }
  };

  return (
    <div className="space-y-8" data-testid="admin-gallery-page">
      <h1 className="text-2xl font-bold">Gallery</h1>

      <Card>
        <CardHeader><CardTitle>Add image</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="file">Image file</Label>
            <Input id="file" type="file" accept="image/*" data-testid="gallery-file"
                   onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="caption">Caption</Label>
            <Input id="caption" value={caption} data-testid="gallery-caption"
                   onChange={(e) => setCaption(e.target.value)} placeholder="Optional" />
          </div>
          <div className="space-y-2">
            <Label>Section</Label>
            <Select value={section} onValueChange={(v) => setSection(v as GallerySection)}>
              <SelectTrigger data-testid="gallery-section-select"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="WEBSITE">Website gallery</SelectItem>
                <SelectItem value="EVENT">Event</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {section === 'EVENT' && (
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="eventName">Event name</Label>
                <Input id="eventName" value={eventName} data-testid="gallery-event-name"
                       onChange={(e) => setEventName(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="eventDate">Event date</Label>
                <Input id="eventDate" type="date" value={eventDate} data-testid="gallery-event-date"
                       onChange={(e) => setEventDate(e.target.value)} />
              </div>
            </div>
          )}
          <Button onClick={onUpload} disabled={busy} data-testid="gallery-upload">
            {busy ? 'Uploading…' : 'Upload'}
          </Button>
        </CardContent>
      </Card>

      <div>
        <h2 className="mb-3 text-lg font-semibold">
          Images <span data-testid="gallery-count">({items?.length ?? 0})</span>
        </h2>
        {isLoading ? (
          <p className="text-muted-foreground">Loading…</p>
        ) : (items?.length ?? 0) === 0 ? (
          <p className="text-muted-foreground" data-testid="admin-gallery-empty">No images yet.</p>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4" data-testid="admin-gallery-grid">
            {items!.map((item) => (
              <div key={item.id} className="overflow-hidden rounded-lg border" data-testid="admin-gallery-item">
                <img src={item.url} alt={item.caption ?? ''} className="aspect-square w-full object-cover" />
                <div className="flex items-center justify-between gap-2 p-2">
                  <span className="truncate text-xs text-muted-foreground">
                    {item.section === 'EVENT' ? (item.eventName ?? 'Event') : (item.caption || 'Website')}
                  </span>
                  <Button variant="ghost" size="sm" data-testid={`gallery-delete-${item.id}`}
                          onClick={() => onDelete(item.id)}>Delete</Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
