import React, { useState } from 'react';
import { Bookmark, X, Save, AlertCircle } from 'lucide-react';
import { CargoItemInput } from '../../types/api';
import { templateApi } from '../../api/templateApi';

interface SaveTemplateModalProps {
  isOpen: boolean;
  onClose: () => void;
  items: CargoItemInput[];
  onTemplateSaved?: () => void;
}

export const SaveTemplateModal: React.FC<SaveTemplateModalProps> = ({
  isOpen,
  onClose,
  items,
  onTemplateSaved
}) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Template name is required');
      return;
    }
    if (items.length === 0) {
      setError('Cannot save an empty items set as a template');
      return;
    }

    setIsSaving(true);
    setError(null);

    try {
      await templateApi.createTemplate({
        name: name.trim(),
        description: description.trim() || undefined,
        items
      });
      setName('');
      setDescription('');
      onTemplateSaved?.();
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to save cargo template');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      width: '100vw',
      height: '100vh',
      backgroundColor: 'rgba(0, 0, 0, 0.75)',
      backdropFilter: 'blur(5px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 1100,
      padding: '20px'
    }}>
      <div className="glass-panel" style={{
        width: '520px',
        maxWidth: '95vw',
        borderRadius: '12px',
        border: '1px solid var(--border-color)',
        boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.6)',
        overflow: 'hidden'
      }}>
        {/* Header */}
        <div style={{
          padding: '16px 20px',
          borderBottom: '1px solid var(--border-color)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'rgba(255, 255, 255, 0.02)'
        }}>
          <h3 style={{ margin: 0, fontSize: '1.1rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Bookmark size={18} style={{ color: 'var(--accent-primary)' }} /> Save Consignment as Template
          </h3>
          <button
            onClick={onClose}
            data-testid="close-save-template-btn"
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              padding: '4px'
            }}
          >
            <X size={18} />
          </button>
        </div>

        {/* Body */}
        <form onSubmit={handleSave} style={{ padding: '20px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {error && (
            <div style={{
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid var(--accent-danger)',
              borderRadius: '6px',
              padding: '10px 14px',
              color: '#f87171',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'center',
              gap: '8px'
            }}>
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '4px' }}>
              Template Name *
            </label>
            <input
              type="text"
              placeholder="e.g. Standard Flammable Solvents Pack (2 Drums)"
              value={name}
              onChange={(e) => setName(e.target.value)}
              data-testid="template-name-input"
              style={{ width: '100%', fontSize: '0.85rem' }}
              required
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '4px' }}>
              Description (Optional)
            </label>
            <textarea
              rows={2}
              placeholder="Brief description of this consignment package set..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              data-testid="template-description-input"
              style={{ width: '100%', fontSize: '0.85rem' }}
            />
          </div>

          <div style={{
            background: 'rgba(255, 255, 255, 0.03)',
            borderRadius: '6px',
            padding: '10px 14px',
            fontSize: '0.8rem',
            color: 'var(--text-muted)'
          }}>
            This template will snapshot the current <strong>{items.length} package(s)</strong> with all statutory hazard ratings, dimensions, and weights for private reuse by your shipper account.
          </div>

          {/* Footer */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '8px 14px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid var(--border-color)',
                color: 'var(--text-main)',
                cursor: 'pointer'
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSaving || !name.trim()}
              data-testid="submit-save-template-btn"
              style={{
                padding: '8px 16px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                fontWeight: 600,
                background: (!name.trim() || isSaving) ? 'rgba(59, 130, 246, 0.3)' : 'var(--accent-primary)',
                color: '#fff',
                border: 'none',
                cursor: (!name.trim() || isSaving) ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              <Save size={14} />
              {isSaving ? 'Saving...' : 'Save Template'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
