#!/usr/bin/env python3
"""Elite DTC repair templates — cross-referenced from top 10 global sources."""

# Format: prefix -> (system, urgency, can_drive, cost_min, cost_max, symptoms, causes_ranked, steps)
# Causes: (description, probability)  |  Steps: ordered by cost (cheapest first)

ELITE = {
"P00": {
  "sys": "Fuel/Air Metering", "urg": "pronto", "drive": True,
  "cost": (80, 400), "time_h": 1.5,
  "sym": ["Check Engine encendido","Ralentí inestable","Consumo elevado de combustible","Falta de potencia en aceleración"],
  "causes": [("Fuga de vacío en mangueras o múltiple de admisión","alta"),("Sensor MAF sucio o defectuoso","alta"),("Filtro de aire obstruido","media"),("Sensor O2 degradado o lento","media"),("Cableado/conector del sensor dañado o corroído","baja")],
  "steps": [
    "1. INSPECCIÓN VISUAL ($0): Revise mangueras de vacío, ducto de admisión y conectores por grietas, desconexiones o corrosión.",
    "2. LIMPIEZA MAF ($8-15): Limpie el sensor MAF con limpiador especializado. NO toque el filamento con las manos.",
    "3. FUEL TRIMS EN VIVO ($0): Con escáner, compare STFT y LTFT en ralentí vs 2500 RPM. >±10% confirma problema.",
    "4. PRUEBA DE HUMO ($50-80): Use máquina de humo en múltiple de admisión para localizar fugas de vacío invisibles.",
    "5. MEDICIÓN ELÉCTRICA ($0): Mida voltaje de referencia (5V), señal y masa en conector del sensor afectado.",
    "6. VERIFICACIÓN FINAL: Borre código, conduzca 15 min y verifique que el monitor pase correctamente."
  ]
},
"P01": {
  "sys": "Fuel/Air Metering", "urg": "pronto", "drive": True,
  "cost": (50, 500), "time_h": 1.5,
  "sym": ["Check Engine encendido","Mezcla rica o pobre persistente","Olor a gasolina en escape","Consumo excesivo","Tirones o hesitación"],
  "causes": [("Fuga de vacío severa (múltiple admisión, PCV, servofreno)","alta"),("Sensor MAF sub/sobre-reportando","alta"),("Presión de combustible baja (bomba débil o filtro tapado)","media"),("Inyectores obstruidos o goteando","media"),("Sensor O2 pre-cat envejecido","media"),("Fuga en colector de escape engañando al O2","baja")],
  "steps": [
    "1. ANÁLISIS FUEL TRIMS ($0): STFT+LTFT >+15% = Lean, <-15% = Rich. Compare B1 vs B2 para aislar.",
    "2. PRUEBA RPM ($0): Acelere a 2500 RPM. Si Trim mejora = fuga de vacío. Si empeora = combustible/MAF.",
    "3. PRUEBA DE HUMO ($50-80): Localice fugas de vacío con máquina de humo en la admisión.",
    "4. PRESIÓN COMBUSTIBLE ($0-30): Conecte manómetro al riel. Debe ser 250-450 kPa. Pruebe retención.",
    "5. LIMPIEZA/REEMPLAZO MAF ($8-250): Limpie primero. Si no mejora, reemplace.",
    "6. PRUEBA INYECTORES ($0): Balance de inyectores con escáner bidireccional. Mida resistencia (12-16Ω)."
  ]
},
"P02": {
  "sys": "Fuel/Air Injection", "urg": "pronto", "drive": True,
  "cost": (100, 600), "time_h": 2,
  "sym": ["Fallo de cilindro específico","Ralentí irregular","Olor a combustible crudo","Falta de potencia"],
  "causes": [("Inyector obstruido o con fuga","alta"),("Cableado del inyector en corto o abierto","alta"),("Circuito driver del PCM dañado","baja"),("Riel de combustible con restricción","baja")],
  "steps": [
    "1. RESISTENCIA INYECTOR ($0): Mida con multímetro. Típico 12-16Ω. Fuera de rango = reemplace.",
    "2. SEÑAL PCM ($0-20): Verifique pulso de inyección con luz Noid en cada conector.",
    "3. INTERCAMBIO ($0): Intercambie inyector sospechoso con otro cilindro. Si fallo se mueve, confirma inyector.",
    "4. CABLEADO ($0): Inspeccione arnés por cortocircuito a masa, a positivo o circuito abierto.",
    "5. PRUEBA BALANCE ($0): Active cada inyector con escáner bidireccional y observe caída de RPM."
  ]
},
"P03": {
  "sys": "Ignition/Misfire", "urg": "inmediata", "drive": False,
  "cost": (50, 800), "time_h": 2,
  "sym": ["Motor tiembla o vibra","Check Engine PARPADEANDO","Pérdida severa de potencia","Olor a gasolina en escape","Aceleración con tirones"],
  "causes": [("Bujía desgastada o con gap incorrecto","alta"),("Bobina de encendido con fuga de aislamiento","alta"),("Cable de bujía con alta resistencia (si aplica)","media"),("Inyector obstruido o falla eléctrica","media"),("Baja compresión (anillos, válvulas, empaque cabeza)","baja")],
  "steps": [
    "1. ⚠️ SI CHECK ENGINE PARPADEA: Detenga el vehículo. Misfire severo DESTRUYE el catalizador ($500-2500).",
    "2. INTERCAMBIO BOBINA ($0): Mueva bobina del cilindro afectado a otro. Si fallo se mueve = bobina mala.",
    "3. INSPECCIÓN BUJÍAS ($0): Retire y examine. Negro húmedo=aceite, Negro seco=rico, Blanco=pobre.",
    "4. RESISTENCIA CABLES ($0): Debe ser <15kΩ/pie. Mayor = reemplace cables.",
    "5. COMPRESIÓN ($0-30): Prueba cilindro por cilindro. Variación máx 10-15%. Bajo = falla mecánica.",
    "6. MODE 06 ($0): Revise contadores de misfire para identificar cilindro exacto y severidad."
  ]
},
"P04": {
  "sys": "Emissions (EGR/EVAP/CAT)", "urg": "rutinaria", "drive": True,
  "cost": (20, 1500), "time_h": 2,
  "sym": ["Check Engine encendido","Falla en prueba de emisiones","Olor a huevo podrido (catalizador)","Ralentí irregular con EGR"],
  "causes": [("Tapa de gasolina suelta o dañada","alta"),("Válvula de purga EVAP atascada","alta"),("Catalizador agotado o dañado internamente","media"),("Válvula EGR obstruida con carbón","media"),("Fuga en mangueras del canister EVAP","media"),("Sensor O2 post-cat reportando falso","baja")],
  "steps": [
    "1. TAPA DE GASOLINA ($0-20): Verifique sello y cierre. Reemplace si está agrietada.",
    "2. HUMO EVAP ($50-80): Conecte máquina de humo al puerto EVAP para buscar fugas en mangueras.",
    "3. VÁLVULA PURGA ($0): Pruebe con escáner bidireccional. Debe abrir/cerrar con clic audible.",
    "4. EGR ($0): Limpie válvula y conductos de carbón. Pruebe actuador con escáner bidireccional.",
    "5. CATALIZADOR ($0): Grafique O2 pre-cat vs post-cat. Si post-cat COPIA la onda = catalizador agotado.",
    "6. CONTRAPRESIÓN ($0-30): Mida presión de escape antes del cat. >2 PSI en ralentí = obstruido."
  ]
},
"P05": {
  "sys": "Vehicle Speed/Idle/Aux", "urg": "pronto", "drive": True,
  "cost": (30, 400), "time_h": 1,
  "sym": ["Velocímetro no funciona","Ralentí errático","A/C no enfría","Transmisión no cambia correctamente"],
  "causes": [("Sensor VSS/WSS defectuoso","alta"),("Cuerpo de aceleración sucio","alta"),("Válvula IAC obstruida","media"),("Compresor de A/C sin carga","media")],
  "steps": [
    "1. LIMPIEZA CUERPO ACELERACIÓN ($8-15): Limpie con limpiador especializado. Realice reaprendizaje.",
    "2. SENSOR VELOCIDAD ($0): Verifique señal en vivo. Si marca 0 km/h en movimiento, sensor malo.",
    "3. VÁLVULA IAC ($0): Limpie o reemplace. Debe permitir paso de aire controlado en ralentí.",
    "4. PRESIONES A/C ($0-50): Mida presiones alta/baja del sistema. Compare con tabla del fabricante."
  ]
},
"P06": {
  "sys": "ECU/Internal Processor", "urg": "inmediata", "drive": False,
  "cost": (50, 1200), "time_h": 2,
  "sym": ["Múltiples sistemas fallan","Arranque difícil","Comportamiento errático del motor","Check Engine con varios códigos"],
  "causes": [("Voltaje de batería bajo o inestable","alta"),("Masas del motor/ECU corroídas o sueltas","alta"),("Alternador con voltaje irregular","media"),("Fallo interno del ECU/PCM","baja")],
  "steps": [
    "1. BATERÍA ($0): Mida voltaje reposo >12.4V y carga 13.5-14.8V.",
    "2. MASAS ($0): Inspeccione y limpie TODOS los puntos de masa del motor y ECU.",
    "3. ALIMENTACIÓN ECU ($0): Mida voltaje directo en pines de alimentación del ECU.",
    "4. TSB ($0): Busque boletines de servicio técnico — muchos P06xx se resuelven con actualización software."
  ]
},
"P07": {
  "sys": "Transmission", "urg": "pronto", "drive": True,
  "cost": (100, 2500), "time_h": 3,
  "sym": ["Cambios bruscos o tardíos","Patinaje al acelerar","Check Engine encendido","Transmisión en modo emergencia (limp mode)"],
  "causes": [("Nivel de fluido ATF bajo o degradado","alta"),("Solenoide de cambio defectuoso","alta"),("Cableado de transmisión dañado por calor","media"),("Desgaste de embragues internos","media"),("Válvula del cuerpo (valve body) pegada","baja")],
  "steps": [
    "1. FLUIDO ATF ($0): Verifique nivel, color (rojo=OK, marrón=desgaste, negro=daño) y olor.",
    "2. SOLENOIDES ($0): Active con escáner bidireccional. Mida resistencia (10-25Ω típico).",
    "3. CABLEADO ($0): Inspeccione arnés de transmisión. Propenso a daño por calor y aceite.",
    "4. PRESIONES ($30-80): Conecte manómetro de presión de línea. Compare con especificación OEM.",
    "5. ADAPTACIONES ($0): Borre adaptaciones y realice procedimiento de reaprendizaje."
  ]
},
"P08": {
  "sys": "Transmission Extended", "urg": "pronto", "drive": True,
  "cost": (150, 3000), "time_h": 3,
  "sym": ["Patinaje de transmisión","Golpes al cambiar","RPM altas sin aceleración","Sobrecalentamiento de transmisión"],
  "causes": [("Convertidor de par con deslizamiento","alta"),("Embragues/bandas desgastados","alta"),("Sensor TFT defectuoso","media"),("Fluido incorrecto o degradado","media")],
  "steps": [
    "1. TEMPERATURA ($0): Monitoree temp de transmisión. >120°C = sobrecalentamiento activo.",
    "2. CONVERTIDOR ($0): Active TCC con escáner. Si RPM no bajan ~200, TCC no aplica.",
    "3. FLUIDO ($20-80): Cambie fluido Y filtro con especificación EXACTA del fabricante.",
    "4. STALL TEST ($0): Prueba de parado — RPM máximas en Drive con freno. Compare con OEM spec."
  ]
},
"P09": {
  "sys": "Transmission/Hybrid", "urg": "pronto", "drive": True,
  "cost": (100, 2000), "time_h": 2,
  "sym": ["Modo de emergencia activado","Falta de comunicación TCM-ECU","Cambios erráticos"],
  "causes": [("Módulo TCM sin alimentación o masa","alta"),("Sensores de velocidad entrada/salida","alta"),("Batería HV degradada (híbridos)","media")],
  "steps": [
    "1. COMUNICACIÓN ($0): Verifique comunicación ECU-TCM con escáner. Revise alimentación y masas.",
    "2. SENSORES ($0): Compare velocidad entrada vs salida para detectar patinaje.",
    "3. HÍBRIDO ($0): Verifique voltaje total de batería HV y celdas individuales.",
    "4. TSB ($0): Consulte boletines de servicio técnico del fabricante."
  ]
},
"P0A": {
  "sys": "Hybrid/EV Propulsion", "urg": "inmediata", "drive": False,
  "cost": (200, 5000), "time_h": 4,
  "sym": ["Pérdida de potencia eléctrica","Modo tortuga activado","Advertencia de batería HV","Motor de combustión no arranca"],
  "causes": [("Celda de batería HV débil o en cortocircuito","alta"),("Inversor/convertidor sobrecalentado","media"),("Fallo de aislamiento alto voltaje","media")],
  "steps": [
    "1. ⚠️ SEGURIDAD: Use SIEMPRE guantes dieléctricos clase 0. Desconecte service plug.",
    "2. CELDAS ($0): Mida voltaje individual. Diferencia >0.5V entre celdas = celda defectuosa.",
    "3. AISLAMIENTO ($0): Resistencia de aislamiento <500Ω/V = PELIGROSO. No conducir.",
    "4. REFRIGERACIÓN ($0): Verifique ventilador y ductos de enfriamiento de batería HV."
  ]
},
"B0": {
  "sys": "Body - Airbag/SRS", "urg": "inmediata", "drive": True,
  "cost": (80, 800), "time_h": 2,
  "sym": ["Luz de airbag encendida","Airbags desactivados","Bocina/controles del volante no funcionan"],
  "causes": [("Clockspring (resorte de reloj) dañado","alta"),("Conector SRS bajo asiento desconectado","alta"),("Sensor de impacto dañado","media"),("Módulo SRS con fallo interno","baja")],
  "steps": [
    "1. ⚠️ PRECAUCIÓN: Desconecte batería y espere 10+ minutos antes de tocar sistema SRS.",
    "2. CLOCKSPRING ($0): Gire volante de tope a tope. Si luz aparece al girar = clockspring.",
    "3. CONECTORES ($0): Inspeccione conectores amarillos SRS bajo asientos y tablero.",
    "4. SENSORES ($0): Verifique montaje y cableado de sensores de impacto frontales/laterales."
  ]
},
"B1": {
  "sys": "Body - Climate/Comfort", "urg": "rutinaria", "drive": True,
  "cost": (30, 500), "time_h": 1.5,
  "sym": ["A/C no cambia temperatura","Ventanas no funcionan","Seguros eléctricos fallan","Ruido en tablero"],
  "causes": [("Actuador de compuerta (blend door) trabado","alta"),("Motor de ventana con desgaste","media"),("Módulo de puerta sin comunicación","media")],
  "steps": [
    "1. ACTUADORES ($0): Escuche clics/zumbidos al cambiar temperatura. Sin movimiento = actuador.",
    "2. MOTORES ($0): Mida corriente del motor de ventana. Consumo alto = regulador trabado.",
    "3. MÓDULO PUERTA ($0): Verifique alimentación CAN y voltaje en módulo de puerta.",
    "4. SENSORES ($0): Revise sensores de temperatura interior/exterior y sensor solar."
  ]
},
"B2": {
  "sys": "Body - OEM Specific", "urg": "rutinaria", "drive": True,
  "cost": (30, 600), "time_h": 1.5,
  "sym": ["Sistema keyless no responde","Luces no funcionan correctamente","Asiento eléctrico atascado"],
  "causes": [("Batería de llave agotada","alta"),("Módulo BCM con fallo","media"),("Cableado de iluminación dañado","media")],
  "steps": [
    "1. LLAVE ($5): Reemplace batería de llave/control remoto primero (CR2032 típica).",
    "2. ILUMINACIÓN ($0): Revise bombillas, balastros y circuitos de iluminación.",
    "3. ASIENTOS ($0): Verifique motores, rieles y fusibles del sistema de asientos.",
    "4. BCM ($0): Verifique programación y configuración del módulo de carrocería."
  ]
},
"C0": {
  "sys": "Chassis - ABS/Stability", "urg": "inmediata", "drive": False,
  "cost": (80, 1200), "time_h": 2,
  "sym": ["Luz ABS encendida","Luz de estabilidad encendida","Frenos se sienten esponjosos","ABS no activa en frenado fuerte"],
  "causes": [("Sensor ABS de rueda sucio o dañado","alta"),("Anillo reluctor roto o con dientes faltantes","alta"),("Módulo hidráulico ABS defectuoso","media"),("Líquido de frenos contaminado","media")],
  "steps": [
    "1. ⚠️ SEGURIDAD: Sin ABS funcional, distancia de frenado aumenta significativamente.",
    "2. SENSORES ($0): Limpie sensores y anillos reluctores de las 4 ruedas. Verifique entrehierro.",
    "3. MÓDULO ($0): Revise alimentación, masas y comunicación CAN del módulo ABS.",
    "4. LÍQUIDO ($10-20): Verifique nivel y condición. Purgue si hay aire o contaminación."
  ]
},
"C1": {
  "sys": "Chassis - Steering/Suspension", "urg": "pronto", "drive": True,
  "cost": (50, 800), "time_h": 1.5,
  "sym": ["Dirección dura o asistida intermitente","Luz de dirección encendida","Suspensión irregular"],
  "causes": [("Motor EPS con fallo o sobrecalentado","alta"),("Sensor ángulo dirección descalibrado","media"),("Compresor suspensión neumática débil","media")],
  "steps": [
    "1. EPS ($0): Revise fluido (hidráulica) o motor y módulo (eléctrica).",
    "2. CALIBRACIÓN ($0): Calibre sensor de ángulo de dirección después de alineación.",
    "3. SUSPENSIÓN ($0): En sistemas neumáticos, revise compresor, bolsas y válvulas.",
    "4. SENSORES ($0): Verifique sensores de nivel en cada esquina."
  ]
},
"U0": {
  "sys": "Network Communication CAN", "urg": "inmediata", "drive": False,
  "cost": (50, 1000), "time_h": 2,
  "sym": ["Múltiples luces de advertencia","Instrumentos no funcionan","Sistemas desactivados","No arranca"],
  "causes": [("Módulo sin alimentación (fusible quemado)","alta"),("Cable CAN dañado o en cortocircuito","alta"),("Resistencia terminal CAN faltante","media"),("Módulo con fallo interno","baja")],
  "steps": [
    "1. CAN BUS ($0): Mida CAN-H (~2.5-3.5V) y CAN-L (~1.5-2.5V) con ignición ON.",
    "2. RESISTENCIA ($0): 60Ω entre CAN-H y CAN-L con ignición OFF. 120Ω = resistencia faltante.",
    "3. MÓDULO ($0): Identifique cuál módulo perdió comunicación. Verifique fusibles y alimentación.",
    "4. ARNÉS ($0): Inspeccione bus de datos por cables dañados o en cortocircuito."
  ]
},
"U1": {
  "sys": "Network OEM", "urg": "pronto", "drive": True,
  "cost": (50, 800), "time_h": 1.5,
  "sym": ["Sistema específico no responde","Funciones limitadas","Mensaje de error en tablero"],
  "causes": [("Módulo específico sin alimentación","alta"),("Gateway/BCM con fallo de ruteo","media"),("Requiere reprogramación","media")],
  "steps": [
    "1. ALIMENTACIÓN ($0): Verifique voltaje en módulo afectado. Mínimo 10.5V.",
    "2. GATEWAY ($0): Revise módulo gateway/BCM que coordina comunicación entre redes.",
    "3. PROGRAMACIÓN ($0-200): Algunos U1xxx requieren reprogramación con herramienta OEM.",
    "4. FUSIBLES ($0): Revise todos los fusibles asociados al módulo reportado."
  ]
},
"U2": {
  "sys": "Network Extended", "urg": "pronto", "drive": True,
  "cost": (50, 600), "time_h": 1,
  "sym": ["Módulo no responde en red","Funcionalidad reducida"],
  "causes": [("Conector del módulo corroído","alta"),("Fusible quemado","alta"),("Firmware desactualizado","media")],
  "steps": [
    "1. CONECTORES ($0): Inspeccione terminales por corrosión, dobladas o sueltas.",
    "2. FUSIBLES ($0): Revise fusibles asociados al módulo.",
    "3. ACTUALIZACIÓN ($0-200): Verifique si existe actualización de firmware."
  ]
},
}

