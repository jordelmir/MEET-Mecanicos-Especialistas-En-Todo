import {
  VehicleProfile,
  VehicleDigitalTwin,
  VehicleHealthScore,
  PredictiveMaintenanceAlert,
  MaintenanceRecord,
  RiskCategory,
  DrivingProfile,
  TransmissionType,
  FuelType
} from '../types';

export function calculateCompletenessScore(profile: VehicleProfile): number {
  let score = 0;
  if (profile.nickname) score += 10;
  if (profile.make) score += 10;
  if (profile.model) score += 10;
  if (profile.year) score += 10;
  if (profile.engine) score += 10;
  if (profile.transmission && profile.transmission !== 'UNKNOWN') score += 15;
  if (profile.vin_nullable) score += 15;
  if (profile.plate_nullable) score += 10;
  if (profile.photo_uri_nullable) score += 10;
  return score;
}

export function calculateDataQualityScore(
  profile: VehicleProfile,
  hasScans: boolean,
  hasReports: boolean,
  hasMaintenance: boolean
): 'Alta' | 'Media' | 'Baja' {
  const comp = calculateCompletenessScore(profile);
  let factors = 0;
  if (comp >= 80) factors += 25;
  if (profile.vin_nullable) factors += 25;
  if (hasScans) factors += 20;
  if (hasReports) factors += 15;
  if (hasMaintenance) factors += 15;
  
  if (factors >= 75) return 'Alta';
  if (factors >= 40) return 'Media';
  return 'Baja';
}

export function calculateHealthScore(
  vehicleId: string,
  activeDtcs: string[],
  readinessIncompleteCount: number,
  voltage: number | null,
  coolantTemp: number | null,
  overdueMaintenanceCount: number,
  hasRealObd: boolean
): VehicleHealthScore {
  let engine = 100;
  let transmission = 100;
  let electrical = 100;
  let emissions = 100;
  let brake = 100;
  let suspension = 100;
  let cooling = 100;
  let battery = 100;

  // Deduct based on active DTCs
  activeDtcs.forEach(dtc => {
    const code = dtc.toUpperCase();
    if (code.startsWith('P0230')) {
      electrical -= 30;
      engine -= 25;
    } else if (code.startsWith('P0300') || code.startsWith('P0302') || code.startsWith('P0301') || code.startsWith('P0303') || code.startsWith('P0304')) {
      engine -= 35;
      emissions -= 25;
    } else if (code.startsWith('P0171')) {
      engine -= 15;
      emissions -= 20;
    } else if (code.startsWith('P0420')) {
      emissions -= 40;
    } else if (code.startsWith('P0115')) {
      cooling -= 30;
      electrical -= 15;
    } else if (code.startsWith('P07')) {
      transmission -= 40;
    } else if (code.startsWith('C00') || code.startsWith('C11') || code.startsWith('C12')) {
      brake -= 35;
    }
  });

  // Deduct based on live parameters
  if (voltage !== null) {
    if (voltage < 13.0) {
      electrical -= 25;
      battery -= 30;
    } else if (voltage < 13.5) {
      electrical -= 10;
      battery -= 15;
    } else if (voltage > 15.0) {
      electrical -= 15;
    }
  }

  if (coolantTemp !== null) {
    if (coolantTemp > 105) {
      cooling -= 45;
      engine -= 20;
    } else if (coolantTemp > 98) {
      cooling -= 15;
    }
  }

  // Deduct for incomplete readiness monitors
  emissions -= readinessIncompleteCount * 8;

  // Deduct for overdue maintenance
  const maintenanceDeduction = overdueMaintenanceCount * 10;
  engine -= maintenanceDeduction;
  transmission -= maintenanceDeduction;
  brake -= maintenanceDeduction;
  suspension -= maintenanceDeduction;

  const cap = (val: number) => Math.max(0, Math.min(100, val));
  
  engine = cap(engine);
  transmission = cap(transmission);
  electrical = cap(electrical);
  emissions = cap(emissions);
  brake = cap(brake);
  suspension = cap(suspension);
  cooling = cap(cooling);
  battery = cap(battery);

  const overall = cap(Math.round(
    engine * 0.25 +
    transmission * 0.15 +
    electrical * 0.15 +
    cooling * 0.15 +
    emissions * 0.10 +
    battery * 0.10 +
    brake * 0.05 +
    suspension * 0.05
  ));

  const confidence = !hasRealObd ? 'LOW' : (activeDtcs.length > 0 || voltage === null ? 'MEDIUM' : 'HIGH');

  return {
    vehicle_id: vehicleId,
    overall_score: overall,
    engine_score: engine,
    transmission_score: transmission,
    electrical_score: electrical,
    emissions_score: emissions,
    brake_score: brake,
    suspension_score: suspension,
    cooling_score: cooling,
    battery_score: battery,
    confidence,
    calculated_at: new Date().toISOString(),
  };
}

