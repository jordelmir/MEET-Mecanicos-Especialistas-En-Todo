import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Video, VideoOff, Volume2, VolumeX, MapPin, MapPinOff, Activity, Cpu,
  ShieldCheck, ShieldAlert, Trash2, History, Sparkles, Download, CheckCircle2,
  Settings, Link2, FileText, AlertTriangle, Play, Square, Camera, Tv, Users,
  RefreshCw, Lock, Unlock, ExternalLink, Shield, Info, Compass, HelpCircle, Eye, Sliders
} from 'lucide-react';
import {
  VehicleProfile, DashcamSession, DashcamClip, ClipType, DrivingEvent,
  DrivingEventType, TelemetrySample, OverlayData, TelemetrySource,
  Role, VehicleTimelineEvent
} from '../types';
import { sha256Hex } from '../lib/reports/hash';

interface DashcamHUDProps {
  vehicles: VehicleProfile[];
  activeUserId: string;
  role: Role;
  onAddTimelineEvent: (event: VehicleTimelineEvent) => void;
  onAddFleetIncident?: (incident: any) => void;
  onClose?: () => void;
  // External states injected from App.tsx
  sessions: DashcamSession[];
  clips: DashcamClip[];
  events: DrivingEvent[];
  onAddSession: (s: DashcamSession) => void;
  onAddClip: (c: DashcamClip) => void;
  onAddEvent: (e: DrivingEvent) => void;
  onDeleteClip: (id: string) => void;
  onToggleLockClip: (id: string) => void;
  isObdConnectedGlobal?: boolean;
}

type SkinType = 'NEON_DIGITAL' | 'PREMIUM_COCKPIT' | 'MINIMAL_HUD';
type RecordingMode = 'HUD' | 'DASHCAM' | 'BLACK_BOX' | 'FLEET' | 'INCIDENT';

