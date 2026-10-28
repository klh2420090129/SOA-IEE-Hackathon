import { apiRequest } from './client';
import { 
  CargoTemplate, 
  CreateTemplateRequest, 
  UpdateTemplateRequest 
} from '../types/api';

export const templateApi = {
  createTemplate: async (request: CreateTemplateRequest): Promise<CargoTemplate> => {
    return apiRequest<CargoTemplate>('/api/v1/cargo-templates', {
      method: 'POST',
      body: JSON.stringify(request)
    });
  },

  listTemplates: async (): Promise<CargoTemplate[]> => {
    return apiRequest<CargoTemplate[]>('/api/v1/cargo-templates', {
      method: 'GET'
    });
  },

  getTemplate: async (id: string): Promise<CargoTemplate> => {
    return apiRequest<CargoTemplate>(`/api/v1/cargo-templates/${id}`, {
      method: 'GET'
    });
  },

  updateTemplate: async (id: string, request: UpdateTemplateRequest): Promise<CargoTemplate> => {
    return apiRequest<CargoTemplate>(`/api/v1/cargo-templates/${id}`, {
      method: 'PUT',
      body: JSON.stringify(request)
    });
  },

  deleteTemplate: async (id: string): Promise<void> => {
    return apiRequest<void>(`/api/v1/cargo-templates/${id}`, {
      method: 'DELETE'
    });
  }
};
