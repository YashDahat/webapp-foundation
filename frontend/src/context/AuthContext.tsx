// GENERATED foundation scaffold — do not edit (pipeline overwrites this per business).
// Auth context: owns token persistence + JWT-derived user state. The HTTP login/register
// calls live in services/authService (foundation-owned, cloned verbatim); this delegates to
// them and stores the JWT. UI must use useAuth().login / useAuth().register — not the
// authService functions directly (those don't persist the token).
import React, { createContext, useState, useContext, ReactNode } from 'react';
import * as authApi from '@/services/authService';
import type { RegisterRequest } from '@/types/auth';

export interface AuthUser { username: string; role: string }

/**
 * Result of login()/register(): logged in (both flags false, token persisted), or a pending OTP
 * challenge — twoFactorRequired (finish with verifyTwoFactor) or verificationRequired (finish with
 * verifySignup). challengeId is set for either challenge.
 */
export interface LoginResult { twoFactorRequired: boolean; verificationRequired: boolean; challengeId?: string }

interface AuthContextType {
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  user: AuthUser | null;
  /** Validates credentials. May return a 2FA or email-verification challenge (no token persisted then);
   *  otherwise logs in. */
  login: (username: string, password: string) => Promise<LoginResult>;
  /** Completes 2FA login: verifies the emailed code for the challenge and persists the JWT. */
  verifyTwoFactor: (challengeId: string, code: string) => Promise<void>;
  /** Completes signup: verifies the emailed code, marks the account verified, and persists the JWT. */
  verifySignup: (challengeId: string, code: string) => Promise<void>;
  /** Enable/disable email-OTP 2FA for the current (authenticated) user. */
  enableTwoFactor: () => Promise<void>;
  disableTwoFactor: () => Promise<void>;
  /** Authenticated password change. */
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  /** Self-service signup — creates an UNVERIFIED account and returns a verification challenge. */
  register: (details: RegisterRequest) => Promise<LoginResult>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

const decodeJwt = (token: string): Record<string, unknown> | null => {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
  } catch { return null; }
};

const userFromToken = (token: string): AuthUser | null => {
  const d = decodeJwt(token);
  if (!d) return null;
  const sub = (d['sub'] ?? d['username'] ?? d['email'] ?? '') as string;
  const role = (d['role'] ?? d['authorities'] ?? 'USER') as string;
  return sub ? { username: sub, role } : null;
};

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
  const [isAuthenticated, setIsAuthenticated] = useState(() => !!localStorage.getItem('token'));
  const [isLoading, setIsLoading] = useState(false);
  const [user, setUser] = useState<AuthUser | null>(() => {
    const t = localStorage.getItem('token');
    return t ? userFromToken(t) : null;
  });

  const applyToken = (jwt: string) => {
    if (jwt) {
      localStorage.setItem('token', jwt);
      setToken(jwt);
      setIsAuthenticated(true);
      setUser(userFromToken(jwt));
    }
  };

  const toResult = (res: { twoFactorRequired: boolean; verificationRequired: boolean; challengeId: string | null; token: string | null }): LoginResult => {
    if (res.twoFactorRequired || res.verificationRequired) {
      return {
        twoFactorRequired: res.twoFactorRequired,
        verificationRequired: res.verificationRequired,
        challengeId: res.challengeId ?? undefined,
      };
    }
    if (res.token) applyToken(res.token);
    return { twoFactorRequired: false, verificationRequired: false };
  };

  const login = async (username: string, password: string): Promise<LoginResult> => {
    setIsLoading(true);
    try {
      return toResult(await authApi.login({ username, password }));
    } finally {
      setIsLoading(false);
    }
  };

  const verifyTwoFactor = async (challengeId: string, code: string) => {
    setIsLoading(true);
    try {
      const { token: jwt } = await authApi.verifyTwoFactor({ challengeId, code });
      applyToken(jwt);
    } finally {
      setIsLoading(false);
    }
  };

  const verifySignup = async (challengeId: string, code: string) => {
    setIsLoading(true);
    try {
      const { token: jwt } = await authApi.verifySignup({ challengeId, code });
      applyToken(jwt);
    } finally {
      setIsLoading(false);
    }
  };

  const enableTwoFactor = async () => { await authApi.enableTwoFactor(); };
  const disableTwoFactor = async () => { await authApi.disableTwoFactor(); };
  const changePassword = async (currentPassword: string, newPassword: string) => {
    await authApi.changePassword({ currentPassword, newPassword });
  };

  // Self-service signup — creates an UNVERIFIED account and returns a verification challenge (no token
  // until the emailed OTP is confirmed via verifySignup).
  const register = async (details: RegisterRequest): Promise<LoginResult> => {
    setIsLoading(true);
    try {
      return toResult(await authApi.register(details));
    } finally {
      setIsLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    setToken(null);
    setIsAuthenticated(false);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ token, isAuthenticated, isLoading, user, login, verifyTwoFactor, verifySignup, enableTwoFactor, disableTwoFactor, changePassword, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
};
