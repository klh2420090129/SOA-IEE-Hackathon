import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CandidateViewer2D } from './CandidateViewer2D';
import { CandidateLayoutDto, CompartmentDto, ValidationResultDto } from '../../types/api';

describe('CandidateViewer2D Component', () => {
  const mockCompartment: CompartmentDto = {
    compartmentId: 'comp-standard-40ft',
    lengthMm: 12000,
    widthMm: 2400,
    heightMm: 2600,
    maxPayloadKg: 25000
  };

  const mockLayout: CandidateLayoutDto = {
    layoutId: 'layout-test-01',
    layoutVersion: 1,
    compartmentId: 'comp-standard-40ft',
    contentDigest: 'a1b2c3d4e5f678901234567890abcdefa1b2c3d4e5f678901234567890abcdef',
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

  it('renders placeholder when no candidate layout is provided', () => {
    render(
      <CandidateViewer2D
        compartment={mockCompartment}
        layout={null}
        validation={null}
      />
    );

    expect(screen.getByText(/No candidate layout available/i)).toBeInTheDocument();
  });

  it('renders candidate placements, coordinates, and content digest', () => {
    render(
      <CandidateViewer2D
        compartment={mockCompartment}
        layout={mockLayout}
        validation={null}
      />
    );

    expect(screen.getByText(/OPTIMAL \(PYTHON_ORTOOLS_CPSAT_3D_V1\)/i)).toBeInTheDocument();
    expect(screen.getAllByText('ITEM-SOLVENT-01').length).toBeGreaterThan(0);
    expect(screen.getAllByText('ITEM-EXPLOSIVE-02').length).toBeGreaterThan(0);
    expect(screen.getByText(/UNVALIDATED/i)).toBeInTheDocument();
  });

  it('displays validation passed status and does not claim approval without planner authorization', () => {
    const passedValidation: ValidationResultDto = {
      validationId: 'val-001',
      passed: true,
      validatorVersion: '1.0.0-m1',
      evaluatedRulesCount: 3,
      findings: []
    };

    render(
      <CandidateViewer2D
        compartment={mockCompartment}
        layout={mockLayout}
        validation={passedValidation}
        isApproved={false}
      />
    );

    expect(screen.getByText(/PASSED \(0 Violations\)/i)).toBeInTheDocument();
    expect(screen.getByText(/PENDING LOAD PLANNER REVIEW/i)).toBeInTheDocument();
    expect(screen.queryByText(/AUTHORITATIVELY APPROVED/i)).not.toBeInTheDocument();
  });

  it('highlights safety violations when independent validation detects conflicts', () => {
    const failedValidation: ValidationResultDto = {
      validationId: 'val-fail-002',
      passed: false,
      validatorVersion: '1.0.0-m1',
      evaluatedRulesCount: 3,
      findings: [
        {
          severity: 'CRITICAL',
          code: 'HAZARD_SEGREGATION_VIOLATION',
          message: 'Class 1 and Class 3 require min 3000mm distance',
          involvedItemIds: ['ITEM-SOLVENT-01', 'ITEM-EXPLOSIVE-02']
        }
      ]
    };

    render(
      <CandidateViewer2D
        compartment={mockCompartment}
        layout={mockLayout}
        validation={failedValidation}
        isApproved={false}
      />
    );

    expect(screen.getByText(/FAILED \(1 Violations\)/i)).toBeInTheDocument();
  });
});
