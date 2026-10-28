import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SaveTemplateModal } from './SaveTemplateModal';
import { SavedCargoTemplatesModal } from './SavedCargoTemplatesModal';
import { templateApi } from '../../api/templateApi';
import { CargoItemInput, CargoTemplate } from '../../types/api';

vi.mock('../../api/templateApi', () => ({
  templateApi: {
    createTemplate: vi.fn(),
    listTemplates: vi.fn(),
    updateTemplate: vi.fn(),
    deleteTemplate: vi.fn()
  }
}));

describe('Cargo Templates Frontend Modals Integration', () => {
  const sampleItems: CargoItemInput[] = [
    {
      cargoItemId: 'ITEM-TEST-01',
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
    }
  ];

  const sampleTemplate: CargoTemplate = {
    id: 'tpl-12345',
    shipperId: 'shipper-test',
    name: 'Standard Solvents Pack',
    description: 'Flammables set',
    version: 1,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    items: [
      {
        ...sampleItems[0],
        id: 'tpl-item-01'
      }
    ]
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('SaveTemplateModal', () => {
    it('renders and saves template with valid name', async () => {
      const mockOnClose = vi.fn();
      const mockOnSaved = vi.fn();
      (templateApi.createTemplate as any).mockResolvedValue(sampleTemplate);

      render(
        <SaveTemplateModal
          isOpen={true}
          onClose={mockOnClose}
          items={sampleItems}
          onTemplateSaved={mockOnSaved}
        />
      );

      expect(screen.getByText('Save Consignment as Template')).toBeInTheDocument();

      const nameInput = screen.getByTestId('template-name-input');
      const descInput = screen.getByTestId('template-description-input');
      fireEvent.change(nameInput, { target: { value: 'Standard Solvents Pack' } });
      fireEvent.change(descInput, { target: { value: 'Flammables set' } });

      const submitBtn = screen.getByTestId('submit-save-template-btn');
      fireEvent.click(submitBtn);

      await waitFor(() => {
        expect(templateApi.createTemplate).toHaveBeenCalledWith({
          name: 'Standard Solvents Pack',
          description: 'Flammables set',
          items: sampleItems
        });
        expect(mockOnSaved).toHaveBeenCalled();
        expect(mockOnClose).toHaveBeenCalled();
      });
    });

    it('shows error if name is empty', async () => {
      render(
        <SaveTemplateModal
          isOpen={true}
          onClose={vi.fn()}
          items={sampleItems}
        />
      );

      const submitBtn = screen.getByTestId('submit-save-template-btn');
      expect(submitBtn).toBeDisabled();
    });
  });

  describe('SavedCargoTemplatesModal', () => {
    it('fetches and lists templates, allows loading with replace and append mode', async () => {
      const mockOnClose = vi.fn();
      const mockOnLoad = vi.fn();
      (templateApi.listTemplates as any).mockResolvedValue([sampleTemplate]);

      render(
        <SavedCargoTemplatesModal
          isOpen={true}
          onClose={mockOnClose}
          onLoadTemplate={mockOnLoad}
          existingItemCount={2}
        />
      );

      await waitFor(() => {
        expect(screen.getAllByText('Standard Solvents Pack').length).toBeGreaterThan(0);
        expect(screen.getByText('Flammables set')).toBeInTheDocument();
      });

      // Default is replace mode
      const loadBtn = screen.getByTestId('load-template-confirm-btn');
      fireEvent.click(loadBtn);

      expect(mockOnLoad).toHaveBeenCalledWith(
        [
          expect.objectContaining({
            cargoItemId: 'ITEM-TEST-01',
            hazardClass: '3',
            unNumber: 'UN1993'
          })
        ],
        true // replaceExisting = true
      );
      expect(mockOnClose).toHaveBeenCalled();

      // Switch to append mode
      const appendRadio = screen.getByTestId('load-mode-append');
      fireEvent.click(appendRadio);
      fireEvent.click(loadBtn);

      expect(mockOnLoad).toHaveBeenCalledWith(
        expect.any(Array),
        false // replaceExisting = false
      );
    });

    it('allows renaming template inline', async () => {
      (templateApi.listTemplates as any).mockResolvedValue([sampleTemplate]);
      (templateApi.updateTemplate as any).mockResolvedValue({
        ...sampleTemplate,
        name: 'Renamed Pack'
      });

      render(
        <SavedCargoTemplatesModal
          isOpen={true}
          onClose={vi.fn()}
          onLoadTemplate={vi.fn()}
          existingItemCount={0}
        />
      );

      await waitFor(() => {
        expect(screen.getAllByText('Standard Solvents Pack').length).toBeGreaterThan(0);
      });

      const renameBtn = screen.getByTestId(`rename-template-${sampleTemplate.id}`);
      fireEvent.click(renameBtn);

      const editInput = screen.getByTestId('inline-edit-name-input');
      fireEvent.change(editInput, { target: { value: 'Renamed Pack' } });

      const saveRenameBtn = screen.getByTestId('save-rename-btn');
      fireEvent.click(saveRenameBtn);

      await waitFor(() => {
        expect(templateApi.updateTemplate).toHaveBeenCalledWith(sampleTemplate.id, {
          name: 'Renamed Pack'
        });
        expect(screen.getAllByText('Renamed Pack').length).toBeGreaterThan(0);
      });
    });

    it('allows deleting template after confirmation', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      (templateApi.listTemplates as any).mockResolvedValue([sampleTemplate]);
      (templateApi.deleteTemplate as any).mockResolvedValue(undefined);

      render(
        <SavedCargoTemplatesModal
          isOpen={true}
          onClose={vi.fn()}
          onLoadTemplate={vi.fn()}
          existingItemCount={0}
        />
      );

      await waitFor(() => {
        expect(screen.getAllByText('Standard Solvents Pack').length).toBeGreaterThan(0);
      });

      const deleteBtn = screen.getByTestId(`delete-template-${sampleTemplate.id}`);
      fireEvent.click(deleteBtn);

      await waitFor(() => {
        expect(templateApi.deleteTemplate).toHaveBeenCalledWith(sampleTemplate.id);
        expect(screen.queryByText('Standard Solvents Pack')).toBeNull();
      });
    });
  });
});
