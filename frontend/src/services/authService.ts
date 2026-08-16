// GENERATED from the backend API contract — do not edit by hand.
// Typed client for the FOUNDATION-owned auth API — mirrors com.webappfoundation
// AuthController (ApiInventory skips it, so the worker never derives this file).
//
// FOR UI LOGIN/SIGNUP, use `useAuth().login(...)` / `useAuth().register(...)` from
// context/AuthContext: they call these endpoints AND persist the JWT + update auth state.
// Call these here directly only for programmatic/non-React flows where you manage the
// token yourself — a component that calls these directly will NOT be logged in.
import apiClient from '@/api/client';
import type { AuthRequest, AuthResponse, RegisterRequest } from '@/types/auth';

/** POST /api/v1/auth/login — exchange credentials for a JWT (does not persist it). */
export const login = async (credentials: AuthRequest): Promise<AuthResponse> => {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/login', credentials);
  return response.data;
};

/** POST /api/v1/auth/register — create a USER account and return a JWT (auto-login). */
export const register = async (details: RegisterRequest): Promise<AuthResponse> => {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/register', details);
  return response.data;
};
