import { DtcComponentMap, FuseRelayBox, FuseRelaySlot, WiringCircuit, CircuitNode, CircuitEdge, ComponentTest, ComponentMeasurement, ComponentStatus, SlotStatus, RiskCategory, MeasurementType, DetailedPart, PartSpecification, RepairStep3D, GuidedRepairProcedure } from '../types';

/**
 * 20 DTCs mínimos con mapeo completo de componentes, pruebas sugeridas y notas de precaución.
 */
export const DTC_COMPONENT_MAPS: Record<string, DtcComponentMap> = {
  P0230: {
    dtc_code: 'P0230',
    system: 'Combustible / Eléctrico',
    primary_components: ['fuel_pump_relay', 'fuel_pump_fuse', 'fuel_pump', 'wiring_harness', 'pcm_driver'],
    secondary_components: ['battery', 'ignition_switch', 'main_relay', 'ground_g302'],
    circuits: ['Alimentación de Fuerza (Pin 30)', 'Salida de Fuerza (Pin 87)', 'Línea de Control (Pin 85/86)', 'Masa de Bomba'],
    required_tests: ['test_fuse_continuity', 'test_relay_voltage', 'test_relay_control', 'test_pump_voltage', 'test_pump_ground'],
    caution_notes: [
      'No reemplace la bomba de combustible sin confirmar que le llegan +12V bajo carga.',
      'Asegúrese de desenergizar el circuito antes de manipular la caja de fusibles para evitar cortocircuitos.',
      'El pin de control es conmutado a masa por la PCM. No aplique +12V directos al pin de control de la PCM.'
    ]
  },
  P0171: {
    dtc_code: 'P0171',
    system: 'Combustible / Admisión (Mezcla Pobre)',
    primary_components: ['maf_sensor', 'fuel_pump', 'fuel_filter', 'injector_1', 'vacuum_lines'],
    secondary_components: ['o2_sensor', 'pcv_valve', 'intake_manifold_gasket'],
    circuits: ['Línea de Presión de Combustible', 'Arnés del MAF', 'Señal de Sensor de Oxígeno'],
    required_tests: ['test_fuel_pressure', 'test_maf_signal', 'test_vacuum_leak', 'test_o2_voltage'],
    caution_notes: [
      'Las fugas de vacío no medidas son la causa más común de mezcla pobre en ralentí.',
      'Un filtro de combustible obstruido simula una falla de bomba al limitar el flujo.'
    ]
  },
  P0172: {
    dtc_code: 'P0172',
    system: 'Combustible / Admisión (Mezcla Rica)',
    primary_components: ['injector_1', 'maf_sensor', 'fuel_pressure_regulator'],
    secondary_components: ['o2_sensor', 'spark_plugs', 'air_filter'],
    circuits: ['Control de Inyectores', 'Señal MAF'],
    required_tests: ['test_fuel_pressure_high', 'test_injector_leakage', 'test_maf_calibration'],
    caution_notes: ['Un regulador de presión con diafragma roto gotea gasolina cruda por la manguera de vacío.']
  },
  P0300: {
    dtc_code: 'P0300',
    system: 'Encendido / Combustible (Misfire Múltiple)',
    primary_components: ['spark_plugs', 'ignition_coils', 'injector_1', 'battery'],
    secondary_components: ['maf_sensor', 'crankshaft_sensor', 'vacuum_lines'],
    circuits: ['Alimentación de Bobinas', 'Señales de Pulsos de Inyectores'],
    required_tests: ['test_battery_voltage', 'test_ignition_spark', 'test_compression'],
    caution_notes: ['El misfire persistente puede derretir el catalizador en pocos minutos por combustible no quemado.']
  },
  P0301: {
    dtc_code: 'P0301',
    system: 'Encendido / Combustible (Misfire Cilindro 1)',
    primary_components: ['spark_plugs', 'ignition_coils', 'injector_1'],
    secondary_components: ['engine_block', 'wiring_harness'],
    circuits: ['Señal de Control Bobina 1', 'Pulso de Inyector 1'],
    required_tests: ['test_spark_plug_1', 'test_coil_1_swap', 'test_compression_cyl_1'],
    caution_notes: ['Intercambie la bobina del cilindro 1 con el 2 para verificar si el código de falla se mueve a P0302.']
  },
  P0101: {
    dtc_code: 'P0101',
    system: 'Admisión / Sensores',
    primary_components: ['maf_sensor', 'air_filter', 'wiring_harness'],
    secondary_components: ['pcm_driver', 'vacuum_lines'],
    circuits: ['Alimentación MAF (+12V)', 'Masa MAF', 'Señal de Frecuencia/Voltaje MAF'],
    required_tests: ['test_maf_power', 'test_maf_ground', 'test_maf_frequency'],
    caution_notes: ['Verifique que el ducto de admisión entre el MAF y el cuerpo de aceleración no tenga grietas ni abrazaderas sueltas.']
  },
  P0115: {
    dtc_code: 'P0115',
    system: 'Enfriamiento / Sensores',
    primary_components: ['ect_sensor', 'wiring_harness', 'pcm_driver'],
    secondary_components: ['thermostat', 'radiator_fan'],
    circuits: ['Referencia ECT (+5V)', 'Masa ECT (Retorno)'],
    required_tests: ['test_ect_resistance', 'test_ect_voltage', 'test_ect_wiring_short'],
    caution_notes: ['¡Riesgo de quemaduras! Nunca remueva el sensor de temperatura ni abra el radiador con el motor caliente.']
  },
  P0128: {
    dtc_code: 'P0128',
    system: 'Enfriamiento',
    primary_components: ['thermostat', 'ect_sensor', 'radiator_fan'],
    secondary_components: ['coolant_level', 'wiring_harness'],
    circuits: ['Señal ECT'],
    required_tests: ['test_thermostat_operation', 'test_coolant_temperature_match'],
    caution_notes: ['Un termostato trabado en posición abierta evitará que el motor alcance su temperatura normal de operación.']
  },
  P0130: {
    dtc_code: 'P0130',
    system: 'Escape / Sensores',
    primary_components: ['o2_sensor', 'wiring_harness', 'pcm_driver'],
    secondary_components: ['exhaust_manifold'],
    circuits: ['Señal O2 (+0.1V a +0.9V)', 'Calefactor O2 (+12V)', 'Masa Calefactor'],
    required_tests: ['test_o2_heater_resistance', 'test_o2_signal_voltage', 'test_o2_wiring_short'],
    caution_notes: ['El sensor de oxígeno trabaja a más de 300°C. Espere a que se enfríe antes de tocarlo.']
  },
  P0133: {
    dtc_code: 'P0133',
    system: 'Escape / Sensores',
    primary_components: ['o2_sensor', 'wiring_harness', 'catalytic_converter'],
    secondary_components: ['vacuum_lines', 'exhaust_leaks'],
    circuits: ['Señal O2'],
    required_tests: ['test_o2_response_time', 'test_exhaust_leaks'],
    caution_notes: ['Una pequeña fuga en el múltiple de escape introducirá oxígeno y provocará lecturas lentas o erróneas.']
  },
  P0420: {
    dtc_code: 'P0420',
    system: 'Escape',
    primary_components: ['catalytic_converter', 'o2_sensor', 'exhaust_manifold'],
    secondary_components: ['spark_plugs', 'injector_1'],
    circuits: ['Señales O2 Sensor 1 vs Sensor 2'],
    required_tests: ['test_catalytic_temp_drop', 'test_rear_o2_switching', 'test_engine_misfires'],
    caution_notes: ['El catalizador generalmente falla debido a otros problemas del motor (mezcla rica, consumo de aceite, misfires).']
  },
  P0440: {
    dtc_code: 'P0440',
    system: 'Emisiones (EVAP)',
    primary_components: ['fuel_cap', 'evap_purge_valve', 'evap_vent_valve', 'evap_canister'],
    secondary_components: ['fuel_tank', 'wiring_harness'],
    circuits: ['Control de Válvula Purga', 'Control de Válvula Ventilación'],
    required_tests: ['test_fuel_cap_visual', 'test_purge_solenoid', 'test_smoke_leak_test'],
    caution_notes: ['El tapón de gasolina suelto o agrietado es responsable de más del 50% de los códigos EVAP.']
  },
  P0455: {
    dtc_code: 'P0455',
    system: 'Emisiones (EVAP Fugae Grande)',
    primary_components: ['fuel_cap', 'evap_purge_valve', 'evap_vent_valve', 'evap_canister'],
    secondary_components: ['fuel_tank'],
    circuits: ['Alimentación Solenoides Purga/Vent'],
    required_tests: ['test_smoke_leak_test', 'test_vent_valve_seal'],
    caution_notes: ['Una fuga grande suele ser un tapón de combustible olvidado o una manguera de purga suelta bajo el capó.']
  },
  P0562: {
    dtc_code: 'P0562',
    system: 'Eléctrico (Voltaje del Sistema Bajo)',
    primary_components: ['battery', 'alternator', 'alternator_fuse', 'wiring_harness'],
    secondary_components: ['pcm_driver', 'ground_g302'],
    circuits: ['Línea de Carga Principal (B+)', 'Señal de Campo Alternador', 'Masa Motor'],
    required_tests: ['test_battery_charging_voltage', 'test_voltage_drop_charging', 'test_alternator_ripple'],
    caution_notes: ['Pruebe la batería primero. Una batería dañada con celdas cortocircuitadas sobrecargará y dañará el alternador.']
  },
  P0340: {
    dtc_code: 'P0340',
    system: 'Encendido / Sensores',
    primary_components: ['camshaft_sensor', 'wiring_harness', 'pcm_driver'],
    secondary_components: ['crankshaft_sensor', 'timing_belt'],
    circuits: ['Referencia CMP (+5V o +12V)', 'Masa CMP', 'Señal de Pulso CMP'],
    required_tests: ['test_cmp_reference', 'test_cmp_ground', 'test_cmp_pulse_signal'],
    caution_notes: ['Si hay ruido eléctrico por bobinas dañadas o arnés mal enrutado, se pueden perder los pulsos del sensor.']
  },
  P0335: {
    dtc_code: 'P0335',
    system: 'Encendido / Sensores',
    primary_components: ['crankshaft_sensor', 'wiring_harness', 'pcm_driver'],
    secondary_components: ['starter_motor', 'battery'],
    circuits: ['Referencia CKP (+5V)', 'Masa CKP', 'Señal de Pulso CKP'],
    required_tests: ['test_ckp_reference', 'test_ckp_ground', 'test_ckp_pulse_signal'],
    caution_notes: ['El fallo de este sensor suele provocar que el motor gire con el arranque pero no encienda.']
  },
  P0505: {
    dtc_code: 'P0505',
    system: 'Admisión / Actuadores',
    primary_components: ['iac_valve', 'throttle_body', 'wiring_harness'],
    secondary_components: ['vacuum_lines', 'pcm_driver'],
    circuits: ['Bobina IAC A (Apertura)', 'Bobina IAC B (Cierre)'],
    required_tests: ['test_iac_coils_resistance', 'test_iac_signals', 'test_throttle_cleaning'],
    caution_notes: ['Limpie el cuerpo de aceleración y los pasajes de la válvula IAC de carbón acumulado antes de reemplazar la válvula.']
  },
  P0700: {
    dtc_code: 'P0700',
    system: 'Transmisión',
    primary_components: ['tcm_module', 'wiring_harness', 'pcm_driver'],
    secondary_components: ['transmission_sensors'],
    circuits: ['Línea de Comunicación CAN BUS', 'Alimentación TCM'],
    required_tests: ['test_tcm_power', 'test_can_bus_continuity', 'scan_tcm_codes'],
    caution_notes: ['El código P0700 es solo un aviso de la PCM indicando que la TCM tiene códigos de transmisión específicos activos.']
  },
  P0705: {
    dtc_code: 'P0705',
    system: 'Transmisión / Sensores',
    primary_components: ['transmission_range_sensor', 'wiring_harness', 'shifter_cable'],
    secondary_components: ['tcm_module'],
    circuits: ['Señales de Posición (P/R/N/D)', 'Alimentación Switch Rango'],
    required_tests: ['test_range_sensor_resistance', 'test_shifter_alignment', 'test_range_sensor_voltage'],
    caution_notes: ['El sensor de rango suele estar expuesto al agua y lodo debajo del carro, lo que causa corrosión en su conector.']
  },
  P1709: {
    dtc_code: 'P1709',
    system: 'Transmisión / Switch PNP',
    primary_components: ['transmission_range_sensor', 'wiring_harness', 'clutch_switch_if_manual'],
    secondary_components: ['pcm_driver'],
    circuits: ['Circuito Autodiagnóstico PNP'],
    required_tests: ['test_pnp_continuity', 'test_clutch_switch_operation'],
    caution_notes: ['Este código indica que el switch de Parking/Neutral no estaba en la posición correcta durante la prueba automática.']
  }
};

