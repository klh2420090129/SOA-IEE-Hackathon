import { apiRequest } from './client';
import { 
  CargoDeclaration, 
  CreateDeclarationRequest, 
  DeclarationWorkflowProjection 
} from '../types/api';

export const declarationApi = {
  createDeclaration: async (request: CreateDeclarationRequest): Promise<CargoDeclaration> => {
    return apiRequest<CargoDeclaration>('/api/v1/declarations', {
      method: 'POST',
      body: JSON.stringify(request)
    });
  },

  getDeclaration: async (id: string): Promise<CargoDeclaration> => {
    return apiRequest<CargoDeclaration>(`/api/v1/declarations/${id}`, {
      method: 'GET'
    });
  },

  getWorkflowProjection: async (id: string): Promise<DeclarationWorkflowProjection> => {
    return apiRequest<DeclarationWorkflowProjection>(`/api/v1/declarations/${id}/workflow`, {
      method: 'GET'
    });
  }
};
