import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DeclarationDiscoveryModal } from './DeclarationDiscoveryModal';
import { declarationApi } from '../../api/declarationApi';

describe('DeclarationDiscoveryModal Component', () => {
  const mockDeclarations = [
    {
      id: 'decl-001-uuid',
      referenceNumber: 'DEC-001',
      shipperId: 'usr-shipper-001',
      origin: 'Mumbai',
      destination: 'Delhi',
      version: 1,
      createdAt: '2026-10-28T10:00:00Z',
      status: 'SUBMITTED',
      items: [
        {
          cargoItemId: 'ITEM-1',
          description: 'Flammable Solvent',
          hazardClass: '3',
          unNumber: 'UN1993',
          lengthMm: 1000,
          widthMm: 1000,
          heightMm: 1000,
          weightKg: 500,
          stackable: true,
          maxStackWeightKg: 1000,
          quantity: 2
        }
      ]
    },
    {
      id: 'decl-002-uuid',
      referenceNumber: 'DEC-002',
      shipperId: 'usr-shipper-002',
      origin: 'Chennai',
      destination: 'Bangalore',
      version: 1,
      createdAt: '2026-10-28T11:00:00Z',
      status: 'APPROVED',
      items: []
    }
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders loading state initially and then displays paginated items', async () => {
    vi.spyOn(declarationApi, 'listDeclarations').mockResolvedValue({
      content: mockDeclarations as any,
      page: 0,
      size: 10,
      totalElements: 2,
      totalPages: 1,
      first: true,
      last: true
    });

    const onSelect = vi.fn();
    const onClose = vi.fn();

    render(
      <DeclarationDiscoveryModal
        isOpen={true}
        onClose={onClose}
        onSelectDeclaration={onSelect}
      />
    );

    expect(screen.getByTestId('declaration-discovery-modal')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('DEC-001')).toBeInTheDocument();
    });

    expect(screen.getByText('DEC-002')).toBeInTheDocument();
    expect(screen.getByText(/1 packages/i)).toBeInTheDocument();
    expect(screen.getAllByText(/APPROVED/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/SUBMITTED/i).length).toBeGreaterThan(0);
  });

  it('handles empty results state gracefully', async () => {
    vi.spyOn(declarationApi, 'listDeclarations').mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    });

    render(
      <DeclarationDiscoveryModal
        isOpen={true}
        onClose={vi.fn()}
        onSelectDeclaration={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('discovery-empty-state')).toBeInTheDocument();
      expect(screen.getByText(/No Declarations Found/i)).toBeInTheDocument();
    });
  });

  it('handles error state when API request fails', async () => {
    vi.spyOn(declarationApi, 'listDeclarations').mockRejectedValue(
      new Error('Network connection timeout')
    );

    render(
      <DeclarationDiscoveryModal
        isOpen={true}
        onClose={vi.fn()}
        onSelectDeclaration={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('discovery-error-alert')).toBeInTheDocument();
      expect(screen.getByText(/Network connection timeout/i)).toBeInTheDocument();
    });
  });

  it('filters by status and invokes listDeclarations with updated query', async () => {
    const listSpy = vi.spyOn(declarationApi, 'listDeclarations').mockResolvedValue({
      content: [mockDeclarations[1]] as any,
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true
    });

    render(
      <DeclarationDiscoveryModal
        isOpen={true}
        onClose={vi.fn()}
        onSelectDeclaration={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledWith({
        page: 0,
        size: 10,
        status: undefined,
        shipperId: undefined
      });
    });

    const statusFilter = screen.getByTestId('discovery-status-filter');
    fireEvent.change(statusFilter, { target: { value: 'APPROVED' } });

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledWith({
        page: 0,
        size: 10,
        status: 'APPROVED',
        shipperId: undefined
      });
    });
  });

  it('allows load planner to filter by shipper ID across tenants', async () => {
    const listSpy = vi.spyOn(declarationApi, 'listDeclarations').mockResolvedValue({
      content: [mockDeclarations[0]] as any,
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true
    });

    render(
      <DeclarationDiscoveryModal
        isOpen={true}
        onClose={vi.fn()}
        onSelectDeclaration={vi.fn()}
        isPlanner={true}
      />
    );

    expect(screen.getByTestId('discovery-shipper-filter')).toBeInTheDocument();

    const shipperInput = screen.getByTestId('discovery-shipper-filter');
    fireEvent.change(shipperInput, { target: { value: 'usr-shipper-001' } });

    const refreshBtn = screen.getByTestId('refresh-discovery-btn');
    fireEvent.click(refreshBtn);

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalledWith({
        page: 0,
        size: 10,
        status: undefined,
        shipperId: 'usr-shipper-001'
      });
    });
  });

  it('selects a declaration and closes the modal', async () => {
    vi.spyOn(declarationApi, 'listDeclarations').mockResolvedValue({
      content: [mockDeclarations[0]] as any,
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true
    });

    const onSelect = vi.fn();
    const onClose = vi.fn();

    render(
      <DeclarationDiscoveryModal
        isOpen={true}
        onClose={onClose}
        onSelectDeclaration={onSelect}
      />
    );

    await waitFor(() => {
      expect(screen.getByTestId('select-declaration-decl-001-uuid')).toBeInTheDocument();
    });

    const selectBtn = screen.getByTestId('select-declaration-decl-001-uuid');
    fireEvent.click(selectBtn);

    expect(onSelect).toHaveBeenCalledWith(mockDeclarations[0]);
    expect(onClose).toHaveBeenCalled();
  });
});