/**
 * Pruebas guiadas por componente.
 */
export const COMPONENT_TESTS: Record<string, ComponentTest[]> = {
  fuel_pump_fuse: [
    {
      id: 'test_fuse_continuity',
      component_key: 'fuel_pump_fuse',
      name: 'Prueba de Continuidad y Voltaje del Fusible',
      required_tools: ['Multímetro Digital', 'Lámpara de Pruebas'],
      safety_level: 'SAFE',
      steps: [
        'Localice la caja de fusibles en el vano motor.',
        'Identifique el fusible de la bomba de combustible (15A, ranura F2).',
        'Use el multímetro en escala de Continuidad (Sonido). Coloque las puntas en los terminales metálicos de prueba del fusible.',
        'Mida el voltaje con ignición abierta: coloque la punta negra a masa y la roja en cada uno de los terminales del fusible. Debería medir 12V en ambos lados.'
      ],
      expected_result: 'Continuidad menor a 0.5 Ω y voltaje de batería (+12.4V) en ambos lados.',
      pass_action: 'Fusible en perfecto estado. Proceder a revisar el relé.',
      fail_action: 'Fusible abierto (quemado) o sin alimentación. Reemplazar fusible e investigar posible cortocircuito.'
    }
  ],
  fuel_pump_relay: [
    {
      id: 'test_relay_voltage',
      component_key: 'fuel_pump_relay',
      name: 'Alimentación de Fuerza del Relé (Terminal 30)',
      required_tools: ['Multímetro Digital', 'Pinza extractora de Relés'],
      safety_level: 'SAFE',
      steps: [
        'Extraiga el relé de la bomba de combustible (R2).',
        'Localice el Pin 30 en la base de la caja de fusibles.',
        'Conecte el multímetro en escala de Voltaje de CC. Punta negra a chasis (tierra) y punta roja al Pin 30.',
        'Verifique que haya voltaje constante (incluso con la llave apagada).'
      ],
      expected_result: 'Voltaje de batería constante (entre 12.0V y 12.6V).',
      pass_action: 'Terminal 30 tiene energía directa de batería. Proceder a probar la señal de control.',
      fail_action: 'No hay voltaje. Revisar el fusible principal y el arnés de la caja de fusibles.'
    },
    {
      id: 'test_relay_control',
      component_key: 'fuel_pump_relay',
      name: 'Circuito de Control del Relé (Terminal 85/86)',
      required_tools: ['Multímetro Digital', 'Lámpara de Pruebas Led'],
      safety_level: 'SAFE',
      steps: [
        'Con el relé extraído, identifique las terminales 85 y 86 en la base.',
        'Ponga la llave del carro en posición de Ignición (ON) sin arrancar.',
        'Mida el voltaje en el terminal 86 (+12V proveniente de la ignición).',
        'Mida la conmutación a masa en el terminal 85 provista por la PCM (dura aproximadamente 2 segundos al abrir la ignición para presurizar el riel).'
      ],
      expected_result: 'Voltaje de +12V en Pin 86 e impulso de masa (0V) en Pin 85 durante 2 segundos.',
      pass_action: 'Circuito de control operando bien. La PCM está enviando la orden de activación.',
      fail_action: 'Falta alimentación de ignición o la PCM no conmuta a masa. Revisar PCM y tierras del arnés.'
    }
  ],
  fuel_pump: [
    {
      id: 'test_pump_voltage',
      component_key: 'fuel_pump',
      name: 'Mapeo de Alimentación en Bomba de Combustible',
      required_tools: ['Multímetro Digital', 'Lámpara de Pruebas de Carga'],
      safety_level: 'SAFE',
      steps: [
        'Acceda al conector de la bomba de combustible debajo del asiento trasero.',
        'Desconecte el arnés e identifique los pines de alimentación y masa.',
        'Ponga la punta roja del multímetro en la terminal de alimentación y la negra a chasis.',
        'Pida a un ayudante abrir la ignición y observe el voltaje. Debe sostener +12V durante 2 segundos.'
      ],
      expected_result: 'Voltaje igual al de la batería (+12V) durante la presurización.',
      pass_action: 'La alimentación está llegando al conector de la bomba. Si no arranca, la bomba tiene fallo interno.',
      fail_action: 'No llega voltaje. La falla se encuentra antes: arnés abierto, conector corroído o relé inoperante.'
    },
    {
      id: 'test_pump_ground',
      component_key: 'fuel_pump',
      name: 'Prueba de Caída de Tensión en Masa de la Bomba',
      required_tools: ['Multímetro Digital'],
      safety_level: 'SAFE',
      steps: [
        'Conecte de nuevo el arnés de la bomba.',
        'Ponga el multímetro en escala de Voltaje de CC fino.',
        'Coloque la punta roja en el cable de masa de la bomba (lado arnés conectado) y la punta negra en un punto de chasis desnudo de metal.',
        'Dé marcha al motor y registre la lectura.'
      ],
      expected_result: 'Voltaje de caída de tensión menor a 0.2V.',
      pass_action: 'El circuito de masa de la bomba es confiable y libre de resistencia.',
      fail_action: 'Voltaje superior a 0.5V. Hay alta resistencia en el circuito de masa. Limpie el punto de tierra G302.'
    }
  ]
};

/**
 * Determina el resultado de una medición técnica comparando con los rangos esperados.
 */
export function evaluateMeasurement(
  type: MeasurementType,
  value: number,
  expectedMin: number | null,
  expectedMax: number | null
): 'PASS' | 'FAIL' | 'INCONCLUSIVE' {
  if (value === undefined || isNaN(value)) return 'INCONCLUSIVE';

  // Reglas especiales basadas en tipo
  if (type === 'CONTINUITY' || type === 'RESISTANCE') {
    // Continuidad suele ser buena si es cercana a 0
    const maxLimit = expectedMax !== null ? expectedMax : 1.0; // por defecto < 1 ohmio es excelente
    return value <= maxLimit ? 'PASS' : 'FAIL';
  }

  // Comportamiento genérico de rango (min/max)
  const minOk = expectedMin === null ? true : value >= expectedMin;
  const maxOk = expectedMax === null ? true : value <= expectedMax;

  return (minOk && maxOk) ? 'PASS' : 'FAIL';
}

/**
 * Algoritmo causal de diagnóstico:
 * Recalcula la probabilidad de fallo de cada componente basándose en las mediciones ingresadas.
 */
