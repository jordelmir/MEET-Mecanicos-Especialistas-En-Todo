import React, { useState, useMemo, useEffect, useRef } from 'react';
import { 
  VehicleProfile, 
  Component3D, 
  ComponentStatus, 
  LocationConfidence, 
  DtcComponentMap, 
  FuseRelayBox, 
  FuseRelaySlot, 
  WiringCircuit, 
  CircuitNode, 
  CircuitEdge, 
  ComponentTest, 
  ComponentMeasurement, 
  MeasurementType,
  Role
} from '../types';
import {
  DetailedPart,
  GuidedRepairProcedure,
  RepairStep3D
} from '../types';
import { 
  DTC_COMPONENT_MAPS, 
  COMPONENT_TESTS, 
  evaluateMeasurement, 
  recalculateCausalProbabilities, 
  getWiringCircuitTemplate, 
  getFuseRelayBoxTemplate 
} from '../services/visualDiagnosticsEngine';
import {
  SOURCE_BACKED_PARTS_CATALOG as SUSPENSION_PARTS_CATALOG,
  SOURCE_BACKED_REPAIR_PROCEDURES as GUIDED_REPAIR_PROCEDURES
} from '../services/universalPartsCatalog';
import { 
  Activity, 
  Layers, 
  ShieldAlert, 
  Wrench, 
  ShoppingCart, 
  FileText, 
  Search, 
  HelpCircle, 
  Plus, 
  Info, 
  Check, 
  X, 
  Play, 
  Compass, 
  GitBranch, 
  Grid, 
  Zap, 
  Maximize2 
} from 'lucide-react';

interface VisualDiagnosticsViewProps {
  vehicle: VehicleProfile | null;
  activeDtc: string | null;
  onAddTimelineEvent: (ev: any) => void;
  workOrders: any[];
  onAddMaintenanceRecord: (rec: any) => void;
  onAddPredictiveAlert: (al: any) => void;
  onUpdateDigitalTwin: (dt: any) => void;
}

type TabMode = 'MOTOR' | 'FUSES' | 'WIRING' | 'TESTS' | 'PARTS_3D';

