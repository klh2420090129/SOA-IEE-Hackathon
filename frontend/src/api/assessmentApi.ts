import { apiRequest } from './client';
import { 
  ApproveCandidateRequest, 
  AssessmentEntity, 
  CandidateLayoutDto, 
  CreateAssessmentRequest, 
  OutboxEventDto, 
  RejectCandidateRequest, 
  ValidationResultDto 
} from '../types/api';

export const assessmentApi = {
  createAssessment: async (request: CreateAssessmentRequest): Promise<AssessmentEntity> => {
    return apiRequest<AssessmentEntity>('/api/v1/assessments', {
      method: 'POST',
      body: JSON.stringify(request)
    });
  },

  getAssessment: async (id: string): Promise<AssessmentEntity> => {
    return apiRequest<AssessmentEntity>(`/api/v1/assessments/${id}`, {
      method: 'GET'
    });
  },

  recordCandidate: async (id: string, candidate: CandidateLayoutDto): Promise<AssessmentEntity> => {
    return apiRequest<AssessmentEntity>(`/api/v1/assessments/${id}/candidate`, {
      method: 'POST',
      body: JSON.stringify(candidate)
    });
  },

  recordValidation: async (id: string, validation: ValidationResultDto): Promise<AssessmentEntity> => {
    return apiRequest<AssessmentEntity>(`/api/v1/assessments/${id}/validation`, {
      method: 'POST',
      body: JSON.stringify(validation)
    });
  },

  approveCandidate: async (id: string, request: ApproveCandidateRequest): Promise<AssessmentEntity> => {
    return apiRequest<AssessmentEntity>(`/api/v1/assessments/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify(request)
    });
  },

  rejectCandidate: async (id: string, request: RejectCandidateRequest): Promise<AssessmentEntity> => {
    return apiRequest<AssessmentEntity>(`/api/v1/assessments/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify(request)
    });
  },

  getAssessmentEvents: async (id: string): Promise<OutboxEventDto[]> => {
    return apiRequest<OutboxEventDto[]>(`/api/v1/assessments/${id}/events`, {
      method: 'GET'
    });
  },

  getCurrentLayout: async (id: string): Promise<any> => {
    return apiRequest<any>(`/api/v1/assessments/${id}/layout`, {
      method: 'GET'
    });
  }
};