export function DashcamHUD({
  vehicles,
  activeUserId,
  role,
  onAddTimelineEvent,
  onAddFleetIncident,
  onClose,
  sessions,
  clips,
  events,
  onAddSession,
  onAddClip,
  onAddEvent,
  onDeleteClip,
  onToggleLockClip,
  isObdConnectedGlobal = false
}: DashcamHUDProps) {
  // Config & State
  const [selectedVehicleId, setSelectedVehicleId] = useState<string>(vehicles[0]?.id || '');
  const [activeSkin, setActiveSkin] = useState<SkinType>('NEON_DIGITAL');
  const [mode, setMode] = useState<RecordingMode>('HUD');
  const [isRecording, setIsRecording] = useState(false);
  const [activeSession, setActiveSession] = useState<DashcamSession | null>(null);

  // Consent & Toggles (Offline-first / User-approved)
  const [consentGranted, setConsentGranted] = useState(false);
  const [isAudioEnabled, setIsAudioEnabled] = useState(false); // Default OFF
  const [isGpsEnabled, setIsGpsEnabled] = useState(false); // Default OFF
  const [isObdConnected, setIsObdConnected] = useState(isObdConnectedGlobal);
  const [sensorFusionEnabled, setSensorFusionEnabled] = useState(true);

  // Storage and configuration
  const [circularBufferDuration, setCircularBufferDuration] = useState<number>(30); // in seconds
  const [resolution, setResolution] = useState<'720p' | '1080p'>('720p');
  const [fps, setFps] = useState<number>(30);
  const [qualityMode, setQualityMode] = useState<'LOW_POWER' | 'BALANCED' | 'HIGH_QUALITY'>('BALANCED');

  // Live Simulation state variables
  const [simSpeed, setSimSpeed] = useState<number>(85); // km/h
  const [simRpm, setSimRpm] = useState<number>(2400);
  const [simEct, setSimEct] = useState<number>(90); // °C (Coolant temp)
  const [simVoltage, setSimVoltage] = useState<number>(14.1); // V
  const [simGForce, setSimGForce] = useState<{ x: number; y: number; z: number }>({ x: 0.02, y: -0.05, z: 0.98 });
  const [activeDtc, setActiveDtc] = useState<string | null>(null);
  
  // Real Camera Stream State
  const [cameraStream, setCameraStream] = useState<MediaStream | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  // Buffer circular visual log state
  const [bufferSamples, setBufferSamples] = useState<TelemetrySample[]>([]);
  const [currentNotification, setCurrentNotification] = useState<string | null>(null);

  // View States
  const [currentTab, setCurrentTab] = useState<'live' | 'gallery' | 'settings'>('live');
  const [selectedClipForReport, setSelectedClipForReport] = useState<DashcamClip | null>(null);
  const [showPdfPreview, setShowPdfPreview] = useState(false);

  // Fleet Mode settings
  const [fleetDriverName, setFleetDriverName] = useState('Juan Pérez');
  const [fleetRouteName, setFleetRouteName] = useState('Ruta Metropolitana Centro');

  // LiveLink simulated interaction
  const [liveLinkIncomingRequest, setLiveLinkIncomingRequest] = useState(false);

  // Select active vehicle profile
  const selectedVehicle = vehicles.find(v => v.id === selectedVehicleId) || vehicles[0] || null;

  // Cleanup camera stream
  useEffect(() => {
    return () => {
      if (cameraStream) {
        cameraStream.getTracks().forEach(track => track.stop());
      }
    };
  }, [cameraStream]);

  // Request real camera access
  const handleRequestCamera = async () => {
    try {
      if (cameraStream) {
        cameraStream.getTracks().forEach(track => track.stop());
        setCameraStream(null);
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment', width: 1280, height: 720 },
        audio: isAudioEnabled
      });
      setCameraStream(stream);
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      setConsentGranted(true);
      showNotification('Acceso a cámara inicializado correctamente');
    } catch (err) {
      console.warn("Camera media access failed or was denied. Falling back to simulated road render.", err);
      setConsentGranted(true);
      showNotification('Usando renderizador cinemático simulado');
    }
  };

  // Toast Notification helper
  const showNotification = (msg: string) => {
    setCurrentNotification(msg);
    setTimeout(() => {
      setCurrentNotification(null);
    }, 4000);
  };

  // Simulating road on canvas if camera is not active or for visual HUD overlays
  useEffect(() => {
    let animationFrameId: number;
    let offset = 0;

    const render = () => {
      const canvas = canvasRef.current;
      if (!canvas) {
        animationFrameId = requestAnimationFrame(render);
        return;
      }
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      const w = canvas.width;
      const h = canvas.height;

      // Draw background
      if (cameraStream) {
        // If camera stream exists, clear canvas so video element behind is visible
        ctx.clearRect(0, 0, w, h);
      } else {
        // Draw cinematic road simulation
        ctx.fillStyle = '#060a12';
        ctx.fillRect(0, 0, w, h);

        // Grid / Ground lines
        ctx.strokeStyle = 'rgba(0, 240, 255, 0.05)';
        ctx.lineWidth = 1;
        const horizon = h * 0.45;
        
        // Draw sky gradient
        const skyGrad = ctx.createLinearGradient(0, 0, 0, horizon);
        skyGrad.addColorStop(0, '#020408');
        skyGrad.addColorStop(1, '#050c14');
        ctx.fillStyle = skyGrad;
        ctx.fillRect(0, 0, w, horizon);

        // Draw stars/cyber elements in sky
        ctx.fillStyle = 'rgba(0, 240, 255, 0.4)';
        for (let i = 0; i < 20; i++) {
          const sx = (Math.sin(i * 382.49) * 0.5 + 0.5) * w;
          const sy = (Math.cos(i * 123.84) * 0.5 + 0.5) * horizon;
          ctx.fillRect(sx, sy, 1, 1);
        }

        // Draw road perspective lines
        offset = (offset + simSpeed * 0.1) % 40;
        const roadW = w * 0.4;
        
        // Draw asphalt
        ctx.fillStyle = '#090f19';
        ctx.beginPath();
        ctx.moveTo(w / 2 - 10, horizon);
        ctx.lineTo(w / 2 + 10, horizon);
        ctx.lineTo(w / 2 + roadW, h);
        ctx.lineTo(w / 2 - roadW, h);
        ctx.closePath();
        ctx.fill();

        // Draw center dashed line
        ctx.strokeStyle = '#e2b93c';
        ctx.lineWidth = 4;
        ctx.beginPath();
        for (let y = horizon; y < h; y += 40) {
          const progress = (y - horizon) / (h - horizon);
          const segmentY = horizon + progress * (h - horizon) + (offset * progress) % 40;
          if (segmentY > h) continue;
          
          const currentDashLength = 15 * progress;
          const roadCenter = w / 2;
          ctx.moveTo(roadCenter, segmentY);
          ctx.lineTo(roadCenter, segmentY + currentDashLength);
        }
        ctx.stroke();

        // Side mountains
        ctx.fillStyle = '#03060a';
        ctx.beginPath();
        ctx.moveTo(0, horizon);
        ctx.lineTo(w * 0.2, horizon - 20);
        ctx.lineTo(w * 0.4, horizon);
        ctx.lineTo(w / 2, horizon);
        ctx.lineTo(w * 0.7, horizon - 30);
        ctx.lineTo(w, horizon);
        ctx.lineTo(w, h);
        ctx.lineTo(0, h);
        ctx.closePath();
        ctx.fill();
      }

      // Draw active scanner / overlay marks
      ctx.strokeStyle = 'rgba(0, 240, 255, 0.15)';
      ctx.lineWidth = 1;
      
      // Center crosshair (target)
      ctx.beginPath();
      ctx.arc(w / 2, h / 2, 20, 0, Math.PI * 2);
      ctx.moveTo(w / 2 - 35, h / 2);
      ctx.lineTo(w / 2 - 25, h / 2);
      ctx.moveTo(w / 2 + 25, h / 2);
      ctx.lineTo(w / 2 + 35, h / 2);
      ctx.moveTo(w / 2, h / 2 - 35);
      ctx.lineTo(w / 2, h / 2 - 25);
      ctx.moveTo(w / 2, h / 2 + 25);
      ctx.lineTo(w / 2, h / 2 + 35);
      ctx.stroke();

      // Corner brackets (HUD Viewfinder)
      const borderSize = 25;
      const pad = 15;
      ctx.strokeStyle = isRecording ? 'rgba(239, 68, 68, 0.4)' : 'rgba(0, 240, 255, 0.3)';
      ctx.lineWidth = 2;

      // Top Left
      ctx.beginPath();
      ctx.moveTo(pad, pad + borderSize);
      ctx.lineTo(pad, pad);
      ctx.lineTo(pad + borderSize, pad);
      ctx.stroke();

      // Top Right
      ctx.beginPath();
      ctx.moveTo(w - pad - borderSize, pad);
      ctx.lineTo(w - pad, pad);
      ctx.lineTo(w - pad, pad + borderSize);
      ctx.stroke();

      // Bottom Left
      ctx.beginPath();
      ctx.moveTo(pad, h - pad - borderSize);
      ctx.lineTo(pad, h - pad);
      ctx.lineTo(pad + borderSize, h - pad);
      ctx.stroke();

      // Bottom Right
      ctx.beginPath();
      ctx.moveTo(w - pad - borderSize, h - pad);
      ctx.lineTo(w - pad, h - pad);
      ctx.lineTo(w - pad, h - pad - borderSize);
      ctx.stroke();

      // Flashing REC indicator
      if (isRecording && Math.floor(Date.now() / 600) % 2 === 0) {
        ctx.fillStyle = '#ef4444';
        ctx.beginPath();
        ctx.arc(pad + 20, pad + 20, 6, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 9px monospace';
        ctx.fillText('REC', pad + 32, pad + 23);
      }

      animationFrameId = requestAnimationFrame(render);
    };

    render();
    return () => {
      cancelAnimationFrame(animationFrameId);
    };
  }, [cameraStream, simSpeed, isRecording]);

  // Telemetry buffer sync (Monotonic timestamp sync)
  useEffect(() => {
    if (!isRecording) return;

    const interval = setInterval(() => {
      const sample: TelemetrySample = {
        rpm: isObdConnected ? simRpm : null,
        speed: isObdConnected ? simSpeed : null, // OBD Speed
        temp: isObdConnected ? simEct : null,
        voltage: isObdConnected ? simVoltage : null,
        gForceX: simGForce.x,
        gForceY: simGForce.y,
        gForceZ: simGForce.z,
        lat: isGpsEnabled ? 9.928 + (Math.sin(Date.now() / 10000) * 0.005) : null,
        lng: isGpsEnabled ? -84.085 + (Math.cos(Date.now() / 10000) * 0.005) : null,
        timestamp: Date.now(),
        quality: isObdConnected ? 'GOOD' : 'UNAVAILABLE',
        source: isObdConnected ? 'REAL_OBD' : 'GPS'
      };

      setBufferSamples(prev => {
        const next = [...prev, sample];
        // Keep buffer matching setting (circular buffer: N seconds of samples)
        const cutoff = Date.now() - (circularBufferDuration * 1000);
        return next.filter(s => s.timestamp >= cutoff);
      });
    }, 200); // 5Hz sampling rate

    return () => clearInterval(interval);
  }, [isRecording, isObdConnected, isGpsEnabled, simSpeed, simRpm, simEct, simVoltage, simGForce, circularBufferDuration]);

  // Driving Event Detector Logic
  const runDetection = useCallback((
    type: DrivingEventType,
    severity: 'low' | 'medium' | 'high' | 'critical',
    desc: string,
    overrideValues?: Partial<TelemetrySample>
  ) => {
    if (!selectedVehicle) return;

    // Check circular buffer window to capture pre/post video clip info
    const timestamp = new Date().toISOString();
    const eventId = `evt_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
    
    // Create new DrivingEvent
    const newEvent: DrivingEvent = {
      id: eventId,
      vehicle_id: selectedVehicle.id,
      session_id: activeSession?.id || 'session_offline',
      event_type: type,
      severity,
      timestamp,
      speed_kmh_nullable: isObdConnected ? (overrideValues?.speed ?? simSpeed) : null,
      rpm_nullable: isObdConnected ? (overrideValues?.rpm ?? simRpm) : null,
      gps_lat_nullable: isGpsEnabled ? (overrideValues?.lat ?? 9.9281) : null,
      gps_lng_nullable: isGpsEnabled ? (overrideValues?.lng ?? -84.0852) : null,
      g_force_x: overrideValues?.gForceX ?? simGForce.x,
      g_force_y: overrideValues?.gForceY ?? simGForce.y,
      g_force_z: overrideValues?.gForceZ ?? simGForce.z,
      obd_snapshot_id_nullable: isObdConnected ? `snap_${Date.now()}` : null,
      clip_id_nullable: null, // Linked below
      report_id_nullable: null,
      created_at: timestamp
    };

    // Calculate metadata for video clip
    const clipId = `clip_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`;
    const clipTypeMap: Record<DrivingEventType, ClipType> = {
      HARD_BRAKE: 'HARD_BRAKE',
      HARD_ACCELERATION: 'MANUAL',
      IMPACT_DETECTED: 'IMPACT',
      POSSIBLE_COLLISION: 'IMPACT',
      SHARP_TURN: 'MANUAL',
      OVERHEAT: 'OVERHEAT',
      LOW_VOLTAGE: 'LOW_VOLTAGE',
      CRITICAL_DTC: 'DTC_CRITICAL',
      MANUAL_MARKER: 'MANUAL',
      CAMERA_STARTED: 'MANUAL',
      CAMERA_STOPPED: 'MANUAL'
    };

    // Generate simulated clip file hash SHA-256
    const metadataString = `${eventId}|${clipId}|${selectedVehicle.vin_nullable || 'VIN-UNKNOWN'}|${timestamp}|${severity}`;
    
    sha256Hex(metadataString).then(hash => {
      const newClip: DashcamClip = {
        id: clipId,
        session_id: activeSession?.id || 'session_offline',
        vehicle_id: selectedVehicle.id,
        event_id_nullable: eventId,
        clip_type: clipTypeMap[type] || 'MANUAL',
        start_time: new Date(Date.now() - 10000).toISOString(), // 10s before event
        end_time: new Date(Date.now() + 10000).toISOString(),   // 10s after event
        duration_sec: 20,
        video_uri: `/storage/clips/meet_dashcam_${clipId}.mp4`,
        thumbnail_uri: 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="60" viewBox="0 0 100 60"><rect width="100%" height="100%" fill="%230f172a"/><text x="50%" y="50%" fill="%2300f0ff" font-family="monospace" font-size="8" dominant-baseline="middle" text-anchor="middle">CLIP EVIDENCE</text></svg>',
        telemetry_overlay_enabled: true,
        raw_telemetry_uri_nullable: `/storage/telemetry/meet_telemetry_${clipId}.json`,
        hash_sha256: hash,
        locked: severity !== 'low', // Auto lock critical events
        created_at: timestamp
      };

      newEvent.clip_id_nullable = clipId;
      onAddClip(newClip);
      onAddEvent(newEvent);

      // Ingest to Garage Timeline
      onAddTimelineEvent({
        id: `ev_timeline_${Date.now()}`,
        vehicle_id: selectedVehicle.id,
        event_type: 'INCIDENT_DETECTED',
        title: `Alerta: ${desc}`,
        description: `Evento registrado por la Caja Negra. Tipo: ${type}. Severidad: ${severity.toUpperCase()}. Firma SHA-256 del clip: ${hash.slice(0, 8)}...`,
        severity: severity === 'critical' ? 'critical' : severity === 'high' ? 'high' : 'medium',
        source: 'Caja Negra',
        created_at: timestamp,
        related_report_id_nullable: null,
        related_work_order_id_nullable: null,
        related_part_request_id_nullable: null,
        related_livelink_id_nullable: null
      });

      // B2B Fleet Alerts
      if (mode === 'FLEET' && onAddFleetIncident) {
        onAddFleetIncident({
          id: `fleet_inc_${Date.now()}`,
          vehicle_id: selectedVehicle.id,
          driver_id: 'driver_juan_perez',
          event_type: type,
          severity,
          clip_id: clipId,
          report_id: null,
          status: 'PENDIENTE_REVISION',
          driver_name: fleetDriverName,
          route_name: fleetRouteName
        });
      }

      showNotification(`Caja Negra: ${desc} (Clip protegido guardado)`);
    });
  }, [activeSession, isObdConnected, isGpsEnabled, simSpeed, simRpm, simEct, simVoltage, simGForce, selectedVehicle, onAddClip, onAddEvent, onAddTimelineEvent, mode, fleetDriverName, fleetRouteName, onAddFleetIncident]);

  // Session Handler
  const handleStartSession = () => {
    if (!selectedVehicle) {
      showNotification('Por favor, selecciona un vehículo primero.');
      return;
    }
    const sessionId = `sess_${Date.now()}`;
    const newSession: DashcamSession = {
      id: sessionId,
      vehicle_id: selectedVehicle.id,
      user_id: activeUserId,
      started_at: new Date().toISOString(),
      ended_at_nullable: null,
      mode,
      camera_facing: 'BACK',
      video_enabled: consentGranted,
      audio_enabled: isAudioEnabled,
      gps_enabled: isGpsEnabled,
      obd_enabled: isObdConnected,
      sensor_fusion_enabled: sensorFusionEnabled,
      status: 'ACTIVE',
      storage_path: `/storage/sessions/${sessionId}`,
      created_at: new Date().toISOString()
    };

    onAddSession(newSession);
    setActiveSession(newSession);
    setIsRecording(true);
    setBufferSamples([]);

    onAddTimelineEvent({
      id: `ev_timeline_${Date.now()}`,
      vehicle_id: selectedVehicle.id,
      event_type: 'DASHCAM_SESSION_STARTED',
      title: 'Sesión Dashcam Iniciada',
      description: `Se inició la grabación técnica de telemetría. Modo: ${mode}. OBD: ${isObdConnected ? 'SÍ' : 'NO'}.`,
      severity: 'low',
      source: 'Cámara HUD',
      created_at: new Date().toISOString(),
      related_report_id_nullable: null,
      related_work_order_id_nullable: null,
      related_part_request_id_nullable: null,
      related_livelink_id_nullable: null
    });

    showNotification('Grabación técnica iniciada. Buffer circular activo.');
  };

  const handleStopSession = () => {
    if (!activeSession || !selectedVehicle) return;

    // Trigger timeline event
    onAddTimelineEvent({
      id: `ev_timeline_${Date.now()}`,
      vehicle_id: selectedVehicle.id,
      event_type: 'DASHCAM_SESSION_ENDED',
      title: 'Sesión Dashcam Finalizada',
      description: `Sesión de dashcam completada. Ruta guardada.`,
      severity: 'low',
      source: 'Cámara HUD',
      created_at: new Date().toISOString(),
      related_report_id_nullable: null,
      related_work_order_id_nullable: null,
      related_part_request_id_nullable: null,
      related_livelink_id_nullable: null
    });

    setIsRecording(false);
    setActiveSession(null);
    showNotification('Sesión finalizada y guardada.');
  };

  // Simulating events manually for verification (Test cases)
  const triggerSimulatedHardBrake = () => {
    setSimSpeed(95);
    setSimGForce({ x: 0.1, y: -0.85, z: 0.9 }); // Spike vertical
    showNotification('Simulando frenada fuerte de emergencia...');
    setTimeout(() => {
      setSimSpeed(45);
      runDetection('HARD_BRAKE', 'medium', 'Desaceleración fuerte brusca detectada');
    }, 600);
    setTimeout(() => {
      setSimSpeed(50);
      setSimGForce({ x: 0.02, y: -0.05, z: 0.98 });
    }, 1500);
  };

  const triggerSimulatedImpact = () => {
    setSimGForce({ x: 1.9, y: 1.2, z: 0.5 }); // Vectorial sum > 1.8G
    showNotification('Simulando impacto / fuerza G extrema...');
    setTimeout(() => {
      setSimSpeed(0);
      setSimRpm(0);
      runDetection('IMPACT_DETECTED', 'critical', 'Colisión posible / Aceleración vectorial anormal');
    }, 200);
    setTimeout(() => {
      setSimGForce({ x: 0.01, y: -0.01, z: 1.0 });
    }, 2000);
  };

  const triggerSimulatedOverheat = () => {
    setSimEct(108);
    showNotification('Simulando calentamiento excesivo...');
    setTimeout(() => {
      runDetection('OVERHEAT', 'high', 'Temperatura de refrigerante crítica (108°C)');
    }, 500);
  };

  const triggerSimulatedLowVoltage = () => {
    setSimVoltage(11.8);
    showNotification('Simulando alternador inestable...');
    setTimeout(() => {
      runDetection('LOW_VOLTAGE', 'medium', 'Tensión de sistema baja (11.8V) - Alerta de carga');
    }, 500);
  };

  const triggerSimulatedCriticalDtc = () => {
    setActiveDtc('P0302');
    showNotification('Detectando falla de encendido activa...');
    setTimeout(() => {
      runDetection('CRITICAL_DTC', 'high', 'Código DTC crítico detectado: P0302 (Misfire Cilindro 2)');
    }, 500);
  };

  // Generate Incident PDF Report logic
  const handleOpenPdfReport = (clip: DashcamClip) => {
    setSelectedClipForReport(clip);
    setShowPdfPreview(true);
  };

  const handlePrintPdf = () => {
    window.print();
  };

  // Auto clean unprotected clips mock
  const handleTriggerAutoCleanup = () => {
    const originalCount = clips.length;
    const lockedCount = clips.filter(c => c.locked).length;
    // Delete unlocked clips
    clips.forEach(c => {
      if (!c.locked) {
        onDeleteClip(c.id);
      }
    });
    showNotification(`Limpieza automática completada. Se eliminaron ${originalCount - lockedCount} clips antiguos sin protección. ${lockedCount} clips permanecen protegidos.`);
  };

  // Handle incoming LiveLink Request
  const handleAcceptLiveLinkStream = () => {
    setLiveLinkIncomingRequest(false);
    setConsentGranted(true);
    setIsAudioEnabled(true);
    setIsGpsEnabled(true);
    handleRequestCamera();
    showNotification('Conexión LiveLink activa: compartiendo cámara con mecánico remoto.');
  };

  return (
    <div className="space-y-6 text-slate-100 font-sans">
      
      {/* Dynamic Alert Banner */}
      {currentNotification && (
        <div className="fixed top-24 left-1/2 transform -translate-x-1/2 z-[100] bg-cyan-900 border border-cyan-400 text-cyan-200 px-6 py-3 rounded-xl shadow-2xl flex items-center gap-3 animate-bounce">
          <Activity size={18} className="text-cyan-400 animate-pulse" />
          <span className="font-mono text-xs font-bold uppercase tracking-wider">{currentNotification}</span>
        </div>
      )}

      {/* PDF Report Preview Overlay */}
      {showPdfPreview && selectedClipForReport && (
        <div className="fixed inset-0 bg-slate-950/95 backdrop-blur-md z-[100] flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-slate-900 border border-slate-700/50 rounded-2xl w-full max-w-4xl shadow-2xl overflow-hidden my-8 print:bg-white print:border-none print:shadow-none">
            {/* Action Bar */}
            <div className="bg-slate-950/80 p-4 border-b border-slate-800 flex justify-between items-center print:hidden">
              <span className="font-mono font-bold text-xs tracking-wider text-slate-400">EXPEDIENTE CERTIFICADO DE INCIDENTE</span>
              <div className="flex gap-2">
                <button 
                  onClick={handlePrintPdf}
                  className="flex items-center gap-1.5 bg-cyan-500 hover:bg-cyan-600 text-black px-4 py-2 rounded-xl text-xs font-bold font-mono transition-all"
                >
                  <Download size={14} /> Imprimir / PDF
                </button>
                <button 
                  onClick={() => setShowPdfPreview(false)}
                  className="bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 px-4 py-2 rounded-xl text-xs font-bold font-mono transition-all"
                >
                  Cerrar Vista
                </button>
              </div>
            </div>

            {/* Document Body */}
            <div id="pdf-report-content" className="p-8 space-y-8 bg-slate-900 text-slate-200 print:text-black print:bg-white font-mono text-xs">
              
              {/* Header */}
              <div className="border-b-2 border-cyan-500 pb-6 flex justify-between items-start">
                <div>
                  <h1 className="text-xl font-black text-white print:text-black tracking-widest flex items-center gap-2">
                    ELYSIUM <span className="text-cyan-400">VANGUARD CERTIFICATE</span>
                  </h1>
                  <p className="text-[9px] text-slate-400 tracking-wider mt-1">SISTEMA AUTOMOTRIZ DE REGISTRO FORENSE DE EVIDENCIA</p>
                </div>
                <div className="text-right">
                  <p className="text-slate-300 print:text-black font-bold">REPORTE #INC-{selectedClipForReport.id.split('_')[1]}</p>
                  <p className="text-[10px] text-slate-500">Generado: {new Date(selectedClipForReport.created_at).toLocaleString()}</p>
                </div>
              </div>

              {/* Disclaimer */}
              <div className="bg-cyan-950/20 border border-cyan-500/30 p-4 rounded-xl print:border-black/20">
                <p className="text-cyan-300 print:text-black leading-relaxed font-sans text-xs">
                  <strong>AVISO LEGAL:</strong> Reporte técnico de telemetría y evidencia digital generado de forma automatizada por la aplicación MEET. La interpretación jurídica o pericial de los datos expuestos es competencia exclusiva de las autoridades policiales, compañías aseguradoras o peritos judiciales correspondientes.
                </p>
              </div>

              {/* Grid Metadata */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                
                {/* Vehículo y Viaje */}
                <div className="space-y-3 bg-slate-950/40 p-4 rounded-xl border border-slate-800 print:bg-slate-50 print:border-black/10">
                  <h3 className="font-bold text-white print:text-black border-b border-slate-800 pb-1.5 uppercase text-[10px] tracking-wider text-cyan-400">DATOS DEL VEHÍCULO</h3>
                  <div className="grid grid-cols-2 gap-2 text-[11px]">
                    <span className="text-slate-400">Marca / Modelo:</span>
                    <span className="text-right text-slate-200 print:text-black">{selectedVehicle?.brand} {selectedVehicle?.model}</span>
                    <span className="text-slate-400">Año:</span>
                    <span className="text-right text-slate-200 print:text-black">{selectedVehicle?.year}</span>
                    <span className="text-slate-400">Placa:</span>
                    <span className="text-right text-slate-200 print:text-black">{selectedVehicle?.plate_nullable || 'DATO NO CAPTURADO'}</span>
                    <span className="text-slate-400">Kilometraje:</span>
                    <span className="text-right text-slate-200 print:text-black">{selectedVehicle?.odometer_km ? `${selectedVehicle.odometer_km.toLocaleString()} km` : 'DATO NO CAPTURADO'}</span>
                    <span className="text-slate-400">VIN del Vehículo:</span>
                    <span className="text-right text-slate-200 print:text-black text-[10px]">{selectedVehicle?.vin_nullable || 'DATO NO CAPTURADO'}</span>
                  </div>
                </div>

                {/* Resumen del Incidente */}
                <div className="space-y-3 bg-slate-950/40 p-4 rounded-xl border border-slate-800 print:bg-slate-50 print:border-black/10">
                  <h3 className="font-bold text-white print:text-black border-b border-slate-800 pb-1.5 uppercase text-[10px] tracking-wider text-cyan-400">DETALLES DEL EVENTO</h3>
                  <div className="grid grid-cols-2 gap-2 text-[11px]">
                    <span className="text-slate-400">Tipo de Evento:</span>
                    <span className="text-right font-bold text-cyan-300 print:text-black">{selectedClipForReport.clip_type}</span>
                    <span className="text-slate-400">Severidad:</span>
                    <span className="text-right text-rose-400 print:text-black font-bold uppercase">{selectedClipForReport.locked ? 'PROTEGIDO / EVENTO CRÍTICO' : 'INFORMACIÓN BÁSICA'}</span>
                    <span className="text-slate-400">Duración Grabación:</span>
                    <span className="text-right text-slate-200 print:text-black">{selectedClipForReport.duration_sec} Segundos</span>
                    <span className="text-slate-400">Coordenadas GPS:</span>
                    <span className="text-right text-slate-200 print:text-black">{isGpsEnabled ? '9.9281 N, 84.0852 W' : 'UBICACIÓN OFF'}</span>
                    <span className="text-slate-400">Hora de Inicio:</span>
                    <span className="text-right text-slate-200 print:text-black">{new Date(selectedClipForReport.start_time).toLocaleString()}</span>
                  </div>
                </div>

              </div>

              {/* Telemetry Snapshot Table */}
              <div className="space-y-3">
                <h3 className="font-bold text-white print:text-black border-b border-slate-800 pb-1.5 uppercase text-[10px] tracking-wider text-cyan-400">SNAPSHOT TÉCNICO DE TELEMETRÍA (T-0)</h3>
                <div className="border border-slate-800 rounded-xl overflow-hidden print:border-black/20">
                  <table className="w-full text-[11px] text-left">
                    <thead className="bg-slate-950/80 text-slate-400 border-b border-slate-800 print:bg-slate-100 print:text-black">
                      <tr>
                        <th className="p-3">Parámetro</th>
                        <th className="p-3">Valor de Registro</th>
                        <th className="p-3">Fuente</th>
                        <th className="p-3">Calidad del Enlace</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800 print:divide-black/10">
                      <tr>
                        <td className="p-3 font-bold text-slate-300">Velocidad del Vehículo</td>
                        <td className="p-3">{isObdConnected ? `${simSpeed} km/h` : 'OBD no disponible'}</td>
                        <td className="p-3 font-mono text-[10px]">{isObdConnected ? 'REAL_OBD' : 'GPS / SIMULATED'}</td>
                        <td className="p-3 text-emerald-400 font-bold">BUENA</td>
                      </tr>
                      <tr>
                        <td className="p-3 font-bold text-slate-300">Régimen de Giro (RPM)</td>
                        <td className="p-3">{isObdConnected ? `${simRpm} rpm` : 'OBD no disponible'}</td>
                        <td className="p-3 font-mono text-[10px]">{isObdConnected ? 'REAL_OBD' : 'SIN ENLACE'}</td>
                        <td className="p-3 text-emerald-400 font-bold">BUENA</td>
                      </tr>
                      <tr>
                        <td className="p-3 font-bold text-slate-300">Temperatura Motor (ECT)</td>
                        <td className="p-3">{isObdConnected ? `${simEct} °C` : 'OBD no disponible'}</td>
                        <td className="p-3 font-mono text-[10px]">{isObdConnected ? 'REAL_OBD' : 'SIN ENLACE'}</td>
                        <td className="p-3 text-emerald-400 font-bold">BUENA</td>
                      </tr>
                      <tr>
                        <td className="p-3 font-bold text-slate-300">Tensión Eléctrica</td>
                        <td className="p-3">{isObdConnected ? `${simVoltage} V` : 'OBD no disponible'}</td>
                        <td className="p-3 font-mono text-[10px]">{isObdConnected ? 'REAL_OBD' : 'SIN ENLACE'}</td>
                        <td className="p-3 text-emerald-400 font-bold">BUENA</td>
                      </tr>
                      <tr>
                        <td className="p-3 font-bold text-slate-300">Fuerza G Máxima Alcanzada</td>
                        <td className="p-3">X: {simGForce.x.toFixed(2)}g, Y: {simGForce.y.toFixed(2)}g, Z: {simGForce.z.toFixed(2)}g</td>
                        <td className="p-3 font-mono text-[10px]">ACELERÓMETRO INTERNO</td>
                        <td className="p-3 text-emerald-400 font-bold">DISPOSITIVO NATIVO</td>
                      </tr>
                      <tr>
                        <td className="p-3 font-bold text-slate-300">Códigos DTC Activos</td>
                        <td className="p-3 text-rose-400 font-bold">{activeDtc || 'NINGÚN DTC ACTIVO'}</td>
                        <td className="p-3 font-mono text-[10px]">SCANNER OBD2</td>
                        <td className="p-3 text-slate-400">EVALUADO</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Cryptographic Signature & Verification */}
              <div className="border-t border-slate-800 pt-6 flex flex-col md:flex-row justify-between items-center gap-6 print:border-black/20">
                <div className="space-y-2 max-w-lg">
                  <h4 className="font-bold text-white print:text-black uppercase text-[10px] tracking-wider text-cyan-400">FIRMA CRIPTOGRÁFICA DE INTEGRIDAD (SHA-256)</h4>
                  <p className="font-mono text-cyan-300 print:text-black text-[11px] break-all bg-slate-950/60 p-3 rounded-lg border border-slate-800 print:bg-slate-50 print:border-black/10">
                    {selectedClipForReport.hash_sha256}
                  </p>
                  <p className="text-[10px] text-slate-500 font-sans leading-relaxed">
                    Cualquier alteración en los bytes del video o en los metadatos de telemetría invalidará de inmediato la firma.
                  </p>
                </div>
                
                {/* Simulated Verification QR */}
                <div className="bg-white p-3 rounded-xl border border-slate-700/50 print:border-black/20 text-center shrink-0">
                  <div className="w-24 h-24 bg-slate-900 flex flex-col items-center justify-center border border-slate-700">
                    {/* Retro UI Simulated QR */}
                    <div className="grid grid-cols-4 gap-1 p-2 w-full h-full bg-white text-black">
                      <div className="bg-black"></div><div className="bg-black"></div><div className="bg-white"></div><div className="bg-black"></div>
                      <div className="bg-black"></div><div className="bg-white"></div><div className="bg-black"></div><div className="bg-white"></div>
                      <div className="bg-white"></div><div className="bg-black"></div><div className="bg-black"></div><div className="bg-black"></div>
                      <div className="bg-black"></div><div className="bg-white"></div><div className="bg-white"></div><div className="bg-black"></div>
                    </div>
                  </div>
                  <span className="text-[8px] text-slate-800 font-bold block mt-1">Escanear para Verificar</span>
                </div>
              </div>

            </div>
          </div>
        </div>
      )}

      {/* LiveLink Request Toast */}
      {liveLinkIncomingRequest && (
        <div className="bg-indigo-900/90 border border-indigo-400 text-indigo-100 p-5 rounded-2xl shadow-xl flex flex-col md:flex-row justify-between items-center gap-4 animate-pulse">
          <div className="flex items-center gap-3">
            <Tv size={24} className="text-indigo-400" />
            <div>
              <p className="font-bold text-sm">Petición Externa de LiveLink</p>
              <p className="text-xs text-indigo-200">El mecánico remoto solicita capturar un clip técnico de diagnóstico.</p>
            </div>
          </div>
          <div className="flex gap-2">
            <button 
              onClick={handleAcceptLiveLinkStream}
              className="bg-indigo-500 hover:bg-indigo-600 text-white px-4 py-2 rounded-xl text-xs font-bold font-mono transition-all"
            >
              Aprobar y Grabar
            </button>
            <button 
              onClick={() => setLiveLinkIncomingRequest(false)}
              className="bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2 rounded-xl text-xs font-bold font-mono transition-all"
            >
              Denegar
            </button>
          </div>
        </div>
      )}

      {/* Screen Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border-b border-white/10 pb-4">
        <div>
          <h2 className="text-2xl font-black text-white font-display tracking-wider flex items-center gap-2.5">
            CÁMARA HUD / <span className="text-cyan-400">TESTIGO VEHICULAR</span>
          </h2>
          <p className="text-xs text-slate-400 font-mono tracking-wide mt-1">Caja Negra Automotriz • Dashcam de Evidencias Integrada • Paridad Criptográfica</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setCurrentTab('live')}
            className={`px-4 py-2 rounded-xl font-mono text-xs font-bold uppercase transition-all flex items-center gap-1.5 ${currentTab === 'live' ? 'bg-cyan-500 text-black font-black shadow-[0_0_15px_rgba(0,240,255,0.4)]' : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'}`}
          >
            <Camera size={13} /> Panel HUD
          </button>
          <button
            onClick={() => setCurrentTab('gallery')}
            className={`px-4 py-2 rounded-xl font-mono text-xs font-bold uppercase transition-all flex items-center gap-1.5 ${currentTab === 'gallery' ? 'bg-cyan-500 text-black font-black shadow-[0_0_15px_rgba(0,240,255,0.4)]' : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'}`}
          >
            <History size={13} /> Caja de Evidencias
          </button>
          <button
            onClick={() => setCurrentTab('settings')}
            className={`px-4 py-2 rounded-xl font-mono text-xs font-bold uppercase transition-all flex items-center gap-1.5 ${currentTab === 'settings' ? 'bg-cyan-500 text-black font-black shadow-[0_0_15px_rgba(0,240,255,0.4)]' : 'bg-white/5 border border-white/10 text-slate-400 hover:text-white'}`}
          >
            <Settings size={13} /> Ajustes
          </button>
          {onClose && (
            <button
              onClick={onClose}
              className="bg-rose-500/20 hover:bg-rose-500/30 text-rose-400 border border-rose-500/30 px-3 py-2 rounded-xl font-mono text-xs font-bold"
            >
              Cerrar
            </button>
          )}
        </div>
      </div>

      {/* Main Tab Content */}
      {currentTab === 'live' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          
          {/* Left Column: Visual Stream & HUD overlay */}
          <div className="lg:col-span-2 space-y-4">
            
            {/* Viewfinder block */}
            <div className="relative aspect-video rounded-3xl overflow-hidden border border-white/10 bg-black shadow-2xl flex items-center justify-center">
              
              {/* Real Camera Video Tag */}
              {consentGranted && cameraStream ? (
                <video 
                  ref={videoRef} 
                  autoPlay 
                  playsInline 
                  muted 
                  className="absolute inset-0 w-full h-full object-cover"
                />
              ) : null}

              {/* Dynamic canvas Road Simulation / Overlay */}
              <canvas 
                ref={canvasRef} 
                width={800} 
                height={450} 
                className="absolute inset-0 w-full h-full pointer-events-none z-10"
              />

              {/* Interactive Telemetry Overlay (Skins) */}
              <div className="absolute inset-0 z-20 flex flex-col justify-between p-6 pointer-events-none font-mono text-xs text-white">
                
                {/* Overlay Top Bar */}
                <div className="flex justify-between items-start">
                  {/* OBD State Info */}
                  <div className="bg-black/75 px-3 py-1.5 rounded-lg border border-white/10 flex items-center gap-2 pointer-events-auto">
                    <div className={`w-2 h-2 rounded-full ${isObdConnected ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'}`} />
                    <span className="text-[10px] font-bold">
                      {isObdConnected ? 'OBD: CONECTADO' : 'OBD SIN ENLACE'}
                    </span>
                  </div>
                  
                  {/* GPS & Location overlays */}
                  <div className="flex gap-2 pointer-events-auto">
                    {isGpsEnabled ? (
                      <div className="bg-black/75 px-3 py-1.5 rounded-lg border border-white/10 flex items-center gap-1.5 text-emerald-400">
                        <MapPin size={12} />
                        <span className="text-[10px] font-bold">GPS LOCK</span>
                      </div>
                    ) : (
                      <div className="bg-black/75 px-3 py-1.5 rounded-lg border border-white/10 flex items-center gap-1.5 text-slate-500">
                        <MapPinOff size={12} />
                        <span className="text-[10px] font-bold">GPS OFF</span>
                      </div>
                    )}
                    
                    {isAudioEnabled ? (
                      <div className="bg-black/75 px-3 py-1.5 rounded-lg border border-slate-700 flex items-center gap-1.5 text-emerald-400">
                        <Volume2 size={12} />
                        <span className="text-[10px] font-bold">MIC ON</span>
                      </div>
                    ) : (
                      <div className="bg-black/75 px-3 py-1.5 rounded-lg border border-slate-800 flex items-center gap-1.5 text-slate-500">
                        <VolumeX size={12} />
                        <span className="text-[10px] font-bold">MIC OFF</span>
                      </div>
                    )}
                  </div>
                </div>

                {/* HUD Center Telemetry Gauges depending on Skin */}
                {activeSkin === 'NEON_DIGITAL' && (
                  <div className="flex justify-between items-end mt-auto pt-16">
                    {/* RPM Gauge bar */}
                    <div className="bg-black/85 border border-cyan-500/20 p-3.5 rounded-2xl flex flex-col gap-1 w-32 pointer-events-auto shadow-2xl">
                      <span className="text-[9px] text-cyan-400 font-bold uppercase tracking-wider">Motor RPM</span>
                      <span className="text-xl font-black text-white">{isObdConnected ? simRpm : 'OBD sin enlace'}</span>
                      <div className="w-full bg-slate-950 h-1.5 rounded-full overflow-hidden mt-1">
                        <div 
                          className="bg-cyan-500 h-full transition-all duration-300"
                          style={{ width: isObdConnected ? `${(simRpm / 6000) * 100}%` : '0%' }}
                        />
                      </div>
                    </div>

                    {/* Speedometer Center Ring */}
                    <div className="bg-black/85 border border-cyan-400 p-5 rounded-full flex flex-col items-center justify-center w-28 h-28 pointer-events-auto shadow-[0_0_20px_rgba(0,240,255,0.15)]">
                      <span className="text-[8px] text-slate-400 font-bold uppercase tracking-widest">VELOCIDAD</span>
                      <span className="text-3xl font-black text-cyan-400 tracking-tight leading-none mt-1">
                        {isObdConnected ? simSpeed : 'GPS'}
                      </span>
                      <span className="text-[8px] font-black text-white mt-1">KM/H</span>
                    </div>

                    {/* Right Info: Coolant/Volt */}
                    <div className="bg-black/85 border border-cyan-500/20 p-3.5 rounded-2xl flex flex-col gap-1 w-32 pointer-events-auto shadow-2xl">
                      <span className="text-[9px] text-cyan-400 font-bold uppercase tracking-wider">ECT Temp</span>
                      <span className={`text-sm font-bold ${isObdConnected && simEct > 100 ? 'text-red-400 animate-pulse' : 'text-white'}`}>
                        {isObdConnected ? `${simEct}°C` : 'OBD sin enlace'}
                      </span>
                      <div className="w-full bg-slate-950 h-1 rounded-full overflow-hidden mt-0.5">
                        <div 
                          className={`h-full transition-all ${simEct > 100 ? 'bg-red-500' : 'bg-cyan-500'}`}
                          style={{ width: isObdConnected ? `${(simEct / 130) * 100}%` : '0%' }}
                        />
                      </div>
                      <span className="text-[9px] text-cyan-400 font-bold uppercase tracking-wider mt-1.5">Alternador</span>
                      <span className="text-xs font-bold text-white">
                        {isObdConnected ? `${simVoltage.toFixed(1)}V` : 'OBD sin enlace'}
                      </span>
                    </div>
                  </div>
                )}

                {activeSkin === 'PREMIUM_COCKPIT' && (
                  <div className="flex justify-between items-center mt-auto bg-black/90 p-4 rounded-2xl border border-slate-700/50 pointer-events-auto">
                    <div className="flex gap-4">
                      <div>
                        <span className="text-[9px] text-amber-500 font-bold block uppercase">OBD Speed</span>
                        <span className="text-lg font-black text-white">{isObdConnected ? `${simSpeed} KM/H` : 'OBD sin enlace'}</span>
                      </div>
                      <div className="border-l border-slate-800 pl-4">
                        <span className="text-[9px] text-emerald-400 font-bold block uppercase">GPS Speed</span>
                        <span className="text-lg font-black text-white">{isGpsEnabled ? '83 KM/H' : 'GPS OFF'}</span>
                      </div>
                    </div>
                    <div className="flex gap-4">
                      <div className="text-right">
                        <span className="text-[9px] text-slate-400 font-bold block uppercase">Fuerzas G</span>
                        <span className="text-xs font-mono font-bold text-white">
                          X:{simGForce.x.toFixed(2)} Y:{simGForce.y.toFixed(2)}
                        </span>
                      </div>
                      <div className="border-l border-slate-800 pl-4 text-right">
                        <span className="text-[9px] text-red-500 font-bold block uppercase">DTC Estado</span>
                        <span className="text-xs font-bold text-rose-400 font-mono">
                          {activeDtc ? `DTC: ${activeDtc}` : 'SANO'}
                        </span>
                      </div>
                    </div>
                  </div>
                )}

                {activeSkin === 'MINIMAL_HUD' && (
                  <div className="mt-auto flex flex-col items-center justify-center bg-black/50 p-2 rounded-xl">
                    <span className="text-6xl font-black text-white tracking-tighter">
                      {isObdConnected ? simSpeed : '83'}
                    </span>
                    <span className="text-[9px] text-slate-400 uppercase tracking-widest font-black">KM/H · HUD REFLECT</span>
                  </div>
                )}

              </div>
            </div>

            {/* Live Controller Dashboard controls (OBD Simulator & Physics controls) */}
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl space-y-4">
              <div className="flex justify-between items-center border-b border-slate-800 pb-3">
                <h3 className="text-sm font-bold text-white font-mono uppercase tracking-wider flex items-center gap-2">
                  <Sliders className="text-cyan-400" size={16} /> Panel de Simulación / Pruebas Físicas
                </h3>
                <span className="text-[10px] bg-cyan-950 text-cyan-400 px-2 py-0.5 rounded font-mono border border-cyan-800">CONSOLA</span>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                
                {/* Physics triggers */}
                <div className="bg-slate-950/40 p-3.5 rounded-xl border border-slate-800 flex flex-col gap-2">
                  <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider">Sensores e Impacto</span>
                  <button 
                    onClick={triggerSimulatedHardBrake}
                    className="bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-amber-300 font-mono font-bold text-xs py-2 px-3 rounded-lg transition-all text-left flex justify-between items-center"
                  >
                    <span>Frenado Violento</span>
                    <span className="text-[9px] bg-amber-950 px-1.5 py-0.5 rounded">G_y: -0.85g</span>
                  </button>
                  <button 
                    onClick={triggerSimulatedImpact}
                    className="bg-red-500/10 hover:bg-red-500/20 border border-red-500/30 text-red-300 font-mono font-bold text-xs py-2 px-3 rounded-lg transition-all text-left flex justify-between items-center"
                  >
                    <span>Impacto Colisión</span>
                    <span className="text-[9px] bg-red-950 px-1.5 py-0.5 rounded">G_x: 1.90g</span>
                  </button>
                </div>

                {/* OBD engine triggers */}
                <div className="bg-slate-950/40 p-3.5 rounded-xl border border-slate-800 flex flex-col gap-2">
                  <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider">Parámetros OBD</span>
                  <button 
                    onClick={triggerSimulatedOverheat}
                    className="bg-orange-500/10 hover:bg-orange-500/20 border border-orange-500/30 text-orange-300 font-mono font-bold text-xs py-2 px-3 rounded-lg transition-all text-left flex justify-between items-center"
                  >
                    <span>Sobrecalentar</span>
                    <span className="text-[9px] bg-orange-950 px-1.5 py-0.5 rounded">ECT: 108°C</span>
                  </button>
                  <button 
                    onClick={triggerSimulatedLowVoltage}
                    className="bg-yellow-500/10 hover:bg-yellow-500/20 border border-yellow-500/30 text-yellow-300 font-mono font-bold text-xs py-2 px-3 rounded-lg transition-all text-left flex justify-between items-center"
                  >
                    <span>Bajo Voltaje</span>
                    <span className="text-[9px] bg-yellow-950 px-1.5 py-0.5 rounded">Volt: 11.8V</span>
                  </button>
                </div>

                {/* DTC and other triggers */}
                <div className="bg-slate-950/40 p-3.5 rounded-xl border border-slate-800 flex flex-col gap-2">
                  <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider">Fallas de Código</span>
                  <button 
                    onClick={triggerSimulatedCriticalDtc}
                    className="bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-300 font-mono font-bold text-xs py-2 px-3 rounded-lg transition-all text-left flex justify-between items-center"
                  >
                    <span>DTC Crítico</span>
                    <span className="text-[9px] bg-rose-950 px-1.5 py-0.5 rounded">P0302</span>
                  </button>
                  <button 
                    onClick={() => {
                      setSimSpeed(85);
                      setSimRpm(2400);
                      setSimEct(90);
                      setSimVoltage(14.1);
                      setSimGForce({ x: 0.02, y: -0.05, z: 0.98 });
                      setActiveDtc(null);
                      showNotification('Estado del vehículo restaurado a parámetros normales.');
                    }}
                    className="bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 font-mono font-bold text-xs py-2 px-3 rounded-lg transition-all"
                  >
                    Restablecer Telemetría
                  </button>
                </div>

              </div>

              {/* Direct manual control sliders for testing overlays */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-slate-800 pt-3">
                <div className="flex flex-col gap-1 text-[11px]">
                  <div className="flex justify-between font-mono">
                    <span className="text-slate-400">Control Velocidad OBD (Simulador)</span>
                    <span className="text-cyan-400 font-bold">{simSpeed} km/h</span>
                  </div>
                  <input 
                    type="range" 
                    min="0" 
                    max="180" 
                    value={simSpeed}
                    onChange={(e) => setSimSpeed(parseInt(e.target.value))}
                    className="w-full h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-cyan-400"
                  />
                </div>
                <div className="flex flex-col gap-1 text-[11px]">
                  <div className="flex justify-between font-mono">
                    <span className="text-slate-400">Control Régimen Motor (RPM)</span>
                    <span className="text-cyan-400 font-bold">{simRpm} rpm</span>
                  </div>
                  <input 
                    type="range" 
                    min="600" 
                    max="6000" 
                    value={simRpm}
                    onChange={(e) => setSimRpm(parseInt(e.target.value))}
                    className="w-full h-1 bg-slate-950 rounded-lg appearance-none cursor-pointer accent-cyan-400"
                  />
                </div>
              </div>

            </div>

          </div>

          {/* Right Column: Sessions details, Toggles, Modes and quick stats */}
          <div className="space-y-4">
            
            {/* Active vehicle card */}
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl space-y-4">
              <h3 className="text-xs font-bold text-white font-mono uppercase tracking-wider border-b border-slate-800 pb-2">Expediente Activo</h3>
              <div className="flex items-center gap-3">
                <div className="bg-cyan-500/10 p-2.5 rounded-xl text-cyan-400 border border-cyan-500/30">
                  <Shield size={20} />
                </div>
                <div className="flex-1 min-w-0">
                  <select 
                    value={selectedVehicleId} 
                    onChange={(e) => setSelectedVehicleId(e.target.value)}
                    className="bg-slate-950 border border-slate-800 text-white font-mono text-xs font-bold rounded-xl py-1.5 px-2.5 w-full focus:outline-none focus:border-cyan-400"
                  >
                    {vehicles.map(v => (
                      <option key={v.id} value={v.id}>{v.nickname}</option>
                    ))}
                  </select>
                  <p className="text-[9px] text-slate-500 font-mono mt-1">VIN: {selectedVehicle?.vin_nullable || 'SIN REGISTRO VIN'}</p>
                </div>
              </div>
            </div>

            {/* Session Mode Selector */}
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl space-y-4">
              <h3 className="text-xs font-bold text-white font-mono uppercase tracking-wider border-b border-slate-800 pb-2">Modo de Operación</h3>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { id: 'HUD', label: 'HUD Cabina', icon: <Tv size={12} /> },
                  { id: 'DASHCAM', label: 'Dashcam Cont.', icon: <Video size={12} /> },
                  { id: 'BLACK_BOX', label: 'Caja Negra', icon: <ShieldAlert size={12} /> },
                  { id: 'FLEET', label: 'Fleet Mode', icon: <Users size={12} /> },
                ].map(item => (
                  <button
                    key={item.id}
                    disabled={isRecording}
                    onClick={() => setMode(item.id as RecordingMode)}
                    className={`flex items-center gap-1.5 p-2.5 rounded-xl border font-mono text-[10px] font-bold uppercase transition-all ${mode === item.id ? 'bg-cyan-950/40 text-cyan-400 border-cyan-500' : 'bg-slate-950 text-slate-400 border-slate-800 hover:text-white disabled:opacity-50'}`}
                  >
                    {item.icon} {item.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Recording Controls */}
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl space-y-4">
              <h3 className="text-xs font-bold text-white font-mono uppercase tracking-wider border-b border-slate-800 pb-2">Control de Grabación</h3>
              <div className="space-y-3">
                
                {/* Audio, GPS, OBD toggle permissions (privacy first) */}
                <div className="bg-slate-950/60 p-3 rounded-xl border border-slate-800 space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="font-mono text-[10px] text-slate-400 font-bold uppercase">Cámara / Video</span>
                    <button 
                      onClick={handleRequestCamera}
                      className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded border ${consentGranted ? 'bg-emerald-950/40 text-emerald-400 border-emerald-800' : 'bg-slate-800 text-slate-400 border-slate-700'}`}
                    >
                      {consentGranted ? 'CONCEDIDO' : 'SOLICITAR'}
                    </button>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="font-mono text-[10px] text-slate-400 font-bold uppercase">Ubicación (GPS)</span>
                    <button 
                      onClick={() => setIsGpsEnabled(!isGpsEnabled)}
                      className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded border ${isGpsEnabled ? 'bg-emerald-950/40 text-emerald-400 border-emerald-800' : 'bg-rose-950/40 text-rose-400 border-rose-800'}`}
                    >
                      {isGpsEnabled ? 'ON' : 'OFF'}
                    </button>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="font-mono text-[10px] text-slate-400 font-bold uppercase">Audio Micrófono</span>
                    <button 
                      onClick={() => setIsAudioEnabled(!isAudioEnabled)}
                      className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded border ${isAudioEnabled ? 'bg-emerald-950/40 text-emerald-400 border-emerald-800' : 'bg-rose-950/40 text-rose-400 border-rose-800'}`}
                    >
                      {isAudioEnabled ? 'ON' : 'OFF'}
                    </button>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="font-mono text-[10px] text-slate-400 font-bold uppercase">Conexión OBD2 Link</span>
                    <button 
                      onClick={() => setIsObdConnected(!isObdConnected)}
                      className={`text-[9px] font-mono font-bold px-2 py-0.5 rounded border ${isObdConnected ? 'bg-emerald-950/40 text-emerald-400 border-emerald-800' : 'bg-rose-950/40 text-rose-400 border-rose-800'}`}
                    >
                      {isObdConnected ? 'ON' : 'OFF'}
                    </button>
                  </div>
                </div>

                {isRecording ? (
                  <button 
                    onClick={handleStopSession}
                    className="w-full bg-rose-600 hover:bg-rose-700 text-white font-mono font-bold text-xs py-3 px-4 rounded-xl flex items-center justify-center gap-2 transition-all shadow-[0_0_15px_rgba(239,68,68,0.3)]"
                  >
                    <Square size={14} /> Detener Sesión Testigo
                  </button>
                ) : (
                  <button 
                    onClick={handleStartSession}
                    className="w-full bg-cyan-500 hover:bg-cyan-600 text-black font-mono font-black text-xs py-3 px-4 rounded-xl flex items-center justify-center gap-2 transition-all shadow-[0_0_15px_rgba(6,182,212,0.3)]"
                  >
                    <Play size={14} /> Iniciar Sesión Testigo
                  </button>
                )}

                {isRecording && (
                  <button 
                    onClick={() => runDetection('MANUAL_MARKER', 'low', 'Marca manual ingresada por el conductor')}
                    className="w-full bg-slate-800 hover:bg-slate-700 text-slate-300 font-mono font-bold text-xs py-2 px-4 rounded-xl flex items-center justify-center gap-2 border border-slate-700 transition-all"
                  >
                    <Camera size={14} /> Marcar Evento Manual
                  </button>
                )}

              </div>
            </div>

            {/* Skins Customizer */}
            <div className="bg-slate-900 border border-slate-800 p-5 rounded-2xl space-y-4">
              <h3 className="text-xs font-bold text-white font-mono uppercase tracking-wider border-b border-slate-800 pb-2">Estilo Interfaz HUD</h3>
              <div className="flex gap-2">
                {[
                  { id: 'NEON_DIGITAL', label: 'Neon Cyber' },
                  { id: 'PREMIUM_COCKPIT', label: 'Cockpit' },
                  { id: 'MINIMAL_HUD', label: 'Reflect' }
                ].map(skin => (
                  <button
                    key={skin.id}
                    onClick={() => setActiveSkin(skin.id as SkinType)}
                    className={`flex-1 font-mono text-[9px] font-bold p-2 rounded-lg border text-center transition-all ${activeSkin === skin.id ? 'bg-cyan-500 text-black border-transparent' : 'bg-slate-950 text-slate-400 border-slate-800 hover:text-white'}`}
                  >
                    {skin.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Test Trigger Remote LiveLink Captures */}
            <div className="bg-indigo-950/20 border border-indigo-500/20 p-5 rounded-2xl space-y-3">
              <h3 className="text-xs font-bold text-indigo-300 font-mono uppercase tracking-wider">Simulación LiveLink Remoto</h3>
              <p className="text-[10px] text-slate-400 leading-normal">Simula la petición de un mecánico conectado remotamente al vehículo.</p>
              <button 
                onClick={() => setLiveLinkIncomingRequest(true)}
                className="w-full bg-indigo-600/20 hover:bg-indigo-600/30 border border-indigo-500/30 text-indigo-300 font-mono font-bold text-xs py-2 rounded-xl"
              >
                Simular Llamada Mecánico
              </button>
            </div>

          </div>

        </div>
      )}

      {currentTab === 'gallery' && (
        <div className="bg-slate-900 border border-slate-800 p-6 rounded-3xl space-y-6">
          <div className="flex justify-between items-center border-b border-slate-800 pb-4">
            <div>
              <h3 className="text-lg font-black text-white font-display uppercase tracking-wider flex items-center gap-2">
                <History className="text-cyan-400" size={18} /> Historial Caja Negra & Evidencias
              </h3>
              <p className="text-xs text-slate-400">Lista completa de clips protegidos y snapshots de incidentes del vehículo.</p>
            </div>
            <div className="flex gap-2">
              <button 
                onClick={handleTriggerAutoCleanup}
                className="bg-slate-800 hover:bg-slate-700 border border-slate-700 text-rose-400 font-mono font-bold text-xs px-4 py-2 rounded-xl flex items-center gap-1.5 transition-all"
              >
                <Trash2 size={13} /> Limpieza Automática
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {clips.filter(c => c.vehicle_id === selectedVehicle?.id).length === 0 ? (
              <div className="col-span-full text-center py-16 text-slate-400 border-2 border-dashed border-slate-800 rounded-3xl space-y-3">
                <VideoOff size={40} className="mx-auto text-slate-600 animate-pulse" />
                <p className="font-mono text-xs uppercase tracking-widest font-bold">No se han registrado evidencias todavía</p>
                <p className="text-[11px] max-w-sm mx-auto">Realice una simulación de colisión, frenada brusca, o active la dashcam manual para generar clips de seguridad.</p>
              </div>
            ) : (
              clips.filter(c => c.vehicle_id === selectedVehicle?.id).map((clip) => {
                const associatedEvent = events.find(ev => ev.id === clip.event_id_nullable);
                return (
                  <div key={clip.id} className="bg-slate-950/80 rounded-2xl overflow-hidden border border-slate-800 shadow-lg flex flex-col justify-between hover:border-cyan-500/40 transition-all duration-300">
                    
                    {/* Thumbnail video simulation */}
                    <div className="relative aspect-video bg-slate-900 flex items-center justify-center border-b border-slate-800">
                      <div className="absolute inset-0 opacity-20 bg-[radial-gradient(#00f0ff_1px,transparent_1px)] [background-size:16px_16px]" />
                      <div className="text-center z-10 p-4 space-y-2">
                        <span className="text-[10px] bg-cyan-950 text-cyan-400 px-2 py-0.5 rounded border border-cyan-800 font-mono font-bold">
                          {clip.clip_type}
                        </span>
                        <p className="text-[11px] font-bold text-white font-mono mt-1">LOCKED CLIP EVIDENCE</p>
                        <p className="text-[9px] text-slate-500 font-mono truncate">{clip.video_uri}</p>
                      </div>
                      
                      {/* Locking status badge */}
                      <div className="absolute top-3 right-3 bg-black/85 p-1.5 rounded-lg border border-slate-800 pointer-events-auto">
                        <button 
                          onClick={() => {
                            onToggleLockClip(clip.id);
                            showNotification(clip.locked ? 'Clip desprotegido' : 'Clip protegido contra sobreescritura');
                          }}
                          title={clip.locked ? 'Desproteger' : 'Proteger'}
                        >
                          {clip.locked ? (
                            <Lock size={12} className="text-amber-400" />
                          ) : (
                            <Unlock size={12} className="text-slate-400" />
                          )}
                        </button>
                      </div>
                    </div>

                    {/* Metadata summary */}
                    <div className="p-4 space-y-3 flex-1 flex flex-col justify-between">
                      <div className="space-y-2">
                        <div className="flex justify-between items-center text-[10px] font-mono">
                          <span className="text-slate-400">Fecha/Hora:</span>
                          <span className="text-white">{new Date(clip.created_at).toLocaleString()}</span>
                        </div>
                        <div className="flex justify-between items-center text-[10px] font-mono">
                          <span className="text-slate-400">Duración:</span>
                          <span className="text-white">{clip.duration_sec} seg</span>
                        </div>
                        <div className="flex justify-between items-center text-[10px] font-mono">
                          <span className="text-slate-400">Velocidad Evento:</span>
                          <span className="text-cyan-400 font-bold">{associatedEvent?.speed_kmh_nullable ? `${associatedEvent.speed_kmh_nullable} km/h` : 'OBD sin enlace'}</span>
                        </div>
                        <div className="flex justify-between items-center text-[10px] font-mono">
                          <span className="text-slate-400">Firma SHA-256:</span>
                          <span className="text-slate-400 font-mono text-[9px] truncate max-w-[120px]">{clip.hash_sha256}</span>
                        </div>
                      </div>

                      {/* Action buttons */}
                      <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-800/80">
                        <button
                          onClick={() => handleOpenPdfReport(clip)}
                          className="flex items-center justify-center gap-1 bg-cyan-500 hover:bg-cyan-600 text-black text-[10px] font-bold font-mono py-1.5 rounded-lg transition-all"
                        >
                          <FileText size={11} /> Reporte PDF
                        </button>
                        <button
                          onClick={() => {
                            navigator.clipboard.writeText(clip.hash_sha256);
                            showNotification('Hash SHA-256 copiado al portapapeles');
                          }}
                          className="bg-slate-800 hover:bg-slate-700 text-slate-300 text-[10px] font-bold font-mono py-1.5 rounded-lg border border-slate-700 transition-all"
                        >
                          Copiar Hash
                        </button>
                      </div>

                    </div>

                  </div>
                );
              })
            )}
          </div>

        </div>
      )}

      {currentTab === 'settings' && (
        <div className="bg-slate-900 border border-slate-800 p-6 rounded-3xl space-y-6">
          <h3 className="text-lg font-black text-white font-display uppercase tracking-wider border-b border-slate-800 pb-3 flex items-center gap-2">
            <Settings className="text-cyan-400" size={18} /> Configuración del Sistema Dashcam & Caja Negra
          </h3>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            
            {/* Storage Policy */}
            <div className="bg-slate-950/40 p-5 rounded-2xl border border-slate-800 space-y-4">
              <h4 className="font-bold text-white font-mono uppercase text-xs text-cyan-400">Política de Almacenamiento</h4>
              <div className="space-y-3 text-xs">
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Duración del Buffer Circular:</span>
                  <select 
                    value={circularBufferDuration} 
                    onChange={(e) => setCircularBufferDuration(parseInt(e.target.value))}
                    className="bg-slate-900 border border-slate-800 text-white rounded-lg p-1.5 text-xs focus:outline-none"
                  >
                    <option value={30}>30 Segundos</option>
                    <option value={60}>1 Minuto</option>
                    <option value={180}>3 Minutos</option>
                    <option value={300}>5 Minutos</option>
                  </select>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Límite de Almacenamiento Dashcam:</span>
                  <span className="text-white font-bold font-mono">10 GB máximo</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Resolución de Grabación:</span>
                  <select 
                    value={resolution} 
                    onChange={(e) => setResolution(e.target.value as any)}
                    className="bg-slate-900 border border-slate-800 text-white rounded-lg p-1.5 text-xs focus:outline-none"
                  >
                    <option value="720p">720p (Por defecto)</option>
                    <option value="1080p">1080p HD</option>
                  </select>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Fotogramas por Segundo (FPS):</span>
                  <select 
                    value={fps} 
                    onChange={(e) => setFps(parseInt(e.target.value))}
                    className="bg-slate-900 border border-slate-800 text-white rounded-lg p-1.5 text-xs focus:outline-none"
                  >
                    <option value={30}>30 FPS</option>
                    <option value={60}>60 FPS</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Performance Policy */}
            <div className="bg-slate-950/40 p-5 rounded-2xl border border-slate-800 space-y-4">
              <h4 className="font-bold text-white font-mono uppercase text-xs text-cyan-400">Ahorro de Energía & CPU</h4>
              <div className="space-y-3 text-xs">
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Perfil de Rendimiento:</span>
                  <select 
                    value={qualityMode} 
                    onChange={(e) => setQualityMode(e.target.value as any)}
                    className="bg-slate-900 border border-slate-800 text-white rounded-lg p-1.5 text-xs focus:outline-none"
                  >
                    <option value="LOW_POWER">Bajo Consumo (Optimizado)</option>
                    <option value="BALANCED">Equilibrado</option>
                    <option value="HIGH_QUALITY">Máximo Desempeño</option>
                  </select>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-slate-400">Activar al conectar OBD:</span>
                  <span className="text-emerald-400 font-bold font-mono">AUTOMÁTICO</span>
                </div>
              </div>
            </div>

            {/* B2B Fleet Mode Configuration */}
            {mode === 'FLEET' && (
              <div className="bg-slate-950/40 p-5 rounded-2xl border border-slate-800 space-y-4 col-span-full">
                <h4 className="font-bold text-white font-mono uppercase text-xs text-cyan-400">Ajustes B2B Vanguard Fleet</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
                  <div className="flex flex-col gap-1.5">
                    <label className="text-slate-400">Conductor Asignado:</label>
                    <input 
                      type="text" 
                      value={fleetDriverName}
                      onChange={(e) => setFleetDriverName(e.target.value)}
                      className="bg-slate-900 border border-slate-800 text-white rounded-xl p-2 text-xs focus:outline-none focus:border-cyan-400"
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <label className="text-slate-400">Ruta Asignada:</label>
                    <input 
                      type="text" 
                      value={fleetRouteName}
                      onChange={(e) => setFleetRouteName(e.target.value)}
                      className="bg-slate-900 border border-slate-800 text-white rounded-xl p-2 text-xs focus:outline-none focus:border-cyan-400"
                    />
                  </div>
                </div>
              </div>
            )}

          </div>
        </div>
      )}

    </div>
  );
}
