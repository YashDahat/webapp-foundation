// GENERATED from the backend API contract — do not edit by hand.
// Typed client for the FOUNDATION-owned auth API — mirrors com.webappfoundation
// AuthController (ApiInventory skips it, so the worker never derives this file).
//
// FOR UI LOGIN/SIGNUP, use `useAuth().login(...)` / `useAuth().register(...)` from
// context/AuthContext: they call these endpoints AND persist the JWT + update auth state.
// Call these here directly only for programmatic/non-React flows where you manage the
// token yourself — a component that calls these directly will NOT be logged in.
import apiClient from '@/api/client';
import type {
  AuthRequest,
  AuthResponse,
  ChangePasswordRequest,
  LoginResponse,
  RegisterRequest,
  ResetPasswordRequest,
  VerifyTwoFactorRequest,
} from '@/types/auth';

/**
 * POST /api/v1/auth/login — validate credentials (does not persist any token). Returns a LoginResponse:
 * either a JWT (2FA off) or a { twoFactorRequired, challengeId } challenge (2FA on — finish via
 * verifyTwoFactor).
 */
export const login = async (credentials: AuthRequest): Promise<LoginResponse> => {
  const response = await apiClient.post<LoginResponse>('/api/v1/auth/login', credentials);
  return response.data;
};

/** POST /api/v1/auth/verify-2fa — exchange the login challenge + emailed code for a JWT. */
export const verifyTwoFactor = async (request: VerifyTwoFactorRequest): Promise<AuthResponse> => {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/verify-2fa', request);
  return response.data;
};

/** POST /api/v1/auth/2fa/enable — turn on email-OTP 2FA for the current (authenticated) user. */
export const enableTwoFactor = async (): Promise<void> => {
  await apiClient.post('/api/v1/auth/2fa/enable');
};

/** POST /api/v1/auth/2fa/disable — turn off email-OTP 2FA for the current (authenticated) user. */
export const disableTwoFactor = async (): Promise<void> => {
  await apiClient.post('/api/v1/auth/2fa/disable');
};

/**
 * POST /api/v1/auth/register — create an UNVERIFIED USER account. No token yet: returns
 * { verificationRequired: true, challengeId } and emails a 6-digit code. Finish via verifySignup.
 */
export const register = async (details: RegisterRequest): Promise<LoginResponse> => {
  const response = await apiClient.post<LoginResponse>('/api/v1/auth/register', details);
  return response.data;
};

/** POST /api/v1/auth/verify-signup — verify the signup OTP for the challenge and get a JWT. */
export const verifySignup = async (request: VerifyTwoFactorRequest): Promise<AuthResponse> => {
  const response = await apiClient.post<AuthResponse>('/api/v1/auth/verify-signup', request);
  return response.data;
};

/** POST /api/v1/auth/change-password — authenticated password change. */
export const changePassword = async (request: ChangePasswordRequest): Promise<void> => {
  await apiClient.post('/api/v1/auth/change-password', request);
};

/** POST /api/v1/auth/forgot-password — email a reset link (always resolves; never reveals existence). */
export const forgotPassword = async (email: string): Promise<void> => {
  await apiClient.post('/api/v1/auth/forgot-password', { email });
};

/** POST /api/v1/auth/reset-password — set a new password using the token from the emailed link. */
export const resetPassword = async (request: ResetPasswordRequest): Promise<void> => {
  await apiClient.post('/api/v1/auth/reset-password', request);
};
