import React, { useRef, useState, useMemo } from 'react';
import { Canvas } from '@react-three/fiber';
import { OrbitControls, Grid, Text } from '@react-three/drei';
import * as THREE from 'three';
import { 
  CandidateLayoutDto, 
  CompartmentDto, 
  ValidationResultDto
} from '../../types/api';
import { 
  convertCompartmentToWorld, 
  convertPlacementToWorld, 
  getHazardColor,
  RenderableBoxGeometry,
  RenderableCompartment
} from './coordinateUtils';
import { 
  Box, 
  RotateCcw, 
  Play, 
  Pause, 
  ShieldAlert, 
  CheckCircle2, 
  XCircle
} from 'lucide-react';

interface CandidateViewer3DProps {
  compartment: CompartmentDto;
  layout: CandidateLayoutDto | null;
  validation: ValidationResultDto | null;
  isApproved?: boolean;
  isRejected?: boolean;
  onSelectBox?: (box: RenderableBoxGeometry | null) => void;
}

// Subcomponent: Trailer Compartment Wireframe & Floor Mesh
const CompartmentMesh: React.FC<{ comp: RenderableCompartment }> = ({ comp }) => {
  const [l, h, w] = comp.size;
  const [cx, cy, cz] = comp.center;

  return (
    <group position={[cx, cy, cz]}>
      {/* Semi-transparent bounding cage */}
      <mesh>
        <boxGeometry args={[l, h, w]} />
        <meshStandardMaterial
          color="#1e293b"
          transparent
          opacity={0.12}
          depthWrite={false}
          side={THREE.DoubleSide}
        />
      </mesh>

      {/* Outer bounding line cage */}
      <lineSegments>
        <edgesGeometry args={[new THREE.BoxGeometry(l, h, w)]} />
        <lineBasicMaterial color="#38bdf8" linewidth={2} />
      </lineSegments>

      {/* Cargo trailer floor mesh */}
      <mesh position={[0, -h / 2 + 0.005, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <planeGeometry args={[l, w]} />
        <meshStandardMaterial color="#0f172a" roughness={0.8} />
      </mesh>

      {/* Coordinate axis labels inside trailer */}
      <Text
        position={[-l / 2 + 0.5, -h / 2 + 0.1, -w / 2 + 0.2]}
        rotation={[-Math.PI / 2, 0, 0]}
        fontSize={0.25}
        color="#64748b"
      >
        Origin (0,0) - Cab/Front
      </Text>
      <Text
        position={[l / 2 - 0.5, -h / 2 + 0.1, -w / 2 + 0.2]}
        rotation={[-Math.PI / 2, 0, 0]}
        fontSize={0.25}
        color="#64748b"
      >
        Rear Doors ({comp.rawDimensionsMm.length}mm)
      </Text>
    </group>
  );
};

// Subcomponent: Individual 3D Cargo Box
const CargoBox: React.FC<{
  box: RenderableBoxGeometry;
  isSelected: boolean;
  isViolation: boolean;
  animationProgress: number; // 0 to 1
  boxIndex: number;
  totalBoxes: number;
  onClick: () => void;
}> = ({ box, isSelected, isViolation, animationProgress, boxIndex, totalBoxes, onClick }) => {
  const [sizeX, sizeY, sizeZ] = box.size;
  const [targetX, targetY, targetZ] = box.center;

  // Staggered sequential loading animation:
  // Each box starts descending from sky (+5m) when its fractional time window arrives
  const boxStartFraction = totalBoxes > 1 ? boxIndex / totalBoxes : 0;
  const boxEndFraction = totalBoxes > 1 ? (boxIndex + 1) / totalBoxes : 1;

  let currentY = targetY;
  let currentOpacity = 0.9;

  if (animationProgress < 1.0) {
    if (animationProgress <= boxStartFraction) {
      // Not yet placed
      currentY = targetY + 4.0;
      currentOpacity = 0.0;
    } else if (animationProgress >= boxEndFraction) {
      // Fully landed
      currentY = targetY;
      currentOpacity = 0.9;
    } else {
      // In descent
      const localT = (animationProgress - boxStartFraction) / (boxEndFraction - boxStartFraction);
      currentY = targetY + (1 - localT) * 4.0;
      currentOpacity = 0.3 + localT * 0.6;
    }
  }

  const { color, edgeColor } = getHazardColor(box.hazardClass, isSelected, isViolation);

  if (currentOpacity <= 0.05) {
    return null;
  }

  return (
    <group position={[targetX, currentY, targetZ]} onClick={(e) => { e.stopPropagation(); onClick(); }}>
      {/* Solid Box Mesh */}
      <mesh castShadow receiveShadow>
        <boxGeometry args={[sizeX, sizeY, sizeZ]} />
        <meshStandardMaterial
          color={color}
          roughness={0.4}
          metalness={0.1}
          transparent={currentOpacity < 0.9}
          opacity={currentOpacity}
        />
      </mesh>

      {/* Wireframe Outline */}
      <lineSegments>
        <edgesGeometry args={[new THREE.BoxGeometry(sizeX, sizeY, sizeZ)]} />
        <lineBasicMaterial color={edgeColor} linewidth={isSelected || isViolation ? 3 : 1} />
      </lineSegments>

      {/* Label on top of the box */}
      <Text
        position={[0, sizeY / 2 + 0.02, 0]}
        rotation={[-Math.PI / 2, 0, 0]}
        fontSize={Math.min(sizeX, sizeZ) * 0.22}
        color="#ffffff"
        anchorX="center"
        anchorY="middle"
      >
        {`${box.cargoItemId}\nCls ${box.hazardClass}`}
      </Text>
    </group>
  );
};

export const CandidateViewer3D: React.FC<CandidateViewer3DProps> = ({
  compartment,
  layout,
  validation,
  isApproved = false,
  isRejected = false,
  onSelectBox
}) => {
  const [selectedBox, setSelectedBox] = useState<RenderableBoxGeometry | null>(null);

  // Animation controls
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackProgress, setPlaybackProgress] = useState<number>(1.0);
  const animationFrameRef = useRef<number | null>(null);

  // Camera preset controls
  const controlsRef = useRef<any>(null);

  // Pre-calculate 3D renderable objects
  const worldCompartment = useMemo(() => convertCompartmentToWorld(compartment), [compartment]);

  const renderableBoxes = useMemo(() => {
    if (!layout || !layout.placements) return [];
    return layout.placements.map(p => convertPlacementToWorld(p, compartment));
  }, [layout, compartment]);

  // Set of cargo item IDs with validation findings
  const violationItemIds = useMemo(() => {
    const ids = new Set<string>();
    if (!validation || !validation.findings) return ids;
    for (const f of validation.findings) {
      if (f.involvedItemIds) {
        f.involvedItemIds.forEach(id => ids.add(id));
      }
    }
    return ids;
  }, [validation]);

  const handleSelectBox = (box: RenderableBoxGeometry | null) => {
    setSelectedBox(box);
    if (onSelectBox) onSelectBox(box);
  };

  // Camera Presets
  const setCameraPreset = (preset: 'isometric' | 'top' | 'side' | 'rear') => {
    if (!controlsRef.current) return;
    const controls = controlsRef.current;
    switch (preset) {
      case 'isometric':
        controls.object.position.set(10, 8, 9);
        break;
      case 'top':
        controls.object.position.set(0, 16, 0.01);
        break;
      case 'side':
        controls.object.position.set(0, 3, 10);
        break;
      case 'rear':
        controls.object.position.set(11, 2.5, 0);
        break;
    }
    controls.target.set(0, 1.3, 0);
    controls.update();
  };

  // Playback Loop
  const togglePlay = () => {
    if (isPlaying) {
      setIsPlaying(false);
      if (animationFrameRef.current) cancelAnimationFrame(animationFrameRef.current);
    } else {
      setIsPlaying(true);
      if (playbackProgress >= 1.0) setPlaybackProgress(0.0);
      animateLoop();
    }
  };

  const animateLoop = () => {
    setPlaybackProgress(prev => {
      if (prev >= 1.0) {
        setIsPlaying(false);
        return 1.0;
      }
      return Math.min(prev + 0.008, 1.0);
    });
    animationFrameRef.current = requestAnimationFrame(animateLoop);
  };

  if (!layout) {
    return (
      <div style={{
        background: 'rgba(15, 23, 42, 0.4)',
        border: '1px dashed var(--border-color)',
        borderRadius: '8px',
        padding: '56px 24px',
        textAlign: 'center',
        color: 'var(--text-muted)'
      }}>
        <Box size={44} style={{ margin: '0 auto 12px', opacity: 0.4 }} />
        <p style={{ margin: 0, fontSize: '0.95rem' }}>
          No candidate layout available. Run optimization from an assessment to view 3D cargo simulation.
        </p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* 3D Visualizer Top Action Bar */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        background: 'rgba(15, 23, 42, 0.85)',
        padding: '10px 16px',
        borderRadius: '8px',
        border: '1px solid var(--border-color)',
        flexWrap: 'wrap',
        gap: '10px'
      }}>
        {/* Status Indicators */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Status:</span>
            <span className={`badge badge-${layout.solverStatus === 'OPTIMAL' || layout.solverStatus === 'FEASIBLE' ? 'approved' : 'validation-failed'}`}>
              {layout.solverStatus} ({layout.provenance})
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Independent 3D Validation:</span>
            {validation ? (
              <span className={`badge badge-${validation.passed ? 'approved' : 'validation-failed'}`}>
                {validation.passed ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                {validation.passed ? 'VERIFIED PASSED' : `VIOLATION DETECTED (${validation.findings.length})`}
              </span>
            ) : (
              <span className="badge badge-draft">PENDING AUDIT</span>
            )}
          </div>

          <div>
            {isApproved ? (
              <span className="badge badge-approved" style={{ fontWeight: 700 }}>
                DECISION: APPROVED
              </span>
            ) : isRejected ? (
              <span className="badge badge-validation-failed" style={{ fontWeight: 700 }}>
                DECISION: REJECTED (HOLD)
              </span>
            ) : (
              <span className="badge badge-review-required">
                PENDING REVIEW
              </span>
            )}
          </div>
        </div>

        {/* Camera Views & Playback */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <div style={{ display: 'flex', gap: '4px', background: 'rgba(0,0,0,0.3)', padding: '2px', borderRadius: '6px' }}>
            <button
              onClick={() => setCameraPreset('isometric')}
              title="Isometric 3D View"
              style={{ padding: '4px 8px', fontSize: '0.75rem', borderRadius: '4px', background: 'transparent', color: '#cbd5e1', border: 'none' }}
            >
              Isometric
            </button>
            <button
              onClick={() => setCameraPreset('top')}
              title="Top-Down Overhead View"
              style={{ padding: '4px 8px', fontSize: '0.75rem', borderRadius: '4px', background: 'transparent', color: '#cbd5e1', border: 'none' }}
            >
              Top-Down
            </button>
            <button
              onClick={() => setCameraPreset('side')}
              title="Side Elevation View"
              style={{ padding: '4px 8px', fontSize: '0.75rem', borderRadius: '4px', background: 'transparent', color: '#cbd5e1', border: 'none' }}
            >
              Side
            </button>
            <button
              onClick={() => setCameraPreset('rear')}
              title="Rear Door Loading View"
              style={{ padding: '4px 8px', fontSize: '0.75rem', borderRadius: '4px', background: 'transparent', color: '#cbd5e1', border: 'none' }}
            >
              Rear
            </button>
          </div>

          {/* Illustrative Placement Sequence Playback */}
          <button
            onClick={togglePlay}
            title="Illustrative Loading Animation"
            style={{
              padding: '6px 10px',
              background: isPlaying ? 'rgba(239, 68, 68, 0.2)' : 'rgba(59, 130, 246, 0.2)',
              color: isPlaying ? '#f87171' : '#60a5fa',
              border: '1px solid var(--border-color)',
              borderRadius: '6px',
              fontSize: '0.75rem',
              display: 'flex',
              alignItems: 'center',
              gap: '6px'
            }}
          >
            {isPlaying ? <Pause size={12} /> : <Play size={12} />}
            {isPlaying ? 'Pause' : 'Play Sequence'}
          </button>

          <button
            onClick={() => { setIsPlaying(false); setPlaybackProgress(1.0); }}
            title="Reset to Full Layout"
            style={{
              padding: '6px',
              background: 'transparent',
              color: 'var(--text-muted)',
              border: '1px solid var(--border-color)',
              borderRadius: '6px'
            }}
          >
            <RotateCcw size={14} />
          </button>
        </div>
      </div>

      {/* Main 3D Canvas Viewport */}
      <div style={{
        width: '100%',
        height: '460px',
        background: 'radial-gradient(circle at 50% 30%, #0f172a 0%, #030712 100%)',
        border: '1px solid var(--border-color)',
        borderRadius: '8px',
        position: 'relative',
        overflow: 'hidden'
      }}>
        <Canvas
          camera={{ position: [10, 8, 9], fov: 45 }}
          shadows
          style={{ width: '100%', height: '100%' }}
          onPointerDown={() => handleSelectBox(null)}
        >
          <ambientLight intensity={0.8} />
          <directionalLight position={[12, 18, 10]} intensity={1.2} castShadow />
          <directionalLight position={[-10, 10, -10]} intensity={0.4} />

          {/* Reference Ground Grid */}
          <Grid
            position={[0, -0.01, 0]}
            args={[24, 24]}
            cellSize={1}
            cellThickness={0.6}
            cellColor="#1e293b"
            sectionSize={4}
            sectionThickness={1.2}
            sectionColor="#334155"
            fadeDistance={30}
          />

          {/* Compartment Bounding Mesh */}
          <CompartmentMesh comp={worldCompartment} />

          {/* 3D Placed Cargo Cuboids */}
          {renderableBoxes.map((box, idx) => (
            <CargoBox
              key={box.placementId || idx}
              box={box}
              boxIndex={idx}
              totalBoxes={renderableBoxes.length}
              animationProgress={playbackProgress}
              isSelected={selectedBox?.placementId === box.placementId}
              isViolation={violationItemIds.has(box.cargoItemId)}
              onClick={() => handleSelectBox(box)}
            />
          ))}

          <OrbitControls
            ref={controlsRef}
            target={[0, 1.3, 0]}
            maxPolarAngle={Math.PI / 2 + 0.05}
            minDistance={2}
            maxDistance={35}
            makeDefault
          />
        </Canvas>

        {/* Notice on Animation Scope */}
        {isPlaying && (
          <div style={{
            position: 'absolute',
            bottom: 12,
            left: 12,
            background: 'rgba(15, 23, 42, 0.85)',
            border: '1px solid var(--border-color)',
            padding: '6px 12px',
            borderRadius: '6px',
            fontSize: '0.75rem',
            color: '#94a3b8'
          }}>
            Illustrative placement visualization &bull; Sequence is presentation-only and does not imply operational crane sequence.
          </div>
        )}

        {/* Selected Cargo Overlay Card */}
        {selectedBox && (
          <div style={{
            position: 'absolute',
            top: 14,
            right: 14,
            width: '280px',
            background: 'rgba(15, 23, 42, 0.95)',
            border: '1px solid #38bdf8',
            borderRadius: '8px',
            padding: '14px',
            backdropFilter: 'blur(10px)',
            boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
            fontSize: '0.85rem'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
              <span style={{ fontWeight: 700, color: '#38bdf8' }}>{selectedBox.cargoItemId}</span>
              <span className="badge badge-draft">Unit {selectedBox.instanceIndex + 1}</span>
            </div>
            <div style={{ color: 'var(--text-muted)', fontSize: '0.75rem', marginBottom: '8px' }}>
              Hazard Class: <strong>{selectedBox.hazardClass}</strong> ({selectedBox.unNumber}) &bull; Weight: {selectedBox.weightKg}kg
            </div>
            <div style={{ fontSize: '0.75rem', lineHeight: 1.5, background: 'rgba(0,0,0,0.3)', padding: '8px', borderRadius: '4px' }}>
              <div>Coordinates: <code>x={selectedBox.rawMm.x} y={selectedBox.rawMm.y} z={selectedBox.rawMm.z} mm</code></div>
              <div>Dimensions: <code>{selectedBox.rawMm.length}&times;{selectedBox.rawMm.width}&times;{selectedBox.rawMm.height} mm</code></div>
              <div>Orientation: <code>{selectedBox.orientation}</code></div>
              <div>Stackable: <code>{selectedBox.stackable ? `Yes (Max: ${selectedBox.maxStackWeightKg || 'N/A'}kg)` : 'No'}</code></div>
            </div>
            <button
              onClick={() => handleSelectBox(null)}
              style={{
                marginTop: '10px',
                width: '100%',
                background: 'transparent',
                border: '1px solid var(--border-color)',
                color: 'var(--text-muted)',
                borderRadius: '4px',
                padding: '4px',
                fontSize: '0.75rem'
              }}
            >
              Close Details
            </button>
          </div>
        )}
      </div>

      {/* Safety & Audit Disclaimer Ribbon */}
      <div style={{
        background: 'rgba(15, 23, 42, 0.6)',
        border: '1px solid var(--border-color)',
        borderRadius: '6px',
        padding: '10px 14px',
        fontSize: '0.78rem',
        color: 'var(--text-muted)',
        display: 'flex',
        alignItems: 'center',
        gap: '8px'
      }}>
        <ShieldAlert size={16} style={{ color: 'var(--accent-secondary)' }} />
        <span>
          <strong>Cryptographic Safety Notice:</strong> 3D geometry rendering is for visual inspection and spatial comprehension only. Authoritative co-loading safety is established exclusively by the independent Zero-Trust Validation Engine and Load Planner sign-off against statutory CMV/IMDG rule snapshot <code>{compartment.compartmentId}</code>.
        </span>
      </div>
    </div>
  );
};
