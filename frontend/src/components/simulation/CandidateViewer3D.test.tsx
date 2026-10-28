import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { CandidateViewer3D } from './CandidateViewer3D';
import { CandidateLayoutDto, CompartmentDto, ValidationResultDto } from '../../types/api';

// Mock @react-three/fiber and @react-three/drei canvas components to run in jsdom without WebGL hardware
vi.mock('@react-three/fiber', () => ({
  Canvas: ({ children, onPointerDown }: any) => (
    <div data-testid="r3f-canvas" onMouseDown={onPointerDown}>
      {children}
    </div>
  )
}));

vi.mock('@react-three/drei', () => ({
  OrbitControls: () => <div data-testid="orbit-controls" />,
  Grid: () => <div data-testid="r3f-grid" />,
  Text: ({ children }: any) => <div data-testid="r3f-text">{children}</div>
}));

describe('CandidateViewer3D Component', () => {
  const mockCompartment: CompartmentDto = {
    compartmentId: 'comp-standard-40ft',
    lengthMm: 12000,
    widthMm: 2400,
    heightMm: 2600,
    maxPayloadKg: 25000
  };

  const mockLayout: CandidateLayoutDto = {
    layoutId: 'layout-3d-01',
    layoutVersion: 1,
    compartmentId: 'comp-standard-40ft',
    contentDigest: '33c394a1ee09d28cd7f6b1ebb84ddc5672e463bd7063c1d7abc17d305e582df9',
    solverStatus: 'OPTIMAL',
    provenance: 'PYTHON_ORTOOLS_CPSAT_3D_V1',
    placements: [
      {
        placementId: 'p-1',
        cargoItemId: 'ITEM-SOLVENT-01',
        instanceIndex: 0,
        hazardClass: '3',
        unNumber: 'UN1993',
        xMm: 0,
        yMm: 0,
        zMm: 0,
        lengthMm: 1200,
        widthMm: 1000,
        heightMm: 1000,
        weightKg: 500,
        stackable: true
      },
      {
        placementId: 'p-2',
        cargoItemId: 'ITEM-EXPLOSIVE-02',
        instanceIndex: 0,
        hazardClass: '1',
        unNumber: 'UN0004',
        xMm: 5000,
        yMm: 0,
        zMm: 0,
        lengthMm: 800,
        widthMm: 800,
        heightMm: 800,
        weightKg: 250,
        stackable: false
      }
    ],
    unassignedItems: []
  };

  const mockValidation: ValidationResultDto = {
    validationId: 'val-3d-01',
    passed: true,
    validatorVersion: '1.0.0-m1',
    evaluatedRulesCount: 3,
    findings: []
  };

  it('renders placeholder when layout is null', () => {
    render(
      <CandidateViewer3D
        compartment={mockCompartment}
        layout={null}
        validation={null}
      />
    );

    expect(screen.getByText(/No candidate layout available/i)).toBeInTheDocument();
  });

  it('renders 3D Canvas, solver provenance, and camera view presets', () => {
    render(
      <CandidateViewer3D
        compartment={mockCompartment}
        layout={mockLayout}
        validation={mockValidation}
      />
    );

    expect(screen.getByTestId('r3f-canvas')).toBeInTheDocument();
    expect(screen.getByText(/OPTIMAL \(PYTHON_ORTOOLS_CPSAT_3D_V1\)/i)).toBeInTheDocument();
    expect(screen.getByText(/VERIFIED PASSED/i)).toBeInTheDocument();

    // Verify Camera Presets
    expect(screen.getByTitle(/Isometric 3D View/i)).toBeInTheDocument();
    expect(screen.getByTitle(/Top-Down Overhead View/i)).toBeInTheDocument();
    expect(screen.getByTitle(/Side Elevation View/i)).toBeInTheDocument();
    expect(screen.getByTitle(/Rear Door Loading View/i)).toBeInTheDocument();
  });

  it('toggles illustrative loading sequence animation', () => {
    render(
      <CandidateViewer3D
        compartment={mockCompartment}
        layout={mockLayout}
        validation={mockValidation}
      />
    );

    const playBtn = screen.getByTitle(/Illustrative Loading Animation/i);
    expect(playBtn).toBeInTheDocument();
    expect(screen.getByText('Play Sequence')).toBeInTheDocument();

    fireEvent.click(playBtn);
    expect(screen.getByText('Pause')).toBeInTheDocument();
    expect(screen.getByText(/Illustrative placement visualization/i)).toBeInTheDocument();
  });

  it('displays validation violation notices and authoritative safety disclaimer', () => {
    const failedValidation: ValidationResultDto = {
      validationId: 'val-fail',
      passed: false,
      validatorVersion: '1.0.0-m1',
      evaluatedRulesCount: 3,
      findings: [
        {
          severity: 'CRITICAL',
          code: 'HAZARD_SEGREGATION_VIOLATION',
          message: 'Class 1 and Class 3 segregation breached'
        }
      ]
    };

    render(
      <CandidateViewer3D
        compartment={mockCompartment}
        layout={mockLayout}
        validation={failedValidation}
      />
    );

    expect(screen.getByText(/VIOLATION DETECTED \(1\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Cryptographic Safety Notice/i)).toBeInTheDocument();
  });

  it('handles malformed layout placements gracefully without NaN crashes', () => {
    const malformedLayout: CandidateLayoutDto = {
      layoutId: 'layout-malformed',
      layoutVersion: 1,
      compartmentId: 'comp-standard-40ft',
      contentDigest: 'malformed-digest',
      solverStatus: 'FEASIBLE',
      provenance: 'TEST_ROBUSTNESS',
      placements: [
        {
          placementId: 'p-nan',
          cargoItemId: 'ITEM-BAD',
          instanceIndex: 0,
          hazardClass: '3',
          unNumber: 'UN1993',
          xMm: NaN as any,
          yMm: undefined as any,
          zMm: null as any,
          lengthMm: 0,
          widthMm: -50,
          heightMm: NaN as any,
          weightKg: NaN as any,
          stackable: false
        }
      ],
      unassignedItems: []
    };

    render(
      <CandidateViewer3D
        compartment={mockCompartment}
        layout={malformedLayout}
        validation={mockValidation}
      />
    );

    expect(screen.getByTestId('r3f-canvas')).toBeInTheDocument();
    expect(screen.getByText(/FEASIBLE \(TEST_ROBUSTNESS\)/i)).toBeInTheDocument();
  });
});
