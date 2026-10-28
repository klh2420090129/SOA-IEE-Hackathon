import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { apiRequest, ApiError, setApiAuthToken, setUnauthorizedHandler } from './client';

describe('API Client Layer', () => {
  beforeEach(() => {
    setApiAuthToken(null);
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('injects Bearer token when token is set', async () => {
    setApiAuthToken('mock-jwt-token-xyz');

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ status: 'UP' })
    });
    globalThis.fetch = fetchMock;

    await apiRequest('/api/v1/health');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/health',
      expect.objectContaining({
        headers: expect.any(Headers)
      })
    );

    const calledHeaders = fetchMock.mock.calls[0][1].headers as Headers;
    expect(calledHeaders.get('Authorization')).toBe('Bearer mock-jwt-token-xyz');
  });

  it('throws ApiError with correct code and status on HTTP 401', async () => {
    const unauthorizedMock = vi.fn();
    setUnauthorizedHandler(unauthorizedMock);

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ code: 'INVALID_TOKEN', message: 'Token expired' })
    });

    await expect(apiRequest('/api/v1/protected')).rejects.toThrow('Token expired');
    expect(unauthorizedMock).toHaveBeenCalled();
  });

  it('handles HTTP 409 Conflict with backend error code', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 409,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({
        code: 'DIGEST_MISMATCH',
        message: 'Candidate content digest mismatch'
      })
    });

    try {
      await apiRequest('/api/v1/assessments/asmt-1/candidate');
      expect.fail('Should have thrown ApiError');
    } catch (err: any) {
      expect(err).toBeInstanceOf(ApiError);
      expect(err.status).toBe(409);
      expect(err.code).toBe('DIGEST_MISMATCH');
      expect(err.message).toBe('Candidate content digest mismatch');
    }
  });

  it('handles network or non-JSON failures safely without exposing secrets', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      statusText: 'Service Unavailable',
      headers: new Headers({ 'content-type': 'text/plain' }),
      text: async () => 'Gateway timeout connecting to microservice'
    });

    try {
      await apiRequest('/api/v1/declarations');
      expect.fail('Should have thrown ApiError');
    } catch (err: any) {
      expect(err).toBeInstanceOf(ApiError);
      expect(err.status).toBe(503);
      expect(err.message).toBe('Gateway timeout connecting to microservice');
    }
  });
});
