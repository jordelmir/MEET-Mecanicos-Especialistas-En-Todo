import { DtcEntry } from './obd2-database';

export const OBD2_DATABASE_P9: Record<string, DtcEntry> = {
  // === Códigos U (Redes) Avanzados ===
  'U1001': { title: 'Línea de Comunicación CAN (Nissan)', desc: 'Error temporal o permanente de comunicación en la red CAN.', fix: 'Revisar conectores, daños físicos en cableado de red CAN y empalmes.', severity: 'high' },
  'U0122': { title: 'Pérdida de Comunicación con el Módulo de Control de Dinámica (VDC/ESP)', desc: 'Sin datos del módulo de control de tracción.', fix: 'Revisar módulo VDC, fusibles, red CAN.', severity: 'high' },
  'U1262': { title: 'Fallo de Red de Comunicación SCP (Ford)', desc: 'Error de la red estándar de Ford (SCP).', fix: 'Verificar módulo de instrumentos y empalmes SCP.', severity: 'medium' },
  'U1102': { title: 'Tiempo de Espera de Datos ESP (Mitsubishi)', desc: 'El módulo no recibe mensajes del control electrónico de estabilidad.', fix: 'Verificar conexión y alimentación del módulo ESP/ABS.', severity: 'medium' },
  'U0300': { title: 'Incompatibilidad de Software del Módulo de Control Interno', desc: 'Versión de software de módulo no coincide con el resto de la red.', fix: 'Actualizar firmware de los módulos a la versión compatible. Requiere escáner OEM.', severity: 'high' },

  // === Códigos B (Body / Carrocería) Avanzados ===
  'B2099': { title: 'Alimentación Abierta Llave de Encendido', desc: 'Circuito de retención del cilindro de llave con corto o abierto.', fix: 'Revisar switch de ignición y módulo de la columna de dirección.', severity: 'low' },
  'B2290': { title: 'Módulo de Sistema de Ocupantes (OCS)', desc: 'Sistema de clasificación de ocupante (asiento del pasajero) con falla.', fix: 'Calibrar sensor de peso del asiento del pasajero. No poner objetos bajo el asiento.', severity: 'high' },
  'B1318': { title: 'Voltaje de Batería Bajo (Ford)', desc: 'El módulo (ABS, SRS o BCM) registra un voltaje por debajo de 9.6V.', fix: 'Probar batería, conexiones y regulador de voltaje.', severity: 'medium' },
  'B1147': { title: 'Falla en el Sistema Antirrobo', desc: 'El inmovilizador detectó un intento de robo o llave no reconocida.', fix: 'Verificar transponder, anillo receptor del inmovilizador.', severity: 'high' },
  'B1422': { title: 'Sensor de Velocidad del Vehículo (HVAC Toyota)', desc: 'El panel del aire acondicionado no recibe velocidad para ajuste automático.', fix: 'Verificar bus CAN desde el tablero de instrumentos al módulo A/C.', severity: 'low' },
  'B2799': { title: 'Inmovilizador del Motor (Toyota)', desc: 'Problema de comunicación entre el inmovilizador y la ECU del motor.', fix: 'Hacer puente entre pines 4 y 13 del OBD2 por 30 mins para resincronizar llaves (procedimiento manual Toyota).', severity: 'high' },

  // === Códigos C (Chasis) Avanzados ===
  'C1145': { title: 'Falla del Sensor de Velocidad Rueda Delantera Derecha (Ford)', desc: 'Pérdida de señal del sensor ABS delantero derecho.', fix: 'Revisar sensor, anillo reluctor o balero de rueda magnetizado.', severity: 'medium' },
  'C1201': { title: 'Sistema de Control del Motor (Toyota ABS)', desc: 'El sistema ABS/VSC se desactiva porque hay una falla en el motor (Check Engine encendido).', fix: 'Este es un código reflejo. Escanear el motor y reparar el código P principal.', severity: 'medium' },
  'C1231': { title: 'Falla en el Sensor de Ángulo de Dirección', desc: 'Pérdida del valor central del volante.', fix: 'Calibrar posición cero del sensor de ángulo de dirección (SAS).', severity: 'medium' },
  'C2205': { title: 'Error Interno del Sensor de Ángulo de Dirección', desc: 'El sensor SAS ha fallado de forma interna.', fix: 'Reemplazar reloj espiral / sensor de ángulo de dirección.', severity: 'medium' },
  'C1500': { title: 'Torque Sensor EPS (Dirección Electrónica)', desc: 'Falla en el sensor de torque de la dirección electroasistida.', fix: 'Revisar columna de dirección o módulo EPS. No manipular sin desconectar batería.', severity: 'high' },
  'C0273': { title: 'Falla en el Relé del Motor ABS (GM)', desc: 'El relé principal del motor ABS no opera.', fix: 'Revisar fusibles de alta corriente del ABS y módulo EBCM.', severity: 'high' },

  // === Diagnóstico Avanzado de P-Codes (Misfires Ocultos y Sensores) ===
  'P0300': { title: 'Fallo de Encendido Aleatorio/Múltiple (Misfire)', desc: 'Fallas de combustión irregulares no fijas a un solo cilindro.', fix: 'Revisar fugas de vacío (intake), presión baja de combustible, válvula EGR, o gasolina contaminada.', severity: 'high' },
  'P0313': { title: 'Fallo de Encendido Detectado por Falta de Combustible', desc: 'Misfire registrado debido a que el tanque está vacío o la presión es extremadamente baja.', fix: 'Llenar tanque, purgar sistema de combustible. Revisar presión de bomba.', severity: 'high' },
  'P0314': { title: 'Fallo de Encendido de un Solo Cilindro (Cilindro No Especificado)', desc: 'La ECU detecta misfire pero no puede aislar qué cilindro es.', fix: 'Realizar prueba de balance de cilindros con escáner avanzado.', severity: 'high' },
  'P00B7': { title: 'Flujo Bajo de Refrigerante de Motor', desc: 'Temperatura excesiva o circulación pobre detectada.', fix: 'Revisar nivel de refrigerante, bomba de agua y válvula termostática.', severity: 'high' }
};
