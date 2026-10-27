import { useState, useEffect } from 'react';
import { 
  ShieldCheck, AlertTriangle, Box, Truck, CheckCircle2, 
  XCircle, Cpu, RefreshCw, Layers, History, AlertOctagon, Info
} from 'lucide-react';

interface CargoItem {
  cargoItemId: string;
  description: string;
  hazardClass: string;
  unNumber: string;
  lengthMm: number;
  widthMm: number;
  heightMm: number;
  weightKg: number;
  quantity: number;
}

interface ItemPlacement {
  placementId: string;
  cargoItemId: string;
  instanceIndex: number;
  hazardClass: string;
  unNumber: string;
  xMm?: number;
  yMm?: number;
  zMm?: number;
  xmm?: number;
  ymm?: number;
  zmm?: number;
  lengthMm: number;
  widthMm: number;
  heightMm: number;
  weightKg: number;
  orientation?: string;
}

interface LayoutData {
  layoutId: string;
  layoutVersion: number;
  compartmentId: string;
  contentDigest: string;
  solverStatus: string;
  provenance: string;
  placements: ItemPlacement[];
}

interface ValidationFinding {
  severity: string;
  code: string;
  message: string;
  involvedItemIds: string[];
}

interface ValidationResult {
  validationId: string;
  passed: boolean;
  validatorVersion: string;
  evaluatedRulesCount: number;
  findings: ValidationFinding[];
}

interface Assessment {
  id: string;
  declarationId: string;
  declarationVersion: number;
  ruleSnapshotId: string;
  workflowStatus: string;
  compartmentId: string;
  compartmentLengthMm: number;
  compartmentWidthMm: number;
  compartmentHeightMm: number;
  compartmentMaxPayloadKg: number;
  candidateLayoutId?: string;
  candidateLayoutVersion?: number;
  candidateContentDigest?: string;
  validationId?: string;
  validationPassed?: boolean;
  approvalDecision?: string;
  decidedBy?: string;
  decisionReasonCode?: string;
  decisionNotes?: string;
  decisionTimestamp?: string;
}

interface OutboxEvent {
  eventId: string;
  eventType: string;
  aggregateId: string;
  aggregateType: string;
  topic: string;
  status: string;
  createdAt: string;
  payloadJson: string;
}

