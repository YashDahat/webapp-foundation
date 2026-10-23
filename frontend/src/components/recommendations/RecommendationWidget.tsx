import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useRecommendations } from '@/hooks/recommendationHooks';
import { useCart } from '@/cart';
import type { CatalogItemDto } from '@/types/catalog';
import type { RecommendationType } from '@/types/recommendation';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

interface RecommendationWidgetProps {
  /** Source item the recommendations are for. */
  itemType: string;
  itemId: number | null;
  type: RecommendationType;
  /** Override the default heading. */
  title?: string;
  /** When set, only items offered at this branch are shown. */
  branchId?: number;
}

const DEFAULT_TITLE: Record<RecommendationType, string> = {
  UPSELL: 'Similar products',
  CROSS_SELL: 'Frequently bought together',
};

/**
 * Renders recommendations for a source item, working on resolved CatalogItemDto[] so it supports any
 * sellable type. UPSELL ("Similar products") items link to their detail page; CROSS_SELL items are added in one click when
 * `cartable`, otherwise link to their detail page (they need configuration first, e.g. a size or dates).
 * Renders nothing while loading or when there are no recommendations.
 */
export default function RecommendationWidget({
  itemType,
  itemId,
  type,
  title,
  branchId,
}: RecommendationWidgetProps): React.JSX.Element | null {
  const { data: items, isLoading } = useRecommendations(itemType, itemId, type, branchId);
  const { addItem } = useCart();
  const navigate = useNavigate();

  if (isLoading || !items || items.length === 0) {
    return null;
  }

  const addToCart = (item: CatalogItemDto) => {
    addItem({
      id: `${item.itemType}:${item.itemId}`,
      name: item.name,
      unitPrice: item.price,
      imageUrl: item.imageUrl,
      metadata: { itemType: item.itemType, itemId: item.itemId },
    });
    toast.success('Added to cart');
  };

  const viewDetails = (item: CatalogItemDto) => {
    if (item.detailPath) {
      navigate(item.detailPath);
    }
  };

  return (
    <section className="space-y-4">
      <h2 className="text-lg font-semibold">{title ?? DEFAULT_TITLE[type]}</h2>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {items.map((item) => {
          const canAdd = type === 'CROSS_SELL' && item.cartable;
          return (
            <Card key={`${item.itemType}:${item.itemId}`} className="flex flex-col overflow-hidden">
              <button type="button" onClick={() => viewDetails(item)} className="block text-left">
                {item.imageUrl ? (
                  <img src={item.imageUrl} alt={item.name} className="aspect-square w-full object-cover" />
                ) : (
                  <div className="flex aspect-square w-full items-center justify-center bg-muted text-muted-foreground">
                    No image
                  </div>
                )}
              </button>
              <CardContent className="flex-1 space-y-1 p-4">
                <button type="button" onClick={() => viewDetails(item)} className="text-left font-medium hover:underline">
                  {item.name}
                </button>
                <p className="font-semibold">
                  ₹{item.price}
                  {item.priceUnit ? <span className="text-xs font-normal text-muted-foreground"> {item.priceUnit}</span> : null}
                </p>
              </CardContent>
              <CardFooter className="p-4 pt-0">
                {canAdd ? (
                  <Button className="w-full" onClick={() => addToCart(item)}>
                    Add
                  </Button>
                ) : (
                  <Button variant="outline" className="w-full" onClick={() => viewDetails(item)}>
                    View details
                  </Button>
                )}
              </CardFooter>
            </Card>
          );
        })}
      </div>
    </section>
  );
}
