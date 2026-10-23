// React Query hooks over the product SDK. Mutations wrap withToast for success feedback; request
// failures auto-toast via the apiClient interceptor (never add a manual toast.error).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { productService } from '@/services/productService';
import { withToast } from '@/lib/apiToast';
import type { ProductDto, ProductFilter, UpsertProductRequest } from '@/types/product';

// ── Public ──
export function useProducts(filter: ProductFilter = {}) {
  return useQuery<ProductDto[]>({
    queryKey: ['products', filter],
    queryFn: () => productService.list(filter),
  });
}

export function useProduct(id: number | null) {
  return useQuery<ProductDto>({
    queryKey: ['product', id],
    queryFn: () => productService.get(id as number),
    enabled: id != null,
  });
}

export function useProductBySlug(slug: string | null) {
  return useQuery<ProductDto>({
    queryKey: ['product', 'slug', slug],
    queryFn: () => productService.getBySlug(slug as string),
    enabled: !!slug,
  });
}

// ── Admin ──
export function useAdminProducts() {
  return useQuery<ProductDto[]>({ queryKey: ['admin-products'], queryFn: productService.adminList });
}

export function useCreateProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertProductRequest) => withToast(productService.create(req), 'Product created'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-products'] }),
  });
}

export function useUpdateProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpsertProductRequest }) =>
      withToast(productService.update(id, req), 'Product updated'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-products'] }),
  });
}

export function useDeleteProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(productService.remove(id), 'Product deleted'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-products'] }),
  });
}