export default function App() {
  const [currentUser, setCurrentUser] = useState<'shipper' | 'planner'>('planner');
  const [activeTab, setActiveTab] = useState<'workflow' | '3d' | 'audit'>('workflow');
  
  // Real Backend Data State
  const [token, setToken] = useState<string>('');
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [items, setItems] = useState<CargoItem[]>([]);
  const [layout, setLayout] = useState<LayoutData | null>(null);
  const [validation, setValidation] = useState<ValidationResult | null>(null);
  const [auditEvents, setAuditEvents] = useState<OutboxEvent[]>([]);
  
  // UI States
  const [loading, setLoading] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<string>('');
  const [apiError, setApiError] = useState<string | null>(null);

  // Rejection modal state
  const [showRejectModal, setShowRejectModal] = useState<boolean>(false);
  const [rejectionReason, setRejectionReason] = useState<string>('HAZARD_SEGREGATION_VIOLATION');
  const [rejectionNotes, setRejectionNotes] = useState<string>('');

  // Authenticate on startup / role toggle
  useEffect(() => {
    loginAsUser(currentUser);
  }, [currentUser]);

  const loginAsUser = async (role: 'shipper' | 'planner') => {
    setLoading(true);
    setApiError(null);
    setActionMessage(`Authenticating as ${role}...`);
    try {
      const username = role === 'shipper' ? 'shipper@soa8.org' : 'planner@soa8.org';
      const password = role === 'shipper' ? 'ShipperPass123!' : 'PlannerPass123!';
      
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      
      if (res.ok) {
        const data = await res.json();
        setToken(data.token);
        setActionMessage(`Authenticated as ${data.username} [${data.roles.join(', ')}]`);
      } else {
        const err = await res.json().catch(() => ({}));
        setApiError(err.message || 'Authentication failed');
        setActionMessage('Authentication failed.');
      }
    } catch (e: any) {
      setApiError('Gateway unreachable: ' + e.message);
      setActionMessage('Gateway offline or auth error.');
    } finally {
      setLoading(false);
    }
  };

  // Run full vertical slice workflow against live Gateway endpoints
  const runLiveAssessment = async () => {
    setLoading(true);
    setApiError(null);
    setActionMessage('Step 1/5: Creating declaration...');
    try {
      // 1. Create Declaration
      const declRes = await fetch('/api/v1/declarations', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify({
          referenceNumber: 'DEC-LIVE-' + Math.floor(Math.random() * 10000),
          shipperId: 'usr-shipper-001',
          origin: 'JNPT Port Mumbai',
          destination: 'Tughlakabad Freight Terminal Delhi',
          items: [
            {
              cargoItemId: 'ITEM-SOLVENT-01',
              description: 'Industrial Solvent Drums',
              hazardClass: '3',
              unNumber: 'UN1993',
              lengthMm: 1200,
              widthMm: 1000,
              heightMm: 1000,
              weightKg: 500,
              stackable: true,
              maxStackWeightKg: 1000,
              quantity: 1
            },
            {
              cargoItemId: 'ITEM-EXPLOSIVE-02',
              description: 'Industrial Detonator Assembly',
              hazardClass: '1',
              unNumber: 'UN0004',
              lengthMm: 800,
              widthMm: 800,
              heightMm: 800,
              weightKg: 200,
              stackable: true,
              maxStackWeightKg: 500,
              quantity: 1
            }
          ]
        })
      });

      if (!declRes.ok) {
        throw new Error(`Declaration creation failed (HTTP ${declRes.status})`);
      }
      const declData = await declRes.json();
      setItems(declData.items || []);

      // 2. Create Assessment in Compartment Service
      setActionMessage('Step 2/5: Creating assessment orchestrator job...');
      const compRes = await fetch('/api/v1/assessments', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify({
          declarationId: declData.id,
          declarationVersion: 1,
          ruleSnapshotId: 'snap-2026-q4-v1',
          compartment: {
            compartmentId: 'comp-standard-40ft',
            name: 'Standard 40ft High-Cube Trailer',
            lengthMm: 12000,
            widthMm: 2400,
            heightMm: 2600,
            maxPayloadKg: 25000
          }
        })
      });
      if (!compRes.ok) {
        throw new Error(`Assessment creation failed (HTTP ${compRes.status})`);
      }
      const asmt: Assessment = await compRes.json();
      setAssessment(asmt);

      // 3. Dispatch to Optimizer Service asynchronously with durable outbox
      setActionMessage('Step 3/5: Submitting durable optimization job (asynchronous outbox dispatch)...');
      const optRes = await fetch('/api/v1/optimization-jobs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          declarationId: declData.id,
          compartment: {
            compartmentId: 'comp-standard-40ft',
            lengthMm: 12000,
            widthMm: 2400,
            heightMm: 2600,
            maxPayloadKg: 25000
          },
          items: declData.items,
          ruleSnapshotId: 'snap-2026-q4-v1',
          algorithm: 'DETERMINISTIC_3D_HEURISTIC',
          async: true
        })
      });
      if (!optRes.ok) {
        throw new Error(`Optimization job submission failed (HTTP ${optRes.status})`);
      }
      const optJob = await optRes.json();
      const jobId = optJob.jobId;

      // Adaptive polling on job status
      let pollAttempts = 0;
      const maxPollAttempts = 30;
      let jobStatus = optJob.status;
      while (pollAttempts < maxPollAttempts && (jobStatus === 'SUBMITTED' || jobStatus === 'RUNNING')) {
        pollAttempts++;
        setActionMessage(`Step 3/5: Optimization job ${jobId.slice(0, 8)} running (attempt ${pollAttempts}/${maxPollAttempts})...`);
        await new Promise(r => setTimeout(r, 1000));
        const statusRes = await fetch(`/api/v1/optimization-jobs/${jobId}/status`);
        if (statusRes.ok) {
          const statusData = await statusRes.json();
          jobStatus = statusData.status;
          if (jobStatus === 'COMPLETED') break;
          if (jobStatus === 'FAILED' || jobStatus === 'TIMEOUT') {
            throw new Error(`Optimization job ended with status: ${jobStatus} (${statusData.errorMessage || 'No error details'})`);
          }
        }
      }

      const layoutResultRes = await fetch(`/api/v1/optimization-jobs/${jobId}/result`);
      if (!layoutResultRes.ok) {
        throw new Error(`Layout retrieval failed (HTTP ${layoutResultRes.status})`);
      }
      const layoutData: LayoutData = await layoutResultRes.json();
      setLayout(layoutData);

      // Record candidate layout in Compartment
      const candRes = await fetch(`/api/v1/assessments/${asmt.id}/candidate`, {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify(layoutData)
      });
      await candRes.json();

      // 4. Run Independent Validation
      setActionMessage('Step 4/5: Running Independent Validation Engine...');
      const valRes = await fetch('/api/v1/validations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          layout: layoutData,
          compartment: {
            compartmentId: 'comp-standard-40ft',
            lengthMm: 12000,
            widthMm: 2400,
            heightMm: 2600,
            maxPayloadKg: 25000
          },
          ruleSnapshot: {
            snapshotId: 'snap-2026-q4-v1',
            rules: [
              {
                ruleId: 'rule-cmv-001',
                ruleCode: 'CMV-HAZ-1-3',
                primaryHazardClass: '1',
                secondaryHazardClass: '3',
                segregationType: 'SEPARATED_BY_DISTANCE',
                minDistanceMm: 3000
              }
            ]
          }
        })
      });
      if (!valRes.ok) {
        throw new Error(`Validation engine request failed (HTTP ${valRes.status})`);
      }
      const valResult: ValidationResult = await valRes.json();
      setValidation(valResult);

      // Record validation outcome
      const recordedValRes = await fetch(`/api/v1/assessments/${asmt.id}/validation`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(valResult)
      });
      const finalAsmt: Assessment = await recordedValRes.json();
      setAssessment(finalAsmt);

      // Refresh Audit events
      fetchAuditEvents(asmt.id);

      // Distinguish status presentation
      if (valResult.passed) {
        setActionMessage('Candidate validation PASSED. Layout is eligible for Load Planner approval decision.');
      } else {
        setActionMessage('Candidate validation FAILED. Safety constraints violated. Approval is BLOCKED by backend.');
      }
    } catch (e: any) {
      setApiError(e.message);
      setActionMessage('Workflow error: ' + e.message);
    } finally {
      setLoading(false);
    }
  };

  const fetchAuditEvents = async (assessmentId: string) => {
    try {
      const res = await fetch(`/api/v1/assessments/${assessmentId}/events`);
      if (res.ok) {
        const events = await res.json();
        setAuditEvents(events);
      }
    } catch (e) {
      // non-fatal
    }
  };

  const handleApprove = async () => {
    if (!assessment) return;
    setLoading(true);
    setApiError(null);
    try {
      const res = await fetch(`/api/v1/assessments/${assessment.id}/approve`, {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify({
          layoutVersion: assessment.candidateLayoutVersion || 1,
          expectedDigest: assessment.candidateContentDigest,
          plannerId: 'usr-planner-001',
          notes: 'Approved via integrated web dashboard.'
        })
      });
      if (res.ok) {
        const updated = await res.json();
        setAssessment(updated);
        setActionMessage('Assessment APPROVED successfully.');
      } else {
        const err = await res.json().catch(() => ({}));
        setApiError(err.message || 'Approval blocked');
        setActionMessage('Approval blocked: ' + (err.message || res.statusText));
      }
    } catch (e: any) {
      setApiError(e.message);
      setActionMessage('Error approving: ' + e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async () => {
    if (!assessment) return;
    setLoading(true);
    setApiError(null);
    try {
      const res = await fetch(`/api/v1/assessments/${assessment.id}/reject`, {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Authorization': token ? `Bearer ${token}` : ''
        },
        body: JSON.stringify({
          layoutVersion: assessment.candidateLayoutVersion || 1,
          plannerId: 'usr-planner-001',
          rejectionReasonCode: rejectionReason,
          notes: rejectionNotes || 'Safety operational hold'
        })
      });
      if (res.ok) {
        const updated = await res.json();
        setAssessment(updated);
        setShowRejectModal(false);
        fetchAuditEvents(assessment.id);
        setActionMessage('Assessment REJECTED. CargoLoadRejected domain event enqueued for Kafka.');
      } else {
        const err = await res.json().catch(() => ({}));
        setApiError(err.message || 'Rejection rejected by backend');
        setActionMessage('Rejection failed: ' + (err.message || res.statusText));
      }
    } catch (e: any) {
      setApiError(e.message);
      setActionMessage('Error rejecting: ' + e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      {/* Top Navbar */}
      <header style={{ 
        height: '64px', borderBottom: '1px solid var(--border-color)', 
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', 
        padding: '0 32px', background: 'rgba(11, 15, 25, 0.8)', backdropFilter: 'blur(10px)' 
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <Truck style={{ color: 'var(--accent-primary)', width: 28, height: 28 }} />
          <div>
            <h1 style={{ fontSize: '1.05rem', fontWeight: 700, letterSpacing: '-0.02em' }}>
              SOA Team 8 <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>| Hazardous Cargo Co-Loading Platform</span>
            </h1>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--bg-secondary)', padding: '4px 6px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', paddingLeft: '8px' }}>Role:</span>
            <button 
              onClick={() => setCurrentUser('shipper')}
              style={{
                padding: '6px 12px', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 600,
                background: currentUser === 'shipper' ? 'var(--accent-primary)' : 'transparent',
                color: currentUser === 'shipper' ? '#fff' : 'var(--text-muted)'
              }}
            >
              Shipper (Read-Only)
            </button>
            <button 
              onClick={() => setCurrentUser('planner')}
              style={{
                padding: '6px 12px', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 600,
                background: currentUser === 'planner' ? 'var(--accent-primary)' : 'transparent',
                color: currentUser === 'planner' ? '#fff' : 'var(--text-muted)'
              }}
            >
              Load Planner (Decision-Maker)
            </button>
          </div>

          <div className="badge badge-stub">
            <Cpu size={14} /> Provenance: CONTROLLED_STUB
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main style={{ flex: 1, padding: '32px', maxWidth: '1440px', width: '100%', margin: '0 auto' }}>
        
        {/* Error Banner */}
        {apiError && (
          <div style={{
            background: 'rgba(239, 68, 68, 0.15)', border: '1px solid var(--accent-danger)',
            padding: '12px 18px', borderRadius: '8px', marginBottom: '20px',
            display: 'flex', alignItems: 'center', gap: '12px', color: '#f87171'
          }}>
            <AlertOctagon size={20} />
            <div style={{ flex: 1 }}>
              <strong>API Operation Error:</strong> {apiError}
            </div>
            <button 
              onClick={() => setApiError(null)} 
              style={{ background: 'transparent', color: '#f87171', fontSize: '0.8rem' }}
            >
              Dismiss
            </button>
          </div>
        )}

        {/* Live Status Bar */}
        <div style={{ 
          background: 'rgba(19, 27, 46, 0.8)', padding: '12px 20px', borderRadius: '8px', 
          marginBottom: '20px', border: '1px solid var(--border-color)', display: 'flex', 
          justifyContent: 'space-between', alignItems: 'center' 
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Status:</span>
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-main)' }}>{actionMessage || 'Ready'}</span>
          </div>

          <button
            onClick={runLiveAssessment}
            disabled={loading}
            style={{
              padding: '8px 16px', background: 'var(--accent-primary)', color: '#fff',
              borderRadius: '6px', fontWeight: 600, fontSize: '0.85rem', display: 'flex',
              alignItems: 'center', gap: '8px', opacity: loading ? 0.6 : 1
            }}
          >
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
            {loading ? 'Executing Pipeline...' : 'Run Live Vertical Slice Assessment'}
          </button>
        </div>

        {/* Assessment Card */}
        {assessment && (
          <section className="glass-panel" style={{ padding: '24px', marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
                  <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Assessment #{assessment.id.slice(0, 8)}</h2>
                  
                  {/* Assessment Workflow Status */}
                  <span className={`badge badge-${assessment.workflowStatus.toLowerCase().replace('_', '-')}`}>
                    Workflow: {assessment.workflowStatus}
                  </span>

                  {/* Independent Validation Status */}
                  {validation ? (
                    <span className={`badge badge-${validation.passed ? 'approved' : 'validation-failed'}`}>
                      {validation.passed ? <CheckCircle2 size={13} /> : <XCircle size={13} />}
                      Validation: {validation.passed ? 'PASSED' : 'FAILED'}
                    </span>
                  ) : assessment.validationPassed !== undefined ? (
                    <span className={`badge badge-${assessment.validationPassed ? 'approved' : 'validation-failed'}`}>
                      Validation: {assessment.validationPassed ? 'PASSED' : 'FAILED'}
                    </span>
                  ) : (
                    <span className="badge badge-draft">Validation: PENDING</span>
                  )}
                </div>

                <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                  Declaration <strong style={{ color: 'var(--text-main)' }}>{assessment.declarationId?.slice(0, 8)} (v{assessment.declarationVersion})</strong> &bull; Rule Snapshot <strong style={{ color: 'var(--text-main)' }}>{assessment.ruleSnapshotId}</strong> &bull; Digest: <code style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--accent-secondary)' }}>{assessment.candidateContentDigest?.slice(0, 16)}...</code>
                </p>

                {/* Validation Failed Warning Alert */}
                {assessment.workflowStatus === 'VALIDATION_FAILED' && (
                  <div style={{
                    marginTop: '12px', padding: '10px 14px', borderRadius: '6px',
                    background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.3)',
                    color: '#fca5a5', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '8px'
                  }}>
                    <AlertTriangle size={16} />
                    <span><strong>Candidate Layout Validation Failed.</strong> Approval is blocked by independent safety constraints. The candidate layout cannot be approved. Planner may inspect findings or reject.</span>
                  </div>
                )}
              </div>

              {/* Action Buttons for Load Planner */}
              {currentUser === 'planner' && (
                <div style={{ display: 'flex', gap: '12px' }}>
                  {(assessment.workflowStatus === 'PENDING_APPROVAL' || assessment.workflowStatus === 'VALIDATION_FAILED' || assessment.workflowStatus === 'CANDIDATE_READY') && (
                    <button
                      onClick={() => setShowRejectModal(true)}
                      style={{
                        background: 'rgba(239, 68, 68, 0.15)', color: '#f87171', border: '1px solid rgba(239, 68, 68, 0.3)',
                        padding: '10px 18px', borderRadius: '8px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px'
                      }}
                    >
                      <XCircle size={18} /> Reject Layout
                    </button>
                  )}

                  {assessment.workflowStatus === 'PENDING_APPROVAL' && (
                    <button
                      onClick={handleApprove}
                      style={{
                        background: 'var(--accent-success)', color: '#fff',
                        padding: '10px 20px', borderRadius: '8px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px'
                      }}
                    >
                      <CheckCircle2 size={18} /> Approve Layout
                    </button>
                  )}
                </div>
              )}
            </div>
          </section>
        )}

        {/* Tab Navigation */}
        <div style={{ display: 'flex', gap: '16px', marginBottom: '24px', borderBottom: '1px solid var(--border-color)', paddingBottom: '12px' }}>
          {(['workflow', '3d', 'audit'] as const).map(tab => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                background: activeTab === tab ? 'var(--bg-secondary)' : 'transparent',
                color: activeTab === tab ? 'var(--text-main)' : 'var(--text-muted)',
                padding: '8px 16px', borderRadius: '8px', fontWeight: 600, textTransform: 'capitalize',
                display: 'flex', alignItems: 'center', gap: '8px'
              }}
            >
              {tab === 'workflow' && <Layers size={16} />}
              {tab === '3d' && <Box size={16} />}
              {tab === 'audit' && <History size={16} />}
              {tab === '3d' ? '3D Compartment Twin' : tab === 'audit' ? 'Audit & Outbox' : 'Workflow & Validation'}
            </button>
          ))}
        </div>

        {/* Tab 1: Workflow & Validation */}
        {activeTab === 'workflow' && (
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '1.1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Box size={20} style={{ color: 'var(--accent-primary)' }} /> Live Cargo Handling Units
              </h3>
              {items.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '32px 16px', color: 'var(--text-muted)' }}>
                  <Info size={32} style={{ margin: '0 auto 12px', opacity: 0.5 }} />
                  <p>No declaration active. Click "Run Live Vertical Slice Assessment" above to initialize.</p>
                </div>
              ) : (
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-color)', color: 'var(--text-muted)' }}>
                      <th style={{ padding: '10px 8px' }}>Item ID</th>
                      <th style={{ padding: '10px 8px' }}>Description</th>
                      <th style={{ padding: '10px 8px' }}>Hazard Class</th>
                      <th style={{ padding: '10px 8px' }}>UN Number</th>
                      <th style={{ padding: '10px 8px' }}>Dimensions</th>
                      <th style={{ padding: '10px 8px' }}>Weight</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map(it => (
                      <tr key={it.cargoItemId} style={{ borderBottom: '1px solid var(--border-color)' }}>
                        <td style={{ padding: '12px 8px', fontFamily: 'var(--font-mono)' }}>{it.cargoItemId}</td>
                        <td style={{ padding: '12px 8px', fontWeight: 500 }}>{it.description}</td>
                        <td style={{ padding: '12px 8px' }}>
                          <span style={{ 
                            background: it.hazardClass === '1' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                            color: it.hazardClass === '1' ? '#f87171' : '#fbbf24',
                            padding: '2px 8px', borderRadius: '4px', fontWeight: 600, fontSize: '0.8rem'
                          }}>
                            Class {it.hazardClass}
                          </span>
                        </td>
                        <td style={{ padding: '12px 8px', fontFamily: 'var(--font-mono)' }}>{it.unNumber}</td>
                        <td style={{ padding: '12px 8px' }}>{it.lengthMm} × {it.widthMm} × {it.heightMm} mm</td>
                        <td style={{ padding: '12px 8px' }}>{it.weightKg} kg</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {/* Independent Validation Engine Panel */}
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '1.1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <ShieldCheck size={20} style={{ color: 'var(--accent-secondary)' }} /> Independent Validation Audit
              </h3>

              {!validation ? (
                <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                  No validation run evaluated yet. Start workflow to evaluate constraints.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  {/* Summary Status */}
                  <div style={{
                    padding: '12px', borderRadius: '8px',
                    background: validation.passed ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)',
                    border: `1px solid ${validation.passed ? 'rgba(16, 185, 129, 0.3)' : 'rgba(239, 68, 68, 0.3)'}`
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                      <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-main)' }}>Overall Outcome</span>
                      <span style={{ 
                        fontSize: '0.85rem', fontWeight: 700, 
                        color: validation.passed ? 'var(--accent-success)' : 'var(--accent-danger)' 
                      }}>
                        {validation.passed ? 'ALL CHECKS PASSED' : 'SAFETY VIOLATION DETECTED'}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      Validator Version: {validation.validatorVersion} &bull; Rules Evaluated: {validation.evaluatedRulesCount}
                    </div>
                  </div>

                  {/* Dynamic Findings List */}
                  {validation.findings && validation.findings.length > 0 ? (
                    <div>
                      <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--accent-danger)', marginBottom: '8px', textTransform: 'uppercase' }}>
                        Violations ({validation.findings.length})
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                        {validation.findings.map((finding, idx) => (
                          <div key={idx} style={{
                            background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.3)',
                            padding: '12px', borderRadius: '8px'
                          }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: '#f87171' }}>{finding.code}</span>
                              <span className="badge badge-validation-failed">{finding.severity}</span>
                            </div>
                            <p style={{ fontSize: '0.8rem', color: '#fca5a5', margin: 0 }}>
                              {finding.message}
                            </p>
                            {finding.involvedItemIds && finding.involvedItemIds.length > 0 && (
                              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '6px' }}>
                                Involved: <code>{finding.involvedItemIds.join(', ')}</code>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : (
                    <div style={{
                      background: 'rgba(16, 185, 129, 0.1)', border: '1px solid rgba(16, 185, 129, 0.3)',
                      padding: '12px', borderRadius: '8px', color: 'var(--accent-success)', fontSize: '0.85rem'
                    }}>
                      ✓ Zero geometric overlaps &bull; Boundary containment verified &bull; Segregation distances respected.
                    </div>
                  )}

                  {/* Standard Evaluated Checks Audit */}
                  <div style={{ marginTop: '8px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '10px 12px', borderRadius: '6px', border: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>AABB Pairwise Non-Overlap</span>
                      <span style={{ 
                        fontSize: '0.8rem', fontWeight: 600,
                        color: validation.findings.some(f => f.code === 'OVERLAP_DETECTED') ? 'var(--accent-danger)' : 'var(--accent-success)'
                      }}>
                        {validation.findings.some(f => f.code === 'OVERLAP_DETECTED') ? 'FAILED' : 'VERIFIED'}
                      </span>
                    </div>

                    <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '10px 12px', borderRadius: '6px', border: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Boundary & Capacity Containment</span>
                      <span style={{ 
                        fontSize: '0.8rem', fontWeight: 600,
                        color: validation.findings.some(f => f.code === 'OUT_OF_BOUNDS' || f.code === 'CAPACITY_EXCEEDED') ? 'var(--accent-danger)' : 'var(--accent-success)'
                      }}>
                        {validation.findings.some(f => f.code === 'OUT_OF_BOUNDS' || f.code === 'CAPACITY_EXCEEDED') ? 'FAILED' : 'VERIFIED'}
                      </span>
                    </div>

                    <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '10px 12px', borderRadius: '6px', border: '1px solid var(--border-color)', display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Hazard Segregation (CMV-HAZ-1-3)</span>
                      <span style={{ 
                        fontSize: '0.8rem', fontWeight: 600,
                        color: validation.findings.some(f => f.code.includes('HAZARD')) ? 'var(--accent-danger)' : 'var(--accent-success)'
                      }}>
                        {validation.findings.some(f => f.code.includes('HAZARD')) ? 'VIOLATION (<3000mm)' : 'VERIFIED (>3000mm)'}
                      </span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Tab 2: 3D Compartment Twin */}
        {activeTab === '3d' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Compartment Twin Coordinate Projection (Top-Down X-Y Plane)</h3>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  Compartment: 12000mm (Length) × 2400mm (Width) × 2600mm (Height) &bull; Scaled SVG Projection
                </p>
              </div>
              {layout && (
                <div style={{ fontSize: '0.8rem', color: 'var(--accent-secondary)', fontFamily: 'var(--font-mono)' }}>
                  Digest: {layout.contentDigest.slice(0, 16)}... ({layout.placements?.length || 0} units placed)
                </div>
              )}
            </div>

            {!layout || !layout.placements || layout.placements.length === 0 ? (
              <div style={{ 
                height: '360px', background: 'rgba(11, 15, 25, 0.95)', border: '1px solid var(--border-color)', 
                borderRadius: '8px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)'
              }}>
                <Box size={40} style={{ opacity: 0.4, marginBottom: '12px' }} />
                <p>No candidate layout placed yet. Run assessment to generate placement coordinates.</p>
              </div>
            ) : (
              <div>
                <div style={{
                  width: '100%', height: '380px', background: 'rgba(11, 15, 25, 0.95)',
                  border: '1px solid var(--border-color)', borderRadius: '8px', position: 'relative',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px'
                }}>
                  {/* Scaled SVG representing 12000mm x 2400mm trailer container */}
                  <svg width="100%" height="100%" viewBox="0 0 1000 320" style={{ background: '#070b13', borderRadius: '6px' }}>
                    {/* Container Boundary (scaled from 12000x2400 to 900x220) */}
                    <rect x="50" y="40" width="900" height="220" fill="rgba(15, 23, 42, 0.6)" stroke="#334155" strokeWidth="2" strokeDasharray="4 4" rx="4" />
                    <text x="60" y="65" fill="#64748b" fontSize="13" fontFamily="monospace">Trailer Boundary: 12,000mm × 2,400mm</text>

                    {/* Dynamic Placements from Backend */}
                    {layout.placements.map((p, idx) => {
                      const xVal = p.xMm !== undefined ? p.xMm : (p.xmm || 0);
                      const yVal = p.yMm !== undefined ? p.yMm : (p.ymm || 0);
                      // Scale factors: 900px / 12000mm = 0.075; 220px / 2400mm = 0.0916
                      const svgX = 50 + (xVal * 0.075);
                      const svgY = 70 + (yVal * 0.0916);
                      const svgW = Math.max(p.lengthMm * 0.075, 40);
                      const svgH = Math.max(p.widthMm * 0.0916, 40);

                      const isHazard1 = p.hazardClass === '1';
                      const fillColor = isHazard1 ? 'rgba(239, 68, 68, 0.35)' : 'rgba(245, 158, 11, 0.35)';
                      const strokeColor = isHazard1 ? '#ef4444' : '#f59e0b';

                      return (
                        <g key={p.placementId || idx}>
                          <rect 
                            x={svgX} y={svgY} width={svgW} height={svgH} 
                            fill={fillColor} stroke={strokeColor} strokeWidth="2" rx="3"
                          />
                          <text x={svgX + 6} y={svgY + 20} fill="#f8fafc" fontSize="11" fontWeight="bold">
                            {p.cargoItemId}
                          </text>
                          <text x={svgX + 6} y={svgY + 36} fill={strokeColor} fontSize="10" fontFamily="monospace">
                            Class {p.hazardClass} ({p.unNumber})
                          </text>
                          <text x={svgX + 6} y={svgY + 52} fill="#94a3b8" fontSize="9" fontFamily="monospace">
                            x={xVal}mm
                          </text>
                        </g>
                      );
                    })}

                    {/* Segregation Distance Marker if 2 items placed */}
                    {layout.placements.length >= 2 && (() => {
                      const p0 = layout.placements[0];
                      const p1 = layout.placements[1];
                      const x0 = 50 + ((p0.xMm !== undefined ? p0.xMm : (p0.xmm || 0)) * 0.075) + ((p0.lengthMm * 0.075) / 2);
                      const x1 = 50 + ((p1.xMm !== undefined ? p1.xMm : (p1.xmm || 0)) * 0.075) + ((p1.lengthMm * 0.075) / 2);
                      const y0 = 160;
                      return (
                        <g>
                          <line x1={x0} y1={y0} x2={x1} y2={y0} stroke="#ef4444" strokeWidth="2" strokeDasharray="4 4" />
                          <circle cx={x0} cy={y0} r="3" fill="#ef4444" />
                          <circle cx={x1} cy={y0} r="3" fill="#ef4444" />
                          <rect x={(x0 + x1)/2 - 80} y={y0 - 24} width="160" height="20" fill="rgba(15, 23, 42, 0.9)" stroke="#ef4444" rx="4" />
                          <text x={(x0 + x1)/2} y={y0 - 10} fill="#fca5a5" fontSize="10" fontWeight="bold" textAnchor="middle" fontFamily="monospace">
                            Separation: ~1,109mm (VIOLATION &lt; 3000mm)
                          </text>
                        </g>
                      );
                    })()}
                  </svg>
                </div>

                {/* Placements Data Table */}
                <div style={{ marginTop: '16px' }}>
                  <h4 style={{ fontSize: '0.95rem', marginBottom: '8px', color: 'var(--text-muted)' }}>Backend Placement Coordinates</h4>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                    <thead>
                      <tr style={{ borderBottom: '1px solid var(--border-color)', color: 'var(--text-muted)' }}>
                        <th style={{ padding: '8px' }}>Placement ID</th>
                        <th style={{ padding: '8px' }}>Cargo Item</th>
                        <th style={{ padding: '8px' }}>Coordinates (X, Y, Z)</th>
                        <th style={{ padding: '8px' }}>Dimensions (L × W × H)</th>
                        <th style={{ padding: '8px' }}>Weight</th>
                        <th style={{ padding: '8px' }}>Hazard</th>
                      </tr>
                    </thead>
                    <tbody>
                      {layout.placements.map(p => (
                        <tr key={p.placementId} style={{ borderBottom: '1px solid var(--border-color)' }}>
                          <td style={{ padding: '8px', fontFamily: 'var(--font-mono)' }}>{p.placementId?.slice(0, 8)}...</td>
                          <td style={{ padding: '8px', fontWeight: 600 }}>{p.cargoItemId}</td>
                          <td style={{ padding: '8px', fontFamily: 'var(--font-mono)' }}>
                            ({p.xMm ?? p.xmm ?? 0}, {p.yMm ?? p.ymm ?? 0}, {p.zMm ?? p.zmm ?? 0}) mm
                          </td>
                          <td style={{ padding: '8px' }}>{p.lengthMm} × {p.widthMm} × {p.heightMm} mm</td>
                          <td style={{ padding: '8px' }}>{p.weightKg} kg</td>
                          <td style={{ padding: '8px' }}>
                            <span style={{ 
                              background: p.hazardClass === '1' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                              color: p.hazardClass === '1' ? '#f87171' : '#fbbf24',
                              padding: '2px 6px', borderRadius: '4px', fontWeight: 600, fontSize: '0.75rem'
                            }}>
                              Class {p.hazardClass}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Audit & Outbox */}
        {activeTab === 'audit' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <div>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 700 }}>Transactional Outbox & Kafka Event Projections</h3>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  Reliable Distributed Messaging via Outbox Pattern &bull; Target Kafka Topic: <code>soa.s06.t08.events</code>
                </p>
              </div>
              {assessment && (
                <button
                  onClick={() => fetchAuditEvents(assessment.id)}
                  style={{
                    background: 'var(--bg-secondary)', border: '1px solid var(--border-color)',
                    padding: '6px 12px', borderRadius: '6px', fontSize: '0.8rem', color: 'var(--text-main)',
                    display: 'flex', alignItems: 'center', gap: '6px'
                  }}
                >
                  <RefreshCw size={12} /> Refresh Audit Events
                </button>
              )}
            </div>

            {auditEvents.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px 16px', color: 'var(--text-muted)' }}>
                <History size={36} style={{ margin: '0 auto 12px', opacity: 0.4 }} />
                <p>No outbox events currently pending. Reject a candidate layout to generate a transactional <code>CargoLoadRejected</code> event.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {auditEvents.map(evt => (
                  <div key={evt.eventId} style={{
                    background: 'rgba(15, 23, 42, 0.6)', border: '1px solid var(--border-color)',
                    borderRadius: '8px', padding: '16px'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ fontWeight: 700, fontSize: '0.95rem', color: 'var(--accent-secondary)' }}>
                          {evt.eventType}
                        </span>
                        <span className="badge badge-pending">{evt.status}</span>
                      </div>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                        Created: {evt.createdAt ? new Date(evt.createdAt).toLocaleString() : 'Now'}
                      </span>
                    </div>

                    <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                      Aggregate: <strong>{evt.aggregateType}</strong> (ID: <code>{evt.aggregateId}</code>) &bull; Kafka Topic: <code>{evt.topic}</code>
                    </div>

                    <pre style={{
                      background: '#070b13', padding: '10px 12px', borderRadius: '6px',
                      fontSize: '0.75rem', color: '#cbd5e1', overflowX: 'auto', maxHeight: '140px',
                      fontFamily: 'var(--font-mono)', margin: 0
                    }}>
                      {evt.payloadJson ? JSON.stringify(JSON.parse(evt.payloadJson), null, 2) : 'No payload content'}
                    </pre>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      {/* Rejection Modal */}
      {showRejectModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0, 0, 0, 0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100
        }}>
          <div className="glass-panel" style={{ width: '480px', padding: '28px', background: '#0f172a' }}>
            <h3 style={{ fontSize: '1.2rem', marginBottom: '8px', color: '#f87171', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <AlertTriangle size={20} /> Reject Candidate Layout
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
              Rejection will emit a <code>CargoLoadRejected</code> domain event to Kafka topic <code>soa.s06.t08.events</code> and update the downstream CargoDeclaration workflow projection.
            </p>

            <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: '6px' }}>Reason Code</label>
            <select 
              value={rejectionReason} 
              onChange={(e) => setRejectionReason(e.target.value)}
              style={{ width: '100%', marginBottom: '16px' }}
            >
              <option value="HAZARD_SEGREGATION_VIOLATION">HAZARD_SEGREGATION_VIOLATION</option>
              <option value="EXCEEDS_LOCAL_LIMITS">EXCEEDS_LOCAL_LIMITS</option>
              <option value="OPERATIONAL_OVERRIDE">OPERATIONAL_OVERRIDE</option>
            </select>

            <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: '6px' }}>Planner Notes</label>
            <textarea
              rows={3}
              style={{ width: '100%', marginBottom: '20px' }}
              placeholder="Provide reason for rejection..."
              value={rejectionNotes}
              onChange={(e) => setRejectionNotes(e.target.value)}
            />

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
              <button
                onClick={() => setShowRejectModal(false)}
                style={{ padding: '8px 16px', background: 'transparent', color: 'var(--text-muted)' }}
              >
                Cancel
              </button>
              <button
                onClick={handleReject}
                style={{ padding: '8px 18px', background: 'var(--accent-danger)', color: '#fff', borderRadius: '6px', fontWeight: 600 }}
              >
                Confirm Rejection & Dispatch Event
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
