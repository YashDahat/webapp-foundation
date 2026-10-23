// FOUNDATION-owned address-book SDK (com.webappfoundation AddressController). Identity is server-derived.
import apiClient from '@/api/client';
import type { UpsertAddressRequest, UserAddressDto } from '@/types/address';

export const addressService = {
  list: async (): Promise<UserAddressDto[]> => {
    const { data } = await apiClient.get<UserAddressDto[]>('/api/v1/addresses');
    return data;
  },
  add: async (request: UpsertAddressRequest): Promise<UserAddressDto> => {
    const { data } = await apiClient.post<UserAddressDto>('/api/v1/addresses', request);
    return data;
  },
  update: async (id: number, request: UpsertAddressRequest): Promise<UserAddressDto> => {
    const { data } = await apiClient.put<UserAddressDto>(`/api/v1/addresses/${id}`, request);
    return data;
  },
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/addresses/${id}`);
  },
  setDefault: async (id: number): Promise<UserAddressDto> => {
    const { data } = await apiClient.put<UserAddressDto>(`/api/v1/addresses/${id}/default`);
    return data;
  },
};
