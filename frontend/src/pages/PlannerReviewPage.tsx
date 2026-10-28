import React, { useState } from 'react';
import { 
  Cpu, 
  ShieldCheck, 
  CheckCircle2, 
  AlertTriangle, 
  Search, 
  Play, 
  FileCheck, 
  Ban, 
  Clock
} from 'lucide-react';
import { assessmentApi } from '../api/assessmentApi';
import { optimizationApi } from '../api/optimizationApi';
import { validationApi } from '../api/validationApi';
import { declarationApi } from '../api/declarationApi';
import { rulesApi } from '../api/rulesApi';
import { 
  AssessmentEntity, 
  CandidateLayoutDto, 
  CargoDeclaration, 
  CompartmentDto, 
  ValidationResultDto,
  ValidationFindingDto
} from '../types/api';
import { CandidateViewer2D } from '../components/simulation/CandidateViewer2D';
import { CandidateViewer3D } from '../components/simulation/CandidateViewer3D';
import { CanvasErrorBoundary } from '../components/common/CanvasErrorBoundary';
import { useOptimizationJob } from '../hooks/useOptimizationJob';
import { useAuth } from '../context/AuthContext';
import { DeclarationDiscoveryModal } from '../components/declaration/DeclarationDiscoveryModal';

export const PlannerReviewPage: React.FC = () => {
  const { user, hasRole } = useAuth();
  const isLoadPlanner = hasRole('ROLE_LOAD_PLANNER');

  // Active Session State
  const [assessmentIdInput, setAssessmentIdInput] = useState('');
  const [declarationIdInput, setDeclarationIdInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isDiscoveryOpen, setIsDiscoveryOpen] = useState(false);

  // Entities
  const [assessment, setAssessment] = useState<AssessmentEntity | null>(null);
  const [declaration, setDeclaration] = useState<CargoDeclaration | null>(null);
  const [candidateLayout, setCandidateLayout] = useState<CandidateLayoutDto | null>(null);
  const [validationResult, setValidationResult] = useState<ValidationResultDto | null>(null);

  // Optimization Polling Hook
  const { isPolling, pollJob, cancelPolling } = useOptimizationJob();

  // Algorithm Selection
  const [selectedAlgorithm, setSelectedAlgorithm] = useState<'HEURISTIC_EP_3D_V1' | 'PYTHON_CPSAT_3D_V1' | 'CONTROLLED_STUB'>('HEURISTIC_EP_3D_V1');

  // Visualization View Mode: 3D interactive vs 2D/Table fallback
  const [viewMode, setViewMode] = useState<'3d' | '2d'>('3d');

  // Rejection Modal
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [rejectionReason, setRejectionReason] = useState('HAZARD_SEGREGATION_VIOLATION');
  const [rejectionNotes, setRejectionNotes] = useState('');

  // Default trailer compartment
  const compartment: CompartmentDto = {
    compartmentId: 'comp-standard-40ft',
    name: 'Standard 40ft High-Cube Trailer',
    lengthMm: 12000,
    widthMm: 2400,
    heightMm: 2600,
    maxPayloadKg: 25000
  };

  // 1. Create or Load Assessment
  const handleCreateAssessmentFromDeclaration = async () => {
    if (!declarationIdInput.trim()) return;
    setLoading(true);
    setErrorMessage(null);
    setActionMessage('Fetching declaration...');
    try {
      const decl = await declarationApi.getDeclaration(declarationIdInput.trim());
      setDeclaration(decl);

      setActionMessage('Initializing Assessment in Compartment Service...');
      const asmt = await assessmentApi.createAssessment({
        declarationId: decl.id,
        declarationVersion: decl.version || 1,
        ruleSnapshotId: 'snap-2026-q4-v1',
        compartment
      });
      setAssessment(asmt);
      setCandidateLayout(null);
      setValidationResult(null);
      setActionMessage(`Assessment initialized: ${asmt.id} (Workflow Status: ${asmt.workflowStatus})`);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to create assessment');
      setActionMessage('');
    } finally {
      setLoading(false);
    }
  };

  const handleLookupAssessment = async () => {
    const rawInput = assessmentIdInput.trim();
    if (!rawInput) return;
    setLoading(true);
    setErrorMessage(null);
    setActionMessage('Looking up assessment...');
    try {
      let asmt: AssessmentEntity | null = null;
      let decl: CargoDeclaration | null = null;

      // Step 1: Attempt direct Assessment lookup
      try {
        asmt = await assessmentApi.getAssessment(rawInput);
      } catch (asmtErr: any) {
        // If not found as an assessment, check if the input is a Declaration ID
        try {
          const projection = await declarationApi.getWorkflowProjection(rawInput);
          if (projection && projection.assessmentId) {
            asmt = await assessmentApi.getAssessment(projection.assessmentId);
            setActionMessage(`Resolved Declaration ${rawInput.slice(0, 8)} to Assessment ${asmt.id.slice(0, 8)}`);
          } else {
            throw asmtErr;
          }
        } catch {
          // Both lookups failed, rethrow original error
          throw asmtErr;
        }
      }

      if (!asmt) {
        throw new Error(`Assessment not found: ${rawInput}`);
      }

      // Successfully resolved assessment: set assessment state
      setAssessment(asmt);

      // Load associated declaration
      try {
        decl = await declarationApi.getDeclaration(asmt.declarationId);
        setDeclaration(decl);
      } catch {
        setDeclaration(null);
      }

      // Load associated candidate layout if one exists
      if (asmt.candidateLayoutId) {
        try {
          const layoutEntity = await assessmentApi.getCurrentLayout(asmt.id);
          if (layoutEntity) {
            const placements = typeof layoutEntity.placementsJson === 'string'
              ? JSON.parse(layoutEntity.placementsJson)
              : (layoutEntity.placements || []);
            setCandidateLayout({
              layoutId: layoutEntity.layoutId,
              layoutVersion: layoutEntity.layoutVersion,
              compartmentId: layoutEntity.compartmentId,
              contentDigest: layoutEntity.contentDigest,
              solverStatus: layoutEntity.solverStatus,
              provenance: layoutEntity.provenance,
              ruleSnapshotId: asmt.ruleSnapshotId,
              placements,
              unassignedItems: []
            });
          }
        } catch (layoutErr) {
          console.warn('Could not load candidate layout for assessment:', layoutErr);
          setCandidateLayout(null);
        }
      } else {
        setCandidateLayout(null);
      }

      // If validation result ID exists, retrieve it
      if (asmt.validationId) {
        try {
          const val = await validationApi.getValidationRun(asmt.validationId);
          setValidationResult(val);
        } catch {
          setValidationResult(null);
        }
      } else {
        setValidationResult(null);
      }

      setActionMessage(`Assessment ${asmt.id.slice(0, 8)} loaded (Workflow: ${asmt.workflowStatus})`);
    } catch (err: any) {
      // Clear stale assessment and layout data on failed lookup to avoid visual mismatch
      setAssessment(null);
      setDeclaration(null);
      setCandidateLayout(null);
      setValidationResult(null);
      setActionMessage('');
      setErrorMessage(
        err.message?.includes('not found')
          ? `Assessment not found: ${rawInput}. Enter a valid Assessment ID or Declaration ID.`
          : (err.message || 'Assessment not found')
      );
    } finally {
      setLoading(false);
    }
  };

  // 2. Submit Optimization & Poll
  const handleTriggerOptimization = async () => {
    if (!assessment || !declaration) return;
    setLoading(true);
    setErrorMessage(null);
    setActionMessage(`Submitting optimization job with algorithm: ${selectedAlgorithm}...`);
    try {
      const job = await optimizationApi.submitJob({
        declarationId: declaration.id,
        compartment,
        items: declaration.items,
        ruleSnapshotId: assessment.ruleSnapshotId || 'snap-2026-q4-v1',
        algorithm: selectedAlgorithm,
        async: true,
        timeLimitSeconds: 20
      });

      if (!job || !job.jobId) {
        throw new Error('Optimization service returned invalid job submission response without jobId');
      }

      setActionMessage(`Optimization job ${job.jobId.slice(0, 8)} submitted. Polling status...`);
      const layout = await pollJob(job.jobId);
      setCandidateLayout(layout);

      // Record candidate layout in assessment
      setActionMessage('Recording candidate layout in Compartment Assessment...');
      let updatedAsmt = assessment;
      try {
        updatedAsmt = await assessmentApi.recordCandidate(assessment.id, layout);
        setAssessment(updatedAsmt);
      } catch (recErr: any) {
        console.error('Candidate persistence error:', recErr);
        setErrorMessage(`Candidate layout produced, but recording to assessment failed: ${recErr.message}`);
        return;
      }

      // Trigger automatic zero-trust validation audit
      await handleExecuteValidation(updatedAsmt, layout);
    } catch (err: any) {
      setErrorMessage(err.message || 'Optimization workflow failed');
    } finally {
      setLoading(false);
    }
  };

  // 3. Independent Validation
  const handleExecuteValidation = async (asmt: AssessmentEntity, layout: CandidateLayoutDto) => {
    setActionMessage('Step 3: Running Independent Zero-Trust 3D Validation Engine...');
    try {
      // Resolve rule snapshot
      let snapshotDto = undefined;
      try {
        snapshotDto = await rulesApi.getRuleSnapshot(asmt.ruleSnapshotId || 'snap-2026-q4-v1');
      } catch {}

      const valResult = await validationApi.runValidation({
        layout,
        compartment,
        ruleSnapshot: snapshotDto
      });
      setValidationResult(valResult);

      // Authoritatively bind validation outcome
      const boundAsmt = await assessmentApi.recordValidation(asmt.id, valResult);
      setAssessment(boundAsmt);

      if (valResult.passed) {
        setActionMessage('Validation PASSED (0 violations). Candidate layout is eligible for approval.');
      } else {
        setActionMessage(`Validation FAILED (${valResult.findings.length} violations detected). Approval is blocked.`);
      }
    } catch (err: any) {
      setErrorMessage('Validation execution error: ' + err.message);
    }
  };

  // 4. Authorized Decision: Approve
  const handleApprove = async () => {
    if (!assessment || !candidateLayout) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const updated = await assessmentApi.approveCandidate(assessment.id, {
        layoutVersion: assessment.candidateLayoutVersion || 1,
        expectedDigest: candidateLayout.contentDigest,
        plannerId: user?.userId || 'usr-planner-001',
        notes: 'Authoritatively approved by Load Planner after independent validation audit.'
      });
      setAssessment(updated);
      setActionMessage('Assessment APPROVED successfully. Plan marked authoritative.');
    } catch (err: any) {
      setErrorMessage(err.message || 'Approval blocked by security rule');
    } finally {
      setLoading(false);
    }
  };

  // 5. Authorized Decision: Reject
  const handleReject = async () => {
    if (!assessment) return;
    setLoading(true);
    setErrorMessage(null);
    try {
      const updated = await assessmentApi.rejectCandidate(assessment.id, {
        layoutVersion: assessment.candidateLayoutVersion || 1,
        plannerId: user?.userId || 'usr-planner-001',
        rejectionReasonCode: rejectionReason,
        notes: rejectionNotes || 'Operational or hazard segregation hold'
      });
      setAssessment(updated);
      setShowRejectModal(false);
      setActionMessage(`Assessment REJECTED (${rejectionReason}). CargoLoadRejected event broadcasted to Kafka.`);
    } catch (err: any) {
      setErrorMessage(err.message || 'Rejection failed');
    } finally {
      setLoading(false);
    }
  };

  const isApproved = assessment?.workflowStatus === 'APPROVED';
  const isRejected = assessment?.workflowStatus === 'REJECTED';
  const canApprove = isLoadPlanner && !isApproved && !isRejected && validationResult?.passed === true && assessment?.workflowStatus === 'PENDING_APPROVAL';
  const canReject = isLoadPlanner && !isApproved && !isRejected && assessment?.workflowStatus !== 'DRAFT';

  return (
    <div style={{ maxWidth: '1240px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Session Management Bar */}
      <div className="glass-panel" style={{ padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '14px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button
            onClick={() => setIsDiscoveryOpen(true)}
            data-testid="open-planner-discovery-btn"
            style={{
              padding: '8px 14px',
              background: 'rgba(59, 130, 246, 0.15)',
              color: '#60a5fa',
              border: '1px solid rgba(59, 130, 246, 0.3)',
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              cursor: 'pointer'
            }}
          >
            <Search size={14} /> Browse Declarations
          </button>
          <input
            type="text"
            placeholder="Enter Declaration ID..."
            value={declarationIdInput}
            onChange={(e) => setDeclarationIdInput(e.target.value)}
            style={{ width: '220px', fontSize: '0.85rem' }}
          />
          <button
            onClick={handleCreateAssessmentFromDeclaration}
            disabled={loading || !declarationIdInput.trim()}
            style={{
              padding: '8px 14px',
              background: 'var(--accent-primary)',
              color: '#fff',
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              cursor: 'pointer'
            }}
          >
            <Play size={14} /> Start New Assessment
          </button>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <input
            type="text"
            placeholder="Lookup Assessment or Declaration ID..."
            value={assessmentIdInput}
            onChange={(e) => setAssessmentIdInput(e.target.value)}
            style={{ width: '260px', fontSize: '0.85rem' }}
          />
          <button
            onClick={handleLookupAssessment}
            disabled={loading || !assessmentIdInput.trim()}
            style={{
              padding: '8px 14px',
              background: 'var(--bg-secondary)',
              color: 'var(--text-main)',
              border: '1px solid var(--border-color)',
              borderRadius: '6px',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'center',
              gap: '6px'
            }}
          >
            <Search size={14} /> Lookup
          </button>
        </div>
      </div>

      {/* Live Operational Status Messages */}
      {actionMessage && (
        <div style={{
          background: 'rgba(59, 130, 246, 0.1)',
          border: '1px solid rgba(59, 130, 246, 0.3)',
          borderRadius: '6px',
          padding: '10px 16px',
          color: '#93c5fd',
          fontSize: '0.85rem',
          display: 'flex',
          alignItems: 'center',
          gap: '8px'
        }}>
          <Clock size={16} className={isPolling || loading ? 'spin' : ''} />
          <span>{actionMessage}</span>
          {isPolling && (
            <button
              onClick={cancelPolling}
              style={{
                marginLeft: 'auto',
                background: 'transparent',
                border: '1px solid rgba(239, 68, 68, 0.4)',
                color: '#f87171',
                padding: '2px 8px',
                borderRadius: '4px',
                fontSize: '0.75rem'
              }}
            >
              Cancel Polling
            </button>
          )}
        </div>
      )}

      {errorMessage && (
        <div style={{
          background: 'rgba(239, 68, 68, 0.15)',
          border: '1px solid var(--accent-danger)',
          borderRadius: '8px',
          padding: '12px 16px',
          color: '#f87171',
          display: 'flex',
          alignItems: 'center',
          gap: '10px',
          fontSize: '0.85rem'
        }}>
          <AlertTriangle size={18} />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Assessment Header Card */}
      {assessment && (
        <div className="glass-panel" style={{ padding: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px' }}>
                <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>
                  Assessment #{assessment.id.slice(0, 8)}
                </h3>
                <span className={`badge badge-${assessment.workflowStatus.toLowerCase().replace('_', '-')}`}>
                  Workflow: {assessment.workflowStatus}
                </span>
                {assessment.ruleSnapshotId && (
                  <span className="badge badge-draft">
                    Snapshot: {assessment.ruleSnapshotId}
                  </span>
                )}
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Declaration ID: <code>{assessment.declarationId}</code> (v{assessment.declarationVersion}) &bull; Compartment: <code>{assessment.compartmentId}</code>
              </div>
            </div>

            {/* Decision & Action Bar */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              {/* Algorithm Selector */}
              {!isApproved && !isRejected && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <select
                    value={selectedAlgorithm}
                    onChange={(e: any) => setSelectedAlgorithm(e.target.value)}
                    disabled={isPolling || loading}
                    style={{ fontSize: '0.8rem', padding: '6px 10px' }}
                  >
                    <option value="HEURISTIC_EP_3D_V1">Heuristic 3D (HEURISTIC_EP_3D_V1)</option>
                    <option value="PYTHON_CPSAT_3D_V1">OR-Tools CP-SAT Worker (PYTHON_CPSAT_3D_V1)</option>
                    <option value="CONTROLLED_STUB">Deterministic Controlled Stub</option>
                  </select>

                  <button
                    onClick={handleTriggerOptimization}
                    disabled={isPolling || loading}
                    style={{
                      padding: '8px 14px',
                      background: 'var(--accent-primary)',
                      color: '#fff',
                      borderRadius: '6px',
                      fontWeight: 600,
                      fontSize: '0.85rem',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px'
                    }}
                  >
                    <Cpu size={14} className={isPolling ? 'spin' : ''} />
                    {isPolling ? 'Solving...' : 'Run Optimization'}
                  </button>
                </div>
              )}

              {/* Authoritative Approval Button */}
              {canApprove && (
                <button
                  onClick={handleApprove}
                  disabled={loading}
                  style={{
                    padding: '8px 16px',
                    background: 'var(--accent-success)',
                    color: '#fff',
                    borderRadius: '6px',
                    fontWeight: 700,
                    fontSize: '0.85rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px'
                  }}
                >
                  <FileCheck size={15} /> Authorize & Approve Layout
                </button>
              )}

              {/* Rejection Button */}
              {canReject && (
                <button
                  onClick={() => setShowRejectModal(true)}
                  disabled={loading}
                  style={{
                    padding: '8px 14px',
                    background: 'rgba(239, 68, 68, 0.2)',
                    color: '#f87171',
                    border: '1px solid rgba(239, 68, 68, 0.4)',
                    borderRadius: '6px',
                    fontWeight: 600,
                    fontSize: '0.85rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px'
                  }}
                >
                  <Ban size={15} /> Operational Rejection
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* View Mode Toggle: 3D Interactive Simulation vs 2D Plan Fallback */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border-color)', paddingBottom: '10px' }}>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={() => setViewMode('3d')}
            style={{
              padding: '6px 14px',
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: 600,
              background: viewMode === '3d' ? 'var(--bg-secondary)' : 'transparent',
              color: viewMode === '3d' ? 'var(--text-main)' : 'var(--text-muted)',
              border: viewMode === '3d' ? '1px solid var(--border-color)' : '1px solid transparent'
            }}
          >
            Interactive 3D Simulation
          </button>
          <button
            onClick={() => setViewMode('2d')}
            style={{
              padding: '6px 14px',
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: 600,
              background: viewMode === '2d' ? 'var(--bg-secondary)' : 'transparent',
              color: viewMode === '2d' ? 'var(--text-main)' : 'var(--text-muted)',
              border: viewMode === '2d' ? '1px solid var(--border-color)' : '1px solid transparent'
            }}
          >
            2D Floor-Plan &amp; Table Fallback
          </button>
        </div>

        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          {viewMode === '3d' ? 'WebGL 3D Orbit Controls Enabled' : 'Accessible 2D SVG & Structured Coordinate Grid'}
        </div>
      </div>

      {/* Primary Visualizer Viewport */}
      {viewMode === '3d' ? (
        <CanvasErrorBoundary onFallbackTo2D={() => setViewMode('2d')}>
          <CandidateViewer3D
            compartment={compartment}
            layout={candidateLayout}
            validation={validationResult}
            isApproved={isApproved}
            isRejected={isRejected}
          />
        </CanvasErrorBoundary>
      ) : (
        <CandidateViewer2D
          compartment={compartment}
          layout={candidateLayout}
          validation={validationResult}
          isApproved={isApproved}
          isRejected={isRejected}
        />
      )}

      {/* Independent Validation Audit Details */}
      {validationResult && (
        <div className="glass-panel" style={{ padding: '20px' }}>
          <h3 style={{ fontSize: '1.05rem', margin: '0 0 14px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldCheck size={18} style={{ color: validationResult.passed ? 'var(--accent-success)' : 'var(--accent-danger)' }} />
            Independent 3D Validation Engine Findings ({validationResult.findings.length})
          </h3>

          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
            Validator Engine: {validationResult.validatorVersion} &bull; Rules Evaluated: {validationResult.evaluatedRulesCount} &bull; Candidate Digest: <code>{validationResult.candidateContentDigest?.slice(0, 24)}...</code>
          </div>

          {/* Centre of Gravity (CoG) & Load-Balance Feasibility Status */}
          {validationResult.cogXMm !== undefined && validationResult.cogXMm !== null && (
            <div style={{
              background: 'rgba(255, 255, 255, 0.03)',
              border: '1px solid var(--border-color)',
              borderRadius: '6px',
              padding: '12px 14px',
              marginBottom: '12px',
              fontSize: '0.85rem'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <span style={{ fontWeight: 600, color: 'var(--text-main)' }}>Centre of Gravity (CoG) & Load Balance</span>
                <span className={`badge ${
                  validationResult.loadBalanceStatus === 'BALANCED'
                    ? 'badge-ready'
                    : validationResult.loadBalanceStatus === 'OUT_OF_BOUNDS'
                    ? 'badge-validation-failed'
                    : 'badge-draft'
                }`}>
                  {validationResult.loadBalanceStatus === 'BALANCED'
                    ? 'BALANCED (ENFORCED BOUNDS)'
                    : validationResult.loadBalanceStatus === 'OUT_OF_BOUNDS'
                    ? 'OUT OF BOUNDS'
                    : 'NOT CONFIGURED (INFORMATIONAL)'}
                </span>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '10px', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                <div>CoG X (Longitudinal): <strong style={{ color: 'var(--text-main)' }}>{validationResult.cogXMm} mm</strong></div>
                <div>CoG Y (Lateral): <strong style={{ color: 'var(--text-main)' }}>{validationResult.cogYMm} mm</strong></div>
                <div>CoG Z (Elevation): <strong style={{ color: 'var(--text-main)' }}>{validationResult.cogZMm} mm</strong></div>
                <div>Total Placed Mass: <strong style={{ color: 'var(--text-main)' }}>{validationResult.totalMassKg} kg</strong></div>
              </div>
              {validationResult.loadBalanceStatus === 'NOT_CONFIGURED' && (
                <div style={{ marginTop: '8px', fontSize: '0.75rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
                  Notice: Physical axle limits and compartment CoG envelopes are not configured. Calculated CoG is reported for engineering review; physical load-balance feasibility has not been established.
                </div>
              )}
            </div>
          )}

          {validationResult.findings.length === 0 ? (
            <div style={{
              background: 'rgba(16, 185, 129, 0.1)',
              border: '1px solid rgba(16, 185, 129, 0.3)',
              borderRadius: '6px',
              padding: '12px',
              color: 'var(--accent-success)',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}>
              <CheckCircle2 size={16} />
              <span>Compliant: AABB containment verified, no 3D geometric collisions, support and stacking verified, and IMDG segregation minimums satisfied.</span>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {validationResult.findings.map((finding: ValidationFindingDto, idx: number) => (
                <div key={idx} style={{
                  background: 'rgba(239, 68, 68, 0.1)',
                  border: '1px solid rgba(239, 68, 68, 0.3)',
                  borderRadius: '6px',
                  padding: '10px 14px'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                    <span style={{ fontWeight: 600, color: '#f87171', fontSize: '0.85rem' }}>{finding.code}</span>
                    <span className="badge badge-validation-failed">{finding.severity}</span>
                  </div>
                  <div style={{ color: '#fca5a5', fontSize: '0.8rem' }}>{finding.message}</div>
                  {finding.involvedItemIds && finding.involvedItemIds.length > 0 && (
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                      Involved Items: <code>{finding.involvedItemIds.join(', ')}</code>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Rejection Modal */}
      {showRejectModal && (
        <div style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0,0,0,0.7)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 100
        }}>
          <div className="glass-panel" style={{ width: '480px', padding: '24px', background: '#0f172a' }}>
            <h3 style={{ fontSize: '1.15rem', color: '#f87171', margin: '0 0 10px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <AlertTriangle size={18} /> Reject Candidate Layout
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '14px' }}>
              Submitting a rejection transitions the assessment to REJECTED and enqueues a transactional <code>CargoLoadRejected</code> event to Kafka for downstream projection.
            </p>

            <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '4px' }}>Rejection Reason Code</label>
            <select
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              style={{ width: '100%', marginBottom: '14px' }}
            >
              <option value="HAZARD_SEGREGATION_VIOLATION">HAZARD_SEGREGATION_VIOLATION</option>
              <option value="OPERATIONAL_CAPACITY_HOLD">OPERATIONAL_CAPACITY_HOLD</option>
              <option value="EXCEEDS_LOCAL_LIMITS">EXCEEDS_LOCAL_LIMITS</option>
            </select>

            <label style={{ display: 'block', fontSize: '0.8rem', marginBottom: '4px' }}>Planner Operational Notes</label>
            <textarea
              rows={3}
              value={rejectionNotes}
              onChange={(e) => setRejectionNotes(e.target.value)}
              placeholder="Operational justification for rejection..."
              style={{ width: '100%', marginBottom: '18px' }}
            />

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                onClick={() => setShowRejectModal(false)}
                style={{ background: 'transparent', color: 'var(--text-muted)', border: 'none', padding: '8px 14px' }}
              >
                Cancel
              </button>
              <button
                onClick={handleReject}
                style={{
                  background: 'var(--accent-danger)',
                  color: '#fff',
                  border: 'none',
                  padding: '8px 16px',
                  borderRadius: '6px',
                  fontWeight: 600
                }}
              >
                Confirm Rejection & Dispatch Event
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Cross-Tenant Declaration Discovery Modal */}
      <DeclarationDiscoveryModal
        isOpen={isDiscoveryOpen}
        onClose={() => setIsDiscoveryOpen(false)}
        onSelectDeclaration={(decl) => {
          setDeclarationIdInput(decl.id);
          setDeclaration(decl);
        }}
        title="Consignment Declarations Queue"
        roleDescription="Browse cross-tenant declarations, filter by status, and queue for 3D optimization assessment"
        isPlanner={true}
      />
    </div>
  );
};
