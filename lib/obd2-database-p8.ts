import { DtcEntry } from './obd2-database';

export const OBD2_DATABASE_P8: Record<string, DtcEntry> = {
  // === Específicos: Grupo VAG (Volkswagen, Audi, Seat, Skoda) ===
  'P1296': { title: 'Mal Funcionamiento en Sistema de Enfriamiento (VAG)', desc: 'Falla en el sensor de temperatura del refrigerante o termostato (falla muy común en motores 1.8T).', fix: 'Reemplazar sensor de temperatura del refrigerante (sensor verde) y revisar termostato.', severity: 'medium' },
  'P2181': { title: 'Rendimiento del Sistema de Enfriamiento (VAG)', desc: 'El motor no alcanza la temperatura de operación correcta.', fix: 'Reemplazar termostato y sensor G62. Verificar nivel de anticongelante G12/G13.', severity: 'medium' },
  'P3081': { title: 'Temperatura del Motor Muy Baja (VAG)', desc: 'El sensor de temperatura indica que el motor no calienta.', fix: 'Revisar sensor de temperatura G62 y termostato abierto.', severity: 'medium' },
  'P1545': { title: 'Mal Funcionamiento Cuerpo de Aceleración (VAG)', desc: 'Error de control de la mariposa electrónica.', fix: 'Limpiar cuerpo de aceleración y realizar alineación básica (Throttle Body Alignment) con escáner.', severity: 'high' },
  'P1602': { title: 'Corte de Tensión Borne 30 (VAG)', desc: 'Interrupción de suministro de energía por voltaje bajo (batería desconectada).', fix: 'Borrar código. Si regresa, revisar estado de la batería, bornes y alternador.', severity: 'low' },
  
  // === Específicos: Hyundai / Kia ===
  'P0011': { title: 'Posición Árbol de Levas A - Sobre Avanzado (Hyundai/Kia)', desc: 'Falla típica en motores Theta II/Nu por solenoides CVVT obstruidos.', fix: 'Revisar y limpiar solenoide (OCV) del CVVT. Cambiar aceite de motor.', severity: 'high' },
  'P1326': { title: 'Sensor de Detonación (Sistema KSDS - Hyundai/Kia)', desc: 'El Sistema de Detección del Sensor Knock (KSDS) detectó vibraciones anormales (falla grave de motor).', fix: '¡DETENER MOTOR INMEDIATAMENTE! Posible desgaste severo de cojinetes de biela. Revisión bajo garantía.', severity: 'high' },
  'P0420': { title: 'Eficiencia Catalítica Baja (Hyundai/Kia)', desc: 'El catalizador no filtra adecuadamente. Común en motores 1.6L GDI.', fix: 'Revisar actualizaciones de software del PCM, verificar bobinas antes de cambiar catalizador.', severity: 'medium' },
  
  // === Específicos: Mazda (Skyactiv) ===
  'P011A': { title: 'Sensor de Temperatura ECT 1 / ECT 2 Correlación (Mazda)', desc: 'Discrepancia entre los dos sensores de refrigerante (común en Skyactiv).', fix: 'Reemplazar válvula de control de flujo de refrigerante (termovalve).', severity: 'medium' },
  'P0191': { title: 'Sensor de Presión de Riel de Combustible (Mazda)', desc: 'Presión irregular en bomba de alta (HPFP) en motores Skyactiv GDI.', fix: 'Revisar bomba de alta presión de combustible, tapón en filtro.', severity: 'high' },
  'P061B': { title: 'Cálculo de Torque del Módulo de Control Interno (Mazda)', desc: 'Error interno en el cálculo de torque.', fix: 'Limpiar sensor MAF, cuerpo de aceleración y revisar fugas de vacío.', severity: 'medium' },

  // === Sistemas de Control Híbrido (Prius / Toyota) ===
  'P3000': { title: 'Sistema de Control de Batería HV', desc: 'Falla genérica detectada por el módulo de la batería híbrida.', fix: 'Escanear módulo HV para leer submódulo y códigos detallados de voltaje.', severity: 'high' },
  'P3009': { title: 'Fuga de Alto Voltaje (Toyota Híbrido)', desc: 'Pérdida de aislamiento en el sistema de alta tensión.', fix: '¡PELIGRO! Revisar integridad de cables naranjas y bobinados de MG1/MG2.', severity: 'high' },
  'P0A0F': { title: 'El Motor No Arranca (Híbrido)', desc: 'El motor de combustión no logró arrancar después del giro de MG1.', fix: 'Revisar falta de combustible, sistema de encendido o cuerpo de aceleración sucio.', severity: 'high' },

  // === Más P1xxx Específicos ===
  'P1009': { title: 'Avance de Sincronización VTC Avanzado (Honda)', desc: 'Sistema VTC atascado mecánicamente o actuador fallando.', fix: 'Revisar nivel de aceite, limpiar malla/filtro del actuador VTC.', severity: 'high' },
  'P1135': { title: 'Circuito del Calentador A/F (B1S1) (Toyota)', desc: 'Falla en el circuito del calentador del sensor Air/Fuel.', fix: 'Revisar relé A/F, fusibles y reemplazar sensor A/F superior.', severity: 'medium' },
  'P1258': { title: 'Protección de Sobrecalentamiento Activa (GM)', desc: 'El PCM alternará el disparo de inyectores para enfriar el motor.', fix: 'El motor está críticamente caliente. Apagar y revisar bomba de agua, radiador.', severity: 'high' },
  'P1443': { title: 'Falla en la Válvula de Control de Purga EVAP (Ford)', desc: 'La válvula termistor del EVAP no muestra voltaje correcto.', fix: 'Reemplazar válvula de control de purga y sensor de flujo EVAP.', severity: 'low' },
  'P1633': { title: 'Voltaje KAM Muy Bajo (Ford)', desc: 'El voltaje de memoria Keep Alive (KAM) al PCM es muy bajo.', fix: 'Revisar fusibles de KAM, batería y cableado hacia la computadora.', severity: 'low' }
};
