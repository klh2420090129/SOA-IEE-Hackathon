import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useOptimizationJob } from './useOptimizationJob';
import { optimizationApi } from '../api/optimizationApi';

describe('useOptimizationJob Hook', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('successfully polls until job COMPLETED and returns candidate layout', async () => {
    const getStatusMock = vi.fn()
      .mockResolvedValueOnce({ jobId: 'job-1', status: 'SUBMITTED' })
      .mockResolvedValueOnce({ jobId: 'job-1', status: 'RUNNING' })
      .mockResolvedValueOnce({ jobId: 'job-1', status: 'COMPLETED' });

    const getResultMock = vi.fn().mockResolvedValue({
      layoutId: 'layout-1',
      layoutVersion: 1,
      compartmentId: 'comp-1',
      contentDigest: 'sha256-mock-digest',
      solverStatus: 'OPTIMAL',
      provenance: 'PYTHON_ORTOOLS_CPSAT_3D_V1',
      placements: [],
      unassignedItems: []
    });

    vi.spyOn(optimizationApi, 'getJobStatus').mockImplementation(getStatusMock);
    vi.spyOn(optimizationApi, 'getJobResult').mockImplementation(getResultMock);

    const { result } = renderHook(() => useOptimizationJob());

    let layoutResult: any;
    await act(async () => {
      layoutResult = await result.current.pollJob('job-1', 10, 10);
    });

    expect(getStatusMock).toHaveBeenCalledTimes(3);
    expect(getResultMock).toHaveBeenCalledWith('job-1');
    expect(layoutResult.solverStatus).toBe('OPTIMAL');
    expect(layoutResult.provenance).toBe('PYTHON_ORTOOLS_CPSAT_3D_V1');
    expect(result.current.isPolling).toBe(false);
  });

  it('fails fast when solver status is FAILED', async () => {
    vi.spyOn(optimizationApi, 'getJobStatus').mockResolvedValue({
      jobId: 'job-err',
      status: 'FAILED',
      errorMessage: 'Infeasible cargo combination'
    } as any);

    const { result } = renderHook(() => useOptimizationJob());

    await act(async () => {
      await expect(result.current.pollJob('job-err', 5, 10)).rejects.toThrow(
        'Infeasible cargo combination'
      );
    });

    expect(result.current.isPolling).toBe(false);
    expect(result.current.error).toBe('Infeasible cargo combination');
  });

  it('allows user cancellation during polling', async () => {
    vi.spyOn(optimizationApi, 'getJobStatus').mockImplementation(async () => {
      return { jobId: 'job-slow', status: 'RUNNING' } as any;
    });

    const { result } = renderHook(() => useOptimizationJob());

    let pollPromise: Promise<any>;
    act(() => {
      pollPromise = result.current.pollJob('job-slow', 50, 100);
    });

    act(() => {
      result.current.cancelPolling();
    });

    await act(async () => {
      await expect(pollPromise).rejects.toThrow('Polling cancelled by user');
    });

    expect(result.current.isPolling).toBe(false);
  });
});
