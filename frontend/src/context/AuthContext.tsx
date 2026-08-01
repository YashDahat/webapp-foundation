// GENERATED foundation scaffold — do not edit (pipeline overwrites this per business).
// Self-contained auth context: reads token from localStorage, decodes JWT claims,
// calls /api/v1/auth/login directly — no dependency on generated service/types files.
import React, { createContext, useState, useContext, ReactNode } from 'react';
import apiClient from '@/api/client';

interface AuthUser { username: string; role: string }

interface AuthContextType {
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  user: AuthUser | null;
  login: (username: string, password: string) => Promise<void>;
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

  const login = async (username: string, password: string) => {
    setIsLoading(true);
    try {
      const res = await apiClient.post<{ token: string }>('/api/v1/auth/login', { username, password });
      const jwt = res.data.token;
      if (jwt) {
        localStorage.setItem('token', jwt);
        setToken(jwt);
        setIsAuthenticated(true);
        setUser(userFromToken(jwt));
      }
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
    <AuthContext.Provider value={{ token, isAuthenticated, isLoading, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
};
