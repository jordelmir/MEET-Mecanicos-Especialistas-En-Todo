import { describe, it, expect, vi } from 'vitest';
import {
  SafetyPreconditionEngine,
  ObdSnapshotEngine,
  BidirectionalExecutor,
  BidirectionalCapability,
  LiveTelemetry,
  CommandProfile
} from '../../bidirectional';

describe('SafetyPreconditionEngine', () => {
  const baseCapability: BidirectionalCapability = {
    id: 'cap_fuel_pump',
    vehicleId: 'default_veh',
    ecuAddress: '7E0',
    system: 'Combustible',
    actionType: 'ACTIVE_TEST',
    actionKey: 'fuel_pump',
    displayName: 'Prueba Activa de Bomba',
    description: 'Prueba de bomba',
    protocol: 'CAN',
    supported: true,
    supportConfidence: 'CONFIRMED',
    requiredPermissions: ['user_confirmed'],
    requiredConditions: ['ignition_on', 'engine_off', 'vehicle_stationary', 'battery_voltage_min'],
    riskLevel: 'MEDIUM',
    commandProfileId: 'prof_fuel_pump',
    createdAt: new Date().toISOString()
  };

  it('bloquea vehículo en movimiento', () => {
    const telemetry: LiveTelemetry = {
      rpm: 0,
      speed: 15, // In motion!
      temp: 90,
      voltage: 12.6,
      load: 0,
      maf: 0
    };
    
    const verdict = SafetyPreconditionEngine.evaluatePreconditions(baseCapability, telemetry);
    expect(verdict.passed).toBe(false);
    expect(verdict.failedConditions).toContain('vehicle_stationary');
    expect(verdict.reason).toContain('en movimiento');
  });

  it('bloquea voltaje bajo de batería', () => {
    const telemetry: LiveTelemetry = {
      rpm: 0,
      speed: 0,
      temp: 90,
      voltage: 10.8, // Low voltage!
      load: 0,
      maf: 0
    };

    const verdict = SafetyPreconditionEngine.evaluatePreconditions(baseCapability, telemetry);
    expect(verdict.passed).toBe(false);
    expect(verdict.failedConditions).toContain('battery_voltage_min');
    expect(verdict.reason.toLowerCase()).toContain('voltaje de batería bajo');
  });

  it('bloquea temperatura insegura o motor encendido', () => {
    const capWithTemp: BidirectionalCapability = {
      ...baseCapability,
      requiredConditions: ['engine_off', 'coolant_temp_max']
    };

    const telemetry: LiveTelemetry = {
      rpm: 800, // Engine is running!
      speed: 0,
      temp: 118, // Overheating!
      voltage: 14.1,
      load: 15,
      maf: 4.2
    };

    const verdict = SafetyPreconditionEngine.evaluatePreconditions(capWithTemp, telemetry);
    expect(verdict.passed).toBe(false);
    expect(verdict.failedConditions).toContain('engine_off');
    expect(verdict.failedConditions).toContain('coolant_temp_max');
  });

  it('permite acción con condiciones correctas', () => {
    const telemetry: LiveTelemetry = {
      rpm: 0,
      speed: 0,
      temp: 90,
      voltage: 12.4, // Nominal
      load: 0,
      maf: 0
    };

    const verdict = SafetyPreconditionEngine.evaluatePreconditions(baseCapability, telemetry);
    expect(verdict.passed).toBe(true);
    expect(verdict.failedConditions.length).toBe(0);
  });
});

describe('ObdSnapshotEngine', () => {
  it('captura pre y post snapshots y los compara', () => {
    const telemetry: LiveTelemetry = {
      rpm: 0,
      speed: 0,
      temp: 85,
      voltage: 12.2,
      load: 0,
      maf: 0
    };

    const preSnapshot = ObdSnapshotEngine.capture('veh_123', telemetry, ['P0230', 'P0171']);
    expect(preSnapshot.dtcsActive).toEqual(['P0230', 'P0171']);
    expect(preSnapshot.ecuVoltage).toBe(12.2);

    const postSnapshot = ObdSnapshotEngine.capture('veh_123', telemetry, ['P0171']); // P0230 is cleared!
    const diff = ObdSnapshotEngine.compare(preSnapshot, postSnapshot);

    expect(diff.clearedDtcs).toEqual(['P0230']);
    expect(diff.addedDtcs.length).toBe(0);
    expect(diff.hasSignificantChanges).toBe(true);
  });
});

describe('BidirectionalExecutor', () => {
  const profile: CommandProfile = {
    id: 'prof_fuel_pump',
    actionKey: 'fuel_pump',
    protocol: 'ISO 15765-4 (CAN)',
    requestBytes: '30 01 00 01',
    positiveResponsePattern: '70 01',
    negativeResponsePatterns: ['7F 30 22'],
    timeoutMs: 100,
    retries: 2,
    requiresSecurityAccess: false
  };

  it('maneja ejecución y pausa el polling', async () => {
    const spyRandom = vi.spyOn(Math, 'random').mockReturnValue(0.99);

    const action = {
      id: 'act_1',
      capabilityId: 'cap_1',
      vehicleId: 'veh_1',
      userId: 'user_1',
      status: 'CREATED' as const,
      requestedAt: new Date().toISOString(),
      startedAt: null,
      completedAt: null,
      failedAt: null,
      preSnapshotId: 'snap_pre',
      postSnapshotId: null,
      result: null,
      errorMessage: null,
      auditHash: 'hash_123'
    };

    expect(BidirectionalExecutor.isQueueBusy()).toBe(false);
    
    const execPromise = BidirectionalExecutor.executeAction(action, profile);
    
    expect(BidirectionalExecutor.isQueueBusy()).toBe(true);

    const res = await execPromise;
    expect(res.result).toBe('SUCCESS');
    expect(res.logs.length).toBeGreaterThan(0);
    
    expect(BidirectionalExecutor.isQueueBusy()).toBe(false);

    spyRandom.mockRestore();
  });
});
