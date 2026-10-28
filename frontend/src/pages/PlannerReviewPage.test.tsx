import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PlannerReviewPage } from './PlannerReviewPage';
import { assessmentApi } from '../api/assessmentApi';
import { declarationApi } from '../api/declarationApi';

// Mock child visualizers so we don't need full WebGL rendering in page test
vi.mock('../components/simulation/CandidateViewer3D', () => ({
  CandidateViewer3D: () => <div data-testid="candidate-viewer-3d">Mock 3D Viewer</div>
}));

vi.mock('../components/simulation/CandidateViewer2D', () => ({
  CandidateViewer2D: () => <div data-testid="candidate-viewer-2d">Mock 2D Viewer</div>
}));

// Mock Auth context to provide a logged-in Load Planner
vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { userId: 'usr-planner-001', username: 'planner@soa8.org', roles: ['ROLE_LOAD_PLANNER'] },
    hasRole: (role: string) => role === 'ROLE_LOAD_PLANNER',
    login: vi.fn(),
    logout: vi.fn()
  }),
  AuthProvider: ({ children }: any) => <>{children}</>
}));

describe('PlannerReviewPage Component Workflow Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders initial lookup inputs and toggle tabs', () => {
    render(<PlannerReviewPage />);
    expect(screen.getByPlaceholderText(/Enter Declaration ID/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Lookup Assessment ID/i)).toBeInTheDocument();
    expect(screen.getByText(/Interactive 3D Simulation/i)).toBeInTheDocument();
    expect(screen.getByText(/2D Floor-Plan & Table Fallback/i)).toBeInTheDocument();
  });

  it('switches between 3D view and 2D fallback view', () => {
    render(<PlannerReviewPage />);
    
    // Default is 3D view
    expect(screen.getByTestId('candidate-viewer-3d')).toBeInTheDocument();
    expect(screen.queryByTestId('candidate-viewer-2d')).not.toBeInTheDocument();

    // Click 2D fallback toggle
    const toggle2D = screen.getByText(/2D Floor-Plan & Table Fallback/i);
    fireEvent.click(toggle2D);

    expect(screen.getByTestId('candidate-viewer-2d')).toBeInTheDocument();
    expect(screen.queryByTestId('candidate-viewer-3d')).not.toBeInTheDocument();
  });

  it('loads assessment and declaration when looked up by ID', async () => {
    const mockAssessment = {
      id: 'asmt-test-12345678',
      declarationId: 'decl-test-1',
      declarationVersion: 1,
      ruleSnapshotId: 'snap-2026-q4-v1',
      compartmentId: 'comp-standard-40ft',
      workflowStatus: 'PENDING_APPROVAL',
      validationPassed: true
    };

    const mockDeclaration = {
      id: 'decl-test-1',
      referenceNumber: 'DEC-123456',
      shipperId: 'usr-shipper-001',
      version: 1,
      status: 'SUBMITTED',
      origin: 'Mumbai',
      destination: 'Delhi',
      items: []
    };

    vi.spyOn(assessmentApi, 'getAssessment').mockResolvedValue(mockAssessment as any);
    vi.spyOn(declarationApi, 'getDeclaration').mockResolvedValue(mockDeclaration as any);

    render(<PlannerReviewPage />);

    const input = screen.getByPlaceholderText(/Lookup Assessment ID/i);
    fireEvent.change(input, { target: { value: 'asmt-test-12345678' } });

    const lookupBtn = screen.getByRole('button', { name: /Lookup/i });
    fireEvent.click(lookupBtn);

    await waitFor(() => {
      expect(screen.getByText(/Assessment #asmt-tes/i)).toBeInTheDocument();
      expect(screen.getByText(/Workflow: PENDING_APPROVAL/i)).toBeInTheDocument();
    });
  });

  it('renders visible error state when optimization submission fails and does not crash shell', async () => {
    const mockAssessment = {
      id: 'asmt-opt-fail',
      declarationId: 'decl-opt-fail',
      declarationVersion: 1,
      ruleSnapshotId: 'snap-2026-q4-v1',
      compartmentId: 'comp-standard-40ft',
      workflowStatus: 'DRAFT'
    };

    const mockDeclaration = {
      id: 'decl-opt-fail',
      referenceNumber: 'DEC-OPT-FAIL',
      shipperId: 'usr-shipper-001',
      version: 1,
      status: 'SUBMITTED',
      origin: 'Mumbai',
      destination: 'Delhi',
      items: [
        {
          cargoItemId: 'ITEM-1',
          description: 'Solvent',
          hazardClass: '3',
          unNumber: 'UN1993',
          lengthMm: 1000,
          widthMm: 1000,
          heightMm: 1000,
          weightKg: 500,
          stackable: true,
          quantity: 1
        }
      ]
    };

    vi.spyOn(assessmentApi, 'getAssessment').mockResolvedValue(mockAssessment as any);
    vi.spyOn(declarationApi, 'getDeclaration').mockResolvedValue(mockDeclaration as any);

    const optimizationApiModule = await import('../api/optimizationApi');
    vi.spyOn(optimizationApiModule.optimizationApi, 'submitJob').mockRejectedValue(
      new Error('Optimization engine queue full')
    );

    render(<PlannerReviewPage />);

    const input = screen.getByPlaceholderText(/Lookup Assessment ID/i);
    fireEvent.change(input, { target: { value: 'asmt-opt-fail' } });

    const lookupBtn = screen.getByRole('button', { name: /Lookup/i });
    fireEvent.click(lookupBtn);

    await waitFor(() => {
      expect(screen.getByText(/Run Optimization/i)).toBeInTheDocument();
    });

    const optBtn = screen.getByText(/Run Optimization/i);
    fireEvent.click(optBtn);

    await waitFor(() => {
      expect(screen.getByText(/Optimization engine queue full/i)).toBeInTheDocument();
      // Ensure page shell still exists
      expect(screen.getByTestId('candidate-viewer-3d')).toBeInTheDocument();
    });
  });
});
