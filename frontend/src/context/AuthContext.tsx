import React, { createContext, useState, useContext, ReactNode } from 'react';
import { AuthRequest, AuthResponse } from '@/types/auth';
import { login as authServiceLogin } from '@/services/authService';

interface AuthContextType {
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  user: { username: string; role: string } | null;
  login: (credentials: AuthRequest) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

const decodeJwt = (token: string) => {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(function (c) {
          return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        })
        .join(''),
    );
    return JSON.parse(jsonPayload);
  } catch (error) {
    console.error('Failed to decode JWT:', error);
    return null;
  }
};

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
  const [isAuthenticated, setIsAuthenticated] = useState<boolean>(() => !!localStorage.getItem('token'));
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [user, setUser] = useState<{ username: string; role: string } | null>(() => {
    const storedToken = localStorage.getItem('token');
    if (storedToken) {
      const decoded = decodeJwt(storedToken);
      if (decoded && decoded.sub && decoded.role) {
        return { username: decoded.sub, role: decoded.role };
      }
    }
    return null;
  });

  const login = async (credentials: AuthRequest) => {
    setIsLoading(true);
    try {
      const response: AuthResponse = await authServiceLogin(credentials);
      if (response.token) {
        localStorage.setItem('token', response.token);
        setToken(response.token);
        setIsAuthenticated(true);
        const decoded = decodeJwt(response.token);
        if (decoded && decoded.sub && decoded.role) {
          setUser({ username: decoded.sub, role: decoded.role });
        }
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
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
