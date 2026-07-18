import { DiagnosticSnapshot, PROVENANCE_SIMULATED, simulatedValue } from './reports/types';

// ============================================================
// 1. DATA MODELS & TYPES
// ============================================================

export type SupportConfidence = 'CONFIRMED' | 'LIKELY' | 'UNKNOWN' | 'UNSUPPORTED';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type ActionStatus =
  | 'CREATED'
  | 'PRECHECK_RUNNING'
  | 'BLOCKED'
  | 'WAITING_CONFIRMATION'
  | 'EXECUTING'
  | 'VERIFYING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type ExecutionResult =
  | 'SUCCESS'
  | 'NEGATIVE_RESPONSE'
  | 'TIMEOUT'
  | 'UNSUPPORTED'
  | 'SAFETY_BLOCKED'
  | 'ECU_REJECTED'
  | 'ADAPTER_ERROR'
  | 'PARSE_ERROR'
  | 'USER_CANCELLED';

export interface CommandProfile {
  id: string;
  actionKey: string;
  protocol: string;
  requestBytes: string;
  positiveResponsePattern: string;
  negativeResponsePatterns: string[];
  timeoutMs: number;
  retries: number;
  requiresSecurityAccess: boolean;
  notes?: string;
}

export interface BidirectionalCapability {
  id: string;
  vehicleId: string;
  ecuAddress: string;
  system: string;
  actionType: 'ACTIVE_TEST' | 'SERVICE_RESET' | 'ADAPTATION' | 'RESTRICTED';
  actionKey: string;
  displayName: string;
  description: string;
  protocol: string;
  supported: boolean;
  supportConfidence: SupportConfidence;
  requiredPermissions: string[];
  requiredConditions: string[];
  riskLevel: RiskLevel;
  commandProfileId: string | null;
  createdAt: string;
}

export interface BidirectionalAction {
  id: string;
  capabilityId: string;
  vehicleId: string;
  userId: string;
  status: ActionStatus;
  requestedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  failedAt: string | null;
  preSnapshotId: string;
  postSnapshotId: string | null;
  result: ExecutionResult | null;
  errorMessage: string | null;
  auditHash: string;
}

export interface ServiceResetProcedure {
  id: string;
  actionKey: string;
  title: string;
  description: string;
  vehicleScope: string;
  steps: string[];
  requiredConditions: string[];
  warnings: string[];
  expectedResult: string;
  validationSteps: string[];
}

export interface BidirectionalAuditLog {
  id: string;
  actionId: string;
  vehicleId: string;
  userId: string;
  commandHash: string;
  preSnapshotHash: string;
  postSnapshotHash: string | null;
  result: ExecutionResult;
  timestamp: string;
  appVersion: string;
  deviceHash: string;
}

export interface LiveTelemetry {
  rpm: number;
  speed: number;
  temp: number;
  voltage: number;
  load: number;
  maf: number;
  parkingBrakeOn?: boolean;
  brakePedalPressed?: boolean;
  transmissionParkOrNeutral?: boolean;
  doorsClosed?: boolean;
  fuelLevel?: number;
  adapterQuality?: number; // 0-100
}

export interface PrecheckVerdict {
  passed: boolean;
  failedConditions: string[];
  reason: string;
  alternativeSuggestion: string;
}

// ============================================================
// 2. SAFETY PRECONDITION ENGINE
// ============================================================

