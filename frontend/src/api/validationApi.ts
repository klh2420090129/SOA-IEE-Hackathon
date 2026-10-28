import { apiRequest } from './client';
import { RunValidationRequest, ValidationResultDto } from '../types/api';

export const validationApi = {
  runValidation: async (request: RunValidationRequest): Promise<ValidationResultDto> => {
    return apiRequest<ValidationResultDto>('/api/v1/validations', {
      method: 'POST',
      body: JSON.stringify(request)
    });
  },

  getValidationRun: async (validationId: string): Promise<ValidationResultDto> => {
    return apiRequest<ValidationResultDto>(`/api/v1/validations/${validationId}`, {
      method: 'GET'
    });
  }
};
