import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { App } from './App';
import { authApi } from './api/authApi';

describe('Authentication Flow & Screen Navigation Regression Suite', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('renders login screen when no valid session token exists', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Freight Platform Authentication/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Sign In to Freight System/i })).toBeInTheDocument();
    });
  });

  it('navigates to Load Planner Console on successful planner login', async () => {
    const mockAuthResponse = {
      token: 'mock-jwt-planner-token',
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
      userId: 'usr-planner-001',
      username: 'planner@soa8.org',
      roles: ['ROLE_LOAD_PLANNER']
    };

    const mockUserClaims = {
      userId: 'usr-planner-001',
      username: 'planner@soa8.org',
      roles: ['ROLE_LOAD_PLANNER']
    };

    vi.spyOn(authApi, 'login').mockResolvedValue(mockAuthResponse);
    vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue(mockUserClaims);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Sign In to Freight System/i })).toBeInTheDocument();
    });

    const signInBtn = screen.getByRole('button', { name: /Sign In to Freight System/i });
    fireEvent.click(signInBtn);

    await waitFor(() => {
      expect(screen.getByText(/Load Planner Console/i)).toBeInTheDocument();
      expect(screen.queryByText(/Freight Platform Authentication/i)).not.toBeInTheDocument();
    });
  });

  it('navigates to Shipper Declarations on successful shipper login', async () => {
    const mockAuthResponse = {
      token: 'mock-jwt-shipper-token',
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
      userId: 'usr-shipper-001',
      username: 'shipper@soa8.org',
      roles: ['ROLE_SHIPPER']
    };

    const mockUserClaims = {
      userId: 'usr-shipper-001',
      username: 'shipper@soa8.org',
      roles: ['ROLE_SHIPPER']
    };

    vi.spyOn(authApi, 'login').mockResolvedValue(mockAuthResponse);
    vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue(mockUserClaims);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Sign In to Freight System/i })).toBeInTheDocument();
    });

    // Select Shipper persona
    const shipperRoleBtn = screen.getByRole('button', { name: /^Shipper$/i });
    fireEvent.click(shipperRoleBtn);

    const signInBtn = screen.getByRole('button', { name: /Sign In to Freight System/i });
    fireEvent.click(signInBtn);

    await waitFor(() => {
      expect(screen.getByText(/Shipper Declarations/i)).toBeInTheDocument();
      expect(screen.queryByText(/Freight Platform Authentication/i)).not.toBeInTheDocument();
    });
  });

  it('displays error banner and remains on login screen when credentials are invalid', async () => {
    vi.spyOn(authApi, 'login').mockRejectedValue(new Error('Invalid username or password'));

    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Sign In to Freight System/i })).toBeInTheDocument();
    });

    const signInBtn = screen.getByRole('button', { name: /Sign In to Freight System/i });
    fireEvent.click(signInBtn);

    await waitFor(() => {
      expect(screen.getByText(/Invalid username or password/i)).toBeInTheDocument();
      expect(screen.getByText(/Freight Platform Authentication/i)).toBeInTheDocument();
    });
  });

  it('restores authenticated session when a valid token is pre-stored in localStorage', async () => {
    localStorage.setItem('soa8_auth_token', 'pre-existing-valid-jwt');

    const mockUserClaims = {
      userId: 'usr-planner-001',
      username: 'planner@soa8.org',
      roles: ['ROLE_LOAD_PLANNER']
    };

    vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue(mockUserClaims);

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/Load Planner Console/i)).toBeInTheDocument();
      expect(screen.queryByText(/Freight Platform Authentication/i)).not.toBeInTheDocument();
    });
  });
});
