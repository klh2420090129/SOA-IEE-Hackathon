import React from 'react';
import { 
  Box, 
  CheckCircle2, 
  XCircle, 
  ShieldAlert,
  Layers
} from 'lucide-react';
import { 
  CandidateLayoutDto, 
  CompartmentDto, 
  ValidationResultDto 
} from '../../types/api';

interface CandidateViewer2DProps {
  compartment: CompartmentDto;
  layout: CandidateLayoutDto | null;
  validation: ValidationResultDto | null;
  isApproved?: boolean;
  isRejected?: boolean;
}

export const CandidateViewer2D: React.FC<CandidateViewer2DProps> = ({
  compartment,
  layout,
  validation,
  isApproved = false,
  isRejected = false
}) => {
  if (!layout) {
    return (
      <div style={{
        background: 'rgba(15, 23, 42, 0.4)',
        border: '1px dashed var(--border-color)',
        borderRadius: '8px',
        padding: '48px 24px',
        textAlign: 'center',
        color: 'var(--text-muted)'
      }}>
        <Box size={40} style={{ margin: '0 auto 12px', opacity: 0.4 }} />
        <p style={{ margin: 0, fontSize: '0.95rem' }}>
          No candidate layout available. Run optimization from an assessment to view 2D cargo placement.
        </p>
      </div>
    );
  }

  const compL = compartment.lengthMm || 12000;
  const compW = compartment.widthMm || 2400;

  // SVG Scaled coordinates:
  // Trailer envelope viewport: width 900px, height 220px (margin x=50, y=40)
  const svgWidth = 1000;
  const svgHeight = 320;
  const envelopeX = 50;
  const envelopeY = 40;
  const envelopeW = 900;
  const envelopeH = 220;

  const scaleX = envelopeW / compL;
  const scaleY = envelopeH / compW;

  const segregationFindings = validation?.findings?.filter(
    f => f.code === 'HAZARD_SEGREGATION_VIOLATION' || f.code === 'HAZARD_COLOAD_FORBIDDEN'
  ) || [];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* 2D Header Status Ribbon */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        background: 'rgba(15, 23, 42, 0.7)',
        padding: '10px 16px',
        borderRadius: '8px',
        border: '1px solid var(--border-color)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Candidate Solver Status:</span>
            <span className={`badge badge-${layout.solverStatus === 'OPTIMAL' || layout.solverStatus === 'FEASIBLE' ? 'approved' : 'validation-failed'}`}>
              {layout.solverStatus} ({layout.provenance || 'OPTIMIZER'})
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Zero-Trust Validation:</span>
            {validation ? (
              <span className={`badge badge-${validation.passed ? 'approved' : 'validation-failed'}`}>
                {validation.passed ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                {validation.passed ? 'PASSED (0 Violations)' : `FAILED (${validation.findings.length} Violations)`}
              </span>
            ) : (
              <span className="badge badge-draft">UNVALIDATED</span>
            )}
          </div>
        </div>

        <div>
          {isApproved ? (
            <span className="badge badge-approved" style={{ fontWeight: 700 }}>
              DECISION: AUTHORITATIVELY APPROVED
            </span>
          ) : isRejected ? (
            <span className="badge badge-validation-failed" style={{ fontWeight: 700 }}>
              DECISION: REJECTED (CARGO HOLD)
            </span>
          ) : (
            <span className="badge badge-review-required">
              PENDING LOAD PLANNER REVIEW
            </span>
          )}
        </div>
      </div>

      {/* SVG Compartment Twin */}
      <div style={{
        width: '100%',
        height: '360px',
        background: 'rgba(7, 11, 19, 0.95)',
        border: '1px solid var(--border-color)',
        borderRadius: '8px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '12px',
        position: 'relative'
      }}>
        <svg width="100%" height="100%" viewBox={`0 0 ${svgWidth} ${svgHeight}`} style={{ borderRadius: '6px' }}>
          {/* Compartment Boundary */}
          <rect
            x={envelopeX}
            y={envelopeY}
            width={envelopeW}
            height={envelopeH}
            fill="rgba(15, 23, 42, 0.6)"
            stroke="#3b82f6"
            strokeWidth="2"
            strokeDasharray="6 4"
            rx="4"
          />
          <text x={envelopeX + 12} y={envelopeY + 22} fill="#94a3b8" fontSize="12" fontFamily="monospace">
            Compartment: {compartment.compartmentId} &bull; {compL}mm (L) &times; {compW}mm (W) &bull; Max: {compartment.maxPayloadKg}kg
          </text>

          {/* Unit Placements */}
          {layout.placements.map((p, idx) => {
            const px = envelopeX + (p.xMm * scaleX);
            const py = envelopeY + (p.yMm * scaleY);
            const pw = Math.max(p.lengthMm * scaleX, 30);
            const ph = Math.max(p.widthMm * scaleY, 30);

            const isClass1 = p.hazardClass === '1';
            const isClass8 = p.hazardClass === '8';
            const isClass3 = p.hazardClass === '3';
            
            const fillColor = isClass1 
              ? 'rgba(239, 68, 68, 0.35)' 
              : isClass8 
              ? 'rgba(168, 85, 247, 0.35)' 
              : isClass3 
              ? 'rgba(245, 158, 11, 0.35)' 
              : 'rgba(59, 130, 246, 0.35)';

            const strokeColor = isClass1 
              ? '#ef4444' 
              : isClass8 
              ? '#c084fc' 
              : isClass3 
              ? '#f59e0b' 
              : '#60a5fa';

            return (
              <g key={p.placementId || idx}>
                <rect
                  x={px}
                  y={py}
                  width={pw}
                  height={ph}
                  fill={fillColor}
                  stroke={strokeColor}
                  strokeWidth="2"
                  rx="3"
                />
                <text x={px + 6} y={py + 16} fill="#f8fafc" fontSize="10" fontWeight="bold">
                  {p.cargoItemId}
                </text>
                <text x={px + 6} y={py + 28} fill={strokeColor} fontSize="9" fontFamily="monospace">
                  Cls {p.hazardClass} ({p.unNumber})
                </text>
                <text x={px + 6} y={py + 40} fill="#cbd5e1" fontSize="8" fontFamily="monospace">
                  x:{p.xMm} y:{p.yMm} z:{p.zMm}
                </text>
              </g>
            );
          })}

          {/* Dynamic Segregation Violation Visual Indicators */}
          {segregationFindings.map((v, vIdx) => {
            const involved = v.involvedItemIds || [];
            const p0 = layout.placements.find(p => involved.includes(p.cargoItemId));
            const p1 = layout.placements.filter(p => involved.includes(p.cargoItemId))[1];

            if (p0 && p1) {
              const x0 = envelopeX + (p0.xMm * scaleX) + ((p0.lengthMm * scaleX) / 2);
              const x1 = envelopeX + (p1.xMm * scaleX) + ((p1.lengthMm * scaleX) / 2);
              const yLine = envelopeY + envelopeH - 30 - (vIdx * 28);

              return (
                <g key={`violation-${vIdx}`}>
                  <line x1={x0} y1={yLine} x2={x1} y2={yLine} stroke="#ef4444" strokeWidth="2" strokeDasharray="4 4" />
                  <circle cx={x0} cy={yLine} r="4" fill="#ef4444" />
                  <circle cx={x1} cy={yLine} r="4" fill="#ef4444" />
                  <rect
                    x={Math.min(x0, x1)}
                    y={yLine - 22}
                    width={Math.max(Math.abs(x1 - x0), 160)}
                    height="18"
                    fill="rgba(15, 23, 42, 0.95)"
                    stroke="#ef4444"
                    rx="3"
                  />
                  <text x={(x0 + x1) / 2} y={yLine - 9} fill="#fca5a5" fontSize="9" fontWeight="bold" textAnchor="middle" fontFamily="monospace">
                    {v.code}: {v.message.slice(0, 45)}...
                  </text>
                </g>
              );
            }
            return null;
          })}

          {/* Legend / Footer Indicator */}
          <text x={envelopeX + 12} y={envelopeY + envelopeH + 26} fill="#64748b" fontSize="11" fontFamily="sans-serif">
            Candidate Content Digest (SHA-256): {layout.contentDigest.slice(0, 32)}...
          </text>
        </svg>
      </div>

      {/* Unassigned Units Notice (if solver could not place all items) */}
      {layout.unassignedItems && layout.unassignedItems.length > 0 && (
        <div style={{
          background: 'rgba(239, 68, 68, 0.1)',
          border: '1px solid rgba(239, 68, 68, 0.3)',
          borderRadius: '8px',
          padding: '12px 16px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#f87171', fontWeight: 600, fontSize: '0.9rem', marginBottom: '8px' }}>
            <ShieldAlert size={16} />
            <span>Unassigned Physical Units ({layout.unassignedItems.length})</span>
          </div>
          <table style={{ width: '100%', fontSize: '0.85rem', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid rgba(239, 68, 68, 0.2)' }}>
                <th style={{ padding: '6px 8px' }}>Item ID</th>
                <th style={{ padding: '6px 8px' }}>Unit #</th>
                <th style={{ padding: '6px 8px' }}>Reason Code</th>
                <th style={{ padding: '6px 8px' }}>Explanation</th>
              </tr>
            </thead>
            <tbody>
              {layout.unassignedItems.map((u, i) => (
                <tr key={i} style={{ borderBottom: '1px solid rgba(239, 68, 68, 0.1)' }}>
                  <td style={{ padding: '6px 8px', fontWeight: 600, color: '#fca5a5' }}>{u.cargoItemId}</td>
                  <td style={{ padding: '6px 8px', fontFamily: 'monospace' }}>Unit {u.instanceIndex + 1}</td>
                  <td style={{ padding: '6px 8px' }}>
                    <span className="badge badge-validation-failed">{u.reasonCode}</span>
                  </td>
                  <td style={{ padding: '6px 8px', color: '#cbd5e1' }}>{u.reasonDescription}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Accessible Tabular Fallback */}
      <div style={{
        background: 'rgba(15, 23, 42, 0.5)',
        border: '1px solid var(--border-color)',
        borderRadius: '8px',
        padding: '16px'
      }}>
        <h4 style={{ fontSize: '0.9rem', margin: '0 0 10px 0', display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Layers size={15} style={{ color: 'var(--accent-primary)' }} />
          Authoritative Placement Coordinates & Dimensions Table (Screen-Reader & 2D Audit Fallback)
        </h4>
        <table style={{ width: '100%', fontSize: '0.85rem', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead>
            <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)' }}>
              <th style={{ padding: '8px' }}>Placement ID</th>
              <th style={{ padding: '8px' }}>Cargo Item</th>
              <th style={{ padding: '8px' }}>Class (UN#)</th>
              <th style={{ padding: '8px' }}>Position (x, y, z mm)</th>
              <th style={{ padding: '8px' }}>Dimensions (L&times;W&times;H mm)</th>
              <th style={{ padding: '8px' }}>Weight</th>
              <th style={{ padding: '8px' }}>Stackable</th>
            </tr>
          </thead>
          <tbody>
            {layout.placements.map((p, idx) => (
              <tr key={p.placementId || idx} style={{ borderBottom: '1px solid var(--border-color)' }}>
                <td style={{ padding: '8px', fontFamily: 'monospace' }}>{p.placementId || `placement-${idx + 1}`}</td>
                <td style={{ padding: '8px', fontWeight: 600 }}>{p.cargoItemId}</td>
                <td style={{ padding: '8px' }}>Class {p.hazardClass} ({p.unNumber})</td>
                <td style={{ padding: '8px', fontFamily: 'monospace' }}>
                  ({p.xMm}, {p.yMm}, {p.zMm})
                </td>
                <td style={{ padding: '8px' }}>{p.lengthMm} &times; {p.widthMm} &times; {p.heightMm}</td>
                <td style={{ padding: '8px' }}>{p.weightKg} kg</td>
                <td style={{ padding: '8px' }}>{p.stackable ? 'Yes' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