export function recalculateCausalProbabilities(
  dtcCode: string,
  measurements: ComponentMeasurement[]
): Record<string, { probability: number; status: ComponentStatus; rationale: string }> {
  const result: Record<string, { probability: number; status: ComponentStatus; rationale: string }> = {};
  
  const map = DTC_COMPONENT_MAPS[dtcCode];
  if (!map) return {};

  const allComponents = [...map.primary_components, ...map.secondary_components];
  const baseProb = Math.round(100 / allComponents.length);
  allComponents.forEach(comp => {
    result[comp] = {
      probability: baseProb,
      status: 'RELATED_TO_DTC',
      rationale: 'Componente relacionado directamente con el circuito del DTC activo.'
    };
  });

  if (dtcCode === 'P0230') {
    const fuseMeas = measurements.find(m => m.component_id === 'fuel_pump_fuse' && m.test_id === 'test_fuse_continuity');
    const relay30Meas = measurements.find(m => m.component_id === 'fuel_pump_relay' && m.test_id === 'test_relay_voltage');
    const relayControlMeas = measurements.find(m => m.component_id === 'fuel_pump_relay' && m.test_id === 'test_relay_control');
    const pumpVoltMeas = measurements.find(m => m.component_id === 'fuel_pump' && m.test_id === 'test_pump_voltage');
    const pumpGroundMeas = measurements.find(m => m.component_id === 'fuel_pump' && m.test_id === 'test_pump_ground');

    if (fuseMeas) {
      if (fuseMeas.result === 'FAIL') {
        result['fuel_pump_fuse'] = { probability: 95, status: 'CONFIRMED_FAULT', rationale: 'Fusible fallido en la prueba de continuidad bajo carga.' };
        result['fuel_pump_relay'] = { probability: 2, status: 'NORMAL', rationale: 'Se aisló la falla al fusible de fuerza.' };
        result['fuel_pump'] = { probability: 2, status: 'NORMAL', rationale: 'Bomba de combustible sin tensión de entrada.' };
        return result;
      } else if (fuseMeas.result === 'PASS') {
        result['fuel_pump_fuse'] = { probability: 1, status: 'TEST_PASSED', rationale: 'Fusible verificado OK. Continuidad y tensión correctas.' };
      }
    }

    if (relay30Meas) {
      if (relay30Meas.result === 'FAIL') {
        result['wiring_harness'] = { probability: 85, status: 'CONFIRMED_FAULT', rationale: 'Sin alimentación en Pin 30 de la base del relé. Arnés cortado o fusible principal quemado.' };
        result['fuel_pump_relay'] = { probability: 5, status: 'TEST_REQUIRED', rationale: 'Relé inoperante por falta de alimentación de entrada.' };
        return result;
      } else if (relay30Meas.result === 'PASS') {
        result['wiring_harness'] = { probability: 10, status: 'NORMAL', rationale: 'Línea de alimentación de batería a relé verificada OK.' };
      }
    }

    if (relayControlMeas) {
      if (relayControlMeas.result === 'FAIL') {
        result['pcm_driver'] = { probability: 90, status: 'CONFIRMED_FAULT', rationale: 'La PCM no envía señal de activación por Pin 85 o falta masa constante.' };
        result['fuel_pump_relay'] = { probability: 5, status: 'NORMAL', rationale: 'Relé libre de sospecha, no recibe señal del computador.' };
        return result;
      } else if (relayControlMeas.result === 'PASS') {
        result['pcm_driver'] = { probability: 2, status: 'TEST_PASSED', rationale: 'Señal del driver PCM verificada correcta.' };
      }
    }

    if (pumpVoltMeas) {
      if (pumpVoltMeas.result === 'FAIL') {
        result['fuel_pump_relay'] = { probability: 80, status: 'CONFIRMED_FAULT', rationale: 'Relé energizado no saca voltaje por terminal 87 hacia el arnés.' };
        result['fuel_pump'] = { probability: 5, status: 'TEST_REQUIRED', rationale: 'Bomba sin alimentación eléctrica.' };
        return result;
      } else if (pumpVoltMeas.result === 'PASS') {
        result['fuel_pump_relay'] = { probability: 2, status: 'TEST_PASSED', rationale: 'El relé activa y envía tensión correctamente.' };
        result['fuel_pump'] = { probability: 95, status: 'CONFIRMED_FAULT', rationale: 'La bomba de combustible recibe +12V pero no presuriza. Bobinado defectuoso.' };
      }
    }

    if (pumpGroundMeas) {
      if (pumpGroundMeas.result === 'FAIL') {
        result['ground_g302'] = { probability: 90, status: 'CONFIRMED_FAULT', rationale: 'Alta resistencia medida en la línea de masa de la bomba.' };
        result['fuel_pump'] = { probability: 5, status: 'SUSPECT', rationale: 'Bomba inoperante por retorno de masa deficiente.' };
      } else if (pumpGroundMeas.result === 'PASS') {
        result['ground_g302'] = { probability: 1, status: 'TEST_PASSED', rationale: 'Masa de la bomba de combustible limpia y firme.' };
      }
    }
  }

  return result;
}

/**
 * Genera proceduralmente la estructura del arnés de circuitos ( WiringCircuit ) para visualización interactiva.
 */
export function getWiringCircuitTemplate(vehicleId: string, dtcCode: string): WiringCircuit {
  const nodes: CircuitNode[] = [
    { id: 'batt', type: 'POWER_SUPPLY', label: 'Batería (+12.4V)', pin_nullable: null, expected_voltage_nullable: 12.4, expected_resistance_nullable: null, test_point: true },
    { id: 'fuse_main', type: 'FUSE', label: 'Fusible B+ (80A)', pin_nullable: 'FL-A', expected_voltage_nullable: 12.4, expected_resistance_nullable: 0.1, test_point: true },
    { id: 'ign_sw', type: 'CONNECTOR', label: 'Switch de Ignición', pin_nullable: 'IGN1', expected_voltage_nullable: 12.4, expected_resistance_nullable: null, test_point: false }
  ];

  const edges: CircuitEdge[] = [
    { from_node: 'batt', to_node: 'fuse_main', wire_color_nullable: 'Rojo', expected_signal: 'Fuerza Constante', status: 'NORMAL' },
    { from_node: 'fuse_main', to_node: 'ign_sw', wire_color_nullable: 'Blanco-Rojo', expected_signal: 'Fuerza Constante', status: 'NORMAL' }
  ];

  if (dtcCode === 'P0230' || dtcCode.startsWith('P02')) {
    nodes.push(
      { id: 'fuse_pump', type: 'FUSE', label: 'Fusible Bomba (15A)', pin_nullable: 'F2', expected_voltage_nullable: 12.4, expected_resistance_nullable: 0.2, test_point: true },
      { id: 'relay_pump', type: 'RELAY', label: 'Relé Bomba R2', pin_nullable: 'R2-30', expected_voltage_nullable: 12.4, expected_resistance_nullable: 75.0, test_point: true },
      { id: 'conn_pump', type: 'CONNECTOR', label: 'Conector C301 (Vano)', pin_nullable: 'Pin 4', expected_voltage_nullable: 12.4, expected_resistance_nullable: null, test_point: true },
      { id: 'pump', type: 'ACTUATOR', label: 'Bomba Combustible', pin_nullable: 'M01', expected_voltage_nullable: 12.0, expected_resistance_nullable: 1.5, test_point: true },
      { id: 'pcm', type: 'ECU_PIN', label: 'Driver PCM', pin_nullable: 'C01-85', expected_voltage_nullable: 0.1, expected_resistance_nullable: null, test_point: true },
      { id: 'ground_pump', type: 'GROUND', label: 'Tierra G302', pin_nullable: null, expected_voltage_nullable: 0.0, expected_resistance_nullable: 0.1, test_point: true }
    );

    edges.push(
      { from_node: 'ign_sw', to_node: 'fuse_pump', wire_color_nullable: 'Negro-Amarillo', expected_signal: 'Ignición ON (+12V)', status: 'NORMAL' },
      { from_node: 'fuse_pump', to_node: 'relay_pump', wire_color_nullable: 'Azul-Blanco', expected_signal: 'Ignición ON (+12V)', status: 'NORMAL' },
      { from_node: 'relay_pump', to_node: 'conn_pump', wire_color_nullable: 'Verde-Amarillo', expected_signal: 'Ignición ON (+12V durante 2s)', status: 'NORMAL' },
      { from_node: 'conn_pump', to_node: 'pump', wire_color_nullable: 'Verde-Amarillo', expected_signal: 'Ignición ON (+12V)', status: 'NORMAL' },
      { from_node: 'pump', to_node: 'ground_pump', wire_color_nullable: 'Negro', expected_signal: 'Masa constante (0V)', status: 'NORMAL' },
      { from_node: 'ign_sw', to_node: 'relay_pump', wire_color_nullable: 'Negro-Azul', expected_signal: 'Bobina control (+12V)', status: 'NORMAL' },
      { from_node: 'relay_pump', to_node: 'pcm', wire_color_nullable: 'Rosado', expected_signal: 'Masa control conmutada (0V)', status: 'NORMAL' }
    );
  } else {
    nodes.push(
      { id: 'fuse_gen', type: 'FUSE', label: 'Fusible Control (10A)', pin_nullable: 'F6', expected_voltage_nullable: 12.4, expected_resistance_nullable: 0.2, test_point: true },
      { id: 'sensor_gen', type: 'SENSOR', label: 'Sensor Relacionado', pin_nullable: 'S1', expected_voltage_nullable: 5.0, expected_resistance_nullable: 2500, test_point: true },
      { id: 'ground_gen', type: 'GROUND', label: 'Tierra Chasis G101', pin_nullable: null, expected_voltage_nullable: 0.0, expected_resistance_nullable: 0.1, test_point: true }
    );

    edges.push(
      { from_node: 'ign_sw', to_node: 'fuse_gen', wire_color_nullable: 'Gris', expected_signal: 'Ignición ON (+12V)', status: 'NORMAL' },
      { from_node: 'fuse_gen', to_node: 'sensor_gen', wire_color_nullable: 'Café', expected_signal: 'Alimentación Regulada', status: 'NORMAL' },
      { from_node: 'sensor_gen', to_node: 'ground_gen', wire_color_nullable: 'Negro', expected_signal: 'Masa de Retorno', status: 'NORMAL' }
    );
  }

  return {
    id: `wire_${vehicleId}_${dtcCode}`,
    vehicle_id: vehicleId,
    circuit_name: `Topología del Circuito para DTC ${dtcCode}`,
    related_dtcs: [dtcCode],
    nodes,
    edges,
    confidence: 85
  };
}

/**
 * Genera el layout de la caja de fusibles ( FuseRelayBox ) con fusibles relacionados.
 */