export class SafetyPreconditionEngine {
  static evaluatePreconditions(
    capability: BidirectionalCapability,
    telemetry: LiveTelemetry,
    dtcCodes: string[] = []
  ): PrecheckVerdict {
    const failedConditions: string[] = [];
    const reasons: string[] = [];

    const checkCondition = (condition: string) => {
      switch (condition) {
        case 'vehicle_stationary':
          if (telemetry.speed > 0) {
            failedConditions.push('vehicle_stationary');
            reasons.push(`El vehículo está en movimiento (${telemetry.speed} km/h). Debe detenerse por completo.`);
          }
          break;
        case 'engine_running':
          if (telemetry.rpm < 500) {
            failedConditions.push('engine_running');
            reasons.push(`El motor está apagado (${telemetry.rpm} RPM). Se requiere motor encendido.`);
          }
          break;
        case 'engine_off':
          if (telemetry.rpm > 0) {
            failedConditions.push('engine_off');
            reasons.push(`El motor está encendido (${telemetry.rpm} RPM). Apague el motor antes de continuar.`);
          }
          break;
        case 'ignition_on':
          // We assume battery voltage > 11.0 indicates ignition or charging state, but mostly check voltage minimum
          if (telemetry.voltage < 11.5) {
            failedConditions.push('ignition_on');
            reasons.push(`Ignición no detectada o voltaje demasiado bajo (${telemetry.voltage}V). Coloque llave en ON.`);
          }
          break;
        case 'battery_voltage_min':
          if (telemetry.voltage < 11.8) {
            failedConditions.push('battery_voltage_min');
            reasons.push(`Voltaje de batería bajo (${telemetry.voltage}V). Mínimo requerido: 11.8V.`);
          }
          break;
        case 'battery_voltage_max':
          if (telemetry.voltage > 15.5) {
            failedConditions.push('battery_voltage_max');
            reasons.push(`Voltaje de batería sobrecargado (${telemetry.voltage}V). Máximo seguro: 15.5V.`);
          }
          break;
        case 'coolant_temp_min':
          if (telemetry.temp < 70) {
            failedConditions.push('coolant_temp_min');
            reasons.push(`Temperatura de motor fría (${telemetry.temp}°C). Caliente el motor hasta >= 70°C.`);
          }
          break;
        case 'coolant_temp_max':
          if (telemetry.temp > 115) {
            failedConditions.push('coolant_temp_max');
            reasons.push(`Sobrecalentamiento detectado (${telemetry.temp}°C). Deje enfriar el motor.`);
          }
          break;
        case 'parking_brake_on':
          if (telemetry.parkingBrakeOn === false) {
            failedConditions.push('parking_brake_on');
            reasons.push('Freno de mano desactivado. Active el freno de mano por seguridad.');
          }
          break;
        case 'brake_pedal_pressed':
          if (telemetry.brakePedalPressed === false) {
            failedConditions.push('brake_pedal_pressed');
            reasons.push('Pedal de freno suelto. Mantenga presionado el pedal de freno.');
          }
          break;
        case 'transmission_park_or_neutral':
          if (telemetry.transmissionParkOrNeutral === false) {
            failedConditions.push('transmission_park_or_neutral');
            reasons.push('Transmisión enganchada. Coloque la palanca en Park (P) o Neutral (N).');
          }
          break;
        case 'doors_closed':
          if (telemetry.doorsClosed === false) {
            failedConditions.push('doors_closed');
            reasons.push('Puertas abiertas. Cierre todas las puertas del habitáculo.');
          }
          break;
        case 'fuel_level_min':
          if (telemetry.fuelLevel !== undefined && telemetry.fuelLevel < 15) {
            failedConditions.push('fuel_level_min');
            reasons.push(`Nivel de combustible bajo (${telemetry.fuelLevel}%). Se requiere al menos 15% (especialmente para DPF).`);
          }
          break;
        case 'no_critical_dtcs':
          const criticalDtcs = dtcCodes.filter(c => c.startsWith('P02') || c.startsWith('P03') || c.startsWith('C12'));
          if (criticalDtcs.length > 0) {
            failedConditions.push('no_critical_dtcs');
            reasons.push(`Presencia de DTCs críticos (${criticalDtcs.join(', ')}). Corrija las fallas eléctricas antes de continuar.`);
          }
          break;
        case 'adapter_quality_min':
          if (telemetry.adapterQuality !== undefined && telemetry.adapterQuality < 60) {
            failedConditions.push('adapter_quality_min');
            reasons.push(`Señal del adaptador OBD2 inestable (${telemetry.adapterQuality}%). Use conexión USB o acerque dispositivo.`);
          }
          break;
        case 'ecu_session_ready':
          // session setup check
          break;
        default:
          break;
      }
    };

    capability.requiredConditions.forEach(checkCondition);

    const passed = failedConditions.length === 0;
    const reason = passed ? 'Todas las condiciones de seguridad aprobadas.' : reasons.join(' ');
    
    // Formulate intelligent alternatives
    let alternativeSuggestion = '';
    if (failedConditions.includes('vehicle_stationary')) {
      alternativeSuggestion = 'Detenga el vehículo en un área segura, aplique el freno de mano y reintente.';
    } else if (failedConditions.includes('battery_voltage_min')) {
      alternativeSuggestion = 'Conecte un cargador de batería estabilizado de taller o arranque el vehículo unos minutos para recuperar voltaje.';
    } else if (failedConditions.includes('engine_off')) {
      alternativeSuggestion = 'Apague el motor dejando la ignición en posición de contacto (ON/ACC).';
    } else if (failedConditions.includes('coolant_temp_min')) {
      alternativeSuggestion = 'Mantenga el motor encendido a 2000 RPM en ralentí para acelerar el calentamiento del anticongelante.';
    } else {
      alternativeSuggestion = 'Asegúrese de seguir estrictamente el manual del fabricante del vehículo.';
    }

    return {
      passed,
      failedConditions,
      reason,
      alternativeSuggestion,
    };
  }
}

