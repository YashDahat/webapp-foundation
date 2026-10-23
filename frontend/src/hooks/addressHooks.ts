// React Query hooks over the address-book SDK. Mutations wrap withToast; failures auto-toast.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addressService } from '@/services/addressService';
import { withToast } from '@/lib/apiToast';
import type { UpsertAddressRequest, UserAddressDto } from '@/types/address';

export function useAddresses() {
  return useQuery<UserAddressDto[]>({ queryKey: ['addresses'], queryFn: addressService.list });
}

export function useAddAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertAddressRequest) => withToast(addressService.add(req), 'Address saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
  });
}

export function useUpdateAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpsertAddressRequest }) =>
      withToast(addressService.update(id, req), 'Address updated'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
  });
}

export function useDeleteAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(addressService.remove(id), 'Address removed'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
  });
}

export function useSetDefaultAddress() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(addressService.setDefault(id), 'Default address set'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['addresses'] }),
  });
}
