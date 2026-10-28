import React, { useState, useRef } from 'react';
import { 
  X, 
  Upload, 
  AlertTriangle, 
  CheckCircle2, 
  Trash2, 
  Download, 
  ArrowRight
} from 'lucide-react';
import { CargoItemInput } from '../../types/api';
import { 
  ImportParseResult, 
  parseAndValidateCsv, 
  parseAndValidateJson, 
  SAMPLE_CSV_TEMPLATE, 
  SAMPLE_JSON_TEMPLATE 
} from '../../utils/cargoImportParser';

interface BulkCargoImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onImportItems: (items: CargoItemInput[], replaceExisting: boolean) => void;
  existingItemCount: number;
}

export const BulkCargoImportModal: React.FC<BulkCargoImportModalProps> = ({
  isOpen,
  onClose,
  onImportItems,
  existingItemCount
}) => {
  const [activeTab, setActiveTab] = useState<'upload' | 'paste'>('upload');
  const [importFormat, setImportFormat] = useState<'csv' | 'json'>('csv');
  const [rawText, setRawText] = useState<string>('');
  const [fileName, setFileName] = useState<string>('');
  const [parseResult, setParseResult] = useState<ImportParseResult | null>(null);
  const [importMode, setImportMode] = useState<'replace' | 'append'>('replace');
  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setFileName(file.name);
    const isJson = file.name.endsWith('.json');
    setImportFormat(isJson ? 'json' : 'csv');

    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      setRawText(content);
      executeParsing(content, isJson ? 'json' : 'csv');
    };
    reader.readAsText(file);
  };

  const executeParsing = (content: string, format: 'csv' | 'json') => {
    if (!content.trim()) {
      setParseResult(null);
      return;
    }
    const result = format === 'csv' 
      ? parseAndValidateCsv(content) 
      : parseAndValidateJson(content);
    setParseResult(result);
  };

  const handlePasteChange = (text: string) => {
    setRawText(text);
    executeParsing(text, importFormat);
  };

  const handleFormatToggle = (fmt: 'csv' | 'json') => {
    setImportFormat(fmt);
    if (rawText.trim()) {
      executeParsing(rawText, fmt);
    }
  };

  const handleDeleteRow = (rowNumber: number) => {
    if (!parseResult) return;
    const updatedRows = parseResult.rows.filter(r => r.rowNumber !== rowNumber);
    setParseResult({
      ...parseResult,
      totalRows: updatedRows.length,
      validRowsCount: updatedRows.filter(r => r.isValid).length,
      invalidRowsCount: updatedRows.filter(r => !r.isValid).length,
      rows: updatedRows
    });
  };

  const handleDownloadTemplate = (type: 'csv' | 'json') => {
    const content = type === 'csv' ? SAMPLE_CSV_TEMPLATE : SAMPLE_JSON_TEMPLATE;
    const mime = type === 'csv' ? 'text/csv' : 'application/json';
    const filename = `cargo_catalogue_template.${type}`;
    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleExecuteImport = () => {
    if (!parseResult) return;
    const validItems = parseResult.rows
      .filter(r => r.isValid)
      .map(r => r.item);

    if (validItems.length === 0) return;

    onImportItems(validItems, importMode === 'replace');
    onClose();
  };

  const validItemsCount = parseResult?.validRowsCount || 0;

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
      zIndex: 1000,
      padding: '20px'
    }}>
      <div className="glass-panel" style={{
        width: '1000px',
        maxWidth: '95vw',
        maxHeight: '90vh',
        display: 'flex',
        flexDirection: 'column',
        borderRadius: '12px',
        border: '1px solid var(--border-color)',
        boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.6)',
        overflow: 'hidden'
      }}>
        {/* Modal Header */}
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
              <Upload size={20} style={{ color: 'var(--accent-primary)' }} /> Bulk Cargo Catalogue Import
            </h3>
            <p style={{ margin: '4px 0 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Import multiple dangerous cargo items from CSV or JSON. Schema validated client-side against safety rules before declaration staging.
            </p>
          </div>
          <button 
            onClick={onClose} 
            data-testid="close-modal-btn"
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

        {/* Modal Body */}
        <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1, display: 'flex', flexDirection: 'column', gap: '16px' }}>
          
          {/* Controls: Mode & Template Downloads */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
            <div style={{ display: 'flex', gap: '8px' }}>
              <button
                type="button"
                onClick={() => setActiveTab('upload')}
                style={{
                  padding: '6px 14px',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  background: activeTab === 'upload' ? 'var(--accent-primary)' : 'rgba(255, 255, 255, 0.05)',
                  color: activeTab === 'upload' ? '#fff' : 'var(--text-main)',
                  border: '1px solid var(--border-color)',
                  cursor: 'pointer'
                }}
              >
                File Upload
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('paste')}
                style={{
                  padding: '6px 14px',
                  borderRadius: '6px',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  background: activeTab === 'paste' ? 'var(--accent-primary)' : 'rgba(255, 255, 255, 0.05)',
                  color: activeTab === 'paste' ? '#fff' : 'var(--text-main)',
                  border: '1px solid var(--border-color)',
                  cursor: 'pointer'
                }}
              >
                Direct Paste
              </button>
            </div>

            {/* Template Downloads */}
            <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Download Schema Template:</span>
              <button
                type="button"
                onClick={() => handleDownloadTemplate('csv')}
                style={{
                  padding: '4px 10px',
                  borderRadius: '4px',
                  fontSize: '0.75rem',
                  background: 'rgba(255, 255, 255, 0.05)',
                  border: '1px solid var(--border-color)',
                  color: 'var(--text-main)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
              >
                <Download size={12} /> CSV
              </button>
              <button
                type="button"
                onClick={() => handleDownloadTemplate('json')}
                style={{
                  padding: '4px 10px',
                  borderRadius: '4px',
                  fontSize: '0.75rem',
                  background: 'rgba(255, 255, 255, 0.05)',
                  border: '1px solid var(--border-color)',
                  color: 'var(--text-main)',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '4px'
                }}
              >
                <Download size={12} /> JSON
              </button>
            </div>
          </div>

          {/* Active Tab View */}
          {activeTab === 'upload' ? (
            <div 
              onClick={() => fileInputRef.current?.click()}
              style={{
                border: '2px dashed var(--border-color)',
                borderRadius: '8px',
                padding: '30px 20px',
                textAlign: 'center',
                cursor: 'pointer',
                background: 'rgba(255, 255, 255, 0.01)',
                transition: 'all 0.2s ease'
              }}
            >
              <input 
                ref={fileInputRef} 
                type="file" 
                accept=".csv,.json,text/csv,application/json" 
                style={{ display: 'none' }} 
                onChange={handleFileChange}
                data-testid="file-upload-input"
              />
              <Upload size={32} style={{ color: 'var(--accent-primary)', margin: '0 auto 8px auto' }} />
              <div style={{ fontWeight: 600, fontSize: '0.95rem' }}>
                {fileName ? `Loaded: ${fileName}` : 'Click or drop CSV / JSON catalogue file here'}
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                Strict integer millimetres (L/W/H), weight in kg, valid statutory hazard class and UN# required.
              </div>
            </div>
          ) : (
            <div>
              <div style={{ display: 'flex', gap: '8px', marginBottom: '8px', alignItems: 'center' }}>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Format:</span>
                <button
                  type="button"
                  data-testid="toggle-csv-format"
                  onClick={() => handleFormatToggle('csv')}
                  style={{
                    padding: '3px 8px',
                    fontSize: '0.75rem',
                    borderRadius: '4px',
                    border: '1px solid var(--border-color)',
                    background: importFormat === 'csv' ? 'rgba(59, 130, 246, 0.2)' : 'transparent',
                    color: importFormat === 'csv' ? '#60a5fa' : 'var(--text-muted)'
                  }}
                >
                  CSV
                </button>
                <button
                  type="button"
                  data-testid="toggle-json-format"
                  onClick={() => handleFormatToggle('json')}
                  style={{
                    padding: '3px 8px',
                    fontSize: '0.75rem',
                    borderRadius: '4px',
                    border: '1px solid var(--border-color)',
                    background: importFormat === 'json' ? 'rgba(59, 130, 246, 0.2)' : 'transparent',
                    color: importFormat === 'json' ? '#60a5fa' : 'var(--text-muted)'
                  }}
                >
                  JSON
                </button>
              </div>
              <textarea
                rows={6}
                value={rawText}
                placeholder={importFormat === 'csv' 
                  ? "cargoItemId,description,hazardClass,unNumber,lengthMm,widthMm,heightMm,weightKg,stackable,maxStackWeightKg,quantity\nITEM-01,Nitric Acid,8,UN2031,1000,1000,1000,450,false,0,1" 
                  : "[\n  {\n    \"cargoItemId\": \"ITEM-01\",\n    \"description\": \"Nitric Acid\",\n    \"hazardClass\": \"8\",\n    \"unNumber\": \"UN2031\",\n    \"lengthMm\": 1000,\n    \"widthMm\": 1000,\n    \"heightMm\": 1000,\n    \"weightKg\": 450,\n    \"stackable\": false,\n    \"maxStackWeightKg\": 0,\n    \"quantity\": 1\n  }\n]"
                }
                onChange={(e) => handlePasteChange(e.target.value)}
                data-testid="paste-textarea"
                style={{
                  width: '100%',
                  fontFamily: 'monospace',
                  fontSize: '0.8rem',
                  padding: '10px',
                  borderRadius: '6px',
                  border: '1px solid var(--border-color)',
                  background: 'rgba(0, 0, 0, 0.3)',
                  color: 'var(--text-main)',
                  resize: 'vertical'
                }}
              />
            </div>
          )}

          {/* Global Parse Errors */}
          {parseResult?.globalErrors && parseResult.globalErrors.length > 0 && (
            <div style={{
              background: 'rgba(239, 68, 68, 0.15)',
              border: '1px solid var(--accent-danger)',
              borderRadius: '8px',
              padding: '12px 16px',
              color: '#f87171'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontWeight: 600, fontSize: '0.85rem' }}>
                <AlertTriangle size={16} /> Syntax / Format Error
              </div>
              <ul style={{ margin: '6px 0 0 16px', padding: 0, fontSize: '0.8rem' }}>
                {parseResult.globalErrors.map((err, i) => (
                  <li key={i}>{err}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Validation Summary & Preview Table */}
          {parseResult && parseResult.rows.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                  <span style={{ fontWeight: 600, fontSize: '0.9rem' }}>
                    Preview ({parseResult.totalRows} items parsed)
                  </span>
                  <span style={{ 
                    fontSize: '0.75rem', 
                    padding: '2px 8px', 
                    borderRadius: '12px', 
                    background: 'rgba(34, 197, 94, 0.15)', 
                    color: '#4ade80',
                    border: '1px solid rgba(34, 197, 94, 0.3)'
                  }}>
                    {parseResult.validRowsCount} Valid
                  </span>
                  {parseResult.invalidRowsCount > 0 && (
                    <span style={{ 
                      fontSize: '0.75rem', 
                      padding: '2px 8px', 
                      borderRadius: '12px', 
                      background: 'rgba(239, 68, 68, 0.15)', 
                      color: '#f87171',
                      border: '1px solid rgba(239, 68, 68, 0.3)'
                    }}>
                      {parseResult.invalidRowsCount} Invalid (Errors Below)
                    </span>
                  )}
                </div>

                <div style={{ display: 'flex', gap: '8px', alignItems: 'center', fontSize: '0.8rem' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="importMode"
                      checked={importMode === 'replace'}
                      onChange={() => setImportMode('replace')}
                    />
                    Replace declaration items
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '4px', cursor: 'pointer' }}>
                    <input
                      type="radio"
                      name="importMode"
                      checked={importMode === 'append'}
                      onChange={() => setImportMode('append')}
                    />
                    Append to existing ({existingItemCount})
                  </label>
                </div>
              </div>

              {/* Table Container */}
              <div style={{ 
                maxHeight: '260px', 
                overflowY: 'auto', 
                border: '1px solid var(--border-color)', 
                borderRadius: '6px',
                background: 'rgba(0, 0, 0, 0.2)'
              }}>
                <table style={{ width: '100%', fontSize: '0.8rem', borderCollapse: 'collapse', textAlign: 'left' }}>
                  <thead>
                    <tr style={{ background: 'rgba(255, 255, 255, 0.03)', color: 'var(--text-muted)', borderBottom: '1px solid var(--border-color)', position: 'sticky', top: 0 }}>
                      <th style={{ padding: '6px 10px' }}>#</th>
                      <th style={{ padding: '6px 10px' }}>Status</th>
                      <th style={{ padding: '6px 10px' }}>Item ID</th>
                      <th style={{ padding: '6px 10px' }}>Description</th>
                      <th style={{ padding: '6px 10px' }}>Hazard / UN</th>
                      <th style={{ padding: '6px 10px' }}>Dimensions (mm)</th>
                      <th style={{ padding: '6px 10px' }}>Weight (kg)</th>
                      <th style={{ padding: '6px 10px' }}>Stackable</th>
                      <th style={{ padding: '6px 10px' }}>Qty</th>
                      <th style={{ padding: '6px 10px' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {parseResult.rows.map((r) => (
                      <React.Fragment key={r.rowNumber}>
                        <tr style={{ 
                          borderBottom: r.errors.length > 0 ? 'none' : '1px solid var(--border-color)',
                          background: r.isValid ? 'transparent' : 'rgba(239, 68, 68, 0.05)'
                        }}>
                          <td style={{ padding: '6px 10px', color: 'var(--text-muted)' }}>{r.rowNumber}</td>
                          <td style={{ padding: '6px 10px' }}>
                            {r.isValid ? (
                              <span style={{ color: '#4ade80', display: 'flex', alignItems: 'center', gap: '3px' }}>
                                <CheckCircle2 size={13} /> OK
                              </span>
                            ) : (
                              <span style={{ color: '#f87171', display: 'flex', alignItems: 'center', gap: '3px', fontWeight: 600 }}>
                                <AlertTriangle size={13} /> INVALID
                              </span>
                            )}
                          </td>
                          <td style={{ padding: '6px 10px', fontWeight: 600 }}>{r.item.cargoItemId || '—'}</td>
                          <td style={{ padding: '6px 10px', maxWidth: '160px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {r.item.description || '—'}
                          </td>
                          <td style={{ padding: '6px 10px' }}>
                            {r.item.hazardClass ? `Class ${r.item.hazardClass}` : '—'} / {r.item.unNumber || '—'}
                          </td>
                          <td style={{ padding: '6px 10px' }}>
                            {r.item.lengthMm} &times; {r.item.widthMm} &times; {r.item.heightMm}
                          </td>
                          <td style={{ padding: '6px 10px' }}>{r.item.weightKg}</td>
                          <td style={{ padding: '6px 10px' }}>{r.item.stackable ? 'Yes' : 'No'}</td>
                          <td style={{ padding: '6px 10px' }}>{r.item.quantity}</td>
                          <td style={{ padding: '6px 10px' }}>
                            <button
                              type="button"
                              onClick={() => handleDeleteRow(r.rowNumber)}
                              title="Remove item from import"
                              style={{
                                background: 'transparent',
                                border: 'none',
                                color: '#f87171',
                                cursor: 'pointer',
                                padding: '2px'
                              }}
                            >
                              <Trash2 size={14} />
                            </button>
                          </td>
                        </tr>
                        {r.errors.length > 0 && (
                          <tr style={{ borderBottom: '1px solid var(--border-color)', background: 'rgba(239, 68, 68, 0.08)' }}>
                            <td colSpan={10} style={{ padding: '4px 10px 8px 30px' }}>
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                {r.errors.map((err, eIdx) => (
                                  <span key={eIdx} style={{
                                    fontSize: '0.72rem',
                                    color: '#fca5a5',
                                    background: 'rgba(239, 68, 68, 0.2)',
                                    padding: '2px 6px',
                                    borderRadius: '4px',
                                    border: '1px solid rgba(239, 68, 68, 0.3)'
                                  }}>
                                    [{err.field}] {err.message}
                                  </span>
                                ))}
                              </div>
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div style={{
          padding: '16px 24px',
          borderTop: '1px solid var(--border-color)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: 'rgba(255, 255, 255, 0.02)'
        }}>
          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
            {parseResult ? (
              <span>
                {parseResult.validRowsCount} valid package(s) ready to stage into declaration authoring form.
              </span>
            ) : (
              <span>Provide CSV or JSON to preview and validate before staging.</span>
            )}
          </div>

          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              type="button"
              onClick={onClose}
              style={{
                padding: '8px 16px',
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
              type="button"
              onClick={handleExecuteImport}
              disabled={!parseResult || parseResult.validRowsCount === 0}
              data-testid="confirm-import-btn"
              style={{
                padding: '8px 18px',
                borderRadius: '6px',
                fontSize: '0.85rem',
                fontWeight: 600,
                background: (!parseResult || parseResult.validRowsCount === 0) 
                  ? 'rgba(59, 130, 246, 0.3)' 
                  : 'var(--accent-primary)',
                color: '#fff',
                border: 'none',
                cursor: (!parseResult || parseResult.validRowsCount === 0) ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
            >
              Stage {validItemsCount} Packages to Declaration <ArrowRight size={14} />
            </button>
          </div>
        </div>

      </div>
    </div>
  );
};