export function getFuseRelayBoxTemplate(vehicleId: string, location: 'ENGINE_BAY' | 'UNDER_DASH'): FuseRelayBox & { slots: FuseRelaySlot[] } {
  const box: FuseRelayBox = {
    id: `box_${location.toLowerCase()}_${vehicleId}`,
    vehicle_id: vehicleId,
    location: location === 'ENGINE_BAY' ? 'ENGINE_BAY' : 'UNDER_DASH',
    label: location === 'ENGINE_BAY' ? 'Caja de Fusibles y Relés del Motor (B+)' : 'Caja de Fusibles del Habitáculo',
    layout_template: 'GENERIC_GRID',
    confidence: 90
  };

  const slots: FuseRelaySlot[] = [];

  if (location === 'ENGINE_BAY') {
    slots.push(
      { id: `slot_f1_${box.id}`, box_id: box.id, slot_code: 'F1', label: 'Fusible Principal', amperage_nullable: 80, component_protected: 'Alternador / Batería / Ignición', related_dtcs: ['P0562'], position_row: 1, position_col: 1, status: 'NORMAL' },
      { id: `slot_f2_${box.id}`, box_id: box.id, slot_code: 'F2', label: 'Fusible Bomba Combustible', amperage_nullable: 15, component_protected: 'Bomba de Combustible', related_dtcs: ['P0230'], position_row: 1, position_col: 2, status: 'NORMAL' },
      { id: `slot_f3_${box.id}`, box_id: box.id, slot_code: 'F3', label: 'Fusible Control Motor (ECU)', amperage_nullable: 30, component_protected: 'PCM / Inyectores', related_dtcs: ['P0300', 'P0301'], position_row: 1, position_col: 3, status: 'NORMAL' },
      { id: `slot_f4_${box.id}`, box_id: box.id, slot_code: 'F4', label: 'Fusible Alternador', amperage_nullable: 10, component_protected: 'Alternador Field', related_dtcs: ['P0562'], position_row: 2, position_col: 1, status: 'NORMAL' },
      { id: `slot_r1_${box.id}`, box_id: box.id, slot_code: 'R1', label: 'Relé de Ignición / Main', amperage_nullable: null, component_protected: 'Sistema de Encendido / Inyectores', related_dtcs: ['P0300'], position_row: 3, position_col: 1, status: 'NORMAL' },
      { id: `slot_r2_${box.id}`, box_id: box.id, slot_code: 'R2', label: 'Relé Bomba Combustible', amperage_nullable: null, component_protected: 'Bomba de Combustible', related_dtcs: ['P0230'], position_row: 3, position_col: 2, status: 'NORMAL' },
      { id: `slot_r3_${box.id}`, box_id: box.id, slot_code: 'R3', label: 'Relé Motor Arranque', amperage_nullable: null, component_protected: 'Marcha / Arranque', related_dtcs: ['P0335'], position_row: 3, position_col: 3, status: 'NORMAL' }
    );
  } else {
    slots.push(
      { id: `slot_f5_${box.id}`, box_id: box.id, slot_code: 'F5', label: 'Fusible Sensores Habitáculo', amperage_nullable: 10, component_protected: 'Sensor de Rango Shifter', related_dtcs: ['P0705'], position_row: 1, position_col: 1, status: 'NORMAL' },
      { id: `slot_f6_${box.id}`, box_id: box.id, slot_code: 'F6', label: 'Fusible Tablero Instrumentos', amperage_nullable: 10, component_protected: 'Diagnóstico OBD2 / CAN Gateway', related_dtcs: ['P0700'], position_row: 1, position_col: 2, status: 'NORMAL' },
      { id: `slot_f7_${box.id}`, box_id: box.id, slot_code: 'F7', label: 'Fusible OBD2 Port', amperage_nullable: 15, component_protected: 'Puerto DLC OBD2', related_dtcs: [], position_row: 2, position_col: 1, status: 'NORMAL' }
    );
  }

  return { ...box, slots };
}