// ============================================================
// 3. OBD SNAPSHOT ENGINE
// ============================================================

export class ObdSnapshotEngine {
  static capture(
    vehicleId: string,
    telemetry: LiveTelemetry,
    dtcCodes: string[],
    notes: string = 'Autocaptura de seguridad previa a prueba activa.'
  ): DiagnosticSnapshot {
    const timestamp = Date.now();
    return {
      id: `snapshot_${timestamp}_${Math.random().toString(36).substring(2, 7)}`,
      vehicleId,
      sessionId: null,
      createdAtMs: timestamp,
      dtcsActive: [...dtcCodes],
      dtcsPending: [],
      dtcsPermanent: [],
      freezeFramePidValues: {
        '0C': telemetry.rpm, // Engine RPM
        '0D': telemetry.speed, // Vehicle Speed
        '05': telemetry.temp, // Coolant Temp
        '42': telemetry.voltage, // Control Module Voltage
      },
      livePids: {
        rpm: simulatedValue(telemetry.rpm, timestamp),
        speed: simulatedValue(telemetry.speed, timestamp),
        temp: simulatedValue(telemetry.temp, timestamp),
        voltage: simulatedValue(telemetry.voltage, timestamp),
      },
      readiness: {
        misfire: true,
        fuelSystem: true,
        components: true,
        catalyst: false,
        evap: false,
        oxygenSensor: true,
      },
      ecuVoltage: telemetry.voltage,
      rpm: telemetry.rpm,
      coolantTempC: telemetry.temp,
      speedKph: telemetry.speed,
      engineLoadPct: telemetry.load,
      fuelTrimStft: 0.8,
      fuelTrimLtft: 1.2,
      rawFrames: [
        `TX: 010C -> RX: 41 0C ${Math.round(telemetry.rpm * 4).toString(16)}`,
        `TX: 010D -> RX: 41 0D ${Math.round(telemetry.speed).toString(16)}`,
        `TX: 0105 -> RX: 41 05 ${Math.round(telemetry.temp + 40).toString(16)}`,
      ],
      notes,
      provenance: PROVENANCE_SIMULATED,
    };
  }

  static compare(pre: DiagnosticSnapshot, post: DiagnosticSnapshot) {
    const clearedDtcs = pre.dtcsActive.filter(code => !post.dtcsActive.includes(code));
    const addedDtcs = post.dtcsActive.filter(code => !pre.dtcsActive.includes(code));
    
    const voltageDelta = (post.ecuVoltage || 0) - (pre.ecuVoltage || 0);
    const rpmDelta = (post.rpm || 0) - (pre.rpm || 0);
    const tempDelta = (post.coolantTempC || 0) - (pre.coolantTempC || 0);

    return {
      clearedDtcs,
      addedDtcs,
      voltageDelta,
      rpmDelta,
      tempDelta,
      hasSignificantChanges: clearedDtcs.length > 0 || addedDtcs.length > 0 || Math.abs(voltageDelta) > 1.5 || Math.abs(tempDelta) > 10,
    };
  }
}

// ============================================================
// 4. BIDIRECTIONAL EXECUTOR (SERIAL COMMAND QUEUE)
// ============================================================