# Manufacturer-specific overrides for P1xxx codes
MFR_OVERRIDES = {
  "P10": ("Fuel/Air OEM Específico", "pronto", True, (50, 500), 1.5),
  "P11": ("Fuel/Air OEM Específico", "pronto", True, (50, 500), 1.5),
  "P12": ("Fuel/Air OEM Específico", "pronto", True, (50, 500), 1.5),
  "P13": ("Fuel/Air OEM Específico", "pronto", True, (50, 500), 1.5),
  "P2": ("Fuel/Air Extended SAE", "pronto", True, (80, 600), 2),
  "P3": ("OEM Powertrain Extended", "pronto", True, (80, 800), 2),
  "U3": ("Network Reserved", "pronto", True, (50, 500), 1),
}

def get_elite(code):
    """Get best matching elite template for a DTC code."""
    c = code.upper()
    # Try 3-char, 2-char, 1-char prefix
    for plen in [3, 2]:
        key = c[:plen]
        if key in ELITE:
            return ELITE[key]
    # Manufacturer-specific fallbacks
    for plen in [3, 2]:
        key = c[:plen]
        if key in MFR_OVERRIDES:
            s, u, d, cost, t = MFR_OVERRIDES[key]
            base = ELITE.get("P00", ELITE.get("U0"))
            return {**base, "sys": s, "urg": u, "drive": d, "cost": cost, "time_h": t}
    # Final fallback by first char
    fb = {"P": "P00", "B": "B1", "C": "C0", "U": "U0"}
    return ELITE.get(fb.get(c[0], "P00"), ELITE["P00"])
