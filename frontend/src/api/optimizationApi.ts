import { apiRequest } from './client';
import { 
  CandidateLayoutDto, 
  CreateOptimizationJobRequest, 
  OptimizationJobEntity,
  OptimizationJobStatusResponse 
} from '../types/api';

export const optimizationApi = {
  submitJob: async (request: CreateOptimizationJobRequest): Promise<OptimizationJobEntity> => {
    return apiRequest<OptimizationJobEntity>('/api/v1/optimization-jobs', {
      method: 'POST',
      body: JSON.stringify(request)
    });
  },

  getJobStatus: async (jobId: string): Promise<OptimizationJobStatusResponse> => {
    return apiRequest<OptimizationJobStatusResponse>(`/api/v1/optimization-jobs/${jobId}/status`, {
      method: 'GET'
    });
  },

  getJobResult: async (jobId: string): Promise<CandidateLayoutDto> => {
    return apiRequest<CandidateLayoutDto>(`/api/v1/optimization-jobs/${jobId}/result`, {
      method: 'GET'
    });
  },

  getJob: async (jobId: string): Promise<OptimizationJobEntity> => {
    return apiRequest<OptimizationJobEntity>(`/api/v1/optimization-jobs/${jobId}`, {
      method: 'GET'
    });
  }
};