export function VisualDiagnosticsView({
  vehicle,
  activeDtc,
  onAddTimelineEvent,
  workOrders,
  onAddMaintenanceRecord,
  onAddPredictiveAlert,
  onUpdateDigitalTwin
}: VisualDiagnosticsViewProps) {
  const [tabMode, setTabMode] = useState<TabMode>('MOTOR');
  const [selectedCompKey, setSelectedCompKey] = useState<string | null>(null);
  const [qualityMode, setQualityMode] = useState<'HIGH_QUALITY' | 'BALANCED' | 'LOW_POWER' | '2D_FALLBACK'>('BALANCED');
  const [searchQuery, setSearchQuery] = useState('');
  
  // Toggles for layers
  const [layers, setLayers] = useState({
    mechanical: true,
    sensors: true,
    actuators: true,
    wiring: true,
    fuses: true,
    activeDtcs: true,
    testsPending: true
  });

  // State for logged measurements
  const [measurements, setMeasurements] = useState<ComponentMeasurement[]>([]);
  // Input form state for active test step
  const [measValue, setMeasValue] = useState<string>('');
  const [measNotes, setMeasNotes] = useState<string>('');
  const [activeTestStepIdx, setActiveTestStepIdx] = useState<number>(0);
  const [selectedTestId, setSelectedTestId] = useState<string | null>(null);

  // Fuse box box selection (engine vs cabin)
  const [activeFuseBoxLoc, setActiveFuseBoxLoc] = useState<'ENGINE_BAY' | 'UNDER_DASH'>('ENGINE_BAY');

  // 3D Canvas states
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [rotationX, setRotationX] = useState(0.4);
  const [rotationY, setRotationY] = useState(0.6);
  const [isDragging, setIsDragging] = useState(false);
  const dragStart = useRef({ x: 0, y: 0 });

  // Get active vehicle info
  const activeVehId = vehicle?.id || 'veh_generic';
  const isSpecificVerna = vehicle?.brand?.toLowerCase() === 'hyundai' && vehicle?.model?.toLowerCase()?.includes('accent');

  // Fallback to P0230 if no active DTC is passed
  const currentDtc = activeDtc || 'P0230';
  const mapData = useMemo(() => DTC_COMPONENT_MAPS[currentDtc] || DTC_COMPONENT_MAPS['P0230'], [currentDtc]);

  // Recalculated component probabilities based on measurements
  const componentDiagnostics = useMemo(() => {
    return recalculateCausalProbabilities(currentDtc, measurements);
  }, [currentDtc, measurements]);

  // Static components catalog for 3D motor representation
  const componentsList = useMemo<Component3D[]>(() => {
    const base: Component3D[] = [
      {
        id: 'engine_block',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'engine_block',
        name: 'Monobloc / Bloque Motor',
        system: 'Mecánico',
        subsystem: 'Cárter',
        description: 'Bloque de cilindros principal que contiene pistones y bielas.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: 0, position_y: 0, position_z: 0,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 2.2,
        location_confidence: 'EXACT',
        related_dtcs: ['P0300', 'P0301'],
        related_symptoms: ['Falta potencia', 'Vibración fuerte'],
        related_pids: ['RPM', 'LOAD'],
        related_tests: [], related_parts: ['Empaque de culata'],
        safety_notes: ['Asegurar refrigeración fría antes de desmontar.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'battery',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'battery',
        name: 'Batería (12V B+)',
        system: 'Eléctrico',
        subsystem: 'Carga/Arranque',
        description: 'Batería principal de plomo-ácido de arranque de 12V.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: -2.2, position_y: 0.8, position_z: -1.2,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 0.8,
        location_confidence: 'EXACT',
        related_dtcs: ['P0562', 'P0230'],
        related_symptoms: ['No arranca', 'Luces bajas'],
        related_pids: ['VPWR'],
        related_tests: ['test_battery_charging_voltage'], related_parts: ['Batería 12V L2'],
        safety_notes: ['¡Atención! Ácido corrosivo y riesgo de cortocircuito pesado.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'alternator',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'alternator',
        name: 'Alternador de Carga',
        system: 'Eléctrico',
        subsystem: 'Carga/Arranque',
        description: 'Generador trifásico con regulador de voltaje integrado.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: 1.6, position_y: 0.5, position_z: -0.6,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 0.7,
        location_confidence: 'EXACT',
        related_dtcs: ['P0562'],
        related_symptoms: ['Batería se descarga', 'Luz de batería encendida'],
        related_pids: ['VPWR'],
        related_tests: ['test_battery_charging_voltage'], related_parts: ['Alternador OEM'],
        safety_notes: ['Desconectar polo negativo de batería antes de intervenir.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'fuel_pump',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'fuel_pump',
        name: 'Bomba de Combustible',
        system: 'Combustible',
        subsystem: 'Alimentación',
        description: 'Bomba eléctrica sumergida de alta presión en el tanque de combustible.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: -3.8, position_y: -1.2, position_z: 2.8,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 0.9,
        location_confidence: 'EXACT',
        related_dtcs: ['P0230', 'P0171'],
        related_symptoms: ['Arranque prolongado', 'El motor tironea', 'No arranca'],
        related_pids: ['FRP', 'FLI'],
        related_tests: ['test_pump_voltage', 'test_pump_ground'], related_parts: ['Módulo Bomba Gasolina'],
        safety_notes: ['¡Riesgo extremo de incendio! Trabajar en áreas bien ventiladas sin fuentes de ignición.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'fuel_pump_relay',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'fuel_pump_relay',
        name: 'Relé de Bomba de Combustible',
        system: 'Eléctrico',
        subsystem: 'Fusibles/Relés',
        description: 'Relé electromecánico de 4 pines que activa la alimentación de la bomba.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: -2.0, position_y: 1.4, position_z: -1.0,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 0.4,
        location_confidence: 'EXACT',
        related_dtcs: ['P0230'],
        related_symptoms: ['No enciende el motor', 'Falta combustible'],
        related_pids: [],
        related_tests: ['test_relay_voltage', 'test_relay_control'], related_parts: ['Relé Automotriz 40A'],
        safety_notes: ['No utilizar jumpers improvisados en la caja de fusibles.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'fuel_pump_fuse',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'fuel_pump_fuse',
        name: 'Fusible Bomba (15A F2)',
        system: 'Eléctrico',
        subsystem: 'Fusibles/Relés',
        description: 'Fusible tipo mini de 15A que protege el circuito de fuerza de la bomba.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: -2.1, position_y: 1.4, position_z: -0.9,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 0.3,
        location_confidence: 'EXACT',
        related_dtcs: ['P0230'],
        related_symptoms: ['No arranca'],
        related_pids: [],
        related_tests: ['test_fuse_continuity'], related_parts: ['Kit Fusibles Mini'],
        safety_notes: ['Nunca reemplace por un fusible de mayor amperaje.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'pcm_driver',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'pcm_driver',
        name: 'Módulo de Control del Motor (PCM/ECU)',
        system: 'ECU/Módulos',
        subsystem: 'Control',
        description: 'Computadora principal del tren motriz que controla inyección y encendido.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: -1.6, position_y: 1.1, position_z: 0.5,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 1.0,
        location_confidence: 'EXACT',
        related_dtcs: ['P0230', 'P0340', 'P0335'],
        related_symptoms: ['Luz MIL encendida', 'Falla de comunicación CAN'],
        related_pids: ['VPWR', 'MIL'],
        related_tests: [], related_parts: ['PCM Reacondicionada'],
        safety_notes: ['Desconectar batería antes de retirar los conectores multipín.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'maf_sensor',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'maf_sensor',
        name: 'Sensor de Flujo de Masa de Aire (MAF)',
        system: 'Sensores',
        subsystem: 'Admisión',
        description: 'Sensor de alambre caliente que mide el volumen de aire que ingresa al motor.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: -0.8, position_y: 0.6, position_z: -0.6,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 0.5,
        location_confidence: 'HIGH',
        related_dtcs: ['P0101', 'P0171', 'P0172'],
        related_symptoms: ['Ralentí inestable', 'Humo negro', 'Consumo excesivo'],
        related_pids: ['MAF'],
        related_tests: [], related_parts: ['Sensor MAF OEM'],
        safety_notes: ['No tocar el filamento interno sensible con los dedos o herramientas.'],
        created_at: new Date().toISOString()
      },
      {
        id: 'ect_sensor',
        vehicle_3d_profile_id: 'prof_1',
        component_key: 'ect_sensor',
        name: 'Sensor de Temperatura del Refrigerante (ECT)',
        system: 'Sensores',
        subsystem: 'Enfriamiento',
        description: 'Termistor NTC instalado en la toma de agua del motor.',
        mesh_uri_nullable: null,
        icon_uri_nullable: null,
        position_x: 1.3, position_y: 0.2, position_z: -0.3,
        rotation_x: 0, rotation_y: 0, rotation_z: 0, scale: 0.4,
        location_confidence: 'HIGH',
        related_dtcs: ['P0115', 'P0128'],
        related_symptoms: ['Abanicos directos', 'ECT muy alta', 'Arranque difícil frío'],
        related_pids: ['ECT'],
        related_tests: [], related_parts: ['Sensor ECT'],
        safety_notes: ['Riesgo de escaldadura. Esperar a que el motor enfríe totalmente.'],
        created_at: new Date().toISOString()
      }
    ];
    return base;
  }, []);

  // Filter components based on query and layers
  const filteredComponents = useMemo(() => {
    return componentsList.filter(comp => {
      // Filter by search query
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        const matchesQuery = 
          comp.name.toLowerCase().includes(query) ||
          comp.component_key.toLowerCase().includes(query) ||
          comp.related_dtcs.some(d => d.toLowerCase().includes(query));
        if (!matchesQuery) return false;
      }

      // Filter by system layers
      if (comp.subsystem === 'Fusibles/Relés' && !layers.fuses) return false;
      if (comp.system === 'Sensores' && !layers.sensors) return false;
      if (comp.system === 'Actuadores' && !layers.actuators) return false;
      if (comp.system === 'Mecánico' && !layers.mechanical) return false;
      if (comp.system === 'Eléctrico' && !layers.wiring) return false;

      return true;
    });
  }, [componentsList, searchQuery, layers]);

  // Selected component details
  const activeComp = useMemo(() => {
    return componentsList.find(c => c.component_key === selectedCompKey) || null;
  }, [componentsList, selectedCompKey]);

  // Get active tests for selected component
  const activeTests = useMemo(() => {
    if (!selectedCompKey) return [];
    return COMPONENT_TESTS[selectedCompKey] || [];
  }, [selectedCompKey]);

  // 3D Canvas Projection Drawing Logic
  useEffect(() => {
    if (tabMode !== 'MOTOR' || !canvasRef.current) return;
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animFrameId: number;

    const render = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      
      // Draw Grid Floor (futuristic neon matrix)
      ctx.strokeStyle = 'rgba(6, 182, 212, 0.1)';
      ctx.lineWidth = 1;
      const gridSize = 10;
      const spacing = 40;
      
      for (let i = -gridSize; i <= gridSize; i++) {
        // Line X-parallel
        let p1 = project3D(i * spacing, -50, -gridSize * spacing);
        let p2 = project3D(i * spacing, -50, gridSize * spacing);
        ctx.beginPath();
        ctx.moveTo(p1.x, p1.y);
        ctx.lineTo(p2.x, p2.y);
        ctx.stroke();

        // Line Z-parallel
        let p3 = project3D(-gridSize * spacing, -50, i * spacing);
        let p4 = project3D(gridSize * spacing, -50, i * spacing);
        ctx.beginPath();
        ctx.moveTo(p3.x, p3.y);
        ctx.lineTo(p4.x, p4.y);
        ctx.stroke();
      }

      // Draw wireframe engine block block
      ctx.strokeStyle = 'rgba(148, 163, 184, 0.3)';
      ctx.lineWidth = 2;
      const blockWidth = 100;
      const blockHeight = 60;
      const blockDepth = 120;

      // Draw box wireframe
      const vertices = [
        { x: -blockWidth/2, y: -blockHeight/2, z: -blockDepth/2 },
        { x: blockWidth/2, y: -blockHeight/2, z: -blockDepth/2 },
        { x: blockWidth/2, y: blockHeight/2, z: -blockDepth/2 },
        { x: -blockWidth/2, y: blockHeight/2, z: -blockDepth/2 },
        { x: -blockWidth/2, y: -blockHeight/2, z: blockDepth/2 },
        { x: blockWidth/2, y: -blockHeight/2, z: blockDepth/2 },
        { x: blockWidth/2, y: blockHeight/2, z: blockDepth/2 },
        { x: -blockWidth/2, y: blockHeight/2, z: blockDepth/2 },
      ];

      const projectedVerts = vertices.map(v => project3D(v.x, v.y, v.z));
      const edges = [
        [0, 1], [1, 2], [2, 3], [3, 0], // front
        [4, 5], [5, 6], [6, 7], [7, 4], // back
        [0, 4], [1, 5], [2, 6], [3, 7]  // links
      ];

      ctx.beginPath();
      edges.forEach(([from, to]) => {
        ctx.moveTo(projectedVerts[from].x, projectedVerts[from].y);
        ctx.lineTo(projectedVerts[to].x, projectedVerts[to].y);
      });
      ctx.stroke();

      // Render Components as 3D holographic nodes
      filteredComponents.forEach(comp => {
        // Multiply position by scale for drawing
        const scaleFactor = 60;
        const projected = project3D(
          comp.position_x * scaleFactor,
          comp.position_y * scaleFactor,
          comp.position_z * scaleFactor
        );

        const isSelected = comp.component_key === selectedCompKey;
        const isRelatedToDtc = mapData.primary_components.includes(comp.component_key) || mapData.secondary_components.includes(comp.component_key);
        
        // Determine color based on health status/diagnostics
        let nodeColor = 'rgba(6, 182, 212, 0.7)'; // Cyan default
        let glowColor = 'rgba(6, 182, 212, 0.2)';
        
        const diag = componentDiagnostics[comp.component_key];
        if (diag) {
          if (diag.status === 'CONFIRMED_FAULT') {
            nodeColor = 'rgba(239, 68, 68, 0.9)'; // Red
            glowColor = 'rgba(239, 68, 68, 0.4)';
          } else if (diag.status === 'TEST_PASSED') {
            nodeColor = 'rgba(34, 197, 94, 0.9)'; // Green
            glowColor = 'rgba(34, 197, 94, 0.4)';
          } else if (diag.status === 'SUSPECT') {
            nodeColor = 'rgba(249, 115, 22, 0.9)'; // Orange
            glowColor = 'rgba(249, 115, 22, 0.4)';
          } else if (isRelatedToDtc) {
            nodeColor = 'rgba(234, 179, 8, 0.9)'; // Yellow
            glowColor = 'rgba(234, 179, 8, 0.4)';
          }
        }

        // Draw glow ring
        ctx.beginPath();
        ctx.arc(projected.x, projected.y, (12 + comp.scale * 4) * (isSelected ? 1.6 : 1), 0, Math.PI * 2);
        ctx.fillStyle = glowColor;
        ctx.fill();
        if (isSelected) {
          ctx.strokeStyle = '#22d3ee';
          ctx.lineWidth = 2;
          ctx.stroke();
        }

        // Draw solid node core
        ctx.beginPath();
        ctx.arc(projected.x, projected.y, 6 + comp.scale * 2, 0, Math.PI * 2);
        ctx.fillStyle = nodeColor;
        ctx.fill();

        // Draw labels
        ctx.fillStyle = isSelected ? '#ffffff' : '#94a3b8';
        ctx.font = isSelected ? 'bold 11px monospace' : '9px monospace';
        ctx.fillText(comp.name, projected.x + 14, projected.y + 4);
      });

      // Ambient HUD lines
      ctx.strokeStyle = 'rgba(6, 182, 212, 0.3)';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      // Target Reticle
      ctx.arc(canvas.width / 2, canvas.height / 2, 8, 0, Math.PI * 2);
      ctx.moveTo(canvas.width / 2 - 20, canvas.height / 2);
      ctx.lineTo(canvas.width / 2 + 20, canvas.height / 2);
      ctx.moveTo(canvas.width / 2, canvas.height / 2 - 20);
      ctx.lineTo(canvas.width / 2, canvas.height / 2 + 20);
      ctx.stroke();

      animFrameId = requestAnimationFrame(render);
    };

    render();

    return () => {
      cancelAnimationFrame(animFrameId);
    };
  }, [filteredComponents, rotationX, rotationY, selectedCompKey, mapData, componentDiagnostics, tabMode]);

  // Coordinate Projection math
  const project3D = (x: number, y: number, z: number) => {
    const canvas = canvasRef.current;
    const width = canvas ? canvas.width : 500;
    const height = canvas ? canvas.height : 350;

    // Y rotation
    let rotX1 = x * Math.cos(rotationY) - z * Math.sin(rotationY);
    let rotZ1 = x * Math.sin(rotationY) + z * Math.cos(rotationY);

    // X rotation
    let rotY2 = y * Math.cos(rotationX) - rotZ1 * Math.sin(rotationX);
    let rotZ2 = y * Math.sin(rotationX) + rotZ1 * Math.cos(rotationX);

    // Perspective factor
    const fov = 350;
    const dist = 300;
    const factor = fov / (dist + rotZ2);
    
    return {
      x: width / 2 + rotX1 * factor,
      y: height / 2 - rotY2 * factor,
      z: rotZ2
    };
  };

  // Drag interaction handlers
  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    dragStart.current = { x: e.clientX, y: e.clientY };
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    const deltaX = e.clientX - dragStart.current.x;
    const deltaY = e.clientY - dragStart.current.y;
    
    setRotationY(prev => prev + deltaX * 0.01);
    setRotationX(prev => Math.max(-Math.PI/3, Math.min(Math.PI/3, prev + deltaY * 0.01)));
    
    dragStart.current = { x: e.clientX, y: e.clientY };
  };

  const handleMouseUp = () => {
    setIsDragging(false);
  };

  // Canvas Click Detection
  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const rect = canvasRef.current?.getBoundingClientRect();
    if (!rect) return;
    const clickX = e.clientX - rect.left;
    const clickY = e.clientY - rect.top;

    let clickedKey: string | null = null;
    let minDistance = 25; // Click radius target threshold

    filteredComponents.forEach(comp => {
      const scaleFactor = 60;
      const proj = project3D(
        comp.position_x * scaleFactor,
        comp.position_y * scaleFactor,
        comp.position_z * scaleFactor
      );

      const dist = Math.hypot(clickX - proj.x, clickY - proj.y);
      if (dist < minDistance) {
        minDistance = dist;
        clickedKey = comp.component_key;
      }
    });

    if (clickedKey) {
      setSelectedCompKey(clickedKey);
    }
  };

  // Search box helper selection
  const handleSearchSelect = (key: string) => {
    setSelectedCompKey(key);
    setSearchQuery('');
    // Auto-focus Y rotation to center the item
    const comp = componentsList.find(c => c.component_key === key);
    if (comp) {
      setRotationY(Math.atan2(comp.position_x, comp.position_z));
    }
  };

  // Handle guided testing workflow step logging
  const handleStartTest = (testId: string) => {
    setSelectedTestId(testId);
    setActiveTestStepIdx(0);
    setMeasValue('');
    setMeasNotes('');
  };

  const handleNextStep = (test: ComponentTest) => {
    if (activeTestStepIdx < test.steps.length - 1) {
      setActiveTestStepIdx(prev => prev + 1);
    } else {
      // Completed, log measurement
      const numVal = parseFloat(measValue);
      const isOk = evaluateMeasurement(
        selectedCompKey === 'fuel_pump_fuse' ? 'CONTINUITY' : 'VOLTAGE',
        numVal,
        selectedCompKey === 'fuel_pump_fuse' ? 0 : 11.5,
        selectedCompKey === 'fuel_pump_fuse' ? 1.0 : 14.5
      );

      const newMeas: ComponentMeasurement = {
        id: `meas_${Date.now()}`,
        vehicle_id: activeVehId,
        component_id: selectedCompKey || '',
        test_id: test.id,
        measurement_type: selectedCompKey === 'fuel_pump_fuse' ? 'CONTINUITY' : 'VOLTAGE',
        value: numVal || 0,
        unit: selectedCompKey === 'fuel_pump_fuse' ? 'Ω' : 'V',
        expected_min_nullable: selectedCompKey === 'fuel_pump_fuse' ? 0 : 11.5,
        expected_max_nullable: selectedCompKey === 'fuel_pump_fuse' ? 1.0 : 14.5,
        result: isOk,
        notes: measNotes || 'Prueba guiada completada.',
        photo_uri_nullable: null,
        created_at: new Date().toISOString()
      };

      setMeasurements(prev => [...prev, newMeas]);
      
      // Update local storage digital twin risk/health if measurements update
      const updatedDiag = recalculateCausalProbabilities(currentDtc, [...measurements, newMeas]);
      const compDiag = updatedDiag[selectedCompKey || ''];
      
      if (compDiag) {
        // Log timeline event
        onAddTimelineEvent({
          id: `ev_test_${Date.now()}`,
          vehicle_id: activeVehId,
          event_type: 'COMPONENT_TEST_COMPLETED',
          title: `Prueba: ${test.name}`,
          description: `Resultado: ${isOk === 'PASS' ? 'Pasó (Correcto)' : 'Falló (Defectuoso)'}. Detalle: ${newMeas.value} ${newMeas.unit}. ${measNotes}`,
          severity: isOk === 'PASS' ? 'low' : 'high',
          source: 'AI',
          related_report_id_nullable: null,
          related_work_order_id_nullable: null,
          related_part_request_id_nullable: null,
          related_livelink_id_nullable: null,
          created_at: new Date().toISOString()
        });

        // Trigger predictive alert if failed
        if (isOk === 'FAIL') {
          onAddPredictiveAlert({
            id: `alert_pred_${Date.now()}`,
            vehicle_id: activeVehId,
            component: selectedCompKey === 'fuel_pump_relay' ? 'alternador' : 'batería',
            risk_level: 'HIGH',
            predicted_issue: `Falla confirmada de circuito en ${activeComp?.name}.`,
            evidence: [`Voltaje medido fuera de rango esperado: ${newMeas.value} ${newMeas.unit}.`, compDiag.rationale],
            recommended_action: `Solicitar revisión eléctrica urgente del circuito. ${test.fail_action}`,
            due_in_km_nullable: 100,
            due_in_days_nullable: 3,
            confidence: 95,
            status: 'active',
            created_at: new Date().toISOString()
          });

          // Sync digital twin
          onUpdateDigitalTwin({
            vehicle_id: activeVehId,
            baseline_created_at: new Date().toISOString(),
            baseline_confidence: 95,
            normal_idle_rpm_min: 750,
            normal_idle_rpm_max: 820,
            normal_voltage_min: 13.8,
            normal_voltage_max: 14.4,
            normal_ect_min: 88,
            normal_ect_max: 96,
            normal_fuel_trim_min: -3,
            normal_fuel_trim_max: 3,
            normal_maf_min: 3,
            normal_maf_max: 6,
            normal_map_min: 30,
            normal_map_max: 42,
            driving_profile: 'MIXED',
            health_score: 55, // Critical degradation
            risk_score: 85, // Critical risk
            last_updated_at: new Date().toISOString()
          });
        }
      }

      setSelectedTestId(null);
    }
  };

  // SVG wiring and fuse layouts templates
  const wiringTemplate = useMemo(() => {
    return getWiringCircuitTemplate(activeVehId, currentDtc);
  }, [activeVehId, currentDtc]);

  const activeBox = useMemo(() => {
    return getFuseRelayBoxTemplate(activeVehId, activeFuseBoxLoc);
  }, [activeVehId, activeFuseBoxLoc]);

  return (
    <div className="flex flex-col gap-6 text-white">
      
      {/* HUD Header */}
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between border-b border-cyan-500/20 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-mono text-[10px] bg-cyan-500/10 text-cyan-400 px-2 py-0.5 rounded border border-cyan-500/20 font-bold uppercase tracking-wider">
              Holograma Diagnóstico 3D
            </span>
            <span className="font-mono text-[10px] bg-yellow-500/10 text-yellow-400 px-2 py-0.5 rounded border border-yellow-500/20 font-bold">
              DTC: {currentDtc}
            </span>
          </div>
          <h1 className="text-2xl font-black tracking-tight text-white mt-1">
            Topología del Motor y Circuito Eléctrico
          </h1>
          <p className="text-xs text-slate-400 mt-0.5">
            {isSpecificVerna ? (
              <span className="text-emerald-400 font-bold">✓ Plantilla OEM Específica: Hyundai Accent Verna 2005 1.6L</span>
            ) : (
              <span className="text-yellow-500 font-bold">⚠️ Plantilla Genérica FWD L4 (Ubicaciones aproximadas)</span>
            )}
          </p>
        </div>

        {/* Tab Buttons */}
        <div className="flex bg-slate-900/60 p-1 rounded-xl border border-white/5 self-start">
          {[
            { id: 'MOTOR', label: 'Motor 3D', icon: <Compass size={14} /> },
            { id: 'FUSES', label: 'Fusibles/Relés', icon: <Grid size={14} /> },
            { id: 'WIRING', label: 'Topología Arnés', icon: <GitBranch size={14} /> },
            { id: 'TESTS', label: 'Pruebas Guiadas', icon: <Wrench size={14} /> },
            { id: 'PARTS_3D', label: 'Piezas', icon: <ShoppingCart size={14} /> }
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setTabMode(tab.id as TabMode)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                tabMode === tab.id 
                  ? 'bg-cyan-500 text-black shadow-lg shadow-cyan-500/20' 
                  : 'text-slate-400 hover:text-white'
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main interactive grid area */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.3fr_0.7fr] gap-6">
        
        {/* Visual workspace area */}
        <div className="glass rounded-2xl border border-cyan-500/10 p-5 bg-slate-950/40 min-h-[400px] flex flex-col justify-between relative overflow-hidden">
          
          {/* Top workspace overlays (Search + Quality) */}
          <div className="flex items-center justify-between gap-4 z-10">
            {/* Search inputs */}
            <div className="relative max-w-xs w-full">
              <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-slate-400">
                <Search size={14} />
              </span>
              <input
                type="text"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder="Buscar componente o DTC..."
                className="bg-slate-900/60 border border-white/10 rounded-lg py-1.5 pl-9 pr-4 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 w-full"
              />
              
              {/* Autocomplete query drop panel */}
              {searchQuery && (
                <div className="absolute top-full left-0 right-0 mt-1 bg-slate-900 border border-white/10 rounded-lg shadow-xl max-h-48 overflow-y-auto z-20">
                  {componentsList
                    .filter(c => c.name.toLowerCase().includes(searchQuery.toLowerCase()) || c.related_dtcs.includes(searchQuery.toUpperCase()))
                    .map(c => (
                      <button
                        key={c.id}
                        onClick={() => handleSearchSelect(c.component_key)}
                        className="w-full text-left px-3 py-2 text-xs hover:bg-cyan-500/10 border-b border-white/5 flex items-center justify-between"
                      >
                        <span>{c.name}</span>
                        <span className="text-[10px] text-cyan-400 font-mono">{c.system}</span>
                      </button>
                    ))}
                </div>
              )}
            </div>

            {/* Quality control toggler */}
            <select
              value={qualityMode}
              onChange={e => setQualityMode(e.target.value as any)}
              className="bg-slate-900/60 border border-white/10 rounded-lg px-2 py-1 text-[10px] font-mono text-cyan-400 focus:outline-none"
            >
              <option value="HIGH_QUALITY">CALIDAD ALTA</option>
              <option value="BALANCED">RENDIMIENTO BALANCEADO</option>
              <option value="LOW_POWER">MODO AHORRO</option>
              <option value="2D_FALLBACK">MODO 2D (ESTÁTICO)</option>
            </select>
          </div>

          {/* VIEW RENDERERS */}
          <div className="flex-1 flex items-center justify-center my-4">
            
            {/* Mode 1: 3D Motor Canvas */}
            {tabMode === 'MOTOR' && (
              <div className="relative w-full h-[350px] flex items-center justify-center">
                <canvas
                  ref={canvasRef}
                  width={550}
                  height={350}
                  onMouseDown={handleMouseDown}
                  onMouseMove={handleMouseMove}
                  onMouseUp={handleMouseUp}
                  onMouseLeave={handleMouseUp}
                  onClick={handleCanvasClick}
                  className="cursor-grab active:cursor-grabbing max-w-full"
                />
                
                {/* 3D controls instruction hint */}
                <div className="absolute bottom-2 left-2 flex items-center gap-1.5 text-[9px] font-mono text-slate-500 bg-slate-950/60 px-2 py-1 rounded">
                  <Maximize2 size={10} className="text-cyan-500" />
                  <span>Arrastrar para rotar · Clic en nodo para ver detalles</span>
                </div>
              </div>
            )}

            {/* Mode 2: Caja de fusibles */}
            {tabMode === 'FUSES' && (
              <div className="w-full flex flex-col gap-4">
                <div className="flex justify-center gap-3 border-b border-white/5 pb-2">
                  <button
                    onClick={() => setActiveFuseBoxLoc('ENGINE_BAY')}
                    className={`px-3 py-1 rounded text-xs font-bold ${
                      activeFuseBoxLoc === 'ENGINE_BAY' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-white'
                    }`}
                  >
                    Vano Motor (Engine Bay)
                  </button>
                  <button
                    onClick={() => setActiveFuseBoxLoc('UNDER_DASH')}
                    className={`px-3 py-1 rounded text-xs font-bold ${
                      activeFuseBoxLoc === 'UNDER_DASH' ? 'bg-cyan-500/10 text-cyan-400 border border-cyan-500/20' : 'text-slate-400 hover:text-white'
                    }`}
                  >
                    Habitáculo (Under Dash)
                  </button>
                </div>

                <div className="grid grid-cols-3 sm:grid-cols-4 gap-3 bg-slate-950/60 p-4 rounded-xl border border-white/5 max-w-md mx-auto w-full">
                  {activeBox.slots.map(slot => {
                    const isRelatedToDtc = slot.related_dtcs.includes(currentDtc);
                    const diagnostic = componentDiagnostics[slot.component_protected === 'Bomba de Combustible' ? 'fuel_pump_fuse' : ''];
                    
                    let bgStyle = 'bg-slate-900 hover:bg-slate-800 border-white/10';
                    let textStyle = 'text-slate-400';
                    
                    if (isRelatedToDtc) {
                      bgStyle = 'bg-yellow-500/10 hover:bg-yellow-500/20 border-yellow-500/30';
                      textStyle = 'text-yellow-400 font-bold';
                    }
                    if (diagnostic?.status === 'CONFIRMED_FAULT') {
                      bgStyle = 'bg-red-500/20 hover:bg-red-500/30 border-red-500/40 animate-pulse';
                      textStyle = 'text-red-400 font-black';
                    }
                    if (diagnostic?.status === 'TEST_PASSED') {
                      bgStyle = 'bg-green-500/20 hover:bg-green-500/30 border-green-500/40';
                      textStyle = 'text-green-400';
                    }

                    return (
                      <div
                        key={slot.id}
                        onClick={() => {
                          if (slot.slot_code === 'F2') setSelectedCompKey('fuel_pump_fuse');
                          if (slot.slot_code === 'R2') setSelectedCompKey('fuel_pump_relay');
                        }}
                        className={`p-3 rounded-lg border text-center flex flex-col justify-between items-center gap-1 cursor-pointer transition-all ${bgStyle}`}
                      >
                        <div className="text-[10px] font-mono text-slate-500 uppercase tracking-widest">{slot.slot_code}</div>
                        <div className={`text-xs font-black truncate max-w-full ${textStyle}`}>{slot.label}</div>
                        {slot.amperage_nullable && (
                          <span className="text-[9px] font-mono bg-white/5 px-1.5 py-0.5 rounded text-slate-400">
                            {slot.amperage_nullable}A
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            {/* Mode 3: Wiring Topology Diagram Graph */}
            {tabMode === 'WIRING' && (
              <div className="w-full h-[320px] overflow-x-auto overflow-y-hidden flex items-center justify-center p-4">
                <svg className="w-full min-w-[650px] h-full" style={{ background: 'transparent' }}>
                  <defs>
                    <marker id="arrow" viewBox="0 0 10 10" refX="22" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                      <path d="M 0 0 L 10 5 L 0 10 z" fill="rgba(6, 182, 212, 0.4)" />
                    </marker>
                  </defs>

                  {/* Draw edges lines */}
                  {wiringTemplate.edges.map((edge, idx) => {
                    const fromNode = wiringTemplate.nodes.find(n => n.id === edge.from_node);
                    const toNode = wiringTemplate.nodes.find(n => n.id === edge.to_node);
                    if (!fromNode || !toNode) return null;

                    // Compute node locations
                    const fromIdx = wiringTemplate.nodes.indexOf(fromNode);
                    const toIdx = wiringTemplate.nodes.indexOf(toNode);
                    const fromX = 60 + fromIdx * 80;
                    const fromY = 120 + (fromIdx % 2 === 0 ? -30 : 30);
                    const toX = 60 + toIdx * 80;
                    const toY = 120 + (toIdx % 2 === 0 ? -30 : 30);

                    return (
                      <g key={idx}>
                        <line
                          x1={fromX}
                          y1={fromY}
                          x2={toX}
                          y2={toY}
                          stroke={edge.wire_color_nullable === 'Rojo' ? '#ef4444' : 'rgba(6, 182, 212, 0.4)'}
                          strokeWidth="2.5"
                          markerEnd="url(#arrow)"
                          strokeDasharray={edge.wire_color_nullable?.includes('-') ? '4 2' : 'none'}
                        />
                        <text
                          x={(fromX + toX) / 2}
                          y={(fromY + toY) / 2 - 8}
                          fill="rgba(148, 163, 184, 0.7)"
                          fontSize="7"
                          fontFamily="monospace"
                          textAnchor="middle"
                        >
                          {edge.wire_color_nullable}
                        </text>
                      </g>
                    );
                  })}

                  {/* Draw nodes circles */}
                  {wiringTemplate.nodes.map((node, idx) => {
                    const nodeX = 60 + idx * 80;
                    const nodeY = 120 + (idx % 2 === 0 ? -30 : 30);
                    
                    let fill = '#0f172a';
                    let stroke = 'rgba(6, 182, 212, 0.5)';
                    
                    if (node.type === 'POWER_SUPPLY') { stroke = '#f43f5e'; }
                    if (node.type === 'FUSE') { stroke = '#eab308'; }
                    if (node.type === 'RELAY') { stroke = '#3b82f6'; }
                    if (node.type === 'GROUND') { stroke = '#10b981'; }

                    const isSelected = selectedCompKey === (
                      node.id.includes('pump') && !node.id.includes('relay') && !node.id.includes('fuse') && !node.id.includes('ground') ? 'fuel_pump' :
                      node.id.includes('relay') ? 'fuel_pump_relay' :
                      node.id.includes('fuse') && node.id !== 'fuse_main' ? 'fuel_pump_fuse' : null
                    );

                    return (
                      <g 
                        key={node.id} 
                        className="cursor-pointer"
                        onClick={() => {
                          if (node.id.includes('relay')) setSelectedCompKey('fuel_pump_relay');
                          else if (node.id.includes('fuse') && node.id !== 'fuse_main') setSelectedCompKey('fuel_pump_fuse');
                          else if (node.id.includes('pump') && !node.id.includes('ground')) setSelectedCompKey('fuel_pump');
                        }}
                      >
                        <circle
                          cx={nodeX}
                          cy={nodeY}
                          r={isSelected ? "18" : "14"}
                          fill={fill}
                          stroke={isSelected ? '#22d3ee' : stroke}
                          strokeWidth={isSelected ? '3' : '2'}
                          className="transition-all"
                        />
                        <text
                          cx={nodeX}
                          cy={nodeY}
                          y={nodeY + 4}
                          fill="#ffffff"
                          fontSize="8"
                          fontWeight="bold"
                          textAnchor="middle"
                        >
                          {node.type.substring(0, 3)}
                        </text>
                        <text
                          x={nodeX}
                          y={nodeY + 28}
                          fill={isSelected ? '#22d3ee' : '#94a3b8'}
                          fontSize="8"
                          fontFamily="monospace"
                          fontWeight={isSelected ? 'bold' : 'normal'}
                          textAnchor="middle"
                        >
                          {node.label}
                        </text>
                      </g>
                    );
                  })}
                </svg>
              </div>
            )}

            {/* Mode 4: Pruebas guiadas */}
            {tabMode === 'TESTS' && (
              <div className="w-full max-w-lg mx-auto bg-slate-900/60 p-5 rounded-2xl border border-white/5">
                {!selectedCompKey ? (
                  <div className="text-center py-8 text-slate-400 text-sm">
                    <Wrench className="mx-auto mb-3 text-slate-500" size={32} />
                    Seleccione un componente en el motor o el arnés para ver las pruebas guiadas disponibles.
                  </div>
                ) : activeTests.length === 0 ? (
                  <div className="text-center py-8 text-slate-400 text-sm">
                    <Info className="mx-auto mb-3 text-slate-500" size={32} />
                    No hay pruebas guiadas documentadas para este componente.
                  </div>
                ) : !selectedTestId ? (
                  <div className="flex flex-col gap-4">
                    <h3 className="text-sm font-bold text-slate-200">Pruebas disponibles para: {activeComp?.name}</h3>
                    {activeTests.map(test => (
                      <button
                        key={test.id}
                        onClick={() => handleStartTest(test.id)}
                        className="flex items-center justify-between p-4 rounded-xl bg-slate-950/40 border border-white/5 hover:border-cyan-500/30 transition-all text-left"
                      >
                        <div>
                          <div className="text-sm font-bold text-white">{test.name}</div>
                          <div className="text-[10px] text-slate-400 mt-1 flex items-center gap-2">
                            <span>Herramientas: {test.required_tools.join(', ')}</span>
                            <span className={`px-1.5 py-0.5 rounded text-[8px] font-bold ${
                              test.safety_level === 'SAFE' ? 'bg-green-500/10 text-green-400 border border-green-500/20' : 'bg-yellow-500/10 text-yellow-400 border border-yellow-500/20'
                            }`}>{test.safety_level}</span>
                          </div>
                        </div>
                        <Play size={16} className="text-cyan-400" />
                      </button>
                    ))}
                  </div>
                ) : (
                  // Active test step sequence
                  (() => {
                    const test = activeTests.find(t => t.id === selectedTestId);
                    if (!test) return null;
                    const step = test.steps[activeTestStepIdx];
                    const isLastStep = activeTestStepIdx === test.steps.length - 1;

                    return (
                      <div className="flex flex-col gap-4">
                        <div className="flex justify-between items-center border-b border-white/5 pb-2">
                          <h4 className="text-xs font-bold text-cyan-400 uppercase tracking-wider">{test.name}</h4>
                          <button 
                            onClick={() => setSelectedTestId(null)}
                            className="text-slate-400 hover:text-white"
                          >
                            <X size={16} />
                          </button>
                        </div>

                        {/* Step indicator */}
                        <div className="flex items-center gap-1">
                          {test.steps.map((_, i) => (
                            <div 
                              key={i} 
                              className={`h-1 flex-1 rounded transition-all ${
                                i <= activeTestStepIdx ? 'bg-cyan-500' : 'bg-white/10'
                              }`} 
                            />
                          ))}
                        </div>

                        {/* Step content */}
                        <div className="my-2 min-h-[80px]">
                          <div className="text-[10px] font-mono text-cyan-300 font-bold">PASO {activeTestStepIdx + 1} de {test.steps.length}</div>
                          <div className="text-sm text-slate-200 mt-1.5 leading-relaxed">{step}</div>
                        </div>

                        {/* Measurement inputs on final step */}
                        {isLastStep && (
                          <div className="flex flex-col gap-3 bg-slate-950/40 p-4 rounded-xl border border-white/5 animate-slide-up">
                            <h5 className="text-xs font-bold text-white">Ingresar Lectura de Medición</h5>
                            <div className="flex items-center gap-2">
                              <input
                                type="number"
                                value={measValue}
                                onChange={e => setMeasValue(e.target.value)}
                                placeholder="Valor medido"
                                className="bg-slate-900 border border-white/10 rounded-lg px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 flex-1 font-mono"
                              />
                              <span className="text-sm text-slate-400 font-bold font-mono">
                                {selectedCompKey === 'fuel_pump_fuse' ? 'Ω' : 'V'}
                              </span>
                            </div>
                            <textarea
                              value={measNotes}
                              onChange={e => setMeasNotes(e.target.value)}
                              placeholder="Notas adicionales o comentarios de inspección..."
                              className="bg-slate-900 border border-white/10 rounded-lg p-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500/50 resize-none h-16"
                            />
                          </div>
                        )}

                        {/* Actions buttons */}
                        <div className="flex justify-between items-center mt-2">
                          <button
                            onClick={() => setActiveTestStepIdx(prev => Math.max(0, prev - 1))}
                            disabled={activeTestStepIdx === 0}
                            className="px-4 py-1.5 bg-white/5 hover:bg-white/10 disabled:opacity-50 text-white rounded-lg text-xs font-bold"
                          >
                            Atrás
                          </button>
                          <button
                            onClick={() => handleNextStep(test)}
                            className="px-5 py-1.5 bg-cyan-500 text-black rounded-lg text-xs font-bold shadow-lg shadow-cyan-500/10 hover:shadow-cyan-500/25 transition-all"
                          >
                            {isLastStep ? 'Registrar y Finalizar' : 'Siguiente'}
                          </button>
                        </div>
                      </div>
                    );
                  })()
                )}
              </div>
            )}

            {/* Mode 5: Piezas del Catálogo vinculadas al 3D */}
            {tabMode === 'PARTS_3D' && (
              <div className="w-full bg-slate-900/60 p-4 rounded-2xl border border-white/5 max-h-[520px] overflow-y-auto">
                <div className="flex items-center justify-between mb-3">
                  <h3 className="text-xs font-bold text-orange-300 uppercase tracking-wider font-mono flex items-center gap-1.5">
                    <ShoppingCart size={13} /> Catálogo de Piezas — Suspensión
                  </h3>
                  <span className="text-[9px] text-gray-500 font-mono">{SUSPENSION_PARTS_CATALOG.length} piezas</span>
                </div>
                <div className="space-y-1.5">
                  {SUSPENSION_PARTS_CATALOG.map(part => {
                    const nodeId = part.id.replace('part_', '');
                    const isSelected = selectedCompKey === nodeId;
                    return (
                      <button
                        key={part.id}
                        onClick={() => setSelectedCompKey(isSelected ? null : nodeId)}
                        className={`w-full text-left px-3 py-2.5 rounded-xl transition-all flex items-center gap-2.5 ${
                          isSelected
                            ? 'border border-orange-500/30 bg-orange-500/8 shadow-[0_0_12px_rgba(251,146,60,0.1)]'
                            : 'border border-white/5 hover:border-orange-500/15 hover:bg-white/2'
                        }`}
                      >
                        <div className={`h-6 w-6 rounded flex-shrink-0 flex items-center justify-center text-[10px] font-bold ${
                          isSelected ? 'bg-orange-500/20 text-orange-300' : 'bg-white/5 text-gray-500'
                        }`}>
                          <Layers size={11} />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className={`text-[11px] font-bold font-mono truncate ${isSelected ? 'text-orange-300' : 'text-gray-300'}`}>
                            {part.name}
                          </p>
                          <div className="flex items-center gap-2 mt-0.5">
                            <span className="text-[9px] text-gray-600 font-mono">{part.category}</span>
                            <span className="text-[9px] text-gray-700">·</span>
                            <span className={`text-[8px] font-bold px-1 py-0.5 rounded ${
                              part.confidence_level === 'CONFIRMED'
                                ? 'bg-emerald-500/10 text-emerald-400'
                                : part.confidence_level === 'PROBABLE'
                                  ? 'bg-amber-500/10 text-amber-400'
                                  : 'bg-gray-500/10 text-gray-500'
                            }`}>{part.confidence_level === 'CONFIRMED' ? '✓' : part.confidence_level === 'PROBABLE' ? '~' : '?'}</span>
                            <span className="text-[8px] text-amber-400/50 font-mono">specs en revisión</span>
                          </div>
                        </div>
                        {isSelected && (
                          <div className="flex items-center gap-1 text-[8px] text-orange-400 font-mono">
                            <Activity size={9} /> 3D
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>

                {/* Related Repair Procedures */}
                {GUIDED_REPAIR_PROCEDURES.length > 0 && (
                  <div className="mt-4 pt-3 border-t border-white/5">
                    <h4 className="text-[10px] font-bold text-emerald-400 uppercase tracking-wider font-mono mb-2 flex items-center gap-1">
                      <Wrench size={10} /> Procedimientos de Reparación Guiada
                    </h4>
                    <div className="space-y-1.5">
                      {GUIDED_REPAIR_PROCEDURES.map(proc => (
                        <div
                          key={proc.id}
                          className="flex items-center justify-between px-3 py-2 rounded-lg border border-white/5 hover:border-emerald-500/20 transition-all"
                          style={{ background: 'rgba(0,0,0,0.25)' }}
                        >
                          <div className="flex items-center gap-2 min-w-0 flex-1">
                            <Play size={10} className="text-emerald-400 flex-shrink-0" />
                            <div className="min-w-0">
                              <p className="text-[10px] font-bold text-gray-300 font-mono truncate">{proc.title}</p>
                              <p className="text-[8px] text-gray-600 font-mono">{proc.steps.length} pasos · modo entrenamiento</p>
                            </div>
                          </div>
                          <span className={`text-[8px] font-bold px-1.5 py-0.5 rounded border ${
                            proc.difficulty === 'EASY' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' :
                            proc.difficulty === 'MEDIUM' ? 'bg-amber-500/10 text-amber-400 border-amber-500/20' :
                            'bg-red-500/10 text-red-400 border-red-500/20'
                          }`}>{proc.difficulty}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

          </div>

          {/* Bottom visual overlays layers status */}
          <div className="flex flex-wrap gap-2 border-t border-white/5 pt-4 mt-auto">
            {[
              { id: 'mechanical', label: 'Capa Mecánica' },
              { id: 'sensors', label: 'Sensores' },
              { id: 'actuators', label: 'Actuadores' },
              { id: 'wiring', label: 'Circuitos' },
              { id: 'fuses', label: 'Fusibles' }
            ].map(layer => {
              const active = layers[layer.id as keyof typeof layers];
              return (
                <button
                  key={layer.id}
                  onClick={() => setLayers(prev => ({ ...prev, [layer.id]: !active }))}
                  className={`text-[9px] font-mono px-2 py-1 rounded border transition-all ${
                    active 
                      ? 'bg-cyan-500/10 border-cyan-500/30 text-cyan-400 font-bold' 
                      : 'bg-white/5 border-white/5 text-slate-500'
                  }`}
                >
                  {layer.label}
                </button>
              );
            })}
          </div>

        </div>

        {/* Sidebar info card panel */}
        <div className="flex flex-col gap-6">
          
          {/* Active focus card detail */}
          <div className="glass rounded-2xl border border-white/5 p-5 bg-slate-950/20">
            {activeComp ? (
              <div className="flex flex-col gap-4">
                
                {/* Component identification */}
                <div>
                  <span className="text-[9px] font-mono uppercase bg-cyan-500/10 border border-cyan-500/20 px-2 py-0.5 rounded text-cyan-400 font-bold">
                    {activeComp.system} · {activeComp.subsystem}
                  </span>
                  <h2 className="text-lg font-black text-white mt-1.5">{activeComp.name}</h2>
                  <p className="text-xs text-slate-400 mt-1 leading-relaxed">{activeComp.description}</p>
                </div>

                {/* Status indicator */}
                <div className="bg-slate-900/60 p-4 rounded-xl border border-white/5 flex items-center justify-between">
                  <div>
                    <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold">Probabilidad de Falla</div>
                    <div className="text-xl font-mono font-black mt-0.5 text-white">
                      {componentDiagnostics[activeComp.component_key]?.probability || 0}%
                    </div>
                  </div>
                  <span className={`px-2.5 py-1 rounded-lg text-xs font-mono font-black ${
                    (() => {
                      const stat = componentDiagnostics[activeComp.component_key]?.status || 'RELATED_TO_DTC';
                      if (stat === 'CONFIRMED_FAULT') return 'bg-red-500/10 text-red-400 border border-red-500/20 animate-pulse';
                      if (stat === 'TEST_PASSED') return 'bg-green-500/10 text-green-400 border border-green-500/20';
                      if (stat === 'SUSPECT') return 'bg-orange-500/10 text-orange-400 border border-orange-500/20';
                      return 'bg-yellow-500/10 text-yellow-400 border border-yellow-500/20';
                    })()
                  }`}>
                    {(componentDiagnostics[activeComp.component_key]?.status || 'RELATED_TO_DTC').replace(/_/g, ' ')}
                  </span>
                </div>

                {/* Diagnostics analysis feedback rationale */}
                <div className="text-xs text-slate-300 italic bg-white/[0.02] border border-white/5 p-3 rounded-lg leading-relaxed">
                  " {componentDiagnostics[activeComp.component_key]?.rationale || 'Sin mediciones cargadas aún. El componente se encuentra bajo sospecha genérica.'} "
                </div>

                {/* Caution and alerts warnings */}
                {activeComp.safety_notes.length > 0 && (
                  <div className="bg-yellow-500/10 border border-yellow-500/20 p-3 rounded-xl flex items-start gap-2.5">
                    <ShieldAlert size={16} className="text-yellow-500 flex-shrink-0 mt-0.5" />
                    <div className="text-xs text-yellow-500 leading-relaxed font-mono">
                      <strong>PRECAUCIÓN:</strong> {activeComp.safety_notes[0]}
                    </div>
                  </div>
                )}

                {/* Dynamic Actions triggers */}
                <div className="grid grid-cols-2 gap-3 mt-2 border-t border-white/5 pt-4">
                  {/* Spare parts requisition button */}
                  <button
                    onClick={() => {
                      onAddTimelineEvent({
                        id: `ev_part_req_${Date.now()}`,
                        vehicle_id: activeVehId,
                        event_type: 'PART_REQUESTED',
                        title: `Repuesto Solicitado: ${activeComp.related_parts[0]}`,
                        description: `Solicitud iniciada para ${activeComp.name}. Diagnóstico actual: ${componentDiagnostics[activeComp.component_key]?.status}.`,
                        severity: 'low',
                        source: 'USER',
                        related_report_id_nullable: null,
                        related_work_order_id_nullable: null,
                        related_part_request_id_nullable: null,
                        related_livelink_id_nullable: null,
                        created_at: new Date().toISOString()
                      });
                    }}
                    className={`flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg text-xs font-bold border transition-all ${
                      componentDiagnostics[activeComp.component_key]?.status === 'RELATED_TO_DTC'
                        ? 'bg-slate-900 border-white/10 text-slate-500 cursor-not-allowed'
                        : 'bg-yellow-500 text-black border-yellow-500/25 hover:bg-yellow-400'
                    }`}
                    disabled={componentDiagnostics[activeComp.component_key]?.status === 'RELATED_TO_DTC'}
                  >
                    <ShoppingCart size={13} />
                    Pedir Pieza
                  </button>

                  {/* Mechanical dispatcher booking trigger */}
                  <button
                    onClick={() => {
                      onAddTimelineEvent({
                        id: `ev_mech_req_${Date.now()}`,
                        vehicle_id: activeVehId,
                        event_type: 'MECHANIC_REQUESTED',
                        title: `Servicio Mecánico Solicitado: Carlos Ruiz`,
                        description: `Se solicitó mecánico a domicilio para el diagnóstico físico de ${activeComp.name}.`,
                        severity: 'low',
                        source: 'USER',
                        related_report_id_nullable: null,
                        related_work_order_id_nullable: null,
                        related_part_request_id_nullable: null,
                        related_livelink_id_nullable: null,
                        created_at: new Date().toISOString()
                      });
                    }}
                    className="flex items-center justify-center gap-1.5 py-2 px-3 rounded-lg text-xs font-bold bg-cyan-500 text-black border border-cyan-500/25 hover:bg-cyan-400 transition-all"
                  >
                    <Wrench size={13} />
                    Pedir Mecánico
                  </button>
                </div>

                {/* Trigger test selection view shortcut */}
                {activeTests.length > 0 && tabMode !== 'TESTS' && (
                  <button
                    onClick={() => setTabMode('TESTS')}
                    className="w-full flex items-center justify-center gap-1 py-1.5 text-xs text-cyan-400 hover:text-cyan-300 font-mono"
                  >
                    Ver Pruebas de Taller ({activeTests.length}) →
                  </button>
                )}

              </div>
            ) : (
              <div className="text-center py-12 text-slate-400 text-sm">
                <Compass className="mx-auto mb-3 text-slate-600 animate-spin-slow" size={36} />
                Seleccione un componente en el visor interactivo para iniciar el diagnóstico técnico.
              </div>
            )}
          </div>

          {/* Measurements logging history tracker */}
          <div className="glass rounded-2xl border border-white/5 p-5 bg-slate-950/20">
            <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider mb-3">Historial de Mediciones</h3>
            <div className="flex flex-col gap-2 max-h-48 overflow-y-auto">
              {measurements.map(meas => {
                const comp = componentsList.find(c => c.component_key === meas.component_id);
                return (
                  <div 
                    key={meas.id}
                    className="p-2.5 rounded-lg bg-slate-950/40 border border-white/5 flex items-center justify-between gap-3 text-xs"
                  >
                    <div>
                      <div className="font-bold text-white">{comp?.name || meas.component_id}</div>
                      <div className="text-[10px] text-slate-400 mt-0.5">{meas.notes}</div>
                    </div>
                    <div className="text-right">
                      <div className="font-mono font-bold text-white">{meas.value} {meas.unit}</div>
                      <span className={`text-[8px] font-mono font-bold px-1.5 py-0.5 rounded ${
                        meas.result === 'PASS' ? 'bg-green-500/10 text-green-400' : 'bg-red-500/10 text-red-400'
                      }`}>
                        {meas.result}
                      </span>
                    </div>
                  </div>
                );
              })}
              {measurements.length === 0 && (
                <div className="text-center py-4 text-slate-500 text-xs font-mono">
                  Sin mediciones registradas en esta sesión.
                </div>
              )}
            </div>
          </div>

        </div>

      </div>

    </div>
  );
}
