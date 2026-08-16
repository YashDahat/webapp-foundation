// GENERATED from the backend API contract — do not edit by hand.
// Typed client for the FOUNDATION-owned auth API — mirrors com.webappfoundation
// AuthController (ApiInventory skips it, so the worker never derives this file).
//
// FOR UI LOGIN, use `useAuth().login(username, password)` from context/AuthContext:
// it calls this endpoint AND persists the JWT + updates auth state. Call login()
// here directly only for programmatic/non-React flows where you manage the token
// yourself — a component that calls this directly will NOT be logged in.
import apiClient from '@/api/client';
import type { AuthRequest, AuthResponse } from '@/types/auth';

/** POST /api/v1/auth/login — exchange credentials for a JWT (does not persist it). */
export const login = async (credentials: AuthRequest): Promise<AuthResponse> => {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/login', credentials);
  return response.data;
};