export class BidirectionalExecutor {
  private static activeQueue: Promise<any> = Promise.resolve();
  private static isPollingPaused = false;
  private static activeCount = 0;

  static isQueueBusy(): boolean {
    return this.isPollingPaused || this.activeCount > 0;
  }

  static pausePolling() {
    this.isPollingPaused = true;
    console.log('[MEET] OBD Normal Polling PAUSED. Serial Queue locked for bidirectional control.');
  }

  static resumePolling() {
    this.isPollingPaused = false;
    console.log('[MEET] OBD Normal Polling RESUMED.');
  }

  /**
   * Enqueues an execution of a bidirectional command.
   * Ensures commands are serialized, normal polling is paused, timeouts are enforced.
   */
  static executeAction(
    action: BidirectionalAction,
    profile: CommandProfile,
    onLogUpdate?: (log: string) => void
  ): Promise<{ result: ExecutionResult; logs: string[]; postTelemetryChanges?: Partial<LiveTelemetry>; ecuError?: string }> {
    
    this.activeCount++;
    this.pausePolling();

    const executionPromise = this.activeQueue.then(async () => {
      const logs: string[] = [];
      const addLog = (msg: string) => {
        logs.push(msg);
        if (onLogUpdate) onLogUpdate(msg);
        console.log(`[MEET_BIDIRECTIONAL] ${msg}`);
      };

      addLog(`Inicializando comando serial en canal OBD...`);
      addLog(`Comando clave: ${profile.actionKey} sobre protocolo: ${profile.protocol}`);

      try {
        if (profile.requiresSecurityAccess) {
          addLog(`TX: UDS Session Seed Request (0x27 0x01)`);
          await this.delay(300);
          addLog(`RX: 67 01 AB CD EF 12 (Seed generated)`);
          addLog(`TX: UDS Session Key Send (0x27 0x02 Key: 4F A2 C3 99)`);
          await this.delay(300);
          addLog(`RX: 67 02 (Security Level 1 Active)`);
        }

        let attempt = 0;
        let success = false;
        let finalResult: ExecutionResult = 'SUCCESS';
        let postChanges: Partial<LiveTelemetry> = {};
        let ecuError = '';

        while (attempt < profile.retries && !success) {
          attempt++;
          addLog(`Intento ${attempt}/${profile.retries}: TX: ${profile.requestBytes}`);
          
          await this.delay(profile.timeoutMs * 0.4);

          const roll = Math.random();
          if (roll < 0.05) {
            await this.delay(profile.timeoutMs * 0.6);
            addLog(`ERR: Timeout superado en la respuesta de la ECU (${profile.timeoutMs}ms)`);
            finalResult = 'TIMEOUT';
          } else if (roll < 0.15) {
            const nrcCode = Math.random() > 0.5 ? '22' : '7F';
            if (nrcCode === '22') {
              addLog(`RX: 7F ${profile.requestBytes.split(' ')[0]} 22 (Negative Response: Conditions Not Correct)`);
              ecuError = 'ECU rechazó la acción: condiciones no correctas (ej. motor encendido o marcha colocada).';
              finalResult = 'ECU_REJECTED';
            } else {
              addLog(`RX: 7F ${profile.requestBytes.split(' ')[0]} 12 (Negative Response: Subfunction Not Supported)`);
              ecuError = 'Comando rechazado: Función no soportada por este firmware de ECU.';
              finalResult = 'UNSUPPORTED';
            }
          } else if (roll < 0.20) {
            addLog(`ERR: Error de comunicación del adaptador ELM327 (BUS INIT ERROR)`);
            finalResult = 'ADAPTER_ERROR';
          } else {
            addLog(`RX: ${profile.positiveResponsePattern} (Respuesta Positiva Detectada)`);
            success = true;
            finalResult = 'SUCCESS';

            if (profile.actionKey.includes('fuel_pump')) {
              postChanges = { pressureActive: true } as any; 
            } else if (profile.actionKey.includes('fan')) {
              postChanges = { temp: 88, load: 18 };
            } else if (profile.actionKey.includes('oil')) {
              postChanges = { oilLifePct: 100 } as any;
            } else if (profile.actionKey.includes('throttle')) {
              postChanges = { rpm: 750, load: 12 };
            }
          }
        }

        addLog(`Transmisión serial completada con estado: ${finalResult}`);
        return { result: finalResult, logs, postTelemetryChanges: postChanges, ecuError };
        
      } catch (err: any) {
        addLog(`ERR: Falla catastrófica en ejecutor serial: ${err.message}`);
        return { result: 'PARSE_ERROR' as ExecutionResult, logs, ecuError: err.message };
      } finally {
        this.activeCount--;
        if (this.activeCount === 0) {
          this.resumePolling();
        }
      }
    });

    this.activeQueue = executionPromise.catch(() => {});
    return executionPromise;
  }

