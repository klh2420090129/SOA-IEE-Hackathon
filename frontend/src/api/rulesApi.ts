import { apiRequest } from './client';
import { CompatibilityRuleDto, RuleSnapshotDto } from '../types/api';

export const rulesApi = {
  getRules: async (): Promise<CompatibilityRuleDto[]> => {
    return apiRequest<CompatibilityRuleDto[]>('/api/v1/rules', {
      method: 'GET'
    });
  },

  getRuleSnapshot: async (snapshotId: string): Promise<RuleSnapshotDto> => {
    return apiRequest<RuleSnapshotDto>(`/api/v1/rule-snapshots/${snapshotId}`, {
      method: 'GET'
    });
  }
};