export interface VehicleRiskDetails {
  category: RiskCategory;
  explanation: string;
  evidence: string[];
  next_check: string;
  can_drive: boolean;
}

export function calculateRiskScore(
  activeDtcs: string[],
  voltage: number | null,
  coolantTemp: number | null,
  overdueMaintenanceCount: number
): VehicleRiskDetails {
  let category: RiskCategory = 'LOW';
  const evidence: string[] = [];
  let explanation = 'El vehículo se encuentra en un estado operativo nominal.';
  let can_drive = true;
  let next_check = 'En el próximo servicio de mantenimiento rutinario.';

  const hasCriticalDtc = activeDtcs.some(dtc => {
    const code = dtc.toUpperCase();
    return code.startsWith('P0300') || code.startsWith('P0302') || code.startsWith('P07') || code.startsWith('P0230') || code.startsWith('P0115');
  });

  if (hasCriticalDtc) {
    category = 'CRITICAL';
    can_drive = false;
    explanation = 'Se detectaron fallas críticas en componentes vitales del motor o transmisión.';
    next_check = 'Inmediatamente. No conduzca el vehículo hasta resolver.';
    if (activeDtcs.some(c => c.toUpperCase().startsWith('P0230'))) {
      evidence.push('DTC P0230: Circuito primario de la bomba de combustible defectuoso.');
    }
    if (activeDtcs.some(c => c.toUpperCase().startsWith('P0300') || c.toUpperCase().startsWith('P0302'))) {
      evidence.push('DTC P0300/P0302: Fallo de encendido (misfires) en cilindros.');
    }
    if (activeDtcs.some(c => c.toUpperCase().startsWith('P07'))) {
      evidence.push('DTC de transmisión detectado: Mal funcionamiento hidráulico o electrónico.');
    }
    if (activeDtcs.some(c => c.toUpperCase().startsWith('P0115'))) {
      evidence.push('DTC P0115: Mal funcionamiento en circuito de temperatura de refrigerante.');
    }
  } else if (coolantTemp && coolantTemp > 103) {
    category = 'HIGH';
    can_drive = false;
    explanation = 'El motor experimenta sobretemperatura severa. Riesgo inminente de soplo de empaque de culata o trabado mecánico.';
    next_check = 'Apague el motor y revise nivel de refrigerante, fugas o termostato.';
    evidence.push(`Temperatura de refrigerante elevada a ${coolantTemp}°C.`);
  } else if (voltage && (voltage < 12.8 || voltage > 15.2)) {
    category = 'HIGH';
    can_drive = true;
    explanation = 'El sistema de carga eléctrica se encuentra fuera del rango nominal. Posible alternador deficiente o batería dañada.';
    next_check = 'Medir voltaje en bornes de batería con multímetro y revisar alternador.';
    evidence.push(`Voltaje de carga inestable a ${voltage}V.`);
  } else if (activeDtcs.length > 0) {
    category = 'MEDIUM';
    can_drive = true;
    explanation = 'Existen códigos de diagnóstico activos no críticos (como eficiencia de catalizador o sensores de aire).';
    next_check = 'Agendar diagnóstico OBD computarizado en taller.';
    evidence.push(`Códigos DTC activos: ${activeDtcs.join(', ')}.`);
  } else if (overdueMaintenanceCount > 0) {
    category = 'MEDIUM';
    can_drive = true;
    explanation = 'El vehículo tiene tareas de mantenimiento preventivo vencidas.';
    next_check = 'Cambiar fluidos y filtros correspondientes lo antes posible.';
    evidence.push(`Tareas de mantenimiento vencidas: ${overdueMaintenanceCount}.`);
  }

  return {
    category,
    explanation,
    evidence,
    next_check,
    can_drive,
  };
}

