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
  },

  listDeclarations: async (params?: import('../types/api').DeclarationQueryParams): Promise<import('../types/api').PaginatedResponse<CargoDeclaration>> => {
    const query = new URLSearchParams();
    if (params?.page !== undefined) query.set('page', params.page.toString());
    if (params?.size !== undefined) query.set('size', params.size.toString());
    if (params?.status) query.set('status', params.status);
    if (params?.shipperId) query.set('shipperId', params.shipperId);
    const queryString = query.toString() ? `?${query.toString()}` : '';
    return apiRequest<import('../types/api').PaginatedResponse<CargoDeclaration>>(`/api/v1/declarations${queryString}`, {
      method: 'GET'
    });
  }
};
