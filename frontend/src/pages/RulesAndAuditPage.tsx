import React, { useEffect, useState } from 'react';
import { Sliders, ShieldCheck, Database, RefreshCw, AlertCircle } from 'lucide-react';
import { rulesApi } from '../api/rulesApi';
import { assessmentApi } from '../api/assessmentApi';
import { CompatibilityRuleDto, OutboxEventDto, RuleSnapshotDto } from '../types/api';

export const RulesAndAuditPage: React.FC = () => {
  const [snapshot, setSnapshot] = useState<RuleSnapshotDto | null>(null);
  const [rules, setRules] = useState<CompatibilityRuleDto[]>([]);
  const [events, setEvents] = useState<OutboxEventDto[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [snapRes, rulesRes] = await Promise.all([
        rulesApi.getRuleSnapshot('snap-2026-q4-v1').catch(() => null),
        rulesApi.getRules().catch(() => [])
      ]);
      setSnapshot(snapRes);
      setRules(rulesRes);

      // Attempt to load outbox events from default assessment if available
      try {
        const evts = await assessmentApi.getAssessmentEvents('default');
        setEvents(evts);
      } catch {
        setEvents([]);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load safety rule metadata');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 700, margin: '0 0 4px 0' }}>
            Safety Rules & Cryptographic Governance
          </h2>
          <p style={{ color: 'var(--text-muted)', margin: 0, fontSize: '0.85rem' }}>
            Immutable segregation matrices, pinned CMV/IMDG snapshot digests, and transactional domain event logs.
          </p>
        </div>

        <button
          onClick={loadData}
          disabled={loading}
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
          <RefreshCw size={14} className={loading ? 'spin' : ''} />
          Refresh
        </button>
      </div>

      {error && (
        <div style={{
          background: 'rgba(239, 68, 68, 0.15)',
          border: '1px solid var(--accent-danger)',
          borderRadius: '8px',
          padding: '12px 16px',
          color: '#f87171',
          display: 'flex',
          alignItems: 'center',
          gap: '10px'
        }}>
          <AlertCircle size={18} />
          <span>{error}</span>
        </div>
      )}

      {/* Snapshot Identity Card */}
      {snapshot && (
        <div className="glass-panel" style={{ padding: '20px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <ShieldCheck size={22} style={{ color: 'var(--accent-secondary)' }} />
              <h3 style={{ margin: 0, fontSize: '1.15rem', fontWeight: 700 }}>
                Pinned Snapshot: {snapshot.snapshotId}
              </h3>
              <span className="badge badge-approved">{snapshot.status}</span>
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Jurisdiction: <strong>{snapshot.jurisdiction}</strong> &bull; Effective: {snapshot.effectiveDate}
            </div>
          </div>

          <div style={{
            background: 'rgba(15, 23, 42, 0.6)',
            padding: '12px 16px',
            borderRadius: '6px',
            border: '1px solid var(--border-color)',
            fontSize: '0.85rem',
            fontFamily: 'monospace',
            color: '#93c5fd'
          }}>
            <span style={{ color: 'var(--text-muted)', marginRight: '8px' }}>Cryptographic SHA-256 Digest:</span>
            {snapshot.snapshotDigest}
          </div>
        </div>
      )}

      {/* Rules Table */}
      <div className="glass-panel" style={{ padding: '24px' }}>
        <h3 style={{ fontSize: '1.05rem', margin: '0 0 16px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Sliders size={18} style={{ color: 'var(--accent-primary)' }} />
          Authoritative Segregation Matrix ({rules.length || snapshot?.rules.length || 0} Rules)
        </h3>

        <table style={{ width: '100%', fontSize: '0.85rem', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead>
            <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)' }}>
              <th style={{ padding: '10px 8px' }}>Rule Code</th>
              <th style={{ padding: '10px 8px' }}>Primary Hazard</th>
              <th style={{ padding: '10px 8px' }}>Secondary Hazard</th>
              <th style={{ padding: '10px 8px' }}>Segregation Type</th>
              <th style={{ padding: '10px 8px' }}>Min Distance (mm)</th>
              <th style={{ padding: '10px 8px' }}>Rule Description</th>
            </tr>
          </thead>
          <tbody>
            {(rules.length > 0 ? rules : snapshot?.rules || []).map((r: CompatibilityRuleDto) => (
              <tr key={r.ruleId} style={{ borderBottom: '1px solid var(--border-color)' }}>
                <td style={{ padding: '10px 8px', fontWeight: 600, color: 'var(--accent-secondary)' }}>
                  {r.ruleCode}
                </td>
                <td style={{ padding: '10px 8px' }}>Class {r.primaryHazardClass}</td>
                <td style={{ padding: '10px 8px' }}>Class {r.secondaryHazardClass}</td>
                <td style={{ padding: '10px 8px' }}>
                  <span className={`badge badge-${r.segregationType === 'FORBIDDEN' ? 'validation-failed' : 'review-required'}`}>
                    {r.segregationType}
                  </span>
                </td>
                <td style={{ padding: '10px 8px', fontFamily: 'monospace' }}>
                  {r.minDistanceMm > 0 ? `${r.minDistanceMm} mm` : 'N/A'}
                </td>
                <td style={{ padding: '10px 8px', color: '#cbd5e1' }}>
                  {r.description}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Transactional Outbox Events */}
      <div className="glass-panel" style={{ padding: '24px' }}>
        <h3 style={{ fontSize: '1.05rem', margin: '0 0 14px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Database size={18} style={{ color: '#c084fc' }} />
          Transactional Outbox Event Relays (Kafka soa.s06.t08.events)
        </h3>

        {events.length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            No pending outbox events queued. Rejected candidate layouts enqueue <code>CargoLoadRejected</code> events here before publishing.
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {events.map((evt) => (
              <div key={evt.eventId} style={{
                background: 'rgba(15, 23, 42, 0.6)',
                border: '1px solid var(--border-color)',
                borderRadius: '6px',
                padding: '12px'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                  <span style={{ fontWeight: 600, color: '#c084fc' }}>{evt.eventType}</span>
                  <span className="badge badge-pending">{evt.status}</span>
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Aggregate ID: <code>{evt.aggregateId}</code> &bull; Topic: <code>{evt.topic}</code>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