  private static delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

// ============================================================
// 5. STATIC PROCEDURES & CAPABILITIES DIRECTORY
// ============================================================

export const DEFAULT_PROCEDURES: ServiceResetProcedure[] = [
  {
    id: 'proc_oil_reset',
    actionKey: 'oil_reset',
    title: 'Procedimiento de Restablecimiento de Aceite',
    description: 'Permite reiniciar la vida útil del aceite de motor al 100% en la computadora de abordo del vehículo.',
    vehicleScope: 'Universal / OBD-II General',
    steps: [
      'Coloque el interruptor de encendido en la posición ON (Ignición encendida, motor apagado).',
      'Presione los botones del volante para desplazarse por el menú hasta la pantalla de "Vida de Aceite".',
      'Mantenga presionado el botón OK / RESET por al menos 5 segundos hasta que parpadee.',
      'Suelte el botón y confirme en el tablero que muestra "Aceite 100%".',
      'Apague la ignición (OFF) y arranque el motor para validar.'
    ],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min'],
    warnings: [
      'No restablezca el indicador si no ha realizado el cambio físico del aceite de motor y filtro.',
      'Ignorar este mantenimiento puede provocar acumulación de lodos de carbón en válvulas VVT.'
    ],
    expectedResult: 'Indicador del panel de instrumentos muestra vida útil del aceite al 100%.',
    validationSteps: [
      'Apagar motor por 10 segundos.',
      'Arrancar motor y verificar que la luz de aviso de mantenimiento/llave inglesa amarilla permanezca apagada.'
    ]
  },
  {
    id: 'proc_throttle_relearn',
    actionKey: 'throttle_relearn',
    title: 'Procedimiento de Reaprendizaje de Cuerpo de Aceleración',
    description: 'Sincroniza el sensor de posición de mariposa (TPS) tras limpieza o reemplazo del cuerpo de aceleración electrónico.',
    vehicleScope: 'Sistemas Drive-By-Wire (Gasolina)',
    steps: [
      'Asegúrese que la temperatura del refrigerante del motor esté entre 5°C y 95°C.',
      'Coloque la llave en posición ON durante 2 segundos sin arrancar.',
      'Apague la llave (OFF) durante 10 segundos.',
      'Coloque la llave en posición ON durante 2 segundos.',
      'Arranque el motor y déjelo calentar en ralentí durante al menos 15 minutos en Parking/Neutral.',
      'Verifique que las RPM de ralentí se estabilicen entre 650 y 750 RPM.'
    ],
    requiredConditions: ['vehicle_stationary', 'ignition_on', 'battery_voltage_min'],
    warnings: [
      'No toque el acelerador ni encienda el aire acondicionado durante los 15 minutos de adaptación.',
      'Un cuerpo de aceleración dañado internamente o sucio puede provocar tirones de motor.'
    ],
    expectedResult: 'El ralentí del motor se estabiliza dentro de los parámetros nominales sin oscilaciones.',
    validationSteps: [
      'Efectuar prueba de conducción verificando que al soltar el pedal de acelerador las revoluciones bajen de forma progresiva sin apagarse.'
    ]
  }
];

export const MOCK_CAPABILITIES: BidirectionalCapability[] = [
  // A. Service Resets
  {
    id: 'cap_oil_reset',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Motor (PCM)',
    actionType: 'SERVICE_RESET',
    actionKey: 'oil_reset',
    displayName: 'Restablecer Intervalo de Aceite',
    description: 'Resetea el contador de kilometraje de aceite en la ECU.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: true,
    supportConfidence: 'CONFIRMED',
    requiredPermissions: ['user_confirmed'],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min'],
    riskLevel: 'LOW',
    commandProfileId: 'prof_oil_reset',
    createdAt: new Date().toISOString()
  },
  {
    id: 'cap_battery_reset',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Batería (BMS)',
    actionType: 'SERVICE_RESET',
    actionKey: 'battery_reset',
    displayName: 'Registro de Nueva Batería',
    description: 'Informa a la ECU el cambio de batería para ajustar los ciclos de carga del alternador.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: true,
    supportConfidence: 'LIKELY',
    requiredPermissions: ['user_confirmed'],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min'],
    riskLevel: 'MEDIUM',
    commandProfileId: 'prof_battery_reset',
    createdAt: new Date().toISOString()
  },
  {
    id: 'cap_epb_reset',
    vehicleId: 'default_veh',
    ecuAddress: '7E1',
    system: 'Frenos (ABS/EPB)',
    actionType: 'SERVICE_RESET',
    actionKey: 'epb_reset',
    displayName: 'Apertura de Mordazas EPB (Modo Servicio)',
    description: 'Retrae los servomotores del freno de estacionamiento eléctrico para el cambio seguro de pastillas traseras.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: true,
    supportConfidence: 'CONFIRMED',
    requiredPermissions: ['user_confirmed', 'brake_pedal_pressed'],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min', 'parking_brake_on'],
    riskLevel: 'MEDIUM',
    commandProfileId: 'prof_epb_reset',
    createdAt: new Date().toISOString()
  },
  
  // B. Active Tests
  {
    id: 'cap_fuel_pump',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Combustible (PCM)',
    actionType: 'ACTIVE_TEST',
    actionKey: 'fuel_pump',
    displayName: 'Prueba Activa de Bomba de Gasolina',
    description: 'Fuerza el encendido del relé de la bomba durante 3 segundos para probar presión y continuidad eléctrica.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: true,
    supportConfidence: 'CONFIRMED',
    requiredPermissions: ['user_confirmed'],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min'],
    riskLevel: 'MEDIUM',
    commandProfileId: 'prof_fuel_pump',
    createdAt: new Date().toISOString()
  },
  {
    id: 'cap_cooling_fan',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Enfriamiento (PCM)',
    actionType: 'ACTIVE_TEST',
    actionKey: 'cooling_fan',
    displayName: 'Prueba de Activación de Electroventilador',
    description: 'Enciende el motoventilador de enfriamiento en velocidades baja/alta para diagnosticar fallos de relé o arnés.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: true,
    supportConfidence: 'CONFIRMED',
    requiredPermissions: ['user_confirmed'],
    requiredConditions: ['ignition_on', 'vehicle_stationary', 'battery_voltage_min', 'coolant_temp_max'],
    riskLevel: 'MEDIUM',
    commandProfileId: 'prof_cooling_fan',
    createdAt: new Date().toISOString()
  },
  {
    id: 'cap_evap_purge',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Emisiones (PCM)',
    actionType: 'ACTIVE_TEST',
    actionKey: 'evap_purge',
    displayName: 'Prueba de Solenoide Purga EVAP',
    description: 'Abre el ciclo de purga EVAP para comprobar fugas en la línea de vapores de combustible.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: true,
    supportConfidence: 'LIKELY',
    requiredConditions: ['engine_running', 'vehicle_stationary', 'coolant_temp_min'],
    requiredPermissions: ['user_confirmed'],
    riskLevel: 'MEDIUM',
    commandProfileId: 'prof_evap_purge',
    createdAt: new Date().toISOString()
  },

  // C. Adaptations
  {
    id: 'cap_throttle_relearn',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Aceleración (PCM)',
    actionType: 'ADAPTATION',
    actionKey: 'throttle_relearn',
    displayName: 'Reaprendizaje del Cuerpo de Aceleración',
    description: 'Fuerza a la ECU a recalibrar el tope mínimo y máximo de la mariposa electrónica.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: true,
    supportConfidence: 'CONFIRMED',
    requiredPermissions: ['user_confirmed'],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min'],
    riskLevel: 'MEDIUM',
    commandProfileId: 'prof_throttle_relearn',
    createdAt: new Date().toISOString()
  },

  // D. Restricted / Future Functions (Blocked / Restricted)
  {
    id: 'cap_abs_bleed',
    vehicleId: 'default_veh',
    ecuAddress: '7E1',
    system: 'Seguridad (ABS)',
    actionType: 'RESTRICTED',
    actionKey: 'abs_bleed',
    displayName: 'Purgado del Modulador de Frenos ABS',
    description: 'Fuerza las electroválvulas y bomba de retorno hidráulico del ABS para extraer aire atrapado en los frenos.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: false,
    supportConfidence: 'UNSUPPORTED',
    requiredPermissions: ['critical_clearance', 'user_confirmed'],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min', 'brake_pedal_pressed'],
    riskLevel: 'CRITICAL',
    commandProfileId: null,
    createdAt: new Date().toISOString()
  },
  {
    id: 'cap_dpf_regen',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Emisiones (PCM)',
    actionType: 'RESTRICTED',
    actionKey: 'dpf_regen',
    displayName: 'Regeneración Forzada de DPF en Estacionamiento',
    description: 'Eleva la inyección post-combustión para calentar el escape hasta 600°C y quemar hollín retenido en el filtro de partículas.',
    protocol: 'ISO 15765-4 (CAN)',
    supported: false,
    supportConfidence: 'UNSUPPORTED',
    requiredPermissions: ['critical_clearance', 'user_confirmed'],
    requiredConditions: ['engine_running', 'vehicle_stationary', 'coolant_temp_min', 'fuel_level_min', 'parking_brake_on'],
    riskLevel: 'CRITICAL',
    commandProfileId: null,
    createdAt: new Date().toISOString()
  }
];

export const MOCK_COMMAND_PROFILES: Record<string, CommandProfile> = {
  prof_oil_reset: {
    id: 'prof_oil_reset',
    actionKey: 'oil_reset',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '2E E1 01 64',
    positiveResponsePattern: '6E E1 01',
    negativeResponsePatterns: ['7F 2E 22', '7F 2E 31'],
    timeoutMs: 1000,
    retries: 2,
    requiresSecurityAccess: true
  },
  prof_battery_reset: {
    id: 'prof_battery_reset',
    actionKey: 'battery_reset',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '30 06 01 02',
    positiveResponsePattern: '70 06 01',
    negativeResponsePatterns: ['7F 30 22'],
    timeoutMs: 1500,
    retries: 3,
    requiresSecurityAccess: true
  },
  prof_epb_reset: {
    id: 'prof_epb_reset',
    actionKey: 'epb_reset',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '31 01 0F A0',
    positiveResponsePattern: '71 01 0F A0',
    negativeResponsePatterns: ['7F 31 22', '7F 31 13'],
    timeoutMs: 3000,
    retries: 2,
    requiresSecurityAccess: true
  },
  prof_fuel_pump: {
    id: 'prof_fuel_pump',
    actionKey: 'fuel_pump',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '30 01 00 01',
    positiveResponsePattern: '70 01',
    negativeResponsePatterns: ['7F 30 22', '7F 30 12'],
    timeoutMs: 1000,
    retries: 2,
    requiresSecurityAccess: false
  },
  prof_cooling_fan: {
    id: 'prof_cooling_fan',
    actionKey: 'cooling_fan',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '30 02 04 01',
    positiveResponsePattern: '70 02',
    negativeResponsePatterns: ['7F 30 22'],
    timeoutMs: 1200,
    retries: 2,
    requiresSecurityAccess: false
  },
  prof_evap_purge: {
    id: 'prof_evap_purge',
    actionKey: 'evap_purge',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '30 03 A1 20',
    positiveResponsePattern: '70 03',
    negativeResponsePatterns: ['7F 30 22', '7F 30 7F'],
    timeoutMs: 1000,
    retries: 2,
    requiresSecurityAccess: false
  },
  prof_throttle_relearn: {
    id: 'prof_throttle_relearn',
    actionKey: 'throttle_relearn',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '31 01 AA FF',
    positiveResponsePattern: '71 01 AA FF',
    negativeResponsePatterns: ['7F 31 22', '7F 31 31'],
    timeoutMs: 2000,
    retries: 2,
    requiresSecurityAccess: true
  }
};
