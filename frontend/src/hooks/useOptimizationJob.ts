import { useState, useRef, useCallback } from 'react';
import { optimizationApi } from '../api/optimizationApi';
import { CandidateLayoutDto, OptimizationJobStatusResponse } from '../types/api';

interface UseOptimizationJobResult {
  isPolling: boolean;
  status: OptimizationJobStatusResponse | null;
  candidateLayout: CandidateLayoutDto | null;
  error: string | null;
  pollJob: (jobId: string, maxAttempts?: number, intervalMs?: number) => Promise<CandidateLayoutDto>;
  cancelPolling: () => void;
}

export function useOptimizationJob(): UseOptimizationJobResult {
  const [isPolling, setIsPolling] = useState<boolean>(false);
  const [status, setStatus] = useState<OptimizationJobStatusResponse | null>(null);
  const [candidateLayout, setCandidateLayout] = useState<CandidateLayoutDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cancelledRef = useRef<boolean>(false);

  const cancelPolling = useCallback(() => {
    cancelledRef.current = true;
    setIsPolling(false);
  }, []);

  const pollJob = useCallback(async (
    jobId: string,
    maxAttempts: number = 30,
    intervalMs: number = 1000
  ): Promise<CandidateLayoutDto> => {
    cancelledRef.current = false;
    setIsPolling(true);
    setError(null);

    let attempts = 0;
    while (attempts < maxAttempts && !cancelledRef.current) {
      attempts++;
      try {
        const jobStatus = await optimizationApi.getJobStatus(jobId);
        setStatus(jobStatus);

        if (jobStatus.status === 'COMPLETED') {
          const layout = await optimizationApi.getJobResult(jobId);
          setCandidateLayout(layout);
          setIsPolling(false);
          return layout;
        }

        if (jobStatus.status === 'FAILED' || jobStatus.status === 'TIMEOUT' || jobStatus.status === 'CANCELLED') {
          const msg = jobStatus.errorMessage || `Optimization job terminated with status: ${jobStatus.status}`;
          setError(msg);
          setIsPolling(false);
          throw new Error(msg);
        }
      } catch (err: any) {
        if (cancelledRef.current) {
          throw new Error('Polling cancelled by user');
        }
        // If it was already a terminal state error or explicit ApiError (other than 404), rethrow immediately
        if (err.message && (err.message.includes('terminated with status') || (status && (status.status === 'FAILED' || status.status === 'TIMEOUT' || status.status === 'CANCELLED')))) {
          throw err;
        }
        if (err.status && err.status !== 404) {
          setError(err.message);
          setIsPolling(false);
          throw err;
        }
        if (err instanceof Error && !err.message.includes('404')) {
          setError(err.message);
          setIsPolling(false);
          throw err;
        }
      }

      await new Promise(r => setTimeout(r, intervalMs));
    }

    if (cancelledRef.current) {
      throw new Error('Polling cancelled by user');
    }

    const timeoutMsg = `Optimization job polling timed out after ${maxAttempts}s. Job is still processing in background.`;
    setError(timeoutMsg);
    setIsPolling(false);
    throw new Error(timeoutMsg);
  }, []);

  return {
    isPolling,
    status,
    candidateLayout,
    error,
    pollJob,
    cancelPolling
  };
}
