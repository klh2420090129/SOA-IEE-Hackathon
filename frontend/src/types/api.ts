// Authentication types
export interface UserClaims {
  userId: string;
  username: string;
  roles: string[];
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  username: string;
  roles: string[];
}

// Cargo Declaration types
export interface CargoItemInput {
  cargoItemId?: string;
  description: string;
  hazardClass: string;
  unNumber: string;
  lengthMm: number;
  widthMm: number;
  heightMm: number;
  weightKg: number;
  stackable: boolean;
  maxStackWeightKg: number;
  quantity: number;
}

export interface CargoItemEntity extends CargoItemInput {
  cargoItemId: string;
}

export interface CreateDeclarationRequest {
  referenceNumber: string;
  shipperId: string;
  origin: string;
  destination: string;
  items: CargoItemInput[];
}

export interface CargoDeclaration {
  id: string;
  referenceNumber: string;
  shipperId: string;
  origin: string;
  destination: string;
  version: number;
  createdAt: string;
  items: CargoItemEntity[];
}

export interface DeclarationWorkflowProjection {
  declarationId: string;
  assessmentId?: string | null;
  lastAssessmentId?: string | null;
  workflowStatus?: 'PENDING' | 'OPTIMIZING' | 'APPROVED' | 'REJECTED' | 'SUBMITTED' | string;
  status?: 'SUBMITTED' | 'UNDER_ASSESSMENT' | 'OPTIMIZED' | 'APPROVED' | 'REJECTED' | string;
  rejectionReasonCode?: string | null;
  lastRejectionReasonCode?: string | null;
  rejectionNotes?: string | null;
  lastUpdated?: string | null;
  updatedAt?: string | null;
}

// Cargo Template types
export interface CargoTemplateItem extends CargoItemInput {
  id?: string;
}

export interface CargoTemplate {
  id: string;
  shipperId: string;
  name: string;
  description?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  items: CargoTemplateItem[];
}

export interface CreateTemplateRequest {
  name: string;
  description?: string;
  items: CargoItemInput[];
}

export interface UpdateTemplateRequest {
  name?: string;
  description?: string;
  items?: CargoItemInput[];
}

export interface CompartmentDto {
  compartmentId: string;
  name?: string;
  lengthMm: number;
  widthMm: number;
  heightMm: number;
  maxPayloadKg: number;
}

export interface CreateAssessmentRequest {
  declarationId: string;
  declarationVersion?: number;
  ruleSnapshotId?: string;
  compartment: CompartmentDto;
}

export interface AssessmentEntity {
  id: string;
  declarationId: string;
  declarationVersion: number;
  ruleSnapshotId: string;
  ruleSnapshotDigest: string;
  workflowStatus: 'DRAFT' | 'OPTIMIZING' | 'CANDIDATE_READY' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
  compartmentId: string;
  compartmentLengthMm: number;
  compartmentWidthMm: number;
  compartmentHeightMm: number;
  compartmentMaxPayloadKg: number;
  candidateLayoutId?: string | null;
  candidateLayoutVersion?: number | null;
  candidateContentDigest?: string | null;
  validationId?: string | null;
  validationPassed?: boolean | null;
  approvalDecision?: 'APPROVED' | 'REJECTED' | null;
  decidedBy?: string | null;
  decisionReasonCode?: string | null;
  decisionNotes?: string | null;
  decisionTimestamp?: string | null;
  createdAt: string;
  updatedAt: string;
}

// Candidate Layout & Placements
export interface ItemPlacementDto {
  placementId: string;
  cargoItemId: string;
  instanceIndex: number;
  hazardClass: string;
  unNumber: string;
  xMm: number;
  yMm: number;
  zMm: number;
  lengthMm: number;
  widthMm: number;
  heightMm: number;
  weightKg: number;
  orientation?: string;
  stackable?: boolean;
  maxStackWeightKg?: number | null;
}

export interface UnassignedItemDto {
  cargoItemId: string;
  instanceIndex: number;
  reasonCode: string;
  reasonDescription: string;
}

export interface CandidateLayoutDto {
  layoutId: string;
  layoutVersion: number;
  compartmentId: string;
  contentDigest: string;
  solverStatus: 'OPTIMAL' | 'FEASIBLE' | 'INFEASIBLE' | 'UNKNOWN' | 'TIMEOUT';
  provenance: string;
  ruleSnapshotId?: string;
  executionDurationMs?: number;
  placements: ItemPlacementDto[];
  unassignedItems?: UnassignedItemDto[];
  diagnostics?: string;
}

// Optimization Job
export interface CreateOptimizationJobRequest {
  declarationId: string;
  compartment: CompartmentDto;
  items: CargoItemInput[];
  ruleSnapshotId?: string;
  timeLimitSeconds?: number;
  algorithm?: string;
  async?: boolean;
}

export interface OptimizationJobEntity {
  jobId: string;
  declarationId: string;
  compartmentId: string;
  ruleSnapshotId: string;
  status: 'SUBMITTED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'TIMEOUT' | 'CANCELLED';
  currentAttemptId?: string;
  attemptCount?: number;
  solverStatus?: string;
  provenance?: string;
  timeLimitSeconds?: number;
  executionDurationMs?: number;
  errorMessage?: string;
  resultJson?: string;
  createdAt?: string;
  completedAt?: string;
}

export interface OptimizationJobStatusResponse {
  jobId: string;
  declarationId: string;
  compartmentId: string;
  ruleSnapshotId: string;
  status: 'SUBMITTED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'TIMEOUT' | 'CANCELLED';
  currentAttemptId?: string;
  attemptCount?: number;
  solverStatus?: string;
  provenance?: string;
  timeLimitSeconds?: number;
  executionDurationMs?: number;
  errorMessage?: string;
  createdAt?: string;
  completedAt?: string;
}

// Rules
export interface CompatibilityRuleDto {
  ruleId: string;
  ruleCode: string;
  primaryHazardClass: string;
  secondaryHazardClass: string;
  segregationType: string;
  minDistanceMm: number;
  description: string;
  jurisdiction: string;
}

export interface RuleSnapshotDto {
  snapshotId: string;
  jurisdiction: string;
  effectiveDate: string;
  status: string;
  snapshotDigest: string;
  rules: CompatibilityRuleDto[];
}

// Independent Validation
export interface ValidationFindingDto {
  severity: 'CRITICAL' | 'MAJOR' | 'WARNING' | 'INFO';
  code: string;
  message: string;
  involvedItemIds?: string[];
}

export interface ValidationResultDto {
  validationId: string;
  passed: boolean;
  validatorVersion: string;
  evaluatedRulesCount: number;
  findings: ValidationFindingDto[];
  candidateLayoutId?: string;
  candidateLayoutVersion?: number;
  candidateContentDigest?: string;
  ruleSnapshotId?: string;
  ruleSnapshotDigest?: string;
}

export interface RunValidationRequest {
  layout: CandidateLayoutDto;
  compartment: CompartmentDto;
  ruleSnapshot?: RuleSnapshotDto | { snapshotId: string; rules?: CompatibilityRuleDto[] };
}

// Decision Requests
export interface ApproveCandidateRequest {
  layoutVersion: number;
  expectedDigest: string;
  plannerId: string;
  notes?: string;
}

export interface RejectCandidateRequest {
  layoutVersion: number;
  plannerId: string;
  rejectionReasonCode: string;
  notes?: string;
}

// Outbox / Audit Events
export interface OutboxEventDto {
  eventId: string;
  eventType: string;
  aggregateId: string;
  aggregateType: string;
  topic: string;
  payloadJson: string;
  status: string;
  createdAt?: string;
}