/** @deprecated Unverified legacy seed. Production consumers must use universalPartsCatalog.ts. */
export const LEGACY_UNVERIFIED_SUSPENSION_PARTS_CATALOG: DetailedPart[] = [
  {
    id: "front_subframe",
    name: "Bastidor Auxiliar Delantero (Subframe)",
    aliases: ["cuna de motor", "subframe delantero", "crossmember delantero"],
    category: "Estructura",
    system: "Suspensión / Chasis",
    subsystem: "Subchasis",
    assembly: "Vano Motor",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "CENTER",
    specification: {
      oem_number: "54510-25000",
      equivalent_numbers: ["AM-54510-25000", "AFTER-54510-25000"],
      dimensions: "Estructura soldada reforzada",
      material: "Acero estampado estructural",
      weight_kg: 35.2,
      torque_nm: "95-120 N·m"
    },
    symptoms: ["Alineación inestable", "golpes al acelerar/frenar"],
    related_dtcs: ["P0230", "P0300"],
    confidence_level: "CONFIRMED"
  },
  {
    id: "subframe_bolts",
    name: "Pernos del Bastidor Auxiliar",
    aliases: ["tornillos de cuna", "subframe bolts"],
    category: "Fijación",
    system: "Suspensión / Chasis",
    subsystem: "Elementos de Fijación",
    assembly: "Subchasis",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "CENTER",
    specification: {
      oem_number: "54515-25100",
      equivalent_numbers: ["AM-54515-25100", "AFTER-54515-25100"],
      dimensions: "M14 x 1.5 x 110mm",
      material: "Acero grado 10.9 de alta resistencia",
      weight_kg: 0.25,
      torque_nm: "100-120 N·m"
    },
    symptoms: ["Crujido metálico en baches", "cuna floja"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "engine_mount_front",
    name: "Soporte de Motor Delantero",
    aliases: ["soporte frontal de motor", "front mount"],
    category: "Soportes",
    system: "Motor",
    subsystem: "Monturas",
    assembly: "Bloque de Motor",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "FRONT",
    specification: {
      oem_number: "21910-25000",
      equivalent_numbers: ["AM-21910-25000", "AFTER-21910-25000"],
      dimensions: "140mm x 90mm",
      material: "Acero y caucho vulcanizado",
      weight_kg: 1.8,
      torque_nm: "50-65 N·m"
    },
    symptoms: ["Vibración excesiva en ralentí", "golpeteo al cambiar"],
    related_dtcs: ["P0300"],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_wheel_bearing",
    name: "Rodamiento de Rueda Delantero Izquierdo",
    aliases: ["balinera delantera izquierda", "wheel bearing left"],
    category: "Rodamientos",
    system: "Suspensión",
    subsystem: "Conjunto de Rueda",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "51720-25000",
      equivalent_numbers: ["AM-51720-25000", "AFTER-51720-25000"],
      dimensions: "Diámetro int: 38mm, ext: 70mm, ancho: 37mm",
      material: "Acero aleado cromado de alto carbono",
      weight_kg: 0.75,
      torque_nm: "200-260 N·m (tuerca eje)"
    },
    symptoms: ["Zumbido al rodar que cambia con la velocidad y viraje"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_wheel_bearing",
    name: "Rodamiento de Rueda Delantero Derecho",
    aliases: ["balinera delantera derecha", "wheel bearing right"],
    category: "Rodamientos",
    system: "Suspensión",
    subsystem: "Conjunto de Rueda",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "51720-25000",
      equivalent_numbers: ["AM-51720-25000", "AFTER-51720-25000"],
      dimensions: "Diámetro int: 38mm, ext: 70mm, ancho: 37mm",
      material: "Acero aleado cromado de alto carbono",
      weight_kg: 0.75,
      torque_nm: "200-260 N·m (tuerca eje)"
    },
    symptoms: ["Zumbido al rodar que cambia con la velocidad y viraje"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_lower_control_arm",
    name: "Brazo de Control Inferior Delantero Izquierdo (Tijereta)",
    aliases: ["tijereta izquierda", "brazo inferior izquierdo", "control arm left"],
    category: "Dirección/Suspensión",
    system: "Suspensión",
    subsystem: "Brazos",
    assembly: "Subchasis",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54500-25000",
      equivalent_numbers: ["AM-54500-25000", "AFTER-54500-25000"],
      dimensions: "Longitud: 340mm",
      material: "Acero estampado",
      weight_kg: 4.2,
      torque_nm: "95-120 N·m (pernos buje)"
    },
    symptoms: ["El vehículo tira hacia un lado al frenar", "clonk metálico"],
    related_dtcs: ["P0230"],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_lower_control_arm",
    name: "Brazo de Control Inferior Delantero Derecho (Tijereta)",
    aliases: ["tijereta derecha", "brazo inferior derecho", "control arm right"],
    category: "Dirección/Suspensión",
    system: "Suspensión",
    subsystem: "Brazos",
    assembly: "Subchasis",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54501-25000",
      equivalent_numbers: ["AM-54501-25000", "AFTER-54501-25000"],
      dimensions: "Longitud: 340mm",
      material: "Acero estampado",
      weight_kg: 4.2,
      torque_nm: "95-120 N·m (pernos buje)"
    },
    symptoms: ["El vehículo tira hacia un lado al frenar", "clonk metálico"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_arm_front_bushing",
    name: "Buje Delantero del Brazo Izquierdo",
    aliases: ["bushing pequeño izquierdo", "silentblock delantero izquierdo"],
    category: "Bujes",
    system: "Suspensión",
    subsystem: "Bujes de Brazo",
    assembly: "Brazo de Control",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54551-25000",
      equivalent_numbers: ["AM-54551-25000", "AFTER-54551-25000"],
      dimensions: "Diámetro: 35mm",
      material: "Caucho y metal",
      weight_kg: 0.3,
      torque_nm: "95-120 N·m (apriete en peso)"
    },
    symptoms: ["Inestabilidad en alineación", "cabeceo al acelerar"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_arm_rear_bushing",
    name: "Buje Trasero del Brazo Izquierdo",
    aliases: ["bushing grande izquierdo", "casquillo trasero izquierdo"],
    category: "Bujes",
    system: "Suspensión",
    subsystem: "Bujes de Brazo",
    assembly: "Brazo de Control",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54552-25000",
      equivalent_numbers: ["AM-54552-25000", "AFTER-54552-25000"],
      dimensions: "Diámetro: 60mm",
      material: "Caucho y metal",
      weight_kg: 0.55,
      torque_nm: "95-120 N·m (apriete en peso)"
    },
    symptoms: ["Inestabilidad en alineación", "cabeceo al acelerar"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_arm_front_bushing",
    name: "Buje Delantero del Brazo Derecho",
    aliases: ["bushing pequeño derecho", "silentblock delantero derecho"],
    category: "Bujes",
    system: "Suspensión",
    subsystem: "Bujes de Brazo",
    assembly: "Brazo de Control",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54551-25000",
      equivalent_numbers: ["AM-54551-25000", "AFTER-54551-25000"],
      dimensions: "Diámetro: 35mm",
      material: "Caucho y metal",
      weight_kg: 0.3,
      torque_nm: "95-120 N·m (apriete en peso)"
    },
    symptoms: ["Inestabilidad en alineación", "cabeceo al acelerar"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_arm_rear_bushing",
    name: "Buje Trasero del Brazo Derecho",
    aliases: ["bushing grande derecho", "casquillo trasero derecho"],
    category: "Bujes",
    system: "Suspensión",
    subsystem: "Bujes de Brazo",
    assembly: "Brazo de Control",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54552-25000",
      equivalent_numbers: ["AM-54552-25000", "AFTER-54552-25000"],
      dimensions: "Diámetro: 60mm",
      material: "Caucho y metal",
      weight_kg: 0.55,
      torque_nm: "95-120 N·m (apriete en peso)"
    },
    symptoms: ["Inestabilidad en alineación", "cabeceo al acelerar"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_ball_joint",
    name: "Rótula Inferior Delantera Izquierda",
    aliases: ["rotula izquierda", "ball joint left"],
    category: "Dirección/Suspensión",
    system: "Suspensión",
    subsystem: "Rótulas",
    assembly: "Brazo de Control",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54530-25000",
      equivalent_numbers: ["AM-54530-25000", "AFTER-54530-25000"],
      dimensions: "Perno cónico: 15mm",
      material: "Acero forjado templado",
      weight_kg: 0.65,
      torque_nm: "60-72 N·m (tuerca almena)"
    },
    symptoms: ["Golpeteo seco al pasar baches", "juego en la rueda"],
    related_dtcs: ["P0230"],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_ball_joint",
    name: "Rótula Inferior Delantera Derecha",
    aliases: ["rotula derecha", "ball joint right"],
    category: "Dirección/Suspensión",
    system: "Suspensión",
    subsystem: "Rótulas",
    assembly: "Brazo de Control",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54530-25000",
      equivalent_numbers: ["AM-54530-25000", "AFTER-54530-25000"],
      dimensions: "Perno cónico: 15mm",
      material: "Acero forjado templado",
      weight_kg: 0.65,
      torque_nm: "60-72 N·m (tuerca almena)"
    },
    symptoms: ["Golpeteo seco al pasar baches", "juego en la rueda"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_strut",
    name: "Amortiguador Delantero Izquierdo",
    aliases: ["strut izquierdo", "amortiguador izquierdo"],
    category: "Suspensión",
    system: "Suspensión",
    subsystem: "Amortiguadores",
    assembly: "Torre de Amortiguación",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54650-25100",
      equivalent_numbers: ["AM-54650-25100", "AFTER-54650-25100"],
      dimensions: "Carrera: 150mm",
      material: "Acero y aceite hidráulico presurizado",
      weight_kg: 4.8,
      torque_nm: "60-70 N·m (tuerca superior)"
    },
    symptoms: ["Rebotes excesivos", "fugas de aceite visibles", "inestabilidad"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_strut",
    name: "Amortiguador Delantero Derecho",
    aliases: ["strut derecho", "amortiguador derecho"],
    category: "Suspensión",
    system: "Suspensión",
    subsystem: "Amortiguadores",
    assembly: "Torre de Amortiguación",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54660-25100",
      equivalent_numbers: ["AM-54660-25100", "AFTER-54660-25100"],
      dimensions: "Carrera: 150mm",
      material: "Acero y aceite hidráulico presurizado",
      weight_kg: 4.8,
      torque_nm: "60-70 N·m (tuerca superior)"
    },
    symptoms: ["Rebotes excesivos", "fugas de aceite visibles", "inestabilidad"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_spring",
    name: "Resorte Helicoidal Delantero Izquierdo",
    aliases: ["espiral izquierdo", "spring left"],
    category: "Suspensión",
    system: "Suspensión",
    subsystem: "Resortes",
    assembly: "Amortiguador",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54601-25100",
      equivalent_numbers: ["AM-54601-25100", "AFTER-54601-25100"],
      dimensions: "Diámetro alambre: 12.5mm",
      material: "Acero para resortes silicio-manganeso",
      weight_kg: 2.9,
      torque_nm: "N/A"
    },
    symptoms: ["Vehículo caído de un lado", "chirridos metálicos", "rotura visible"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_spring",
    name: "Resorte Helicoidal Delantero Derecho",
    aliases: ["espiral derecho", "spring right"],
    category: "Suspensión",
    system: "Suspensión",
    subsystem: "Resortes",
    assembly: "Amortiguador",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54601-25100",
      equivalent_numbers: ["AM-54601-25100", "AFTER-54601-25100"],
      dimensions: "Diámetro alambre: 12.5mm",
      material: "Acero para resortes silicio-manganeso",
      weight_kg: 2.9,
      torque_nm: "N/A"
    },
    symptoms: ["Vehículo caído de un lado", "chirridos metálicos", "rotura visible"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_strut_mount",
    name: "Copela Superior Delantera Izquierda",
    aliases: ["base de amortiguador izquierda", "strut mount left"],
    category: "Suspensión",
    system: "Suspensión",
    subsystem: "Copelas",
    assembly: "Carrocería",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54610-25000",
      equivalent_numbers: ["AM-54610-25000", "AFTER-54610-25000"],
      dimensions: "Montaje: 3 espárragos",
      material: "Placa de acero y goma de aislamiento",
      weight_kg: 1.2,
      torque_nm: "20-30 N·m (tuercas torre)"
    },
    symptoms: ["Golpe metálico seco al pasar baches", "goma agrietada"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_strut_mount",
    name: "Copela Superior Delantera Derecha",
    aliases: ["base de amortiguador derecha", "strut mount right"],
    category: "Suspensión",
    system: "Suspensión",
    subsystem: "Copelas",
    assembly: "Carrocería",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54610-25000",
      equivalent_numbers: ["AM-54610-25000", "AFTER-54610-25000"],
      dimensions: "Montaje: 3 espárragos",
      material: "Placa de acero y goma de aislamiento",
      weight_kg: 1.2,
      torque_nm: "20-30 N·m (tuercas torre)"
    },
    symptoms: ["Golpe metálico seco al pasar baches", "goma agrietada"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_strut_bearing",
    name: "Rodamiento de Copela Delantero Izquierdo",
    aliases: ["balinera de copela izquierda", "strut bearing left"],
    category: "Rodamientos",
    system: "Suspensión",
    subsystem: "Copelas",
    assembly: "Copela Superior",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54612-25000",
      equivalent_numbers: ["AM-54612-25000", "AFTER-54612-25000"],
      dimensions: "Espesor: 12mm",
      material: "Plástico reforzado y balines de acero",
      weight_kg: 0.15,
      torque_nm: "N/A"
    },
    symptoms: ["Crujido metálico o salto del resorte al girar la dirección"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_strut_bearing",
    name: "Rodamiento de Copela Delantero Derecho",
    aliases: ["balinera de copela derecha", "strut bearing right"],
    category: "Rodamientos",
    system: "Suspensión",
    subsystem: "Copelas",
    assembly: "Copela Superior",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54612-25000",
      equivalent_numbers: ["AM-54612-25000", "AFTER-54612-25000"],
      dimensions: "Espesor: 12mm",
      material: "Plástico reforzado y balines de acero",
      weight_kg: 0.15,
      torque_nm: "N/A"
    },
    symptoms: ["Crujido metálico o salto del resorte al girar la dirección"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_bump_stop",
    name: "Tope de Amortiguador Izquierdo",
    aliases: ["tope de goma izquierdo", "bump stop left"],
    category: "Topes",
    system: "Suspensión",
    subsystem: "Amortiguadores",
    assembly: "Vástago de Amortiguador",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54625-25000",
      equivalent_numbers: ["AM-54625-25000", "AFTER-54625-25000"],
      dimensions: "Longitud: 85mm",
      material: "Poliuretano microcelular",
      weight_kg: 0.08,
      torque_nm: "N/A"
    },
    symptoms: ["Impacto seco metálico al comprimir a tope"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_bump_stop",
    name: "Tope de Amortiguador Derecho",
    aliases: ["tope de goma derecho", "bump stop right"],
    category: "Topes",
    system: "Suspensión",
    subsystem: "Amortiguadores",
    assembly: "Vástago de Amortiguador",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54625-25000",
      equivalent_numbers: ["AM-54625-25000", "AFTER-54625-25000"],
      dimensions: "Longitud: 85mm",
      material: "Poliuretano microcelular",
      weight_kg: 0.08,
      torque_nm: "N/A"
    },
    symptoms: ["Impacto seco metálico al comprimir a tope"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_dust_boot",
    name: "Guardapolvo de Amortiguador Izquierdo",
    aliases: ["bota protectora izquierda", "dust boot left"],
    category: "Guardapolvos",
    system: "Suspensión",
    subsystem: "Amortiguadores",
    assembly: "Vástago de Amortiguador",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54626-25000",
      equivalent_numbers: ["AM-54626-25000", "AFTER-54626-25000"],
      dimensions: "Fuelle elástico",
      material: "Caucho fuelle flexible",
      weight_kg: 0.1,
      torque_nm: "N/A"
    },
    symptoms: ["Fuelle roto o suelto", "acumulación de polvo en vástago"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_dust_boot",
    name: "Guardapolvo de Amortiguador Derecho",
    aliases: ["bota protectora derecha", "dust boot right"],
    category: "Guardapolvos",
    system: "Suspensión",
    subsystem: "Amortiguadores",
    assembly: "Vástago de Amortiguador",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54626-25000",
      equivalent_numbers: ["AM-54626-25000", "AFTER-54626-25000"],
      dimensions: "Fuelle elástico",
      material: "Caucho fuelle flexible",
      weight_kg: 0.1,
      torque_nm: "N/A"
    },
    symptoms: ["Fuelle roto o suelto", "acumulación de polvo en vástago"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "stabilizer_bar",
    name: "Barra Estabilizadora Delantera",
    aliases: ["barra estabilizadora", "sway bar"],
    category: "Dirección/Suspensión",
    system: "Suspensión",
    subsystem: "Barras",
    assembly: "Subchasis",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "CENTER",
    specification: {
      oem_number: "54811-25000",
      equivalent_numbers: ["AM-54811-25000", "AFTER-54811-25000"],
      dimensions: "Diámetro: 21mm",
      material: "Acero para resortes templado",
      weight_kg: 3.6,
      torque_nm: "35-45 N·m (pernos del soporte)"
    },
    symptoms: ["Inclinación excesiva en curvas", "golpeteo metálico"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "left_stabilizer_link",
    name: "Bieleta Estabilizadora Izquierda",
    aliases: ["link kit izquierdo", "bieleta izquierda", "stabilizer link left"],
    category: "Dirección/Suspensión",
    system: "Suspensión",
    subsystem: "Terminales de Barra",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54830-25000",
      equivalent_numbers: ["AM-54830-25000", "AFTER-54830-25000"],
      dimensions: "Longitud: 240mm",
      material: "Vástago de acero con mini rótulas",
      weight_kg: 0.35,
      torque_nm: "35-45 N·m (tuercas de fijación)"
    },
    symptoms: ["Golpeteo constante rápido en irregularidades pequeñas"],
    related_dtcs: ["P0230"],
    confidence_level: "CONFIRMED"
  },
  {
    id: "right_stabilizer_link",
    name: "Bieleta Estabilizadora Derecha",
    aliases: ["link kit derecho", "bieleta derecha", "stabilizer link right"],
    category: "Dirección/Suspensión",
    system: "Suspensión",
    subsystem: "Terminales de Barra",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54830-25000",
      equivalent_numbers: ["AM-54830-25000", "AFTER-54830-25000"],
      dimensions: "Longitud: 240mm",
      material: "Vástago de acero con mini rótulas",
      weight_kg: 0.35,
      torque_nm: "35-45 N·m (tuercas de fijación)"
    },
    symptoms: ["Golpeteo constante rápido en irregularidades pequeñas"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "stabilizer_bushing_left",
    name: "Buje de Barra Estabilizadora Izquierdo",
    aliases: ["bushing de barra izquierdo", "buje estabilizador izquierdo"],
    category: "Bujes",
    system: "Suspensión",
    subsystem: "Bujes de Barra",
    assembly: "Soporte de Barra",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "54813-25000",
      equivalent_numbers: ["AM-54813-25000", "AFTER-54813-25000"],
      dimensions: "Diámetro interior: 21mm",
      material: "Caucho vulcanizado de alta densidad",
      weight_kg: 0.12,
      torque_nm: "35-45 N·m (pernos soporte)"
    },
    symptoms: ["Crujido de caucho en baches secos o lomos de toro"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "stabilizer_bushing_right",
    name: "Buje de Barra Estabilizadora Derecho",
    aliases: ["bushing de barra derecho", "buje estabilizador derecho"],
    category: "Bujes",
    system: "Suspensión",
    subsystem: "Bujes de Barra",
    assembly: "Soporte de Barra",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "54813-25000",
      equivalent_numbers: ["AM-54813-25000", "AFTER-54813-25000"],
      dimensions: "Diámetro interior: 21mm",
      material: "Caucho vulcanizado de alta densidad",
      weight_kg: 0.12,
      torque_nm: "35-45 N·m (pernos soporte)"
    },
    symptoms: ["Crujido de caucho en baches secos o lomos de toro"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_knuckle",
    name: "Mangueta de Rueda Delantera Izquierda",
    aliases: ["portamasa izquierdo", "nudillo izquierdo", "knuckle left"],
    category: "Estructura",
    system: "Suspensión",
    subsystem: "Manguetas",
    assembly: "Conjunto de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "51715-25000",
      equivalent_numbers: ["AM-51715-25000", "AFTER-51715-25000"],
      dimensions: "Soporte estructural",
      material: "Hierro fundido nodular",
      weight_kg: 5.1,
      torque_nm: "100-120 N·m (strut a mangueta)"
    },
    symptoms: ["Desalineación de camber incorregible", "juego en la rueda"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_knuckle",
    name: "Mangueta de Rueda Delantera Derecha",
    aliases: ["portamasa derecho", "nudillo derecho", "knuckle right"],
    category: "Estructura",
    system: "Suspensión",
    subsystem: "Manguetas",
    assembly: "Conjunto de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "51716-25000",
      equivalent_numbers: ["AM-51716-25000", "AFTER-51716-25000"],
      dimensions: "Soporte estructural",
      material: "Hierro fundido nodular",
      weight_kg: 5.1,
      torque_nm: "100-120 N·m (strut a mangueta)"
    },
    symptoms: ["Desalineación de camber incorregible", "juego en la rueda"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_wheel_hub",
    name: "Cubo de Rueda Delantero Izquierdo",
    aliases: ["flange de rueda izquierdo", "manzana de rueda izquierda", "wheel hub left"],
    category: "Estructura",
    system: "Suspensión",
    subsystem: "Cubo de Rueda",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "51750-25000",
      equivalent_numbers: ["AM-51750-25000", "AFTER-51750-25000"],
      dimensions: "Diámetro: 138mm",
      material: "Acero forjado mecanizado",
      weight_kg: 1.4,
      torque_nm: "200-260 N·m (tuerca eje)"
    },
    symptoms: ["Oscilación o vibración al rodar", "deformación en roscas"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_wheel_hub",
    name: "Cubo de Rueda Delantero Derecho",
    aliases: ["flange de rueda derecho", "manzana de rueda derecha", "wheel hub right"],
    category: "Estructura",
    system: "Suspensión",
    subsystem: "Cubo de Rueda",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "51750-25000",
      equivalent_numbers: ["AM-51750-25000", "AFTER-51750-25000"],
      dimensions: "Diámetro: 138mm",
      material: "Acero forjado mecanizado",
      weight_kg: 1.4,
      torque_nm: "200-260 N·m (tuerca eje)"
    },
    symptoms: ["Oscilación o vibración al rodar", "deformación en roscas"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "wheel_nuts_front_left",
    name: "Tuercas de Rueda Delanteras Izquierdas",
    aliases: ["pernos de rueda izquierdos", "wheel nuts front left"],
    category: "Fijación",
    system: "Frenos / Ruedas",
    subsystem: "Elementos de Fijación",
    assembly: "Cubo de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "52910-25000",
      equivalent_numbers: ["AM-52910-25000", "AFTER-52910-25000"],
      dimensions: "M12 x 1.5, Asiento cónico",
      material: "Acero aleado endurecido grado 8",
      weight_kg: 0.08,
      torque_nm: "90-110 N·m (65-80 lb·ft)"
    },
    symptoms: ["Vibración en volante", "aflojamiento de rueda"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "wheel_nuts_front_right",
    name: "Tuercas de Rueda Delanteras Derechas",
    aliases: ["pernos de rueda derechos", "wheel nuts front right"],
    category: "Fijación",
    system: "Frenos / Ruedas",
    subsystem: "Elementos de Fijación",
    assembly: "Cubo de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "52910-25000",
      equivalent_numbers: ["AM-52910-25000", "AFTER-52910-25000"],
      dimensions: "M12 x 1.5, Asiento cónico",
      material: "Acero aleado endurecido grado 8",
      weight_kg: 0.08,
      torque_nm: "90-110 N·m (65-80 lb·ft)"
    },
    symptoms: ["Vibración en volante", "aflojamiento de rueda"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "steering_rack",
    name: "Cremallera de Dirección Asistida",
    aliases: ["caja de dirección", "steering gear"],
    category: "Dirección",
    system: "Dirección",
    subsystem: "Cremalleras",
    assembly: "Subchasis",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "CENTER",
    specification: {
      oem_number: "57710-25000",
      equivalent_numbers: ["AM-57710-25000", "AFTER-57710-25000"],
      dimensions: "Tipo piñón y cremallera asistida",
      material: "Aluminio fundido y acero de alta dureza",
      weight_kg: 9.5,
      torque_nm: "60-80 N·m (pernos al subchasis)"
    },
    symptoms: ["Fuga de líquido hidráulico", "holgura en el volante", "dirección dura"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "tie_rod_end_left",
    name: "Terminal de Dirección Exterior Izquierdo",
    aliases: ["terminal de direccion izquierdo", "tie rod end left"],
    category: "Dirección/Suspensión",
    system: "Dirección",
    subsystem: "Terminales",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "56820-25000",
      equivalent_numbers: ["AM-56820-25000", "AFTER-56820-25000"],
      dimensions: "Rosca: M14 x 1.5",
      material: "Acero forjado con rótula esférica",
      weight_kg: 0.55,
      torque_nm: "35-45 N·m (tuerca a mangueta)"
    },
    symptoms: ["Juego excesivo en el volante", "desgaste interno/externo de rueda"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "tie_rod_end_right",
    name: "Terminal de Dirección Exterior Derecho",
    aliases: ["terminal de direccion derecho", "tie rod end right"],
    category: "Dirección/Suspensión",
    system: "Dirección",
    subsystem: "Terminales",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "56820-25000",
      equivalent_numbers: ["AM-56820-25000", "AFTER-56820-25000"],
      dimensions: "Rosca: M14 x 1.5",
      material: "Acero forjado con rótula esférica",
      weight_kg: 0.55,
      torque_nm: "35-45 N·m (tuerca a mangueta)"
    },
    symptoms: ["Juego excesivo en el volante", "desgaste interno/externo de rueda"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "tie_rod_inner_left",
    name: "Bieleta de Dirección Interior Izquierda",
    aliases: ["bieleta de dirección izquierda", "axial de dirección izquierda"],
    category: "Dirección/Suspensión",
    system: "Dirección",
    subsystem: "Bieletas de Dirección",
    assembly: "Cremallera",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "57755-25000",
      equivalent_numbers: ["AM-57755-25000", "AFTER-57755-25000"],
      dimensions: "Longitud: 310mm",
      material: "Barra de acero forjado",
      weight_kg: 0.68,
      torque_nm: "70-90 N·m (a cremallera)"
    },
    symptoms: ["Holgura en dirección", "desviación al pasar baches"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "tie_rod_inner_right",
    name: "Bieleta de Dirección Interior Derecha",
    aliases: ["bieleta de dirección derecha", "axial de dirección derecha"],
    category: "Dirección/Suspensión",
    system: "Dirección",
    subsystem: "Bieletas de Dirección",
    assembly: "Cremallera",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "57755-25000",
      equivalent_numbers: ["AM-57755-25000", "AFTER-57755-25000"],
      dimensions: "Longitud: 310mm",
      material: "Barra de acero forjado",
      weight_kg: 0.68,
      torque_nm: "70-90 N·m (a cremallera)"
    },
    symptoms: ["Holgura en dirección", "desviación al pasar baches"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "drive_shaft_left",
    name: "Semieje Delantero Izquierdo",
    aliases: ["eje delantero izquierdo", "CV shaft left"],
    category: "Transmisión/Tren Motriz",
    system: "Transmisión",
    subsystem: "Semiejes",
    assembly: "Cubo de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "49500-25000",
      equivalent_numbers: ["AM-49500-25000", "AFTER-49500-25000"],
      dimensions: "Longitud: 620mm",
      material: "Acero forjado estructural y juntas homocinéticas",
      weight_kg: 6.5,
      torque_nm: "200-260 N·m (tuerca de eje)"
    },
    symptoms: ["Ruido tipo 'clack-clack' al virar acelerando", "grasa derramada"],
    related_dtcs: ["P0230"],
    confidence_level: "CONFIRMED"
  },
  {
    id: "drive_shaft_right",
    name: "Semieje Delantero Derecho",
    aliases: ["eje delantero derecho", "CV shaft right"],
    category: "Transmisión/Tren Motriz",
    system: "Transmisión",
    subsystem: "Semiejes",
    assembly: "Cubo de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "49500-25300",
      equivalent_numbers: ["AM-49500-25300", "AFTER-49500-25300"],
      dimensions: "Longitud: 890mm",
      material: "Acero forjado estructural y juntas homocinéticas",
      weight_kg: 8.2,
      torque_nm: "200-260 N·m (tuerca de eje)"
    },
    symptoms: ["Ruido tipo 'clack-clack' al virar acelerando", "grasa derramada"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "brake_disc_left",
    name: "Disco de Freno Delantero Izquierdo",
    aliases: ["disco izquierdo", "brake rotor left"],
    category: "Frenos",
    system: "Frenos",
    subsystem: "Discos",
    assembly: "Cubo de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "51712-25000",
      equivalent_numbers: ["AM-51712-25000", "AFTER-51712-25000"],
      dimensions: "Diámetro: 241mm, Espesor: 19mm (mín: 17mm)",
      material: "Hierro fundido gris perlítico",
      weight_kg: 4.1,
      torque_nm: "100-115 N·m (montaje cubo)"
    },
    symptoms: ["Vibración en pedal al frenar", "surcos profundos", "pedal largo"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "brake_disc_right",
    name: "Disco de Freno Delantero Derecho",
    aliases: ["disco derecho", "brake rotor right"],
    category: "Frenos",
    system: "Frenos",
    subsystem: "Discos",
    assembly: "Cubo de Rueda",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "51712-25000",
      equivalent_numbers: ["AM-51712-25000", "AFTER-51712-25000"],
      dimensions: "Diámetro: 241mm, Espesor: 19mm (mín: 17mm)",
      material: "Hierro fundido gris perlítico",
      weight_kg: 4.1,
      torque_nm: "100-115 N·m (montaje cubo)"
    },
    symptoms: ["Vibración en pedal al frenar", "surcos profundos", "pedal largo"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "brake_caliper_left",
    name: "Mordaza de Freno Delantera Izquierda (Caliper)",
    aliases: ["caliper izquierdo", "mordaza izquierda"],
    category: "Frenos",
    system: "Frenos",
    subsystem: "Mordazas",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "58110-25000",
      equivalent_numbers: ["AM-58110-25000", "AFTER-58110-25000"],
      dimensions: "Pistón simple: 54mm",
      material: "Hierro fundido y pistón de acero",
      weight_kg: 2.8,
      torque_nm: "65-75 N·m (perno caliper a mangueta)"
    },
    symptoms: ["Frenado desigual", "el carro se carga de un lado al frenar"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "brake_caliper_right",
    name: "Mordaza de Freno Delantera Derecha (Caliper)",
    aliases: ["caliper derecho", "mordaza derecho"],
    category: "Frenos",
    system: "Frenos",
    subsystem: "Mordazas",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "58130-25000",
      equivalent_numbers: ["AM-58130-25000", "AFTER-58130-25000"],
      dimensions: "Pistón simple: 54mm",
      material: "Hierro fundido y pistón de acero",
      weight_kg: 2.8,
      torque_nm: "65-75 N·m (perno caliper a mangueta)"
    },
    symptoms: ["Frenado desigual", "el carro se carga de un lado al frenar"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "brake_pads_front",
    name: "Pastillas de Freno Delanteras (Kit)",
    aliases: ["fricciones delanteras", "pastillas de freno"],
    category: "Frenos",
    system: "Frenos",
    subsystem: "Pastillas",
    assembly: "Mordaza",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "CENTER",
    specification: {
      oem_number: "58101-25A10",
      equivalent_numbers: ["AM-58101-25A10", "AFTER-58101-25A10"],
      dimensions: "Espesor: 10mm (mín: 2mm)",
      material: "Semimetálico / Cerámico",
      weight_kg: 0.9,
      torque_nm: "N/A"
    },
    symptoms: ["Chirrido agudo al frenar", "pedal de freno bajo", "bajo nivel de fluido"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_left_abs_sensor",
    name: "Sensor ABS Delantero Izquierdo",
    aliases: ["sensor velocidad izquierdo", "speed sensor left"],
    category: "Sensores",
    system: "Eléctrico",
    subsystem: "Sensores",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "LEFT",
    specification: {
      oem_number: "95670-25000",
      equivalent_numbers: ["AM-95670-25000", "AFTER-95670-25000"],
      dimensions: "Resistencia: 1.1 - 1.3 kΩ",
      material: "Inductivo magnético",
      weight_kg: 0.15,
      torque_nm: "10-14 N·m"
    },
    symptoms: ["Luz ABS encendida", "lectura de velocidad errática"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
  {
    id: "front_right_abs_sensor",
    name: "Sensor ABS Delantero Derecho",
    aliases: ["sensor velocidad derecho", "speed sensor right"],
    category: "Sensores",
    system: "Eléctrico",
    subsystem: "Sensores",
    assembly: "Mangueta",
    description: "Componente de suspensión y tren motriz para Hyundai Accent 2005.",
    position: "RIGHT",
    specification: {
      oem_number: "95670-25000",
      equivalent_numbers: ["AM-95670-25000", "AFTER-95670-25000"],
      dimensions: "Resistencia: 1.1 - 1.3 kΩ",
      material: "Inductivo magnético",
      weight_kg: 0.15,
      torque_nm: "10-14 N·m"
    },
    symptoms: ["Luz ABS encendida", "lectura de velocidad errática"],
    related_dtcs: [],
    confidence_level: "CONFIRMED"
  },
];


/** @deprecated Unverified legacy seed. Production consumers must use universalPartsCatalog.ts. */
export const LEGACY_UNVERIFIED_GUIDED_REPAIR_PROCEDURES: GuidedRepairProcedure[] = [
  {
    id: "replace-front-left-lower-control-arm",
    title: "Reemplazo de Brazo de Control Inferior Delantero Izquierdo (Tijereta)",
    vehicle_applicability: "Hyundai Accent/Verna 2005 1.6 AT",
    estimated_duration_min: 90,
    difficulty: "MEDIUM",
    safety_level: "CAUTION",
    prerequisites: [
      "Estacionar en superficie nivelada y activar freno de estacionamiento.",
      "Asegurar cuñas en las ruedas traseras.",
      "Tener a mano torquímetro y extractor de rótulas."
    ],
    steps: [
      {
        id: "rca_step1",
        order: 1,
        title: "Retirar Rueda Delantera Izquierda",
        description: "Aflojar tuercas en patrón cruzado con el carro en el suelo. Elevar con gato, colocar torre de soporte de seguridad (borriqueta), retirar tuercas y rueda.",
        type: "DISASSEMBLE",
        target_node_id: "wheel_nuts_front_left",
        animation_action: "TRANSLATE_X",
        required_tools: ["Gato hidráulico", "Torre de soporte", "Llave de ruedas o Dado 19mm"],
        torque_spec: "90-110 N·m (para reensamblaje)",
        warning_notes: "Nunca trabaje bajo el vehículo soportado únicamente por el gato.",
        expected_measurement: "Espesor de tuercas e hilos de rosca sanos"
      },
      {
        id: "rca_step2",
        order: 2,
        title: "Desconectar Bieleta Estabilizadora",
        description: "Sujetar el espárrago de la bieleta con una llave fija mientras afloja la tuerca de seguridad para evitar que gire la rótula de la bieleta.",
        type: "DISASSEMBLE",
        target_node_id: "left_stabilizer_link",
        animation_action: "ROTATE_X",
        required_tools: ["Llave fija 14mm", "Dado 14mm"],
        torque_spec: "35-45 N·m (para reensamblaje)",
        warning_notes: "Sostenga el espárrago adecuadamente para evitar dañar el guardapolvo de la bieleta."
      },
      {
        id: "rca_step3",
        order: 3,
        title: "Retirar Pasador y Tuerca de Rótula",
        description: "Retirar el pasador de chaveta de seguridad (cotter pin), luego aflojar la tuerca almena (castle nut) de la rótula inferior sin retirarla completamente.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_ball_joint",
        animation_action: "ROTATE_X",
        required_tools: ["Pinzas de punta", "Dado o Llave 17mm"],
        torque_spec: "60-72 N·m (para reensamblaje)",
        warning_notes: "Deseche el pasador de chaveta usado y use uno nuevo en el montaje."
      },
      {
        id: "rca_step4",
        order: 4,
        title: "Separar Rótula de la Mangueta",
        description: "Colocar el extractor de rótulas entre la rótula y la mangueta. Apretar el tornillo del extractor hasta liberar el vástago cónico. Retirar la tuerca almena.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_ball_joint",
        animation_action: "TRANSLATE_Y",
        required_tools: ["Extractor de rótulas (Sway bar / Ball joint separator)", "Martillo cara blanda"],
        warning_notes: "No golpee directamente el cuerpo roscado de la rótula con un martillo de acero."
      },
      {
        id: "rca_step5",
        order: 5,
        title: "Retirar Pernos de Sujeción de Bujes",
        description: "Aflojar y retirar los pernos que fijan el buje delantero y buje trasero del brazo al subchasis delantero.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_lower_control_arm",
        animation_action: "EXPLODE",
        required_tools: ["Dado 17mm", "Barra de fuerza", "Llave combinada 17mm"],
        torque_spec: "95-120 N·m (para reensamblaje)",
        warning_notes: "Los pernos pueden estar muy ajustados debido a la corrosión. Use lubricante penetrante si es necesario."
      },
      {
        id: "rca_step6",
        order: 6,
        title: "Extraer el Brazo Viejo e Instalar el Nuevo",
        description: "Extraer el brazo de control inferior del subchasis. Colocar el nuevo brazo inferior con bujes nuevos en el subchasis. Presentar los pernos sin apretar.",
        type: "ASSEMBLE",
        target_node_id: "front_left_lower_control_arm",
        animation_action: "NONE",
        required_tools: ["Brazo de control nuevo", "Palanca (Pry bar)"],
        warning_notes: "No apriete los pernos de los bujes de caucho todavía. Deben apretarse solo a altura de trabajo."
      },
      {
        id: "rca_step7",
        order: 7,
        title: "Conectar Rótula a la Mangueta",
        description: "Insertar el vástago de la rótula en la mangueta, apretar la tuerca almena al torque nominal de 60-72 N·m e instalar un pasador de chaveta nuevo.",
        type: "TORQUE",
        target_node_id: "front_left_ball_joint",
        animation_action: "ROTATE_X",
        required_tools: ["Torquímetro", "Pasador nuevo"],
        torque_spec: "60-72 N·m",
        warning_notes: "Si la ranura no alinea con el orificio, apriete ligeramente más, nunca afloje para alinear."
      },
      {
        id: "rca_step8",
        order: 8,
        title: "Apretar Pernos de Bujes a Altura Normal",
        description: "Bajar el carro a sus llantas o usar un gato bajo el brazo para cargar la suspensión a su altura de rodaje normal. Apretar los pernos de bujes a 95-120 N·m.",
        type: "TORQUE",
        target_node_id: "front_left_lower_control_arm",
        animation_action: "ROTATE_X",
        required_tools: ["Torquímetro", "Gato auxiliar"],
        torque_spec: "95-120 N·m",
        warning_notes: "Apretar los bujes colgando los torsionará permanentemente a la altura normal, acortando drásticamente su vida útil.",
        expected_measurement: "Verificar asentamiento neutro del caucho"
      },
      {
        id: "rca_step9",
        order: 9,
        title: "Reinstalar Rueda y Alinear",
        description: "Reinstalar la llanta, apretar tuercas a 90-110 N·m. Es OBLIGATORIO realizar una alineación de la dirección (toe) después de intervenir la suspensión.",
        type: "ALIGN",
        target_node_id: "wheel_nuts_front_left",
        animation_action: "NONE",
        required_tools: ["Equipo de alineación digital", "Llaves de barra de acoplamiento"],
        torque_spec: "90-110 N·m",
        warning_notes: "La alineación evita el desgaste prematuro de neumáticos y comportamiento errático.",
        expected_measurement: "Toe delantero izquierdo: +0.10° ± 0.05°"
      }
    ],
    final_verification: [
      "Confirmar ausencia de ruidos metálicos (clonk) en prueba estática y de conducción corta.",
      "Verificar que el volante quede centrado circulando en línea recta.",
      "Inspeccionar visualmente que los fuelles y pasadores de chaveta queden firmemente asegurados."
    ]
  },
  {
    id: "replace-front-left-ball-joint",
    title: "Reemplazo de Rótula Inferior Delantera Izquierda",
    vehicle_applicability: "Hyundai Accent/Verna 2005 1.6 AT",
    estimated_duration_min: 60,
    difficulty: "MEDIUM",
    safety_level: "CAUTION",
    prerequisites: [
      "Elevar el vehículo y retirar la rueda delantera izquierda.",
      "Tener prensa para rótulas o dados para prensado si la rótula viene unida a presión."
    ],
    steps: [
      {
        id: "rbj_step1",
        order: 1,
        title: "Separar Rótula de la Mangueta",
        description: "Retirar pasador y tuerca almena. Extraer el acople cónico del nudillo/mangueta con extractor de rótulas.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_ball_joint",
        animation_action: "TRANSLATE_Y",
        required_tools: ["Extractor de rótula"],
        torque_spec: "60-72 N·m (para reensamblaje)",
        warning_notes: "Sostener la mangueta con alambre para no estirar la manguera de freno."
      },
      {
        id: "rbj_step2",
        order: 2,
        title: "Retirar snap ring de retención",
        description: "Usar pinzas para snap ring y retirar el anillo de retención de la rótula ubicado en la parte superior de su encaje en el brazo.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_ball_joint",
        animation_action: "NONE",
        required_tools: ["Alicates para snap ring de expansión"],
        warning_notes: "Use lentes de seguridad, el snap ring puede salir disparado con fuerza."
      },
      {
        id: "rbj_step3",
        order: 3,
        title: "Prensar la Rótula Vieja hacia afuera",
        description: "Colocar la prensa C de rótulas con los adaptadores correctos y girar el tornillo para empujar la rótula hacia abajo sacándola del brazo.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_ball_joint",
        animation_action: "TRANSLATE_Z",
        required_tools: ["Prensa C para rótulas", "Dados/Vasos extractores"],
        warning_notes: "Asegure un soporte firme en la prensa para no doblar el labio del brazo de control."
      },
      {
        id: "rbj_step4",
        order: 4,
        title: "Prensar la Rótula Nueva e Instalar Snap Ring",
        description: "Limpiar el orificio del brazo. Engrasar ligeramente los bordes. Prensar la rótula nueva recta y hasta el fondo. Colocar un nuevo snap ring.",
        type: "ASSEMBLE",
        target_node_id: "front_left_ball_joint",
        animation_action: "TRANSLATE_Z",
        required_tools: ["Prensa C", "Snap ring nuevo"],
        warning_notes: "Asegure que la rótula ingrese totalmente recta y el snap ring asiente en su canal por completo."
      }
    ],
    final_verification: [
      "Comprobar visualmente que el snap ring rodee todo el contorno de la ranura de retención.",
      "Verificar resistencia de giro manual de la nueva rótula."
    ]
  },
  {
    id: "disassemble-front-left-strut",
    title: "Desarmado y Reemplazo de Amortiguador / Strut McPherson Izquierdo",
    vehicle_applicability: "Hyundai Accent/Verna 2005 1.6 AT",
    estimated_duration_min: 75,
    difficulty: "HARD",
    safety_level: "DANGER",
    prerequisites: [
      "Compresor de resortes profesional en excelentes condiciones.",
      "Apoyo y sostén para la mangueta."
    ],
    steps: [
      {
        id: "str_step1",
        order: 1,
        title: "Retirar Conjunto de Strut del Vehículo",
        description: "Liberar soporte de manguera de freno. Retirar los dos pernos inferiores que unen a mangueta. Retirar las tres tuercas superiores en la torre de suspensión y sacar el strut.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_strut",
        animation_action: "EXPLODE",
        required_tools: ["Dados 12, 14, 17mm", "Llaves combinadas"],
        torque_spec: "Tuercas de torre: 20-30 N·m. Pernos mangueta: 100-120 N·m",
        warning_notes: "Sostenga la mangueta; si cae, estirará y romperá el cable ABS o la línea de frenos."
      },
      {
        id: "str_step2",
        order: 2,
        title: "Comprimir el Resorte Helicoidal",
        description: "Colocar los ganchos del compresor de resortes opuestos simétricamente. Apretar alternadamente hasta liberar la tensión sobre la copela superior.",
        type: "INSPECT",
        target_node_id: "front_left_spring",
        animation_action: "NONE",
        required_tools: ["Compresor de resortes helicoidales"],
        warning_notes: "¡Peligro de muerte! No suelte los compresores. No apunte el resorte comprimido hacia personas."
      },
      {
        id: "str_step3",
        order: 3,
        title: "Desmontar Tuerca Central y Separar Conjunto",
        description: "Sujetar el vástago del amortiguador con llave allen/fija y retirar la tuerca de seguridad central. Retirar copela, rodamiento, resorte, tope y fuelle.",
        type: "DISASSEMBLE",
        target_node_id: "front_left_strut_mount",
        animation_action: "EXPLODE",
        required_tools: ["Llave central de vástago", "Dado o llave profunda 17mm"],
        torque_spec: "Tuerca central: 60-70 N·m",
        warning_notes: "Nunca intente aflojar esta tuerca sin el resorte totalmente comprimido y libre."
      },
      {
        id: "str_step4",
        order: 4,
        title: "Inspeccionar y Armar Strut Nuevo",
        description: "Inspeccionar el rodamiento, la base de goma y el tope. Reemplazar amortiguador. Ensamblar alineando las marcas del resorte en sus canales. Apretar tuerca central a 60-70 N·m.",
        type: "ASSEMBLE",
        target_node_id: "front_left_strut",
        animation_action: "ROTATE_X",
        required_tools: ["Torquímetro", "Grasa de rodamiento superior"],
        torque_spec: "60-70 N·m",
        warning_notes: "Asegure que la espira encaje exactamente en la forma del asiento metálico inferior."
      }
    ],
    final_verification: [
      "Confirmar que el resorte no tenga juego libre dentro de sus platos de soporte.",
      "Verificar suavidad de giro de la copela superior antes de instalar."
    ]
  }
];
