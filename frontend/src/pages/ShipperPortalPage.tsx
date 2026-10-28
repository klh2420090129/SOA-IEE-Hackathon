import React, { useState } from 'react';
import { 
  FileText, 
  Plus, 
  Trash2, 
  Search, 
  Send, 
  AlertCircle,
  Upload,
  Bookmark
} from 'lucide-react';
import { declarationApi } from '../api/declarationApi';
import { CargoDeclaration, CargoItemInput, DeclarationWorkflowProjection } from '../types/api';
import { useAuth } from '../context/AuthContext';
import { BulkCargoImportModal } from '../components/declaration/BulkCargoImportModal';
import { SaveTemplateModal } from '../components/declaration/SaveTemplateModal';
import { SavedCargoTemplatesModal } from '../components/declaration/SavedCargoTemplatesModal';

export const ShipperPortalPage: React.FC = () => {
  const { user } = useAuth();

  // Form State
  const [refNumber, setRefNumber] = useState(`DEC-${Date.now().toString().slice(-6)}`);
  const [origin, setOrigin] = useState('JNPT Port Mumbai');
  const [destination, setDestination] = useState('Tughlakabad Freight Terminal Delhi');
  const [items, setItems] = useState<CargoItemInput[]>([
    {
      cargoItemId: 'ITEM-SOLVENT-01',
      description: 'Industrial Flammable Solvents',
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
      description: 'Detonator Assembly Unit',
      hazardClass: '1',
      unNumber: 'UN0004',
      lengthMm: 800,
      widthMm: 800,
      heightMm: 800,
      weightKg: 250,
      stackable: true,
      maxStackWeightKg: 500,
      quantity: 1
    }
  ]);

  // Operational State
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [activeDeclaration, setActiveDeclaration] = useState<CargoDeclaration | null>(null);
  const [workflowProjection, setWorkflowProjection] = useState<DeclarationWorkflowProjection | null>(null);
  const [searchId, setSearchId] = useState('');
  const [searchLoading, setSearchLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [isSaveTemplateModalOpen, setIsSaveTemplateModalOpen] = useState(false);
  const [isSavedTemplatesModalOpen, setIsSavedTemplatesModalOpen] = useState(false);

  const handleImportItems = (importedItems: CargoItemInput[], replaceExisting: boolean) => {
    if (replaceExisting) {
      setItems(importedItems);
    } else {
      setItems((prev) => [...prev, ...importedItems]);
    }
  };

  const handleLoadTemplate = (templateItems: CargoItemInput[], replaceExisting: boolean) => {
    if (replaceExisting) {
      setItems(templateItems);
    } else {
      setItems((prev) => [...prev, ...templateItems]);
    }
  };

  const addItemRow = () => {
    setItems([
      ...items,
      {
        cargoItemId: `ITEM-${Date.now().toString().slice(-4)}`,
        description: 'Chemical Package Drum',
        hazardClass: '8',
        unNumber: 'UN1789',
        lengthMm: 1000,
        widthMm: 1000,
        heightMm: 1000,
        weightKg: 400,
        stackable: false,
        maxStackWeightKg: 0,
        quantity: 1
      }
    ]);
  };

  const removeItemRow = (idx: number) => {
    setItems(items.filter((_, i) => i !== idx));
  };

  const updateItemField = (idx: number, field: keyof CargoItemInput, val: any) => {
    const updated = [...items];
    updated[idx] = { ...updated[idx], [field]: val };
    setItems(updated);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      const created = await declarationApi.createDeclaration({
        referenceNumber: refNumber,
        shipperId: user?.userId || 'usr-shipper-001',
        origin,
        destination,
        items
      });
      setActiveDeclaration(created);
      
      // Fetch initial workflow projection
      const proj = await declarationApi.getWorkflowProjection(created.id);
      setWorkflowProjection(proj);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to submit cargo declaration');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLookup = async () => {
    if (!searchId.trim()) return;
    setSearchLoading(true);
    setErrorMessage(null);
    try {
      const found = await declarationApi.getDeclaration(searchId.trim());
      setActiveDeclaration(found);
      const proj = await declarationApi.getWorkflowProjection(found.id);
      setWorkflowProjection(proj);
    } catch (err: any) {
      setErrorMessage(err.message || `Declaration ${searchId} not found`);
    } finally {
      setSearchLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {/* Page Title & Lookup Ribbon */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 700, margin: '0 0 4px 0' }}>
            Shipper Cargo Declaration Portal
          </h2>
          <p style={{ color: 'var(--text-muted)', margin: 0, fontSize: '0.85rem' }}>
            Register hazardous goods consignments with physical package dimensions, IMDG hazard classifications, and stacking limits.
          </p>
        </div>

        {/* Existing Declaration Lookup */}
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder="Retrieve Declaration ID..."
            value={searchId}
            onChange={(e) => setSearchId(e.target.value)}
            style={{ width: '280px', fontSize: '0.85rem' }}
          />
          <button
            onClick={handleLookup}
            disabled={searchLoading}
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
            <Search size={14} />
            {searchLoading ? 'Looking up...' : 'Lookup'}
          </button>
        </div>
      </div>

      {errorMessage && (
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
          <span style={{ fontSize: '0.9rem' }}>{errorMessage}</span>
        </div>
      )}

      {/* Active Declaration Workflow Card */}
      {activeDeclaration && (
        <div className="glass-panel" style={{ padding: '20px', borderLeft: '4px solid #3b82f6' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontWeight: 700, fontSize: '1.1rem' }}>
                  Declaration: {activeDeclaration.referenceNumber}
                </span>
                <span className="badge badge-approved">
                  {workflowProjection?.status || 'SUBMITTED'}
                </span>
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                ID: <code>{activeDeclaration.id}</code> &bull; Shipper: {activeDeclaration.shipperId} &bull; Route: {activeDeclaration.origin} &rarr; {activeDeclaration.destination}
              </div>
            </div>

            <button
              onClick={() => {
                navigator.clipboard.writeText(activeDeclaration.id);
                alert(`Declaration ID copied: ${activeDeclaration.id}`);
              }}
              style={{
                fontSize: '0.8rem',
                padding: '6px 12px',
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid var(--border-color)',
                borderRadius: '4px',
                color: 'var(--text-main)'
              }}
            >
              Copy ID for Planner
            </button>
          </div>

          {workflowProjection?.status === 'REJECTED' && (
            <div style={{
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: '6px',
              padding: '10px 14px',
              marginTop: '10px',
              color: '#fca5a5',
              fontSize: '0.85rem'
            }}>
              <strong>Downstream Rejection Notice:</strong> This declaration was marked REJECTED by Load Planning. Reason: <code>{workflowProjection.lastRejectionReasonCode}</code>
            </div>
          )}
        </div>
      )}

      {/* Declaration Authoring Form */}
      <form onSubmit={handleSubmit} className="glass-panel" style={{ padding: '24px' }}>
        <h3 style={{ fontSize: '1.1rem', margin: '0 0 16px 0', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <FileText size={18} style={{ color: 'var(--accent-primary)' }} /> Consignment Header & Transport Route
        </h3>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '16px', marginBottom: '24px' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '6px' }}>
              Reference Number *
            </label>
            <input
              type="text"
              required
              value={refNumber}
              onChange={(e) => setRefNumber(e.target.value)}
              style={{ width: '100%' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '6px' }}>
              Origin Facility *
            </label>
            <input
              type="text"
              required
              value={origin}
              onChange={(e) => setOrigin(e.target.value)}
              style={{ width: '100%' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '6px' }}>
              Destination Terminal *
            </label>
            <input
              type="text"
              required
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
              style={{ width: '100%' }}
            />
          </div>
        </div>

        {/* Cargo Items Table */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px', flexWrap: 'wrap', gap: '8px' }}>
          <h4 style={{ margin: 0, fontSize: '0.95rem' }}>
            Consignment Cargo Items ({items.length})
          </h4>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            <button
              type="button"
              data-testid="open-saved-templates-btn"
              onClick={() => setIsSavedTemplatesModalOpen(true)}
              style={{
                padding: '6px 12px',
                background: 'rgba(168, 85, 247, 0.15)',
                color: '#c084fc',
                border: '1px solid rgba(168, 85, 247, 0.3)',
                borderRadius: '6px',
                fontSize: '0.8rem',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                cursor: 'pointer'
              }}
            >
              <Bookmark size={14} /> Saved Templates
            </button>
            <button
              type="button"
              data-testid="open-save-template-btn"
              onClick={() => setIsSaveTemplateModalOpen(true)}
              disabled={items.length === 0}
              style={{
                padding: '6px 12px',
                background: items.length === 0 ? 'rgba(255, 255, 255, 0.05)' : 'rgba(234, 179, 8, 0.15)',
                color: items.length === 0 ? 'var(--text-muted)' : '#facc15',
                border: items.length === 0 ? '1px solid var(--border-color)' : '1px solid rgba(234, 179, 8, 0.3)',
                borderRadius: '6px',
                fontSize: '0.8rem',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                cursor: items.length === 0 ? 'not-allowed' : 'pointer'
              }}
            >
              <Bookmark size={14} /> Save as Template
            </button>
            <button
              type="button"
              data-testid="open-bulk-import-btn"
              onClick={() => setIsImportModalOpen(true)}
              style={{
                padding: '6px 12px',
                background: 'rgba(16, 185, 129, 0.15)',
                color: '#34d399',
                border: '1px solid rgba(16, 185, 129, 0.3)',
                borderRadius: '6px',
                fontSize: '0.8rem',
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                cursor: 'pointer'
              }}
            >
              <Upload size={14} /> Bulk Import (CSV / JSON)
            </button>
            <button
              type="button"
              onClick={addItemRow}
              style={{
                padding: '6px 12px',
                background: 'rgba(59, 130, 246, 0.15)',
                color: '#60a5fa',
                border: '1px solid rgba(59, 130, 246, 0.3)',
                borderRadius: '6px',
                fontSize: '0.8rem',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              <Plus size={14} /> Add Cargo Item
            </button>
          </div>
        </div>

        <div style={{ overflowX: 'auto', marginBottom: '20px' }}>
          <table style={{ width: '100%', fontSize: '0.85rem', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)' }}>
                <th style={{ padding: '8px' }}>Item ID</th>
                <th style={{ padding: '8px' }}>Description</th>
                <th style={{ padding: '8px' }}>Hazard (UN#)</th>
                <th style={{ padding: '8px' }}>Dimensions (L&times;W&times;H mm)</th>
                <th style={{ padding: '8px' }}>Weight (kg)</th>
                <th style={{ padding: '8px' }}>Stackable</th>
                <th style={{ padding: '8px' }}>Qty</th>
                <th style={{ padding: '8px' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {items.map((it, idx) => (
                <tr key={idx} style={{ borderBottom: '1px solid var(--border-color)' }}>
                  <td style={{ padding: '8px' }}>
                    <input
                      type="text"
                      value={it.cargoItemId}
                      onChange={(e) => updateItemField(idx, 'cargoItemId', e.target.value)}
                      style={{ width: '110px', fontSize: '0.8rem' }}
                    />
                  </td>
                  <td style={{ padding: '8px' }}>
                    <input
                      type="text"
                      value={it.description}
                      onChange={(e) => updateItemField(idx, 'description', e.target.value)}
                      style={{ width: '160px', fontSize: '0.8rem' }}
                    />
                  </td>
                  <td style={{ padding: '8px' }}>
                    <div style={{ display: 'flex', gap: '4px' }}>
                      <input
                        type="text"
                        placeholder="Class"
                        value={it.hazardClass}
                        onChange={(e) => updateItemField(idx, 'hazardClass', e.target.value)}
                        style={{ width: '45px', fontSize: '0.8rem' }}
                      />
                      <input
                        type="text"
                        placeholder="UN"
                        value={it.unNumber}
                        onChange={(e) => updateItemField(idx, 'unNumber', e.target.value)}
                        style={{ width: '70px', fontSize: '0.8rem' }}
                      />
                    </div>
                  </td>
                  <td style={{ padding: '8px' }}>
                    <div style={{ display: 'flex', gap: '4px' }}>
                      <input
                        type="number"
                        value={it.lengthMm}
                        onChange={(e) => updateItemField(idx, 'lengthMm', parseInt(e.target.value) || 0)}
                        style={{ width: '60px', fontSize: '0.8rem' }}
                      />
                      <input
                        type="number"
                        value={it.widthMm}
                        onChange={(e) => updateItemField(idx, 'widthMm', parseInt(e.target.value) || 0)}
                        style={{ width: '60px', fontSize: '0.8rem' }}
                      />
                      <input
                        type="number"
                        value={it.heightMm}
                        onChange={(e) => updateItemField(idx, 'heightMm', parseInt(e.target.value) || 0)}
                        style={{ width: '60px', fontSize: '0.8rem' }}
                      />
                    </div>
                  </td>
                  <td style={{ padding: '8px' }}>
                    <input
                      type="number"
                      value={it.weightKg}
                      onChange={(e) => updateItemField(idx, 'weightKg', parseFloat(e.target.value) || 0)}
                      style={{ width: '70px', fontSize: '0.8rem' }}
                    />
                  </td>
                  <td style={{ padding: '8px' }}>
                    <input
                      type="checkbox"
                      checked={it.stackable}
                      onChange={(e) => updateItemField(idx, 'stackable', e.target.checked)}
                    />
                  </td>
                  <td style={{ padding: '8px' }}>
                    <input
                      type="number"
                      min={1}
                      value={it.quantity}
                      onChange={(e) => updateItemField(idx, 'quantity', parseInt(e.target.value) || 1)}
                      style={{ width: '50px', fontSize: '0.8rem' }}
                    />
                  </td>
                  <td style={{ padding: '8px' }}>
                    <button
                      type="button"
                      onClick={() => removeItemRow(idx)}
                      disabled={items.length <= 1}
                      style={{
                        background: 'transparent',
                        color: items.length <= 1 ? 'var(--text-muted)' : '#f87171',
                        border: 'none',
                        padding: '4px'
                      }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button
            type="submit"
            disabled={isSubmitting}
            style={{
              padding: '10px 20px',
              background: 'var(--accent-primary)',
              color: '#fff',
              borderRadius: '6px',
              fontWeight: 600,
              fontSize: '0.9rem',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}
          >
            <Send size={15} />
            {isSubmitting ? 'Registering Declaration...' : 'Submit Cargo Declaration'}
          </button>
        </div>
      </form>

      {/* Bulk Import Modal */}
      <BulkCargoImportModal
        isOpen={isImportModalOpen}
        onClose={() => setIsImportModalOpen(false)}
        onImportItems={handleImportItems}
        existingItemCount={items.length}
      />

      {/* Save Template Modal */}
      <SaveTemplateModal
        isOpen={isSaveTemplateModalOpen}
        onClose={() => setIsSaveTemplateModalOpen(false)}
        items={items}
      />

      {/* Saved Cargo Templates Modal */}
      <SavedCargoTemplatesModal
        isOpen={isSavedTemplatesModalOpen}
        onClose={() => setIsSavedTemplatesModalOpen(false)}
        onLoadTemplate={handleLoadTemplate}
        existingItemCount={items.length}
      />
    </div>
  );
};
