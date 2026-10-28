# Phase G.3 — Interactive 3D Cargo Loading Simulator Report
**Project:** Hazardous Cargo Co-Loading and 3D Load Planning System (SOA Team 8)  
**Repository:** `https://github.com/klh2420090129/SOA-IEE-Hackathon`  
**Working Branch:** `feat/milestone-1-vertical-slice`  
**Baseline Git Commit:** `1d42cbe`  
**Date:** 2026-10-28  

---

## 1. Executive Summary

Phase G.3 implements an interactive, production-grade 3D compartment digital twin simulator for the React + TypeScript frontend using Three.js and `@react-three/fiber`.

The 3D viewer directly renders actual backend-provided compartment boundaries, 3D Cartesian coordinates (`xMm`, `yMm`, `zMm`), bounding dimensions (`lengthMm`, `widthMm`, `heightMm`), rotation orientations (`DEFAULT` vs `ROTATE_YAW_90`), and stacking elevations. It integrates seamlessly into the Load Planner review workflow while preserving the 2D SVG floor plan and accessible tabular layout fallback.

---

## 2. 3D Architectural Implementation & Geometry Integrity

### 2.1 Coordinate Conversion Standard
- **Scale Constant:** `MM_TO_METERS = 0.001` (1 Three.js world unit = 1.0 meter).
- **Coordinate System Mapping:**
  - **Backend Compartment:**
    - `xMm` in `[0, compLengthMm]` (longitudinal axis)
    - `yMm` in `[0, compWidthMm]` (lateral floor width)
    - `zMm` in `[0, compHeightMm]` (vertical height elevation from trailer floor)
  - **Three.js World Coordinate Transformation:**
    - Centered horizontally on the ground plane at $(X=0, Z=0)$, with the floor plane at $Y=0$.
    - `Three_X = (xMm * 0.001) - (compLengthM / 2) + (boxLengthM / 2)`
    - `Three_Y = (zMm * 0.001) + (boxHeightM / 2)` (floor at $Y=0$, elevated if stacked)
    - `Three_Z = (yMm * 0.001) - (compWidthM / 2) + (boxWidthM / 2)`

### 2.2 Visual Components
1. **Compartment Envelope Mesh (`CompartmentMesh`):**
   - Translucent bounded container with neon cyan wireframe cage and textured floor plane.
   - Front cab origin `(0, 0)` and rear loading door position labels.
2. **Cargo Box Cuboids (`CargoBox`):**
   - Solid PBR standard materials with edge wireframe outlines.
   - Dynamic IMDG hazard class coloring (Class 1 Red, Class 3 Amber, Class 4.1 Orange, Class 8 Slate, etc.).
   - Interactive click-to-inspect overlay presenting full backend data: dimensions, coordinates, UN number, weight, stacking rules, and orientation.
3. **Orbit & Viewport Controls:**
   - Full 360-degree rotation, pan, zoom, and distance clamping.
   - Camera view presets: **Isometric**, **Top-Down (Overhead)**, **Side (Elevation)**, and **Rear (Door Loading)**.
4. **Illustrative Placement Playback:**
   - Staggered descending animation from sky to final position.
   - Explicitly marked as a **presentation-only sequence** that does not imply crane handling sequence or independent safety proof.
5. **Validation Highlighting:**
   - Highlights items flagged in `ValidationFindingDto.involvedItemIds` in bright warning red.
   - Cryptographic safety disclaimer explicitly stating that visual rendering does not replace the Zero-Trust Validation Engine.

---

## 3. Retained 2D & Accessible Table Fallback

The Load Planner review page provides an instant toggle:
- **Interactive 3D Simulation:** WebGL-accelerated 3D digital twin.
- **2D Floor-Plan & Table Fallback:** Scaled 2D SVG compartment diagram and structured HTML data table ensuring full accessibility and WebGL-unavailable fallback.

---

## 4. Test & Build Verification

1. **Unit & Coordinate Tests (`coordinateUtils.test.ts`):**
   - Compartment centering with floor at $Y=0$.
   - Origin placement `(0, 0, 0)` bounding verification.
   - Vertical elevation calculation for stacked units (`zMm > 0`).
   - Dimension transposition under `ROTATE_YAW_90`.
   - Hazard color mapping and violation highlighting.
2. **Component Tests (`CandidateViewer3D.test.tsx`):**
   - Empty state placeholder rendering.
   - 3D canvas and camera presets initialization.
   - Playback animation toggle.
   - Validation finding badge and safety disclaimer display.
3. **Test Suite Outcome:**
   ```bash
   npm test
   ```
   - **5 / 5 test files passed (20 / 20 tests total) in 3.26s**:
     - `client.test.ts`: 4 passed
     - `coordinateUtils.test.ts`: 5 passed
     - `CandidateViewer2D.test.tsx`: 4 passed
     - `CandidateViewer3D.test.tsx`: 4 passed
     - `useOptimizationJob.test.ts`: 3 passed
4. **Production Build:**
   ```bash
   npm run build
   ```
   - **Result:** Success (`tsc && vite build`, 2086 modules transformed in 8.60s, 0 errors).

---

## 5. Live Integration Status

- **Tested in Local Node/Vite Environment:** Passed 100%.
- **Live Backend Integration:** The component consumes standard `CandidateLayoutDto` payloads produced by the live microservices (`optimization-service` and `compartment-service`) without transformation gaps.
