import { useEffect, useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useProductBySlug } from '@/hooks/productHooks';
import { loyaltyService } from '@/services/loyaltyService';
import { useCart } from '@/cart';
import { useBranch } from '@/context/BranchContext';
import type { ProductVariantDto } from '@/types/product';
import RecommendationWidget from '@/components/recommendations/RecommendationWidget';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Check, ChevronLeft, ChevronRight } from 'lucide-react';
import ProductReviews from '@/components/reviews/ProductReviews';

/**
 * Public product detail: image gallery, description, price, a size/variant selector (updates price +
 * availability), and a stock-aware Add to cart. When the product has variants a size must be picked
 * before adding; tracked stock disables the button at zero availability. Public page → no ag-grid.
 * (The "Similar products" UPSELL widget and product reviews are mounted at the bottom.)
 */
export default function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { data: product, isLoading, isError } = useProductBySlug(slug ?? null);
  const { addItem } = useCart();
  const { selectedBranchId } = useBranch();

  const [selectedVariantId, setSelectedVariantId] = useState<number | null>(null);
  const [activeIndex, setActiveIndex] = useState(0);

  const gallery = useMemo(() => {
    if (!product) return [] as string[];
    const imgs = product.images && product.images.length > 0 ? product.images : [];
    return product.imageUrl ? [product.imageUrl, ...imgs.filter((i) => i !== product.imageUrl)] : imgs;
  }, [product]);

  // Loyalty: a genuine product view counts only after 10s of dwell (anti-bot). Fire-and-forget beacon.
  const productId = product?.id;
  useEffect(() => {
    if (productId == null) return;
    const timer = setTimeout(
      () => loyaltyService.recordActivity({ type: 'PRODUCT_VIEW', itemType: 'PRODUCT', itemId: productId }),
      10_000,
    );
    return () => clearTimeout(timer);
  }, [productId]);

  if (isLoading) {
    return <p className="mx-auto max-w-5xl px-4 py-10 text-sm text-muted-foreground">Loading…</p>;
  }
  if (isError || !product) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-10">
        <p className="text-sm text-muted-foreground">Product not found.</p>
        <Button asChild variant="link" className="px-0">
          <Link to="/products">← Back to products</Link>
        </Button>
      </div>
    );
  }

  const hasVariants = product.variants.length > 0;
  const selectedVariant: ProductVariantDto | null =
    hasVariants && selectedVariantId != null
      ? product.variants.find((v) => v.id === selectedVariantId) ?? null
      : null;

  const price = selectedVariant?.price ?? product.price;

  // Availability for the current selection (shared-pool tracked stock; per-branch availability is
  // enforced server-side at checkout).
  const available: number | null = hasVariants
    ? selectedVariant && selectedVariant.stockTracked
      ? selectedVariant.available ?? 0
      : null
    : product.stockTracked
      ? product.available ?? 0
      : null;

  const outOfStock = available != null && available <= 0;
  const needsVariant = hasVariants && !selectedVariant;
  const addDisabled = needsVariant || outOfStock;

  const safeIndex = gallery.length > 0 ? activeIndex % gallery.length : 0;
  const currentImage = gallery[safeIndex] ?? null;
  const showImage = (index: number) => {
    if (gallery.length === 0) return;
    setActiveIndex((index + gallery.length) % gallery.length);
  };

  const handleAdd = () => {
    addItem({
      id: `PRODUCT:${product.id}`,
      name: selectedVariant ? `${product.name} (${selectedVariant.label})` : product.name,
      unitPrice: price,
      imageUrl: product.imageUrl,
      variantKey: selectedVariant ? String(selectedVariant.id) : undefined,
      metadata: {
        itemType: 'PRODUCT',
        itemId: product.id,
        ...(selectedVariant ? { variantLabel: selectedVariant.label } : {}),
      },
    });
    toast.success('Added to cart');
    loyaltyService.recordActivity({ type: 'ADD_TO_CART', itemType: 'PRODUCT', itemId: product.id });
  };

  return (
    <div className="mx-auto max-w-5xl space-y-8 px-4 py-10">
      <div className="grid gap-8 md:grid-cols-2">
        {/* Gallery */}
        <div className="space-y-3">
          <div className="relative">
            {currentImage ? (
              <img src={currentImage} alt={product.name} className="aspect-square w-full rounded-lg object-cover" />
            ) : (
              <div className="flex aspect-square w-full items-center justify-center rounded-lg bg-muted text-muted-foreground">
                No image
              </div>
            )}
            {gallery.length > 1 && (
              <>
                <button
                  type="button"
                  aria-label="Previous image"
                  onClick={() => showImage(safeIndex - 1)}
                  className="absolute left-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-background/80 text-foreground shadow transition hover:bg-background"
                >
                  <ChevronLeft className="h-5 w-5" />
                </button>
                <button
                  type="button"
                  aria-label="Next image"
                  onClick={() => showImage(safeIndex + 1)}
                  className="absolute right-2 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-full bg-background/80 text-foreground shadow transition hover:bg-background"
                >
                  <ChevronRight className="h-5 w-5" />
                </button>
                <span className="absolute bottom-2 right-2 rounded-full bg-background/80 px-2 py-0.5 text-xs text-foreground shadow">
                  {safeIndex + 1} / {gallery.length}
                </span>
              </>
            )}
          </div>
          {gallery.length > 1 && (
            <div className="flex flex-wrap gap-2">
              {gallery.map((img, index) => (
                <button
                  key={img}
                  type="button"
                  onClick={() => showImage(index)}
                  className={`h-16 w-16 overflow-hidden rounded border ${
                    index === safeIndex ? 'ring-2 ring-primary' : ''
                  }`}
                >
                  <img src={img} alt="" className="h-full w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Details */}
        <div className="space-y-4">
          <div>
            {product.category && <Badge variant="outline">{product.category}</Badge>}
            <h1 className="mt-2 text-2xl font-bold">{product.name}</h1>
            <p className="mt-1 text-xl font-semibold">₹{price}</p>
          </div>

          {product.description && (
            <p className="whitespace-pre-line text-sm text-muted-foreground">{product.description}</p>
          )}

          {product.features && product.features.length > 0 && (
            <div className="space-y-2">
              <p className="text-sm font-medium">Special features</p>
              <ul className="space-y-1.5">
                {product.features.map((feat, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm text-muted-foreground">
                    <Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                    <span>{feat}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {hasVariants && (
            <div className="space-y-2">
              <p className="text-sm font-medium">Size</p>
              <div className="flex flex-wrap gap-2">
                {product.variants.map((v) => {
                  const vOut = v.stockTracked && v.available != null && v.available <= 0;
                  return (
                    <Button
                      key={v.id}
                      type="button"
                      size="sm"
                      variant={selectedVariantId === v.id ? 'default' : 'outline'}
                      disabled={vOut}
                      onClick={() => setSelectedVariantId(v.id)}
                    >
                      {v.label}
                      {vOut ? ' — out of stock' : ''}
                    </Button>
                  );
                })}
              </div>
            </div>
          )}

          <Button className="w-full sm:w-auto" disabled={addDisabled} onClick={handleAdd}>
            {needsVariant ? 'Select a size' : outOfStock ? 'Out of stock' : 'Add to cart'}
          </Button>
        </div>
      </div>

      <RecommendationWidget itemType="PRODUCT" itemId={product.id} type="UPSELL" branchId={selectedBranchId ?? undefined} />
      <RecommendationWidget itemType="PRODUCT" itemId={product.id} type="CROSS_SELL" branchId={selectedBranchId ?? undefined} />

      <ProductReviews productId={product.id} />
    </div>
  );
}
