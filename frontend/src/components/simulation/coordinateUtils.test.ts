import { describe, it, expect } from 'vitest';
import { 
  convertCompartmentToWorld, 
  convertPlacementToWorld, 
  getHazardColor 
} from './coordinateUtils';
import { CompartmentDto, ItemPlacementDto } from '../../types/api';

describe('coordinateUtils — 3D Space & Coordinate Integrity', () => {
  const compartment: CompartmentDto = {
    compartmentId: 'comp-standard-40ft',
    lengthMm: 12000,
    widthMm: 2400,
    heightMm: 2600,
    maxPayloadKg: 25000
  };

  it('converts compartment millimeters to Three.js centered scene bounds with floor at Y=0', () => {
    const worldComp = convertCompartmentToWorld(compartment);

    expect(worldComp.size[0]).toBeCloseTo(12.0); // 12m length (X)
    expect(worldComp.size[1]).toBeCloseTo(2.6);  // 2.6m height (Y)
    expect(worldComp.size[2]).toBeCloseTo(2.4);  // 2.4m width (Z)

    // Center is at [0, height/2, 0] so floor rests on y=0
    expect(worldComp.center[0]).toBe(0);
    expect(worldComp.center[1]).toBeCloseTo(1.3);
    expect(worldComp.center[2]).toBe(0);
  });

  it('correctly maps placement origin (0, 0, 0) to front-left bottom of trailer interior', () => {
    const originPlacement: ItemPlacementDto = {
      placementId: 'p-origin',
      cargoItemId: 'ITEM-01',
      instanceIndex: 0,
      hazardClass: '3',
      unNumber: 'UN1993',
      xMm: 0,
      yMm: 0,
      zMm: 0,
      lengthMm: 1200,
      widthMm: 1000,
      heightMm: 1000,
      weightKg: 500
    };

    const box = convertPlacementToWorld(originPlacement, compartment);

    // Box dimensions in meters
    expect(box.size[0]).toBeCloseTo(1.2);
    expect(box.size[1]).toBeCloseTo(1.0);
    expect(box.size[2]).toBeCloseTo(1.0);

    // Bounding minimums must match compartment boundary edges:
    // minX = 0 - 12/2 = -6.0
    // minY = 0 (resting on floor)
    // minZ = 0 - 2.4/2 = -1.2
    expect(box.min[0]).toBeCloseTo(-6.0);
    expect(box.min[1]).toBeCloseTo(0.0);
    expect(box.min[2]).toBeCloseTo(-1.2);

    // Center
    expect(box.center[0]).toBeCloseTo(-6.0 + 0.6); // -5.4
    expect(box.center[1]).toBeCloseTo(0.5);
    expect(box.center[2]).toBeCloseTo(-1.2 + 0.5); // -0.7
  });

  it('correctly calculates vertical elevation for stacked items (zMm > 0)', () => {
    const stackedPlacement: ItemPlacementDto = {
      placementId: 'p-stacked',
      cargoItemId: 'ITEM-STACKED',
      instanceIndex: 1,
      hazardClass: '8',
      unNumber: 'UN1789',
      xMm: 0,
      yMm: 0,
      zMm: 1000, // stacked on top of a 1000mm unit
      lengthMm: 1200,
      widthMm: 1000,
      heightMm: 800,
      weightKg: 300
    };

    const box = convertPlacementToWorld(stackedPlacement, compartment);

    // Floor of stacked item is at y = 1.0 meter
    expect(box.min[1]).toBeCloseTo(1.0);
    // Center is at 1.0 + 0.4 = 1.4 meters
    expect(box.center[1]).toBeCloseTo(1.4);
    // Top of stacked item is at 1.8 meters
    expect(box.max[1]).toBeCloseTo(1.8);
  });

  it('retains rotated dimension mapping under ROTATE_YAW_90', () => {
    // When rotated 90 degrees, length and width are swapped in placement by solver
    const rotatedPlacement: ItemPlacementDto = {
      placementId: 'p-rotated',
      cargoItemId: 'ITEM-ROTATED',
      instanceIndex: 0,
      hazardClass: '4.1',
      unNumber: 'UN1325',
      xMm: 2000,
      yMm: 1000,
      zMm: 0,
      lengthMm: 800,  // rotated width
      widthMm: 1200,  // rotated length
      heightMm: 1000,
      weightKg: 400,
      orientation: 'ROTATE_YAW_90'
    };

    const box = convertPlacementToWorld(rotatedPlacement, compartment);

    expect(box.size[0]).toBeCloseTo(0.8);
    expect(box.size[2]).toBeCloseTo(1.2);
    expect(box.size[1]).toBeCloseTo(1.0);
    expect(box.orientation).toBe('ROTATE_YAW_90');
  });

  it('provides distinct colors for safety classifications and highlights violations', () => {
    const normalCls1 = getHazardColor('1', false, false);
    const violationCls1 = getHazardColor('1', false, true);
    const selectedCls1 = getHazardColor('1', true, false);

    expect(normalCls1.color).toBe('#dc2626');
    expect(violationCls1.color).toBe('#ef4444');
    expect(selectedCls1.color).toBe('#38bdf8');
  });
});
