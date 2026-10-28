import { ItemPlacementDto, CompartmentDto } from '../../types/api';

/**
 * MM_TO_METERS scale factor: 1 meter = 1000 mm.
 * Using 1 meter per unit in Three.js aligns with standard Three.js lighting, camera clipping, and physics scale.
 */
export const MM_TO_METERS = 0.001;

export interface RenderableBoxGeometry {
  placementId: string;
  cargoItemId: string;
  instanceIndex: number;
  hazardClass: string;
  unNumber: string;
  weightKg: number;
  stackable: boolean;
  maxStackWeightKg: number | null;
  orientation: string;
  // Dimensions in Three.js meters
  size: [number, number, number]; // [length (x), height (y), width (z)]
  // Center position in Three.js meters
  center: [number, number, number]; // [centerX, centerY, centerZ]
  // Bounding minimums in Three.js meters
  min: [number, number, number]; // [xMin, yMin, zMin]
  max: [number, number, number]; // [xMax, yMax, zMax]
  // Original mm coordinates for display
  rawMm: {
    x: number;
    y: number;
    z: number;
    length: number;
    width: number;
    height: number;
  };
}

export interface RenderableCompartment {
  compartmentId: string;
  name: string;
  // Size in Three.js meters: [length (x), height (y), width (z)]
  size: [number, number, number];
  // Center in Three.js meters: positioned so floor is at y=0, centered at x=0, z=0
  center: [number, number, number];
  rawDimensionsMm: {
    length: number;
    width: number;
    height: number;
    maxPayloadKg: number;
  };
}

/**
 * Converts backend CompartmentDto into Three.js centered scene coordinates.
 * Coordinates are centered at X=0 and Z=0 on the horizontal ground plane, with floor at Y=0.
 */
export function convertCompartmentToWorld(compartment: CompartmentDto): RenderableCompartment {
  const l = (compartment.lengthMm || 12000) * MM_TO_METERS;
  const w = (compartment.widthMm || 2400) * MM_TO_METERS;
  const h = (compartment.heightMm || 2600) * MM_TO_METERS;

  return {
    compartmentId: compartment.compartmentId || 'comp-default',
    name: compartment.name || 'Standard Trailer Compartment',
    size: [l, h, w],
    center: [0, h / 2, 0],
    rawDimensionsMm: {
      length: compartment.lengthMm || 12000,
      width: compartment.widthMm || 2400,
      height: compartment.heightMm || 2600,
      maxPayloadKg: compartment.maxPayloadKg || 25000
    }
  };
}

/**
 * Converts a backend ItemPlacementDto into Three.js box geometry.
 * 
 * Mapping from backend Cartesian frame to Three.js centered coordinates:
 * - Backend Compartment:
 *     x in [0, compLengthMm] (longitudinal)
 *     y in [0, compWidthMm]  (lateral across floor)
 *     z in [0, compHeightMm] (vertical elevation from floor)
 * 
 * - Three.js World Frame:
 *     X axis: Longitudinal axis, centered at 0: Three_X = (xMm * MM_TO_METERS) - (compLengthM / 2) + (placedLengthM / 2)
 *     Y axis: Vertical elevation, floor at Y=0: Three_Y = (zMm * MM_TO_METERS) + (placedHeightM / 2)
 *     Z axis: Lateral axis, centered at 0:     Three_Z = (yMm * MM_TO_METERS) - (compWidthM / 2) + (placedWidthM / 2)
 */
export function convertPlacementToWorld(
  placement: ItemPlacementDto,
  compartment: CompartmentDto
): RenderableBoxGeometry {
  const compLM = (compartment.lengthMm || 12000) * MM_TO_METERS;
  const compWM = (compartment.widthMm || 2400) * MM_TO_METERS;

  const xMm = placement.xMm || 0;
  const yMm = placement.yMm || 0;
  const zMm = placement.zMm || 0;
  const lenMm = placement.lengthMm || 1000;
  const widMm = placement.widthMm || 1000;
  const heiMm = placement.heightMm || 1000;

  const placedLM = lenMm * MM_TO_METERS;
  const placedWM = widMm * MM_TO_METERS;
  const placedHM = heiMm * MM_TO_METERS;

  const centerX = (xMm * MM_TO_METERS) - (compLM / 2) + (placedLM / 2);
  const centerY = (zMm * MM_TO_METERS) + (placedHM / 2);
  const centerZ = (yMm * MM_TO_METERS) - (compWM / 2) + (placedWM / 2);

  const minX = centerX - placedLM / 2;
  const maxX = centerX + placedLM / 2;
  const minY = centerY - placedHM / 2;
  const maxY = centerY + placedHM / 2;
  const minZ = centerZ - placedWM / 2;
  const maxZ = centerZ + placedWM / 2;

  return {
    placementId: placement.placementId || `${placement.cargoItemId}-${placement.instanceIndex}`,
    cargoItemId: placement.cargoItemId,
    instanceIndex: placement.instanceIndex,
    hazardClass: placement.hazardClass,
    unNumber: placement.unNumber,
    weightKg: placement.weightKg,
    stackable: Boolean(placement.stackable),
    maxStackWeightKg: placement.maxStackWeightKg !== undefined ? placement.maxStackWeightKg : null,
    orientation: placement.orientation || 'DEFAULT',
    size: [placedLM, placedHM, placedWM],
    center: [centerX, centerY, centerZ],
    min: [minX, minY, minZ],
    max: [maxX, maxY, maxZ],
    rawMm: {
      x: xMm,
      y: yMm,
      z: zMm,
      length: lenMm,
      width: widMm,
      height: heiMm
    }
  };
}

/**
 * Returns distinct, accessible HSL/Hex hazard class colors for visual grouping.
 */
export function getHazardColor(hazardClass: string, isHighlighted: boolean = false, isViolation: boolean = false): {
  color: string;
  edgeColor: string;
} {
  if (isViolation) {
    return {
      color: '#ef4444',
      edgeColor: '#fee2e2'
    };
  }
  if (isHighlighted) {
    return {
      color: '#38bdf8',
      edgeColor: '#ffffff'
    };
  }

  switch (hazardClass) {
    case '1':
      return { color: '#dc2626', edgeColor: '#fca5a5' }; // Explosives (Red)
    case '2':
    case '2.1':
    case '2.2':
    case '2.3':
      return { color: '#059669', edgeColor: '#6ee7b7' }; // Gases (Green)
    case '3':
      return { color: '#d97706', edgeColor: '#fde68a' }; // Flammable Liquids (Amber/Orange)
    case '4':
    case '4.1':
    case '4.2':
    case '4.3':
      return { color: '#ea580c', edgeColor: '#fed7aa' }; // Flammable Solids (Orange)
    case '5':
    case '5.1':
    case '5.2':
      return { color: '#eab308', edgeColor: '#fef08a' }; // Oxidizers (Yellow)
    case '6':
    case '6.1':
    case '6.2':
      return { color: '#9333ea', edgeColor: '#d8b4fe' }; // Toxic / Infectious (Purple)
    case '7':
      return { color: '#6366f1', edgeColor: '#c7d2fe' }; // Radioactive (Indigo)
    case '8':
      return { color: '#475569', edgeColor: '#cbd5e1' }; // Corrosive (Slate)
    case '9':
      return { color: '#0284c7', edgeColor: '#bae6fd' }; // Miscellaneous (Cyan/Blue)
    default:
      return { color: '#3b82f6', edgeColor: '#bfdbfe' }; // Standard general cargo
  }
}
