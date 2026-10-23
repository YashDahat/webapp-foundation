import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useProducts } from '@/hooks/productHooks';
import { useCart } from '@/cart';
import { useBranch } from '@/context/BranchContext';
import type { ProductDto } from '@/types/product';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';

/** True when a variant-less product has no sellable units left (shared-pool tracked stock). */
function isOutOfStock(p: ProductDto): boolean {
  return p.variants.length === 0 && p.stockTracked && p.available != null && p.available <= 0;
}

/**
 * Public catalog grid: search + category filter over active products. Simple in-stock products get a
 * one-click "Add to cart"; products with variants link to the detail page ("View") to pick a size, and
 * out-of-stock products show a disabled button. Public page → shadcn cards, never ag-grid.
 */
export default function ProductsPage() {
  const { selectedBranchId } = useBranch();
  const { data: products, isLoading } = useProducts({ branchId: selectedBranchId ?? undefined });
  const { addItem } = useCart();
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<string | null>(null);

  const categories = useMemo(
    () => Array.from(new Set((products ?? []).map((p) => p.category).filter((c): c is string => !!c))).sort(),
    [products],
  );

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (products ?? []).filter((p) => {
      const matchesSearch = !q || p.name.toLowerCase().includes(q);
      const matchesCategory = !category || p.category === category;
      return matchesSearch && matchesCategory;
    });
  }, [products, search, category]);

  const handleAdd = (p: ProductDto) => {
    addItem({
      id: `PRODUCT:${p.id}`,
      name: p.name,
      unitPrice: p.price,
      imageUrl: p.imageUrl,
      metadata: { itemType: 'PRODUCT', itemId: p.id },
    });
    toast.success('Added to cart');
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="text-2xl font-bold">Products</h1>
        <Input
          placeholder="Search products…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="sm:max-w-xs"
        />
      </div>

      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <Badge
            variant={category === null ? 'default' : 'outline'}
            className="cursor-pointer"
            onClick={() => setCategory(null)}
          >
            All
          </Badge>
          {categories.map((c) => (
            <Badge
              key={c}
              variant={category === c ? 'default' : 'outline'}
              className="cursor-pointer"
              onClick={() => setCategory(c)}
            >
              {c}
            </Badge>
          ))}
        </div>
      )}

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : visible.length === 0 ? (
        <p className="text-sm text-muted-foreground">No products found.</p>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {visible.map((p) => {
            const hasVariants = p.variants.length > 0;
            const outOfStock = isOutOfStock(p);
            return (
              <Card key={p.id} className="flex flex-col overflow-hidden">
                <Link to={`/products/${p.slug}`} className="block">
                  {p.imageUrl ? (
                    <img src={p.imageUrl} alt={p.name} className="aspect-square w-full object-cover" />
                  ) : (
                    <div className="flex aspect-square w-full items-center justify-center bg-muted text-muted-foreground">
                      No image
                    </div>
                  )}
                </Link>
                <CardContent className="flex-1 space-y-1 p-4">
                  <Link to={`/products/${p.slug}`} className="font-medium hover:underline">
                    {p.name}
                  </Link>
                  {p.category && <p className="text-xs text-muted-foreground">{p.category}</p>}
                  <p className="font-semibold">₹{p.price}</p>
                </CardContent>
                <CardFooter className="p-4 pt-0">
                  {hasVariants ? (
                    <Button asChild variant="outline" className="w-full">
                      <Link to={`/products/${p.slug}`}>View</Link>
                    </Button>
                  ) : outOfStock ? (
                    <Button className="w-full" disabled>
                      Out of stock
                    </Button>
                  ) : (
                    <Button className="w-full" onClick={() => handleAdd(p)}>
                      Add to cart
                    </Button>
                  )}
                </CardFooter>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