export function generatePredictiveAlerts(
  vehicleId: string,
  odometerKm: number,
  activeDtcs: string[],
  voltageHistory: number[],
  ectHistory: number[]
): PredictiveMaintenanceAlert[] {
  const alerts: PredictiveMaintenanceAlert[] = [];
  const nowStr = new Date().toISOString();

  // 1. Fuel pump relay risk (P0230)
  if (activeDtcs.some(dtc => dtc.toUpperCase().startsWith('P0230'))) {
    alerts.push({
      id: `pred_p0230_${Date.now()}`,
      vehicle_id: vehicleId,
      component: 'bomba combustible',
      risk_level: 'CRITICAL',
      predicted_issue: 'Riesgo inminente de no arranque por circuito de bomba cortado.',
      evidence: ['Presencia activa de DTC P0230', 'Falla eléctrica primaria en ramal o relé.'],
      recommended_action: 'Revisar relé de bomba de combustible en caja de fusibles, fusible de bomba y conexiones a tierra.',
      due_in_km_nullable: null,
      due_in_days_nullable: 1,
      confidence: 95,
      status: 'active',
      created_at: nowStr,
    });
  }

  // 2. Battery / Alternator charging decay
  if (voltageHistory.length >= 3) {
    const latest = voltageHistory[voltageHistory.length - 1];
    const prev = voltageHistory[voltageHistory.length - 2];
    const first = voltageHistory[voltageHistory.length - 3];
    
    if (latest < 13.2 && prev < 13.7 && first > 13.9) {
      alerts.push({
        id: `pred_voltage_${Date.now()}`,
        vehicle_id: vehicleId,
        component: 'alternador',
        risk_level: 'HIGH',
        predicted_issue: 'Degradación del voltaje de carga de la batería.',
        evidence: [
          `El voltaje de carga bajó de ${first.toFixed(1)}V a ${latest.toFixed(1)}V en tres sesiones.`,
          'Desviación del rango nominal normal de ralentí caliente.'
        ],
        recommended_action: 'Medir el alternador en banco de pruebas o revisar caída de tensión en cables de batería/alternador.',
        due_in_km_nullable: 150,
        due_in_days_nullable: 7,
        confidence: 85,
        status: 'active',
        created_at: nowStr,
      });
    }
  }

  // 3. Cooling system temperature decay
  if (ectHistory.length >= 3) {
    const latest = ectHistory[ectHistory.length - 1];
    const prev = ectHistory[ectHistory.length - 2];
    
    if (latest > 102 && prev > 96) {
      alerts.push({
        id: `pred_ect_${Date.now()}`,
        vehicle_id: vehicleId,
        component: 'refrigeración',
        risk_level: 'HIGH',
        predicted_issue: 'Tendencia al sobrecalentamiento térmico progresivo.',
        evidence: [
          `Temperatura de refrigerante del motor (ECT) subió de ${prev}°C a ${latest}°C en ralentí.`,
          'Eficiencia reducida del radiador o falla parcial en termostato/abanico.'
        ],
        recommended_action: 'Verificar nivel y flujo de refrigerante, purgar el sistema de enfriamiento y probar abanicos.',
        due_in_km_nullable: 300,
        due_in_days_nullable: 5,
        confidence: 78,
        status: 'active',
        created_at: nowStr,
      });
    }
  }

  // 4. Spark Plugs / Coils (P0300, P0302)
  if (activeDtcs.some(dtc => dtc.toUpperCase().startsWith('P0300') || dtc.toUpperCase().startsWith('P0302'))) {
    alerts.push({
      id: `pred_spark_${Date.now()}`,
      vehicle_id: vehicleId,
      component: 'bujías',
      risk_level: 'HIGH',
      predicted_issue: 'Fallo de encendido recurrente detectado en motor.',
      evidence: ['Múltiples fallos de combustión (misfire) reportados por OBD2.', 'Peligro de daños a catalizador por gasolina sin quemar.'],
      recommended_action: 'Reemplazar bujías de encendido y rotar bobina de cilindro 2 a cilindro 1 para aislar falla.',
      due_in_km_nullable: null,
      due_in_days_nullable: 3,
      confidence: 90,
      status: 'active',
      created_at: nowStr,
    });
  }

  // 5. Scheduled maintenance (oil change) near threshold
  const kmToOilChange = 5000 - (odometerKm % 5000);
  if (kmToOilChange <= 500) {
    alerts.push({
      id: `pred_oil_${Date.now()}`,
      vehicle_id: vehicleId,
      component: 'aceite',
      risk_level: 'LOW',
      predicted_issue: 'Cambio de aceite de motor y filtro próximo a vencer.',
      evidence: [`Faltan ${kmToOilChange} km para cumplir intervalo sugerido de 5,000 km.`],
      recommended_action: 'Realizar cambio de aceite sintético y filtro en taller autorizado.',
      due_in_km_nullable: kmToOilChange,
      due_in_days_nullable: 30,
      confidence: 99,
      status: 'active',
      created_at: nowStr,
    });
  }

  return alerts;
}
