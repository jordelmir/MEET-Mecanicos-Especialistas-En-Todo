import { DtcEntry } from './obd2-database';

export const OBD2_DATABASE_P10: Record<string, DtcEntry> = {
  // === Exóticos y Europeos Avanzados (Mercedes-Benz, Porsche, Land Rover) ===
  'P2006': { title: 'Válvula de Control de Múltiple de Admisión (IMRC) Atrapada Cerrada (MB/VW)', desc: 'Las aletas del múltiple de admisión (tumble flaps) se quedaron pegadas en posición cerrada.', fix: 'Revisar varillaje de admisión por rotura, actuador de vacío o motor electrónico. Limpiar carbonilla.', severity: 'medium' },
  'P2007': { title: 'Válvula de Control de Múltiple de Admisión (IMRC) Atrapada Abierta', desc: 'Las aletas del múltiple de admisión se quedaron pegadas en posición abierta.', fix: 'Verificar mecanismo del IMRC, mangueras de vacío o solenoide de control.', severity: 'medium' },
  'P1372': { title: 'Desviación de Carrera de Elevación de Válvula (Porsche Variocam)', desc: 'Falla en el sistema de levantamiento variable de válvulas.', fix: 'Revisar actuador VarioCam, presión de aceite y estado del árbol de levas.', severity: 'high' },
  'P2279': { title: 'Fuga de Aire en el Sistema de Admisión (MB/VW)', desc: 'Fuga de vacío (aire no medido por el MAF) post cuerpo de aceleración.', fix: 'Revisar válvula PCV (separador de aceite), mangueras de vacío y junta de múltiple de admisión.', severity: 'medium' },
  'P053F': { title: 'Presión de Combustible de Arranque en Frío (Land Rover)', desc: 'La bomba de alta presión (HPFP) no genera presión suficiente durante el arranque inicial.', fix: 'Verificar bomba de alta presión, sensor de presión de riel y desgaste del seguidor (cam follower).', severity: 'high' },
  'P1B00': { title: 'Sistema de Gestión de Energía Híbrida / BMS (Europeos)', desc: 'El sistema de gestión de baterías detectó una anomalía en carga/descarga.', fix: 'Verificar cables HV, temperatura de batería y actualizar software BMS.', severity: 'high' },
  'C1A00': { title: 'Módulo de Control de Suspensión Neumática (Land Rover/Porsche)', desc: 'Falla en el compresor o módulo de la suspensión de aire.', fix: 'Revisar relé del compresor, fugas en balonas de aire y bloque de válvulas.', severity: 'high' },
  
  // === Códigos Específicos de Sistema Start-Stop (Eco) ===
  'P0A8F': { title: 'Rendimiento del Sistema de Batería de 14V (Start-Stop)', desc: 'La batería auxiliar o principal no puede mantener el voltaje para Auto Start-Stop.', fix: 'Probar batería AGM, reemplazar si el SOH (State of Health) es bajo. Resetear BMS.', severity: 'medium' },
  'P0512': { title: 'Circuito de Petición de Motor de Arranque', desc: 'Falla en el circuito de activación del relé de arranque desde el sistema Start-Stop o botón de encendido.', fix: 'Revisar relé de arranque, módulo inmovilizador y botón push-to-start.', severity: 'medium' },

  // === Más Códigos Diésel Heavy Duty e Inyección ===
  'P0087': { title: 'Presión del Riel/Sistema de Combustible - Demasiado Baja', desc: 'Presión insuficiente en la rampa de inyección común (Common Rail / GDI).', fix: 'Verificar filtro de diésel tapado, bomba elevadora en tanque, y fuga en inyectores.', severity: 'high' },
  'P0088': { title: 'Presión del Riel/Sistema de Combustible - Demasiado Alta', desc: 'Regulador de presión atascado cerrado.', fix: 'Revisar sensor de presión de riel, reemplazar válvula reguladora de presión de combustible.', severity: 'high' },
  'P1193': { title: 'Circuito del Sensor de Temperatura de Admisión Alto (Chrysler/FCA)', desc: 'Sensor IAT en corto.', fix: 'Verificar cableado del sensor IAT, cambiar sensor si está internamente cortocircuitado.', severity: 'low' },
  
  // === Monitoreo Avanzado de Catalizadores (Bancos Duales V6/V8) ===
  'P0420': { title: 'Eficiencia del Sistema de Catalizador por Debajo del Umbral (Banco 1)', desc: 'El convertidor catalítico lado cilindro 1 no está purificando gases correctamente.', fix: 'Verificar que no haya fallas de encendido. Si el motor está perfecto, reemplazar catalizador.', severity: 'medium' },
  'P0430': { title: 'Eficiencia del Sistema de Catalizador por Debajo del Umbral (Banco 2)', desc: 'El convertidor catalítico del segundo banco no está purificando gases.', fix: 'Confirmar gráfica del sensor O2 post-catalizador. Reemplazar catalizador B2.', severity: 'medium' },
  
  // === Sensores de Oxígeno Banda Ancha (A/F Ratio Sensors) ===
  'P2195': { title: 'Señal de Sensor O2 Atrapada en Pobre (B1S1)', desc: 'El sensor A/F mide mezcla pobre constantemente a pesar de corrección del ECM.', fix: 'Buscar fuga de vacío masiva, presión baja de combustible o reemplazar sensor A/F B1S1.', severity: 'medium' },
  'P2196': { title: 'Señal de Sensor O2 Atrapada en Rica (B1S1)', desc: 'El sensor A/F mide mezcla rica constantemente.', fix: 'Verificar inyector goteando, regulador de presión roto o MAF sucio.', severity: 'medium' },
  'P2197': { title: 'Señal de Sensor O2 Atrapada en Pobre (B2S1)', desc: 'El sensor A/F del banco 2 mide mezcla pobre constantemente.', fix: 'Buscar fuga de vacío que afecte solo al banco 2 (múltiple fisurado), reemplazar sensor.', severity: 'medium' },
  'P2198': { title: 'Señal de Sensor O2 Atrapada en Rica (B2S1)', desc: 'El sensor A/F del banco 2 mide mezcla rica constantemente.', fix: 'Verificar inyectores B2 o cambiar sensor A/F B2S1.', severity: 'medium' }
};
