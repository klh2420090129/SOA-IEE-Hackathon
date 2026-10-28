import { apiRequest } from './client';
import { AuthResponse, UserClaims } from '../types/api';

export const authApi = {
  login: async (username: string, password: string): Promise<AuthResponse> => {
    return apiRequest<AuthResponse>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password })
    });
  },

  getCurrentUser: async (): Promise<UserClaims> => {
    return apiRequest<UserClaims>('/api/v1/auth/me', {
      method: 'GET'
    });
  }
};
