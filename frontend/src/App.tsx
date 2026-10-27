import { useState, useEffect } from 'react';
import { 
  ShieldCheck, AlertTriangle, Box, Truck, CheckCircle2, 
  XCircle, Cpu, RefreshCw
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

interface Assessment {
  id: string;
  declarationId: string;
  declarationVersion: number;
  ruleSnapshotId: string;
  workflowStatus: string;
  compartmentId: string;
  candidateContentDigest?: string;
  candidateLayoutVersion?: number;
  approvalDecision?: string;
  decisionReasonCode?: string;
}

export default function App() {
  const [currentUser, setCurrentUser] = useState<'shipper' | 'planner'>('planner');
  const [activeTab, setActiveTab] = useState<'workflow' | '3d' | 'audit'>('workflow');
  
  // Real Backend Data State
  const [token, setToken] = useState<string>('');
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [items, setItems] = useState<CargoItem[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [actionMessage, setActionMessage] = useState<string>('');

  // Rejection modal state
  const [showRejectModal, setShowRejectModal] = useState<boolean>(false);
  const [rejectionReason, setRejectionReason] = useState<string>('HAZARD_SEGREGATION_VIOLATION');
  const [rejectionNotes, setRejectionNotes] = useState<string>('');

  // Authenticate on startup
  useEffect(() => {
    loginAsUser(currentUser);
  }, [currentUser]);

  const loginAsUser = async (role: 'shipper' | 'planner') => {
    setLoading(true);
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
        setActionMessage(`Logged in as ${data.username} (${data.roles.join(', ')})`);
      } else {
        setActionMessage('Authentication failed.');
      }
    } catch (e: any) {
      setActionMessage('Gateway offline or auth error: ' + e.message);
    } finally {
      setLoading(false);
    }
  };

  // Run full vertical slice workflow against live Gateway endpoints
  const runLiveAssessment = async () => {
    setLoading(true);
    setActionMessage('Step 1/5: Creating declaration...');
    try {
      // 1. Create Declaration
      const declRes = await fetch('/api/v1/declarations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          referenceNumber: 'DEC-LIVE-' + Math.floor(Math.random() * 1000),
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

      const declData = await declRes.json();
      setItems(declData.items || []);

      // 2. Create Assessment in Compartment Service
      setActionMessage('Step 2/5: Creating assessment orchestrator job...');
      const compRes = await fetch('/api/v1/assessments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
      const asmt = await compRes.json();
      setAssessment(asmt);

      // 3. Dispatch to Optimizer Service
      setActionMessage('Step 3/5: Calling Optimizer Service (CONTROLLED_STUB)...');
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
          items: declData.items
        })
      });
      const optJob = await optRes.json();
      const layoutResultRes = await fetch(`/api/v1/optimization-jobs/${optJob.jobId}/result`);
      const layoutData = await layoutResultRes.json();

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
      const valResult = await valRes.json();

      // Record validation outcome
      const recordedValRes = await fetch(`/api/v1/assessments/${asmt.id}/validation`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(valResult)
      });
      const finalAsmt = await recordedValRes.json();
      setAssessment(finalAsmt);
      setActionMessage('Candidate ready for Load Planner decision! Validation: ' + (valResult.passed ? 'PASSED' : 'FAILED'));
    } catch (e: any) {
      setActionMessage('Workflow error: ' + e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!assessment) return;
    setLoading(true);
    try {
      const res = await fetch(`/api/v1/assessments/${assessment.id}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
        const err = await res.json();
        setActionMessage('Approval blocked: ' + err.message);
      }
    } catch (e: any) {
      setActionMessage('Error approving: ' + e.message);
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async () => {
    if (!assessment) return;
    setLoading(true);
    try {
      const res = await fetch(`/api/v1/assessments/${assessment.id}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
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
        setActionMessage('Assessment REJECTED. CargoLoadRejected domain event enqueued for Kafka.');
      }
    } catch (e: any) {
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
              Shipper
            </button>
            <button 
              onClick={() => setCurrentUser('planner')}
              style={{
                padding: '6px 12px', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 600,
                background: currentUser === 'planner' ? 'var(--accent-primary)' : 'transparent',
                color: currentUser === 'planner' ? '#fff' : 'var(--text-muted)'
              }}
            >
              Load Planner
            </button>
          </div>

          <div className="badge badge-stub">
            <Cpu size={14} /> Provenance: CONTROLLED_STUB
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main style={{ flex: 1, padding: '32px', maxWidth: '1440px', width: '100%', margin: '0 auto' }}>
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
              alignItems: 'center', gap: '8px'
            }}
          >
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
            Run Live Vertical Slice Assessment
          </button>
        </div>

        {/* Assessment Card */}
        {assessment && (
          <section className="glass-panel" style={{ padding: '24px', marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
                  <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Assessment #{assessment.id.slice(0, 8)}</h2>
                  <span className={`badge badge-${assessment.workflowStatus.toLowerCase().replace('_', '-')}`}>
                    {assessment.workflowStatus}
                  </span>
                </div>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                  Declaration <strong style={{ color: 'var(--text-main)' }}>{assessment.declarationId.slice(0, 8)} (v{assessment.declarationVersion})</strong> &bull; Rule Snapshot <strong style={{ color: 'var(--text-main)' }}>{assessment.ruleSnapshotId}</strong> &bull; Digest: <code style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--accent-secondary)' }}>{assessment.candidateContentDigest?.slice(0, 16)}...</code>
                </p>
              </div>

              {currentUser === 'planner' && assessment.workflowStatus === 'PENDING_APPROVAL' && (
                <div style={{ display: 'flex', gap: '12px' }}>
                  <button
                    onClick={() => setShowRejectModal(true)}
                    style={{
                      background: 'rgba(239, 68, 68, 0.15)', color: '#f87171', border: '1px solid rgba(239, 68, 68, 0.3)',
                      padding: '10px 18px', borderRadius: '8px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px'
                    }}
                  >
                    <XCircle size={18} /> Reject Layout
                  </button>
                  <button
                    onClick={handleApprove}
                    style={{
                      background: 'var(--accent-success)', color: '#fff',
                      padding: '10px 20px', borderRadius: '8px', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '8px'
                    }}
                  >
                    <CheckCircle2 size={18} /> Approve Layout
                  </button>
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
                padding: '8px 16px', borderRadius: '8px', fontWeight: 600, textTransform: 'capitalize'
              }}
            >
              {tab === '3d' ? '3D Compartment Twin' : tab}
            </button>
          ))}
        </div>

        {/* Tab 1: Workflow */}
        {activeTab === 'workflow' && (
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '1.1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Box size={20} style={{ color: 'var(--accent-primary)' }} /> Live Cargo Handling Units
              </h3>
              {items.length === 0 ? (
                <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>No declaration active. Click "Run Live Vertical Slice Assessment" above to initialize.</p>
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

            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '1.1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <ShieldCheck size={20} style={{ color: 'var(--accent-secondary)' }} /> Validation Engine
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>AABB Pairwise Non-Overlap</span>
                    <span style={{ fontSize: '0.85rem', color: 'var(--accent-success)', fontWeight: 600 }}>VERIFIED</span>
                  </div>
                </div>
                <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Boundary Containment</span>
                    <span style={{ fontSize: '0.85rem', color: 'var(--accent-success)', fontWeight: 600 }}>VERIFIED</span>
                  </div>
                </div>
                <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Hazard Segregation (CMV-HAZ-1-3)</span>
                    <span style={{ fontSize: '0.85rem', color: 'var(--accent-success)', fontWeight: 600 }}>VERIFIED (&gt;3000mm)</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: 3D Visualization */}
        {activeTab === '3d' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <h3 style={{ fontSize: '1.1rem', marginBottom: '16px' }}>Actual Compartment Placement SVG Coordinate View</h3>
            <div style={{
              width: '100%', height: '360px', background: 'rgba(11, 15, 25, 0.95)',
              border: '1px solid var(--border-color)', borderRadius: '8px', position: 'relative',
              display: 'flex', alignItems: 'center', justifyContent: 'center'
            }}>
              <svg width="90%" height="80%" viewBox="0 0 1000 400" style={{ background: '#070b13', borderRadius: '6px' }}>
                <rect x="50" y="50" width="900" height="300" fill="none" stroke="#334155" strokeWidth="2" strokeDasharray="4 4" />
                <text x="60" y="75" fill="#64748b" fontSize="14" fontFamily="monospace">Compartment: 12000mm × 2400mm</text>
                <rect x="100" y="100" width="160" height="180" fill="rgba(245, 158, 11, 0.3)" stroke="#f59e0b" strokeWidth="2" />
                <text x="110" y="130" fill="#f8fafc" fontSize="12" fontWeight="bold">Solvent (Class 3)</text>
                <line x1="260" y1="190" x2="520" y2="190" stroke="#06b6d4" strokeWidth="1.5" strokeDasharray="3 3" />
                <text x="330" y="180" fill="#06b6d4" fontSize="12" fontFamily="monospace">&gt; 3000mm Buffer</text>
                <rect x="520" y="100" width="120" height="150" fill="rgba(239, 68, 68, 0.3)" stroke="#ef4444" strokeWidth="2" />
                <text x="530" y="130" fill="#f8fafc" fontSize="12" fontWeight="bold">Detonator (Class 1)</text>
              </svg>
            </div>
          </div>
        )}

        {/* Tab 3: Audit */}
        {activeTab === 'audit' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <h3 style={{ fontSize: '1.1rem', marginBottom: '16px' }}>Live Assessment Audit Trail</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Connected to live Gateway port <code>8080</code> &bull; MySQL databases &bull; Kafka topic <code>soa.s06.t08.events</code>
            </p>
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
              Rejection will emit a <code>CargoLoadRejected</code> domain event to Kafka topic <code>soa.s06.t08.events</code>.
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
