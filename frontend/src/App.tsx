import React, { useState } from 'react';
import { 
  ShieldCheck, AlertTriangle, Box, Truck, CheckCircle2, 
  XCircle, Play, RefreshCw, Lock, FileText, Cpu, ArrowRight 
} from 'lucide-react';

interface CargoItem {
  id: string;
  name: string;
  hazardClass: string;
  unNumber: string;
  dims: [number, number, number];
  weightKg: number;
}

export default function App() {
  const [currentUser, setCurrentUser] = useState<'shipper' | 'planner'>('planner');
  const [assessmentState, setAssessmentState] = useState<'DRAFT' | 'OPTIMIZING' | 'CANDIDATE_READY' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'>('PENDING_APPROVAL');
  
  const [items] = useState<CargoItem[]>([
    { id: 'item-001', name: 'Solvent Drums', hazardClass: '3', unNumber: 'UN1993', dims: [1200, 800, 1000], weightKg: 450 },
    { id: 'item-002', name: 'Detonator Relays', hazardClass: '1', unNumber: 'UN0004', dims: [800, 600, 600], weightKg: 180 }
  ]);

  const [activeTab, setActiveTab] = useState<'workflow' | '3d' | 'audit'>('workflow');
  const [rejectionNotes, setRejectionNotes] = useState('');
  const [showRejectModal, setShowRejectModal] = useState(false);

  const handleApprove = () => {
    setAssessmentState('APPROVED');
  };

  const handleReject = () => {
    setAssessmentState('REJECTED');
    setShowRejectModal(false);
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

      {/* Main Content Area */}
      <main style={{ flex: 1, padding: '32px', maxWidth: '1440px', width: '100%', margin: '0 auto' }}>
        {/* Assessment Header Status Card */}
        <section className="glass-panel" style={{ padding: '24px', marginBottom: '24px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
                <h2 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Assessment #ASMT-2026-0881</h2>
                <span className={`badge badge-${assessmentState.toLowerCase().replace('_', '-')}`}>
                  {assessmentState}
                </span>
              </div>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                Bound to Declaration <strong style={{ color: 'var(--text-main)' }}>DEC-2026-001 (v1)</strong> &bull; Rule Snapshot <strong style={{ color: 'var(--text-main)' }}>snap-2026-q4-v1</strong> &bull; Digest: <code style={{ fontFamily: 'var(--font-mono)', fontSize: '0.8rem', color: 'var(--accent-secondary)' }}>e3b0c44298fc...</code>
              </p>
            </div>

            {/* Workflow Actions */}
            {currentUser === 'planner' && assessmentState === 'PENDING_APPROVAL' && (
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

        {/* Navigation Tabs */}
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

        {/* Tab 1: Workflow Lifecycle View */}
        {activeTab === 'workflow' && (
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '1.1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Box size={20} style={{ color: 'var(--accent-primary)' }} /> Cargo Handling Units
              </h3>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--border-color)', color: 'var(--text-muted)' }}>
                    <th style={{ padding: '10px 8px' }}>Item ID</th>
                    <th style={{ padding: '10px 8px' }}>Description</th>
                    <th style={{ padding: '10px 8px' }}>Hazard Class</th>
                    <th style={{ padding: '10px 8px' }}>UN Number</th>
                    <th style={{ padding: '10px 8px' }}>Dimensions (mm)</th>
                    <th style={{ padding: '10px 8px' }}>Weight</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(item => (
                    <tr key={item.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                      <td style={{ padding: '12px 8px', fontFamily: 'var(--font-mono)' }}>{item.id}</td>
                      <td style={{ padding: '12px 8px', fontWeight: 500 }}>{item.name}</td>
                      <td style={{ padding: '12px 8px' }}>
                        <span style={{ 
                          background: item.hazardClass === '1' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                          color: item.hazardClass === '1' ? '#f87171' : '#fbbf24',
                          padding: '2px 8px', borderRadius: '4px', fontWeight: 600, fontSize: '0.8rem'
                        }}>
                          Class {item.hazardClass}
                        </span>
                      </td>
                      <td style={{ padding: '12px 8px', fontFamily: 'var(--font-mono)' }}>{item.unNumber}</td>
                      <td style={{ padding: '12px 8px' }}>{item.dims.join(' × ')}</td>
                      <td style={{ padding: '12px 8px' }}>{item.weightKg} kg</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Validation & Verification Panel */}
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '1.1rem', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <ShieldCheck size={20} style={{ color: 'var(--accent-secondary)' }} /> Independent Validation
              </h3>
              
              <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Geometric Overlap (AABB)</span>
                    <span style={{ fontSize: '0.85rem', color: 'var(--accent-success)', fontWeight: 600 }}>PASSED (0 pairs)</span>
                  </div>
                </div>

                <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Boundary & Bounds Containment</span>
                    <span style={{ fontSize: '0.85rem', color: 'var(--accent-success)', fontWeight: 600 }}>PASSED</span>
                  </div>
                </div>

                <div style={{ background: 'rgba(15, 23, 42, 0.5)', padding: '12px', borderRadius: '8px', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                    <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Hazard Segregation (CMV-HAZ-1-3)</span>
                    <span style={{ fontSize: '0.85rem', color: 'var(--accent-success)', fontWeight: 600 }}>PASSED (&gt;3000mm)</span>
                  </div>
                </div>
              </div>

              <div style={{ marginTop: '20px', padding: '12px', background: 'rgba(99, 102, 241, 0.1)', borderRadius: '8px', border: '1px solid var(--border-active)' }}>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  Validation runs independently on port <code>8086</code>. Candidate layout must pass all rules before becoming eligible for load planner approval.
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: 3D Visualization Canvas (Interactive SVG representation) */}
        {activeTab === '3d' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h3 style={{ fontSize: '1.1rem' }}>Compartment 3D Wireframe Placement View</h3>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Coordinates rendered to scale in millimeters</span>
            </div>

            <div style={{
              width: '100%', height: '360px', background: 'rgba(11, 15, 25, 0.95)',
              border: '1px solid var(--border-color)', borderRadius: '8px', position: 'relative',
              display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden'
            }}>
              <svg width="90%" height="80%" viewBox="0 0 1000 400" style={{ background: '#070b13', borderRadius: '6px' }}>
                {/* Compartment Border */}
                <rect x="50" y="50" width="900" height="300" fill="none" stroke="#334155" strokeWidth="2" strokeDasharray="4 4" />
                <text x="60" y="75" fill="#64748b" fontSize="14" fontFamily="monospace">Trailer Boundary: 12000mm × 2400mm</text>

                {/* Item 1 Placement */}
                <rect x="100" y="100" width="160" height="180" fill="rgba(245, 158, 11, 0.3)" stroke="#f59e0b" strokeWidth="2" />
                <text x="110" y="130" fill="#f8fafc" fontSize="12" fontWeight="bold">item-001 (Class 3)</text>
                <text x="110" y="150" fill="#cbd5e1" fontSize="10">X: 0mm &bull; Y: 0mm</text>

                {/* Segregation Distance Marker */}
                <line x1="260" y1="190" x2="520" y2="190" stroke="#06b6d4" strokeWidth="1.5" strokeDasharray="3 3" />
                <text x="330" y="180" fill="#06b6d4" fontSize="12" fontFamily="monospace">&gt; 3000mm Buffer</text>

                {/* Item 2 Placement */}
                <rect x="520" y="100" width="120" height="150" fill="rgba(239, 68, 68, 0.3)" stroke="#ef4444" strokeWidth="2" />
                <text x="530" y="130" fill="#f8fafc" fontSize="12" fontWeight="bold">item-002 (Class 1)</text>
                <text x="530" y="150" fill="#cbd5e1" fontSize="10">X: 3400mm &bull; Y: 0mm</text>
              </svg>
            </div>
          </div>
        )}

        {/* Tab 3: Audit & Event Trail */}
        {activeTab === 'audit' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <h3 style={{ fontSize: '1.1rem', marginBottom: '16px' }}>Domain Event & Assessment Audit Log</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ padding: '12px', background: 'rgba(15, 23, 42, 0.4)', borderRadius: '8px', borderLeft: '4px solid var(--accent-primary)' }}>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>2026-10-27 13:20:00 UTC</span>
                <p style={{ fontSize: '0.9rem', fontWeight: 600 }}>Assessment Initialized in DRAFT</p>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Created by Load Planner planner@soa8.org for Declaration DEC-2026-001</p>
              </div>

              <div style={{ padding: '12px', background: 'rgba(15, 23, 42, 0.4)', borderRadius: '8px', borderLeft: '4px solid var(--accent-secondary)' }}>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>2026-10-27 13:20:05 UTC</span>
                <p style={{ fontSize: '0.9rem', fontWeight: 600 }}>Candidate Generated (CONTROLLED_STUB)</p>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Candidate layout digest generated: 64-char SHA-256</p>
              </div>

              <div style={{ padding: '12px', background: 'rgba(15, 23, 42, 0.4)', borderRadius: '8px', borderLeft: '4px solid var(--accent-success)' }}>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>2026-10-27 13:20:10 UTC</span>
                <p style={{ fontSize: '0.9rem', fontWeight: 600 }}>Independent Validation Passed</p>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Validated against CMV-HAZ-1-3 rule snapshot. State moved to PENDING_APPROVAL.</p>
              </div>
            </div>
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
              Rejection will emit a <code>CargoLoadRejected</code> domain event to Kafka topic <code>soa.s06.t08.events</code> via transactional outbox.
            </p>

            <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: '6px' }}>Reason Code</label>
            <select style={{ width: '100%', marginBottom: '16px' }}>
              <option value="HAZARD_SEGREGATION_VIOLATION">HAZARD_SEGREGATION_VIOLATION</option>
              <option value="EXCEEDS_LOCAL_LIMITS">EXCEEDS_LOCAL_LIMITS</option>
              <option value="OPERATIONAL_OVERRIDE">OPERATIONAL_OVERRIDE</option>
            </select>

            <label style={{ display: 'block', fontSize: '0.85rem', marginBottom: '6px' }}>Planner Notes</label>
            <textarea
              rows={3}
              style={{ width: '100%', marginBottom: '20px' }}
              placeholder="Provide justification for rejection..."
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
