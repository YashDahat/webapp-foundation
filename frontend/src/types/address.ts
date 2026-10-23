// FOUNDATION-owned address-book types (mirror backend UserAddressDto / UpsertAddressRequest). User-owned
// saved addresses; checkout picks one and the order snapshots it.

export interface UserAddressDto {
  id: number;
  label?: string | null;
  contactName?: string | null;
  phone?: string | null;
  line1?: string | null;
  line2?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  isDefault: boolean;
  createdAt: string;
}

export interface UpsertAddressRequest {
  label?: string | null;
  contactName?: string | null;
  phone?: string | null;
  line1?: string | null;
  line2?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  isDefault?: boolean;
}
