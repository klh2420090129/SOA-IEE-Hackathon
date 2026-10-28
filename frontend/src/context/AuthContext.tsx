import React, { createContext, useContext, useState, useEffect } from 'react';
import { setApiAuthToken, setUnauthorizedHandler } from '../api/client';
import { authApi } from '../api/authApi';
import { UserClaims } from '../types/api';

interface AuthContextType {
  token: string | null;
  user: UserClaims | null;
  loading: boolean;
  error: string | null;
  login: (username: string, roleType?: 'shipper' | 'planner') => Promise<void>;
  logout: () => void;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const TOKEN_KEY = 'soa8_auth_token';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState<UserClaims | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      logout();
      setError('Session expired or unauthorized. Please re-authenticate.');
    });
  }, []);

  useEffect(() => {
    if (token) {
      setApiAuthToken(token);
      authApi.getCurrentUser()
        .then(u => {
          setUser(u);
          setError(null);
        })
        .catch(() => {
          logout();
        })
        .finally(() => {
          setLoading(false);
        });
    } else {
      setApiAuthToken(null);
      setUser(null);
      setLoading(false);
    }
  }, [token]);

  const login = async (username: string, roleType?: 'shipper' | 'planner') => {
    setLoading(true);
    setError(null);
    try {
      // Use standard documented demo credentials for quick switching, or user-supplied password
      let password = 'PlannerPass123!';
      if (roleType === 'shipper' || username.includes('shipper')) {
        password = 'ShipperPass123!';
      }

      const res = await authApi.login(username, password);
      localStorage.setItem(TOKEN_KEY, res.token);
      setApiAuthToken(res.token);
      setToken(res.token);
      setUser({
        userId: res.userId,
        username: res.username,
        roles: res.roles
      });
    } catch (err: any) {
      setError(err.message || 'Login failed');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    localStorage.removeItem(TOKEN_KEY);
    setApiAuthToken(null);
    setToken(null);
    setUser(null);
  };

  const hasRole = (role: string): boolean => {
    if (!user || !user.roles) return false;
    return user.roles.includes(role);
  };

  return (
    <AuthContext.Provider value={{ token, user, loading, error, login, logout, hasRole }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
