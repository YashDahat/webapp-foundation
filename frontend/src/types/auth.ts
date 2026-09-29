// GENERATED from the backend API contract — do not edit by hand.
// Source of truth: webapp-foundation backend (com.webappfoundation.dto / .model),
// renamed to the project package at clone time. Auth is FOUNDATION-OWNED: ApiInventory
// skips AuthController, so the worker never auto-derives these — they live here so the
// frontend (and generated code) has real, importable types for the auth API surface.
// ApiContractCard picks this file up via the marker line above and feeds it to the LLM.

/** Roles issued by the backend — com.webappfoundation.model.Role. */
export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'USER';

/** Request body for POST /api/v1/auth/login — com.webappfoundation.dto.AuthRequest.
 *  `username` is the login identifier: email OR phone (India-first login). */
export interface AuthRequest {
  username: string;
  password: string;
}

/** Response body for POST /api/v1/auth/login and /register — com.webappfoundation.dto.AuthResponse. */
export interface AuthResponse {
  token: string;
}

/**
 * Response body for POST /api/v1/auth/login — com.webappfoundation.dto.LoginResponse.
 * Discriminated by `twoFactorRequired`: when false, `token` is set (logged in); when true, `token` is
 * null and `challengeId` must be sent with the emailed code to /verify-2fa.
 */
export interface LoginResponse {
  token: string | null;
  twoFactorRequired: boolean;
  /** True when the account's email isn't verified yet — finish via /verify-signup with the emailed code. */
  verificationRequired: boolean;
  challengeId: string | null;
}

/** Request body for POST /api/v1/auth/verify-2fa and /verify-signup — { challengeId, code }. */
export interface VerifyTwoFactorRequest {
  challengeId: string;
  code: string;
}

/** POST /api/v1/auth/change-password (authenticated). */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** POST /api/v1/auth/reset-password — token from the emailed link + new password. */
export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

/** Request body for POST /api/v1/auth/register — com.webappfoundation.dto.RegisterRequest.
 *  Self-service signup; the backend always assigns role USER. Email and phone are both
 *  required and unique — either can be used to log in afterward. */
export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
}

/**
 * The authenticated user as the frontend knows it — mirrors com.webappfoundation.model.User
 * minus server-only fields (password). No endpoint returns this directly today; it is decoded
 * from the JWT (see context/AuthContext) or hydrated from a future /me endpoint.
 */
export interface AuthenticatedUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  role: Role;
}
