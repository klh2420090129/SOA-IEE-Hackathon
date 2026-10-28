import React, { useState, useEffect, useCallback } from 'react';
import { 
  X, 
  Search, 
  ChevronLeft, 
  ChevronRight, 
  RotateCw, 
  FileText, 
  Calendar, 
  MapPin, 
  Layers, 
  AlertCircle,
  CheckCircle2,
  Clock,
  Filter
} from 'lucide-react';
import { CargoDeclaration, PaginatedResponse } from '../../types/api';
import { declarationApi } from '../../api/declarationApi';

interface DeclarationDiscoveryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectDeclaration: (declaration: CargoDeclaration) => void;
  title?: string;
  roleDescription?: string;
  isPlanner?: boolean;
}

export const DeclarationDiscoveryModal: React.FC<DeclarationDiscoveryModalProps> = ({
  isOpen,
  onClose,
  onSelectDeclaration,
  title = 'Browse Consignment Declarations',
  roleDescription = 'Select a consignment declaration to load into your operational workspace.',
  isPlanner = false
}) => {
  const [declarations, setDeclarations] = useState<CargoDeclaration[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [shipperFilter, setShipperFilter] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchDeclarations = useCallback(async (targetPage: number = 0) => {
    setLoading(true);
    setError(null);
    try {
      const response: PaginatedResponse<CargoDeclaration> = await declarationApi.listDeclarations({
        page: targetPage,
        size: 10,
        status: statusFilter.trim() || undefined,
        shipperId: (isPlanner && shipperFilter.trim()) ? shipperFilter.trim() : undefined
      });
      setDeclarations(response.content || []);
      setPage(response.page || 0);
      setTotalPages(response.totalPages || 0);
      setTotalElements(response.totalElements || 0);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve declaration listings');
      setDeclarations([]);
    } finally {
      setLoading(false);
    }
  }, [statusFilter, shipperFilter, isPlanner]);

  useEffect(() => {
    if (isOpen) {
      fetchDeclarations(0);
    }
  }, [isOpen, statusFilter, shipperFilter, isPlanner]);

  if (!isOpen) return null;

  const handleStatusChange = (newStatus: string) => {
    setStatusFilter(newStatus);
    setPage(0);
  };

  const getStatusBadge = (status?: string) => {
    switch (status) {
      case 'APPROVED':
        return <span className="badge badge-approved" style={{ fontSize: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '4px' }}><CheckCircle2 size={12} /> APPROVED</span>;
      case 'REJECTED':
        return <span className="badge badge-rejected" style={{ fontSize: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '4px' }}><AlertCircle size={12} /> REJECTED</span>;
      case 'OPTIMIZED':
        return <span className="badge badge-candidate-ready" style={{ fontSize: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '4px' }}><Clock size={12} /> OPTIMIZED</span>;
      case 'UNDER_ASSESSMENT':
        return <span className="badge badge-optimizing" style={{ fontSize: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '4px' }}><Clock size={12} /> UNDER ASSESSMENT</span>;
      case 'SUBMITTED':
      default:
        return <span className="badge badge-submitted" style={{ fontSize: '0.75rem', display: 'inline-flex', alignItems: 'center', gap: '4px' }}><Clock size={12} /> SUBMITTED</span>;
    }
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0,0,0,0.75)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 110,
      padding: '20px'
    }}>
      <div 
        className="glass-panel" 
        style={{
          width: '920px',
          maxWidth: '96vw',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          background: '#0f172a',
          border: '1px solid var(--border-color)',
          borderRadius: '12px',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.7)',
          overflow: 'hidden'
        }}
        data-testid="declaration-discovery-modal"
      >
        {/* Modal Header */}
        <div style={{
          padding: '18px 24px',
          borderBottom: '1px solid var(--border-color)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'rgba(255, 255, 255, 0.02)'
        }}>
          <div>
            <h3 style={{ margin: '0 0 4px 0', fontSize: '1.2rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '8px' }}>
              <FileText size={20} style={{ color: 'var(--accent-primary)' }} />
              {title}
            </h3>
            <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--text-muted)' }}>
              {roleDescription} &bull; Total records: <strong>{totalElements}</strong>
            </p>
          </div>
          <button
            onClick={onClose}
            data-testid="close-discovery-modal-btn"
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              padding: '6px',
              borderRadius: '6px'
            }}
          >
            <X size={20} />
          </button>
        </div>

        {/* Filter Controls Bar */}
        <div style={{
          padding: '14px 24px',
          borderBottom: '1px solid var(--border-color)',
          display: 'flex',
          flexWrap: 'wrap',
          gap: '12px',
          alignItems: 'center',
          background: 'rgba(0, 0, 0, 0.2)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Filter size={15} style={{ color: 'var(--text-muted)' }} />
            <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Status:</span>
            <select
              value={statusFilter}
              onChange={(e) => handleStatusChange(e.target.value)}
              data-testid="discovery-status-filter"
              style={{
                fontSize: '0.82rem',
                padding: '6px 10px',
                borderRadius: '6px',
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-color)',
                color: 'var(--text-main)'
              }}
            >
              <option value="">All Statuses</option>
              <option value="SUBMITTED">SUBMITTED</option>
              <option value="UNDER_ASSESSMENT">UNDER_ASSESSMENT</option>
              <option value="OPTIMIZED">OPTIMIZED</option>
              <option value="APPROVED">APPROVED</option>
              <option value="REJECTED">REJECTED</option>
            </select>
          </div>

          {isPlanner && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>Shipper ID:</span>
              <input
                type="text"
                placeholder="Filter by shipper..."
                value={shipperFilter}
                onChange={(e) => setShipperFilter(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') fetchDeclarations(0); }}
                data-testid="discovery-shipper-filter"
                style={{
                  fontSize: '0.82rem',
                  padding: '6px 10px',
                  borderRadius: '6px',
                  width: '160px'
                }}
              />
            </div>
          )}

          <button
            onClick={() => fetchDeclarations(0)}
            disabled={loading}
            data-testid="refresh-discovery-btn"
            style={{
              padding: '6px 12px',
              borderRadius: '6px',
              background: 'var(--bg-secondary)',
              border: '1px solid var(--border-color)',
              color: 'var(--text-main)',
              fontSize: '0.82rem',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              cursor: 'pointer'
            }}
          >
            <RotateCw size={13} className={loading ? 'spin' : ''} />
            Refresh
          </button>
        </div>

        {/* Content Area */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '16px 24px' }}>
          {error && (
            <div 
              data-testid="discovery-error-alert"
              style={{
                background: 'rgba(239, 68, 68, 0.15)',
                border: '1px solid var(--accent-danger)',
                borderRadius: '8px',
                padding: '12px 16px',
                color: '#f87171',
                marginBottom: '16px',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                fontSize: '0.85rem'
              }}
            >
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          {loading ? (
            <div 
              data-testid="discovery-loading-indicator"
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '60px 0',
                color: 'var(--text-muted)'
              }}
            >
              <RotateCw size={28} className="spin" style={{ marginBottom: '12px', color: 'var(--accent-primary)' }} />
              <span style={{ fontSize: '0.9rem' }}>Loading declarations...</span>
            </div>
          ) : declarations.length === 0 ? (
            <div 
              data-testid="discovery-empty-state"
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '60px 0',
                color: 'var(--text-muted)',
                textAlign: 'center'
              }}
            >
              <Search size={36} style={{ marginBottom: '12px', opacity: 0.4 }} />
              <div style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text-main)', marginBottom: '4px' }}>
                No Declarations Found
              </div>
              <div style={{ fontSize: '0.85rem', maxWidth: '400px' }}>
                {statusFilter ? `No consignments match status filter "${statusFilter}".` : 'No consignment declarations have been registered yet.'}
              </div>
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table 
                data-testid="discovery-declarations-table"
                style={{
                  width: '100%',
                  fontSize: '0.85rem',
                  borderCollapse: 'collapse',
                  textAlign: 'left'
                }}
              >
                <thead>
                  <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)' }}>
                    <th style={{ padding: '10px 12px' }}>Reference / ID</th>
                    <th style={{ padding: '10px 12px' }}>Status</th>
                    <th style={{ padding: '10px 12px' }}>Shipper</th>
                    <th style={{ padding: '10px 12px' }}>Route</th>
                    <th style={{ padding: '10px 12px' }}>Cargo Items</th>
                    <th style={{ padding: '10px 12px' }}>Created</th>
                    <th style={{ padding: '10px 12px', textAlign: 'right' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {declarations.map((decl) => (
                    <tr 
                      key={decl.id}
                      data-testid={`declaration-row-${decl.id}`}
                      style={{
                        borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
                        transition: 'background 0.15s ease'
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255, 255, 255, 0.04)')}
                      onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                    >
                      <td style={{ padding: '10px 12px' }}>
                        <div style={{ fontWeight: 600, color: 'var(--text-main)' }}>{decl.referenceNumber}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}><code>{decl.id.slice(0, 13)}...</code></div>
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        {getStatusBadge((decl as any).status || 'SUBMITTED')}
                      </td>
                      <td style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: '0.82rem' }}>
                        {decl.shipperId}
                      </td>
                      <td style={{ padding: '10px 12px', fontSize: '0.82rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <MapPin size={12} style={{ color: 'var(--text-muted)' }} />
                          <span>{decl.origin}</span>
                          <span style={{ color: 'var(--text-muted)' }}>&rarr;</span>
                          <span>{decl.destination}</span>
                        </div>
                      </td>
                      <td style={{ padding: '10px 12px', fontSize: '0.82rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <Layers size={13} style={{ color: 'var(--text-muted)' }} />
                          <span>{decl.items?.length || 0} packages</span>
                        </div>
                      </td>
                      <td style={{ padding: '10px 12px', color: 'var(--text-muted)', fontSize: '0.78rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                          <Calendar size={12} />
                          <span>{decl.createdAt ? new Date(decl.createdAt).toLocaleDateString() : 'N/A'}</span>
                        </div>
                      </td>
                      <td style={{ padding: '10px 12px', textAlign: 'right' }}>
                        <button
                          onClick={() => {
                            onSelectDeclaration(decl);
                            onClose();
                          }}
                          data-testid={`select-declaration-${decl.id}`}
                          style={{
                            padding: '6px 12px',
                            background: 'var(--accent-primary)',
                            color: '#fff',
                            border: 'none',
                            borderRadius: '6px',
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            cursor: 'pointer'
                          }}
                        >
                          Load
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Pagination Footer */}
        <div style={{
          padding: '14px 24px',
          borderTop: '1px solid var(--border-color)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'rgba(255, 255, 255, 0.02)',
          fontSize: '0.85rem'
        }}>
          <div style={{ color: 'var(--text-muted)' }}>
            Showing page <strong>{totalPages > 0 ? page + 1 : 0}</strong> of <strong>{totalPages}</strong> ({totalElements} total items)
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              onClick={() => fetchDeclarations(page - 1)}
              disabled={page <= 0 || loading}
              data-testid="discovery-prev-page-btn"
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-color)',
                color: page <= 0 ? 'var(--text-muted)' : 'var(--text-main)',
                cursor: page <= 0 ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '0.82rem'
              }}
            >
              <ChevronLeft size={14} /> Previous
            </button>
            <button
              onClick={() => fetchDeclarations(page + 1)}
              disabled={page >= totalPages - 1 || loading}
              data-testid="discovery-next-page-btn"
              style={{
                padding: '6px 12px',
                borderRadius: '6px',
                background: 'var(--bg-secondary)',
                border: '1px solid var(--border-color)',
                color: page >= totalPages - 1 ? 'var(--text-muted)' : 'var(--text-main)',
                cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                fontSize: '0.82rem'
              }}
            >
              Next <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
