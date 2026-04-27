import { DtcEntry } from './obd2-database';

export const OBD2_DATABASE_P7: Record<string, DtcEntry> = {
  // === Híbridos y Vehículos Eléctricos (HEV/EV) P0Axx ===
  'P0A00': { title: 'Circuito del Sensor de Temperatura del Refrigerante del Motor Eléctrico', desc: 'Falla en el circuito del sensor de temperatura del inversor/motor eléctrico.', fix: 'Revisar sensor de temperatura del inversor, bomba de agua eléctrica y nivel de refrigerante HEV.', severity: 'medium' },
  'P0A08': { title: 'Fallo del Circuito del Convertidor DC/DC', desc: 'Problema en el convertidor de alta tensión a 12V.', fix: 'Verificar alimentación de alta tensión, fusibles principales y convertidor DC/DC.', severity: 'high' },
  'P0A09': { title: 'Circuito del Convertidor DC/DC Bajo', desc: 'Señal baja en el circuito de estado del convertidor DC/DC.', fix: 'Revisar conexiones del convertidor DC/DC, estado de la batería de 12V.', severity: 'high' },
  'P0A78': { title: 'Rendimiento del Inversor del Motor de Tracción', desc: 'Mal funcionamiento interno en el inversor del motor eléctrico.', fix: 'Verificar cableado de alta tensión (NARANJA - PELIGRO), módulo inversor. Requiere técnico especializado en HV.', severity: 'high' },
  'P0A79': { title: 'Rendimiento del Inversor del Generador', desc: 'Falla en el inversor del motor-generador (MG1).', fix: 'Verificar módulo inversor/conversor. Aislar sistema HV antes de intervenir.', severity: 'high' },
  'P0A80': { title: 'Reemplazar Paquete de Baterías Híbridas', desc: 'Degradación crítica detectada en los módulos de la batería de alto voltaje.', fix: 'Balancear o reemplazar módulos dañados de la batería HV, o reemplazar paquete completo.', severity: 'high' },
  'P0A81': { title: 'Circuito del Ventilador de la Batería Híbrida', desc: 'Falla en el sistema de enfriamiento de la batería de alto voltaje.', fix: 'Limpiar conductos y ventilador de la batería, revisar motor del ventilador.', severity: 'medium' },
  'P0A93': { title: 'Rendimiento del Sistema de Enfriamiento del Inversor', desc: 'Sobrecalentamiento del sistema inversor/conversor.', fix: 'Verificar bomba de agua del inversor, nivel de refrigerante específico para HEV.', severity: 'high' },
  'P0A94': { title: 'Rendimiento del Convertidor DC/DC', desc: 'Falla general en la conversión de voltaje HV a 12V.', fix: 'Reemplazar conjunto inversor/convertidor. Revisar cableado HV.', severity: 'high' },
  'P0AA6': { title: 'Aislamiento de Alto Voltaje Defectuoso', desc: 'Fuga de corriente de alto voltaje hacia el chasis detectada.', fix: '¡PELIGRO! Riesgo de electrocución. Buscar daño en cables naranjas, motor MG1/MG2 o inversor.', severity: 'high' },
  'P0AC4': { title: 'Módulo de Control Híbrido Solicitó Encender MIL', desc: 'El módulo HEV/EV pide a la ECU del motor encender la luz de falla.', fix: 'Escanear módulo HEV para obtener códigos específicos de la falla híbrida.', severity: 'medium' },

  // === Más Específicos: Nissan / Infiniti ===
  'P0032': { title: 'Calentador Sensor O2 - Alto (B1S1) (Nissan)', desc: 'Cortocircuito a voltaje en el calentador del sensor O2.', fix: 'Verificar mazo de cables del sensor O2, posible cruce con cables de alimentación.', severity: 'medium' },
  'P1111': { title: 'Circuito del Solenoide VVT de Admisión (Nissan)', desc: 'Falla en el sistema de control de válvulas IVT de Nissan.', fix: 'Limpiar solenoide IVT, revisar nivel y presión de aceite.', severity: 'medium' },
  'P1148': { title: 'Control de Lazo Cerrado del Sensor O2 (Nissan)', desc: 'Falla en el control de mezcla cerrada del banco 1.', fix: 'Verificar sensor de relación aire-combustible (A/F) y cableado.', severity: 'medium' },
  'P1564': { title: 'Switch de Control de Crucero en Volante (Nissan)', desc: 'Falla en el circuito del switch (ASCD) en el volante.', fix: 'Revisar conector espiral (clockspring) y switches del volante.', severity: 'low' },
  'P1705': { title: 'Sensor de Posición del Acelerador para TCM (Nissan)', desc: 'La transmisión no recibe señal correcta del TPS.', fix: 'Verificar sensor TPS, cableado entre ECM y TCM.', severity: 'high' },

  // === Más Específicos: BMW / Mini ===
  'P1050': { title: 'Control VVT - Exceso de Corriente (BMW Valvetronic)', desc: 'El motor Valvetronic consume demasiada corriente.', fix: 'Revisar motor Valvetronic, eje excéntrico y sensor de posición.', severity: 'high' },
  'P105D': { title: 'Ajuste Valvetronic - Límite Alcanzado (BMW)', desc: 'El sistema Valvetronic no puede alcanzar la posición comandada.', fix: 'Realizar calibración Valvetronic. Revisar desgaste en eje excéntrico.', severity: 'high' },
  'P112F': { title: 'Presión del Múltiple (MAP) a Ángulo del Acelerador - Rango (BMW)', desc: 'Inconsistencia entre vacío del múltiple y apertura del acelerador.', fix: 'Revisar fugas de vacío, sensor MAP y válvula de ventilación del cárter (PCV).', severity: 'medium' },
  'P14A3': { title: 'Válvula de Contrapresión de Escape (BMW)', desc: 'Falla en el actuador de la solapa de escape.', fix: 'Revisar actuador de la válvula en el escape, mangueras de vacío o motor.', severity: 'low' },
  'P2096': { title: 'Límite Pobre de Regulación de Mezcla (B1) (BMW)', desc: 'Mezcla demasiado pobre en banco 1 después del catalizador.', fix: 'Buscar fugas de admisión, fallas de inyectores y verificar bomba de combustible.', severity: 'medium' },

  // === Más Específicos: Dodge / Chrysler / Jeep ===
  'P0456': { title: 'Fuga Muy Pequeña Sistema EVAP (0.020") (FCA)', desc: 'Micro fuga detectada en sistema de control de emisiones.', fix: 'Cambiar tapa de tanque, revisar sello de bomba NVLD/ESIM.', severity: 'low' },
  'P0522': { title: 'Sensor de Presión de Aceite - Bajo (FCA)', desc: 'Señal de voltaje bajo en sensor de presión de aceite.', fix: 'Reemplazar sensor de presión de aceite, común en motores Pentastar.', severity: 'medium' },
  'P1521': { title: 'Viscosidad de Aceite Incorrecta (FCA MDS)', desc: 'El sistema MDS detectó aceite no compatible.', fix: 'Cambiar aceite, usar estrictamente la viscosidad recomendada (ej: 5W-20).', severity: 'medium' },
  'P1693': { title: 'Código Compañero TCM a ECM (FCA)', desc: 'El TCM solicita a la ECU encender el MIL por falla de caja.', fix: 'Escanear el módulo de transmisión para obtener el código raíz.', severity: 'medium' },
  'P2509': { title: 'Pérdida Intermitente de Alimentación ECM (Cummins/FCA)', desc: 'Pérdida de energía momentánea al módulo del motor.', fix: 'Verificar bornes de batería (corrosión), cables de masa y conectores del ECM.', severity: 'high' },

  // === Fallos Generales y Seguridad ===
  'P1260': { title: 'Robo Detectado - Motor Inmovilizado', desc: 'El sistema antirrobo inmovilizador bloqueó el arranque del motor.', fix: 'Verificar llave transponder, antena del inmovilizador y batería del mando.', severity: 'high' },
  'P1539': { title: 'Clutch del A/C - Exceso de Corriente / Falla (VAG)', desc: 'Falla en el circuito del embrague magnético del compresor A/C.', fix: 'Revisar bobina del compresor, fusible y relé del A/C.', severity: 'low' },
  'P2138': { title: 'Correlación de Voltaje Sensor A/B del Pedal', desc: 'Discrepancia entre pistas de redundancia del pedal de acelerador.', fix: 'Reemplazar conjunto del pedal del acelerador, verificar conector.', severity: 'high' },
  'U1000': { title: 'Falla de Red Clase 2 (GM) / CAN Comunicación (Nissan)', desc: 'Falla genérica de comunicación en la red principal.', fix: 'Verificar empalmes de tierra, humedad en módulos y terminales del conector OBD2.', severity: 'high' }
};
