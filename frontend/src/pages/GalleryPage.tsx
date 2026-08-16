import GallerySection from '@/components/gallery/GallerySection';

// Public gallery page — composes the reusable <GallerySection>. A generated site can use this page
// as-is or drop <GallerySection /> wherever it wants; the data + component are foundation-owned.
export default function GalleryPage() {
  return (
    <div className="mx-auto max-w-5xl space-y-12 px-4 py-12">
      <GallerySection section="WEBSITE" title="Gallery" />
      <GallerySection section="EVENT" title="Events" />
    </div>
  );
}
