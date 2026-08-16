// GENERATED foundation scaffold — reusable public gallery display. Drop <GallerySection /> onto any
// page. section="WEBSITE" renders the main gallery grid; section="EVENT" groups images by event name.
import { useGallery } from '@/hooks/useGallery';
import type { GalleryItemDto, GallerySection as SectionType } from '@/types/gallery';
import { Skeleton } from '@/components/ui/skeleton';

function ImageGrid({ items }: { items: GalleryItemDto[] }) {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4" data-testid="gallery-grid">
      {items.map((item) => (
        <figure key={item.id} className="overflow-hidden rounded-lg border bg-card" data-testid="gallery-image">
          <img
            src={item.url}
            alt={item.caption ?? ''}
            loading="lazy"
            className="aspect-square w-full object-cover"
          />
          {item.caption && (
            <figcaption className="px-2 py-1 text-sm text-muted-foreground">{item.caption}</figcaption>
          )}
        </figure>
      ))}
    </div>
  );
}

export default function GallerySection({
  section = 'WEBSITE',
  title,
}: {
  section?: SectionType;
  title?: string;
}) {
  const { data, isLoading, isError } = useGallery(section);

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="aspect-square w-full rounded-lg" />
        ))}
      </div>
    );
  }
  if (isError) return <p className="text-sm text-destructive">Could not load the gallery.</p>;

  const items = data ?? [];
  if (items.length === 0) {
    return <p className="text-muted-foreground" data-testid="gallery-empty">No images yet.</p>;
  }

  return (
    <section className="space-y-6" data-testid="gallery-section">
      {title && <h2 className="text-2xl font-bold">{title}</h2>}
      {section === 'EVENT' ? (
        // Group event photos by event name (falls back to a single group).
        Object.entries(
          items.reduce<Record<string, GalleryItemDto[]>>((acc, item) => {
            const key = item.eventName ?? 'Events';
            (acc[key] ??= []).push(item);
            return acc;
          }, {}),
        ).map(([eventName, group]) => (
          <div key={eventName} className="space-y-3" data-testid="gallery-event-group">
            <h3 className="text-lg font-semibold">{eventName}</h3>
            <ImageGrid items={group} />
          </div>
        ))
      ) : (
        <ImageGrid items={items} />
      )}
    </section>
  );
}
