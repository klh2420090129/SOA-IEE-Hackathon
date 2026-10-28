import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BulkCargoImportModal } from './BulkCargoImportModal';

describe('BulkCargoImportModal Component', () => {
  const mockOnClose = vi.fn();
  const mockOnImportItems = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when isOpen is false', () => {
    const { container } = render(
      <BulkCargoImportModal
        isOpen={false}
        onClose={mockOnClose}
        onImportItems={mockOnImportItems}
        existingItemCount={0}
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders modal dialog when isOpen is true', () => {
    render(
      <BulkCargoImportModal
        isOpen={true}
        onClose={mockOnClose}
        onImportItems={mockOnImportItems}
        existingItemCount={2}
      />
    );
    expect(screen.getByText('Bulk Cargo Catalogue Import')).toBeInTheDocument();
    expect(screen.getByText('File Upload')).toBeInTheDocument();
    expect(screen.getByText('Direct Paste')).toBeInTheDocument();
  });

  it('allows pasting CSV, displays validation errors, allows row deletion, and imports valid rows', () => {
    render(
      <BulkCargoImportModal
        isOpen={true}
        onClose={mockOnClose}
        onImportItems={mockOnImportItems}
        existingItemCount={1}
      />
    );

    // Switch to paste tab
    fireEvent.click(screen.getByText('Direct Paste'));

    const textarea = screen.getByTestId('paste-textarea');
    const sampleCsv = `cargoItemId,description,hazardClass,unNumber,lengthMm,widthMm,heightMm,weightKg,stackable,maxStackWeightKg,quantity
ITEM-VALID-01,Valid Cargo Description,3,UN1993,1000,1000,1000,500,true,1000,1
ITEM-INVALID-02,,UNKNOWN,1234,0,100,100,0,true,0,0`;

    fireEvent.change(textarea, { target: { value: sampleCsv } });

    // Expect preview
    expect(screen.getByText('Preview (2 items parsed)')).toBeInTheDocument();
    expect(screen.getByText('1 Valid')).toBeInTheDocument();
    expect(screen.getByText('1 Invalid (Errors Below)')).toBeInTheDocument();

    // Check item rows
    expect(screen.getByText('ITEM-VALID-01')).toBeInTheDocument();
    expect(screen.getByText('ITEM-INVALID-02')).toBeInTheDocument();

    // Remove invalid row
    const trashButtons = screen.getAllByTitle('Remove item from import');
    expect(trashButtons.length).toBe(2);
    fireEvent.click(trashButtons[1]); // Delete row 2

    // After deleting row 2, all remaining are valid
    expect(screen.getByText('Preview (1 items parsed)')).toBeInTheDocument();
    expect(screen.getByText('1 Valid')).toBeInTheDocument();
    expect(screen.queryByText(/Invalid/)).toBeNull();

    // Confirm import button
    const importBtn = screen.getByTestId('confirm-import-btn');
    expect(importBtn).not.toBeDisabled();
    fireEvent.click(importBtn);

    expect(mockOnImportItems).toHaveBeenCalledTimes(1);
    expect(mockOnImportItems).toHaveBeenCalledWith(
      [
        expect.objectContaining({
          cargoItemId: 'ITEM-VALID-01',
          hazardClass: '3',
          unNumber: 'UN1993',
          lengthMm: 1000,
          weightKg: 500
        })
      ],
      true // replaceExisting default
    );
    expect(mockOnClose).toHaveBeenCalledTimes(1);
  });

  it('allows pasting JSON and appending to existing items', () => {
    render(
      <BulkCargoImportModal
        isOpen={true}
        onClose={mockOnClose}
        onImportItems={mockOnImportItems}
        existingItemCount={3}
      />
    );

    fireEvent.click(screen.getByText('Direct Paste'));
    fireEvent.click(screen.getByTestId('toggle-json-format'));

    const textarea = screen.getByTestId('paste-textarea');
    const validJson = JSON.stringify([
      {
        cargoItemId: 'ITEM-JSON-01',
        description: 'Corrosive Liquid Drum',
        hazardClass: '8',
        unNumber: 'UN1789',
        lengthMm: 800,
        widthMm: 800,
        heightMm: 900,
        weightKg: 300,
        stackable: false,
        maxStackWeightKg: 0,
        quantity: 2
      }
    ]);

    fireEvent.change(textarea, { target: { value: validJson } });

    expect(screen.getByText('Preview (1 items parsed)')).toBeInTheDocument();
    expect(screen.getByText('1 Valid')).toBeInTheDocument();
    expect(screen.getByText('ITEM-JSON-01')).toBeInTheDocument();

    // Select append
    const appendRadio = screen.getByLabelText(/Append to existing/i);
    fireEvent.click(appendRadio);

    const importBtn = screen.getByTestId('confirm-import-btn');
    fireEvent.click(importBtn);

    expect(mockOnImportItems).toHaveBeenCalledWith(
      [
        expect.objectContaining({
          cargoItemId: 'ITEM-JSON-01',
          hazardClass: '8',
          unNumber: 'UN1789',
          quantity: 2
        })
      ],
      false // replaceExisting = false when append is selected
    );
  });
});
