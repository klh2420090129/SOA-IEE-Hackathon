import React, { useState, useEffect } from 'react';
import { 
  Bookmark, 
  X, 
  Trash2, 
  Edit3, 
  Check, 
  ArrowRight, 
  AlertCircle,
  PackageCheck
} from 'lucide-react';
import { CargoItemInput, CargoTemplate } from '../../types/api';
import { templateApi } from '../../api/templateApi';

interface SavedCargoTemplatesModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoadTemplate: (items: CargoItemInput[], replaceExisting: boolean) => void;
  existingItemCount: number;
}

export const SavedCargoTemplatesModal: React.FC<SavedCargoTemplatesModalProps> = ({
  isOpen,
  onClose,
  onLoadTemplate,
  existingItemCount
}) => {
  const [templates, setTemplates] = useState<CargoTemplate[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<CargoTemplate | null>(null);
  const [loadMode, setLoadMode] = useState<'replace' | 'append'>('replace');

  // Inline rename state
  const [editingTemplateId, setEditingTemplateId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');
  const [isUpdating, setIsUpdating] = useState(false);

  useEffect(() => {
    if (isOpen) {
      fetchTemplates();
    }
  }, [isOpen]);

  const fetchTemplates = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await templateApi.listTemplates();
      setTemplates(data);
      if (data.length > 0 && !selectedTemplate) {
        setSelectedTemplate(data[0]);
      } else if (data.length === 0) {
        setSelectedTemplate(null);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to load cargo templates');
    } finally {
      setIsLoading(false);
    }
  };

  const handleStartRename = (tpl: CargoTemplate, e: React.MouseEvent) => {
    e.stopPropagation();
    setEditingTemplateId(tpl.id);
    setEditingName(tpl.name);
  };

  const handleSaveRename = async (templateId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!editingName.trim()) return;
    setIsUpdating(true);
    try {
      const updated = await templateApi.updateTemplate(templateId, { name: editingName.trim() });
      setTemplates(prev => prev.map(t => t.id === templateId ? updated : t));
      if (selectedTemplate?.id === templateId) {
        setSelectedTemplate(updated);
      }
      setEditingTemplateId(null);
    } catch (err: any) {
      setError(err.message || 'Failed to update template name');
    } finally {
      setIsUpdating(false);
    }
  };

  const handleDeleteTemplate = async (templateId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('Are you sure you want to delete this saved template?')) return;
    try {
      await templateApi.deleteTemplate(templateId);
      setTemplates(prev => prev.filter(t => t.id !== templateId));
      if (selectedTemplate?.id === templateId) {
        const remaining = templates.filter(t => t.id !== templateId);
        setSelectedTemplate(remaining.length > 0 ? remaining[0] : null);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to delete cargo template');
    }
  };

  const handleExecuteLoad = () => {
    if (!selectedTemplate || selectedTemplate.items.length === 0) return;

    // Convert template items to clean CargoItemInputs
    const loadedItems: CargoItemInput[] = selectedTemplate.items.map(it => ({
      cargoItemId: it.cargoItemId || `ITEM-${Date.now().toString().slice(-4)}`,
      description: it.description,
      hazardClass: it.hazardClass,
      unNumber: it.unNumber,
      lengthMm: it.lengthMm,
      widthMm: it.widthMm,
      heightMm: it.heightMm,
      weightKg: it.weightKg,
      stackable: it.stackable,
      maxStackWeightKg: it.maxStackWeightKg,
      quantity: it.quantity
    }));

    onLoadTemplate(loadedItems, loadMode === 'replace');
    onClose();
  };

  if (!isOpen) return null;

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
      zIndex: 1050,
      padding: '20px'
    }}>
      <div className="glass-panel" style={{
        width: '980px',
        maxWidth: '95vw',
        maxHeight: '90vh',
        display: 'flex',
        flexDirection: 'column',
        borderRadius: '12px',
        border: '1px solid var(--border-color)',
        boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.6)',
        overflow: 'hidden'
      }}>
        {/* Header */}
        <div style={{
          padding: '16px 24px',
          borderBottom: '1px solid var(--border-color)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'rgba(255, 255, 255, 0.02)'
        }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.2rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Bookmark size={20} style={{ color: 'var(--accent-primary)' }} /> Saved Cargo Templates
            </h3>
            <p style={{ margin: '4px 0 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Load previously configured hazardous cargo consignments into your active declaration authoring form.
            </p>
          </div>
          <button
            onClick={onClose}
            data-testid="close-templates-modal-btn"
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              padding: '6px'
            }}
          >
            <X size={20} />
          </button>
        </div>

        {/* Body */}
        <div style={{ flex: 1, display: 'flex', minHeight: '360px', overflow: 'hidden' }}>
          {/* Left: Templates Sidebar List */}
          <div style={{
            width: '320px',
            borderRight: '1px solid var(--border-color)',
            background: 'rgba(0, 0, 0, 0.2)',
            display: 'flex',
            flexDirection: 'column'
          }}>
            <div style={{
              padding: '12px 16px',
              borderBottom: '1px solid var(--border-color)',
              fontSize: '0.8rem',
              fontWeight: 600,
              color: 'var(--text-muted)',
              display: 'flex',
              justifyContent: 'space-between'
            }}>
              <span>Your Templates ({templates.length})</span>
            </div>

            <div style={{ flex: 1, overflowY: 'auto' }}>
              {isLoading ? (
                <div style={{ padding: '24px', textAlign: 'center', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  Loading templates...
                </div>
              ) : error ? (
                <div style={{ padding: '16px', color: '#f87171', fontSize: '0.8rem', display: 'flex', gap: '6px' }}>
                  <AlertCircle size={14} /> {error}
                </div>
              ) : templates.length === 0 ? (
                <div style={{ padding: '30px 16px', textAlign: 'center', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  No saved templates found. Click <strong>"Save as Template"</strong> on any consignment with cargo packages to create one.
                </div>
              ) : (
                templates.map(tpl => {
                  const isSelected = selectedTemplate?.id === tpl.id;
                  const isRenaming = editingTemplateId === tpl.id;

                  return (
                    <div
                      key={tpl.id}
                      onClick={() => setSelectedTemplate(tpl)}
                      data-testid={`template-item-${tpl.id}`}
                      style={{
                        padding: '12px 16px',
                        borderBottom: '1px solid var(--border-color)',
                        cursor: 'pointer',
                        background: isSelected ? 'rgba(59, 130, 246, 0.15)' : 'transparent',
                        borderLeft: isSelected ? '3px solid var(--accent-primary)' : '3px solid transparent',
                        transition: 'all 0.15s ease'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                        {isRenaming ? (
                          <div style={{ display: 'flex', gap: '4px', flex: 1, marginRight: '6px' }} onClick={e => e.stopPropagation()}>
                            <input
                              type="text"
                              value={editingName}
                              onChange={e => setEditingName(e.target.value)}
                              data-testid="inline-edit-name-input"
                              style={{ width: '100%', fontSize: '0.8rem', padding: '2px 6px' }}
                              autoFocus
                            />
                            <button
                              type="button"
                              onClick={(e) => handleSaveRename(tpl.id, e)}
                              disabled={isUpdating}
                              data-testid="save-rename-btn"
                              style={{ background: 'var(--accent-primary)', border: 'none', color: '#fff', padding: '2px 6px', borderRadius: '4px', cursor: 'pointer' }}
                            >
                              <Check size={12} />
                            </button>
                            <button
                              type="button"
                              onClick={(e) => { e.stopPropagation(); setEditingTemplateId(null); }}
                              style={{ background: 'transparent', border: '1px solid var(--border-color)', color: 'var(--text-muted)', padding: '2px 4px', borderRadius: '4px', cursor: 'pointer' }}
                            >
                              <X size={12} />
                            </button>
                          </div>
                        ) : (
                          <div style={{ fontWeight: 600, fontSize: '0.88rem', color: isSelected ? '#93c5fd' : 'var(--text-main)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {tpl.name}
                          </div>
                        )}

                        <div style={{ display: 'flex', gap: '2px' }}>
                          {!isRenaming && (
                            <button
                              type="button"
                              onClick={(e) => handleStartRename(tpl, e)}
                              title="Rename template"
                              data-testid={`rename-template-${tpl.id}`}
                              style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '2px' }}
                            >
                              <Edit3 size={13} />
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={(e) => handleDeleteTemplate(tpl.id, e)}
                            title="Delete template"
                            data-testid={`delete-template-${tpl.id}`}
                            style={{ background: 'transparent', border: 'none', color: '#f87171', cursor: 'pointer', padding: '2px' }}
                          >
                            <Trash2 size={13} />
                          </button>
                        </div>
                      </div>

                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px', display: 'flex', justifyContent: 'space-between' }}>
                        <span>{tpl.items.length} package(s)</span>
                        <span>{new Date(tpl.createdAt).toLocaleDateString()}</span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Right: Selected Template Preview & Staging Controls */}
          <div style={{ flex: 1, padding: '20px 24px', display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
            {selectedTemplate ? (
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                {/* Header Summary */}
                <div style={{ marginBottom: '16px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                      <h4 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 600 }}>{selectedTemplate.name}</h4>
                      {selectedTemplate.description && (
                        <p style={{ margin: '4px 0 0 0', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                          {selectedTemplate.description}
                        </p>
                      )}
                    </div>
                    <span style={{ fontSize: '0.75rem', padding: '3px 8px', borderRadius: '4px', background: 'rgba(255, 255, 255, 0.05)', color: 'var(--text-muted)', border: '1px solid var(--border-color)' }}>
                      v{selectedTemplate.version} Schema
                    </span>
                  </div>

                  {/* Mode Selector */}
                  <div style={{ display: 'flex', gap: '16px', marginTop: '14px', padding: '10px 14px', background: 'rgba(255, 255, 255, 0.02)', borderRadius: '6px', border: '1px solid var(--border-color)', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>Load Mode:</span>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '0.8rem', cursor: 'pointer' }}>
                      <input
                        type="radio"
                        name="loadMode"
                        checked={loadMode === 'replace'}
                        onChange={() => setLoadMode('replace')}
                        data-testid="load-mode-replace"
                      />
                      Replace active items
                    </label>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '0.8rem', cursor: 'pointer' }}>
                      <input
                        type="radio"
                        name="loadMode"
                        checked={loadMode === 'append'}
                        onChange={() => setLoadMode('append')}
                        data-testid="load-mode-append"
                      />
                      Append to active ({existingItemCount} existing)
                    </label>
                  </div>
                </div>

                {/* Items Preview Table */}
                <div style={{ flex: 1, overflowY: 'auto', border: '1px solid var(--border-color)', borderRadius: '6px', background: 'rgba(0, 0, 0, 0.2)', marginBottom: '16px' }}>
                  <table style={{ width: '100%', fontSize: '0.8rem', borderCollapse: 'collapse', textAlign: 'left' }}>
                    <thead>
                      <tr style={{ background: 'rgba(255, 255, 255, 0.03)', color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)', position: 'sticky', top: 0 }}>
                        <th style={{ padding: '6px 10px' }}>Item ID</th>
                        <th style={{ padding: '6px 10px' }}>Description</th>
                        <th style={{ padding: '6px 10px' }}>Hazard / UN</th>
                        <th style={{ padding: '6px 10px' }}>Dimensions (mm)</th>
                        <th style={{ padding: '6px 10px' }}>Weight (kg)</th>
                        <th style={{ padding: '6px 10px' }}>Stackable</th>
                        <th style={{ padding: '6px 10px' }}>Qty</th>
                      </tr>
                    </thead>
                    <tbody>
                      {selectedTemplate.items.map((it, idx) => (
                        <tr key={idx} style={{ borderBottom: '1px solid var(--border-color)' }}>
                          <td style={{ padding: '6px 10px', fontWeight: 600 }}>{it.cargoItemId || `ITEM-${idx + 1}`}</td>
                          <td style={{ padding: '6px 10px', maxWidth: '140px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {it.description}
                          </td>
                          <td style={{ padding: '6px 10px' }}>Class {it.hazardClass} / {it.unNumber}</td>
                          <td style={{ padding: '6px 10px' }}>{it.lengthMm} &times; {it.widthMm} &times; {it.heightMm}</td>
                          <td style={{ padding: '6px 10px' }}>{it.weightKg}</td>
                          <td style={{ padding: '6px 10px' }}>{it.stackable ? 'Yes' : 'No'}</td>
                          <td style={{ padding: '6px 10px' }}>{it.quantity}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Notice & Execute Load Button */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <PackageCheck size={14} style={{ color: 'var(--accent-primary)' }} />
                    Loaded items remain fully editable before declaration submission.
                  </div>

                  <button
                    type="button"
                    onClick={handleExecuteLoad}
                    data-testid="load-template-confirm-btn"
                    style={{
                      padding: '8px 18px',
                      borderRadius: '6px',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      background: 'var(--accent-primary)',
                      color: '#fff',
                      border: 'none',
                      cursor: 'pointer',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px'
                    }}
                  >
                    Load {selectedTemplate.items.length} Packages into Form <ArrowRight size={14} />
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                Select a template from the list to preview packages
              </div>
            )}
          </div>
        </div>

      </div>
    </div>
  );
};
