// FOUNDATION-owned admin user-management types (com.webappfoundation admin users API).
import type { Role } from '@/types/auth';

/** Admin-facing view of a user — com.webappfoundation.dto.UserDto (never includes the password). */
export interface UserDto {
  id: number;
  firstName: string | null;
  lastName: string | null;
  email: string;
  phone: string;
  role: Role;
  emailVerified: boolean;
  twoFactorEnabled: boolean;
}
