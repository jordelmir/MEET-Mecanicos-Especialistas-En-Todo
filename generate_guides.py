#!/usr/bin/env python3
"""Generate professional repair guides for all 18K+ DTCs."""
import json, sys

# Load source database
with open("dtc_database.json") as f:
    dtcs = json.load(f)

print(f"Loaded {len(dtcs)} DTCs")

# System-specific guide templates keyed by DTC prefix range
T = {
    "P00": ("Fuel/Air Metering", "Media",
        "1. INSPECCIÓN VISUAL: Revise el cableado, arnés y conectores del circuito afectado por daños, corrosión o desconexiones.\n2. PRUEBAS ELÉCTRICAS: Mida voltaje de alimentación, señal y masa en el conector del sensor/actuador con multímetro.\n3. LECTURA EN VIVO: Compare los valores del sensor en el escáner con las especificaciones OEM. Valores fijos o erráticos indican fallo.\n4. VERIFICACIÓN MECÁNICA: Revise fugas de vacío, filtro de aire y estado de mangueras de admisión."),
    "P01": ("Fuel/Air Metering", "Media",
        "1. ANÁLISIS DE FUEL TRIMS: Revise STFT y LTFT en ralentí y a 2500 RPM. Desviaciones >±10% indican problema.\n2. SENSOR O2/MAF/MAP: Verifique lecturas en vivo. Limpie o reemplace sensores fuera de rango.\n3. SISTEMA DE COMBUSTIBLE: Mida presión de combustible con manómetro. Verifique bomba, filtro y regulador.\n4. FUGAS DE VACÍO: Use máquina de humo para detectar fugas en múltiple de admisión y mangueras."),
    "P02": ("Fuel/Air Injection", "Media",
        "1. CIRCUITO DE INYECTOR: Mida resistencia del inyector (típico 12-16 ohmios). Compare entre cilindros.\n2. SEÑAL PCM: Verifique pulso de inyección con luz Noid o multímetro en cada conector.\n3. CABLEADO: Inspeccione arnés por cortocircuito a masa, a positivo o circuito abierto.\n4. PRUEBA DE BALANCE: Use función bidireccional del escáner para activar cada inyector individualmente."),
    "P03": ("Ignition/Misfire", "Alta",
        "1. DIAGNÓSTICO CRUZADO: Intercambie bobina del cilindro afectado con otro. Si el fallo se mueve, reemplace bobina.\n2. BUJÍAS: Extraiga y examine. Negro=Rico/Fallo chispa, Blanco=Pobre, Aceitoso=Sellos válvula.\n3. COMPRESIÓN: Realice prueba de compresión y fugas (Leak-Down). Variación max 10-15% entre cilindros.\n4. DISTRIBUCIÓN: Si múltiples cilindros fallan, verifique cadena/correa de distribución y marcas de tiempo."),
    "P04": ("Emissions (EGR/EVAP/CAT)", "Media",
        "1. SISTEMA EGR: Limpie válvula EGR y conductos de carbón. Pruebe con escáner bidireccional.\n2. SISTEMA EVAP: Verifique tapa de combustible. Use máquina de humo en puerto EVAP para encontrar fugas.\n3. CATALIZADOR: Grafique O2 pre-cat vs post-cat. Si post-cat copia la onda del pre-cat, catalizador agotado.\n4. VÁLVULAS: Pruebe válvula de purga y ventilación EVAP con vacío y multímetro."),
    "P05": ("Vehicle Speed/Idle/Aux", "Media",
        "1. SENSOR VSS/WSS: Verifique señal de velocidad en vivo. Si marca 0 km/h en movimiento, revise sensor de rueda.\n2. CONTROL DE RALENTÍ: Limpie cuerpo de aceleración y válvula IAC. Realice re-aprendizaje de ralentí.\n3. SISTEMA A/C: Revise presiones del sistema, embrague del compresor y cableado.\n4. CABLEADO: Inspeccione arnés y conectores del circuito reportado por el DTC."),
    "P06": ("ECU/Auxiliary Outputs", "Alta",
        "1. VOLTAJE DE BATERÍA: Mida voltaje en reposo (>12.4V) y en carga (13.5-14.8V). Alternador defectuoso causa errores de ECU.\n2. MASAS DEL MOTOR: Verifique todos los puntos de masa del ECU y motor. Limpie y apriete.\n3. CIRCUITOS INTERNOS: Este código puede indicar fallo interno del ECU. Descarte primero problemas de alimentación.\n4. ACTUALIZACIÓN: Verifique si existe TSB o actualización de software del fabricante para este código."),
    "P07": ("Transmission", "Alta",
        "1. NIVEL Y CONDICIÓN: Verifique nivel, color y olor del fluido de transmisión. Quemado=Daño interno.\n2. SOLENOIDES: Use escáner bidireccional para activar solenoides individualmente. Mida resistencia (típico 10-25 ohmios).\n3. PRESIONES: Conecte manómetro de presión de línea. Compare con especificaciones OEM en cada rango.\n4. CABLEADO: Inspeccione arnés de la transmisión (propenso a daño por calor/aceite). Revise conectores."),
    "P08": ("Transmission", "Alta",
        "1. EMBRAGUES/BANDAS: Si hay patinaje o golpes al cambiar, posible desgaste de embragues internos.\n2. CONVERTIDOR DE PAR: Revise sensor TFT y verifique que el embrague del convertidor (TCC) aplique correctamente.\n3. ADAPTACIONES: Borre adaptaciones de transmisión y realice procedimiento de re-aprendizaje.\n4. FLUIDO: Cambie fluido y filtro de transmisión con especificación exacta del fabricante."),
    "P09": ("Transmission/Hybrid", "Alta",
        "1. MÓDULO TCM: Verifique comunicación entre ECU y TCM. Revise alimentación y masas del módulo.\n2. SENSORES DE VELOCIDAD: Compare velocidad de entrada vs salida de la transmisión para detectar patinaje.\n3. SISTEMA HÍBRIDO: En vehículos híbridos, revise voltaje de batería de alto voltaje y estado de inversores.\n4. TSB: Consulte boletines de servicio técnico del fabricante para este código específico."),
    "P0A": ("Hybrid/EV Propulsion", "Alta",
        "1. BATERÍA HV: Verifique voltaje total y de celdas individuales. Diferencias >0.5V entre celdas indican celda débil.\n2. AISLAMIENTO: Mida resistencia de aislamiento del sistema de alto voltaje. <500 ohmios/V es peligroso.\n3. INVERSORES: Revise temperatura y códigos del inversor/convertidor. Verifique refrigeración.\n4. SEGURIDAD: Use SIEMPRE guantes dieléctricos clase 0. Desconecte servicio plug antes de trabajar."),
    "P10": ("Fuel/Air OEM", "Media",
        "1. CÓDIGO ESPECÍFICO DEL FABRICANTE: Consulte el manual de servicio del fabricante para el procedimiento exacto.\n2. INSPECCIÓN GENERAL: Revise cableado, conectores y componentes del sistema de combustible/aire.\n3. DATOS EN VIVO: Compare lecturas de sensores con valores OEM esperados.\n4. TSB: Busque boletines de servicio técnico del fabricante para este código."),
    "P2": ("Fuel/Air/Emissions OEM", "Media",
        "1. SISTEMA DE INYECCIÓN: Revise circuitos de inyectores, solenoides y actuadores del sistema afectado.\n2. TURBO/SOBREALIMENTACIÓN: Si aplica, verifique actuador de wastegate, válvula de alivio y presión de boost.\n3. POSTRATAMIENTO: Revise sistema DPF/SCR/DOC. Verifique presión diferencial y temperatura de escape.\n4. REDUCTANTE: En sistemas SCR, verifique nivel y calidad del DEF/AdBlue. Revise inyector de urea."),
    "P3": ("OEM Powertrain", "Media",
        "1. CÓDIGO OEM: Este es un código específico del fabricante. Consulte manual de servicio para procedimiento.\n2. VVT/VCT: Muchos P3xxx se relacionan con distribución variable. Revise aceite, solenoides OCV y actuadores.\n3. TURBO: Verifique actuador de geometría variable, sensor de posición de aletas y conductos de vacío.\n4. DIAGNÓSTICO: Use datos en vivo y pruebas bidireccionales según el circuito específico reportado."),
    "B0": ("Body - Airbag/Restraints", "Alta",
        "1. ⚠️ PRECAUCIÓN: Desconecte batería y espere 10+ minutos antes de trabajar en sistema SRS.\n2. RESORTE DE RELOJ: Revise clockspring en el volante. Gire el volante de tope a tope buscando fallas.\n3. CONECTORES: Inspeccione conectores amarillos del SRS bajo los asientos y en el tablero.\n4. SENSORES DE IMPACTO: Verifique que los sensores de impacto estén bien montados y sin daño."),
    "B1": ("Body - Climate/Doors/Windows", "Media",
        "1. ACTUADORES HVAC: Revise actuadores de compuertas de aire (blend door) por ruidos o falta de movimiento.\n2. MÓDULOS DE PUERTA: Verifique alimentación y comunicación CAN del módulo de la puerta afectada.\n3. MOTORES DE VENTANA: Mida corriente del motor. Consumo excesivo indica regulador o guías trabados.\n4. SENSORES: Revise sensores de temperatura interior/exterior y sensor solar."),
    "B2": ("Body - OEM Specific", "Media",
        "1. CÓDIGO OEM DE CARROCERÍA: Consulte manual del fabricante para el sistema afectado.\n2. ILUMINACIÓN: Revise bombillas, balastros de HID/LED y circuitos de iluminación exterior/interior.\n3. ASIENTOS ELÉCTRICOS: Verifique motores, rieles y módulo de memoria de asientos.\n4. SISTEMA KEYLESS: Revise baterías de llaves, antenas del sistema y módulo de acceso."),
    "C0": ("Chassis - ABS/Stability", "Alta",
        "1. SENSORES DE RUEDA ABS: Limpie sensores y anillos reluctores de las 4 ruedas. Verifique entrehierro.\n2. MÓDULO ABS/ESP: Revise alimentación, masas y comunicación CAN del módulo hidráulico.\n3. BOMBA HIDRÁULICA: Escuche la bomba ABS al encender. Zumbido constante indica posible fallo.\n4. LÍQUIDO DE FRENOS: Verifique nivel y condición. Purgue el sistema si hay aire o contaminación."),
    "C1": ("Chassis - Steering/Suspension", "Media",
        "1. DIRECCIÓN ASISTIDA: Revise nivel de fluido (hidráulica) o motor y módulo (eléctrica EPS).\n2. SENSOR DE ÁNGULO: Calibre el sensor de ángulo de dirección después de alineación o reparación.\n3. SUSPENSIÓN: En sistemas neumáticos, revise compresor, bolsas de aire y válvulas de distribución.\n4. SENSORES DE ALTURA: Verifique sensores de nivel en cada esquina del vehículo."),
    "U0": ("Network Communication", "Alta",
        "1. RED CAN BUS: Este código indica pérdida de comunicación con un módulo. Verifique voltaje CAN-H (~2.5-3.5V) y CAN-L (~1.5-2.5V).\n2. RESISTENCIAS TERMINALES: Mida 60 ohmios entre CAN-H y CAN-L con ignición OFF. 120 ohmios = resistencia terminal faltante.\n3. MÓDULO AFECTADO: Identifique el módulo sin comunicación. Verifique su alimentación, masa y fusibles.\n4. ARNÉS: Inspeccione el bus de datos por cables dañados, aplastados o en cortocircuito entre sí."),
    "U1": ("Network OEM", "Media",
        "1. COMUNICACIÓN OEM: Código de red específico del fabricante. Identifique el módulo sin respuesta.\n2. ALIMENTACIÓN: Verifique voltaje de batería en el módulo afectado. Mínimo 10.5V para operación normal.\n3. PROGRAMACIÓN: Algunos U1xxx requieren reprogramación o configuración del módulo con herramienta del fabricante.\n4. GATEWAY: Revise el módulo gateway/BCM que coordina la comunicación entre redes."),
    "U2": ("Network OEM Extended", "Media",
        "1. DIAGNÓSTICO DE RED: Use escáner para identificar qué módulo no responde en la red.\n2. FUSIBLES Y RELÉS: Revise todos los fusibles asociados al módulo reportado.\n3. CONECTORES: Inspeccione conectores del módulo por terminales dobladas, corroídas o sueltas.\n4. ACTUALIZACIÓN: Verifique si existe actualización de firmware disponible para el módulo afectado."),
    "U3": ("Network Reserved", "Media",
        "1. CÓDIGO RESERVADO: Este rango está parcialmente reservado. Consulte documentación OEM.\n2. MÓDULO: Identifique el módulo fuente y destino de la comunicación fallida.\n3. HARDWARE: Verifique integridad física del módulo y su arnés de comunicación.\n4. SOFTWARE: Puede requerir reprogramación con herramienta del fabricante."),
}

def get_template(code):
    c = code.upper()
    # Try most specific match first
    for prefix_len in [3, 2, 1]:
        key = c[:prefix_len]
        if key in T:
            return T[key]
    # Fallback for P1xxx manufacturer codes
    if c.startswith("P1"):
        return T.get("P10", T["P01"])
    if c.startswith("P"):
        return T.get("P2", T["P00"])
    if c.startswith("B"):
        return T.get("B1", T["B0"])
    if c.startswith("C"):
        return T.get("C0", T["C0"])
    if c.startswith("U"):
        return T.get("U0", T["U0"])
    return ("General", "Media", "1. Consulte el manual de servicio del fabricante.\n2. Inspeccione cableado y conectores.\n3. Verifique datos en vivo con escáner.\n4. Realice pruebas eléctricas con multímetro.")

# Load existing solutions - handle broken JSON gracefully
import re
try:
    with open("android/app/src/main/assets/dtc_offline_solutions.json") as f:
        existing = json.load(f)
    existing_codes = {s["code"] for s in existing["dtc_solutions"]}
except json.JSONDecodeError:
    # Extract codes from broken JSON via regex
    with open("android/app/src/main/assets/dtc_offline_solutions.json") as f:
        raw = f.read()
    existing_codes = set(re.findall(r'"code":\s*"([^"]+)"', raw))
print(f"Existing solutions: {len(existing_codes)}")

# Generate guides for ALL DTCs
solutions = []

seen = set()
for dtc in dtcs:
    code = dtc.get("code", "")
    if not code or code in seen:
        continue
    seen.add(code)
    
    system_name, severity, procedure = get_template(code)
    desc_es = dtc.get("descriptionEs", "")
    desc_en = dtc.get("descriptionEn", "")
    desc = desc_es if desc_es else desc_en
    sys_from_db = dtc.get("system", "")
    sev_from_db = dtc.get("severity", severity)
    
    full_solution = f"PROCEDIMIENTO DE DIAGNÓSTICO PROFESIONAL — {code}:\nSistema: {sys_from_db or system_name}\nDescripción: {desc}\n\n{procedure}\n\n5. VERIFICACIÓN FINAL: Borre el código, realice prueba de manejo y verifique que el monitor correspondiente pase correctamente."
    
    solutions.append({
        "code": code,
        "description": desc if desc else f"Código {code} - {system_name}",
        "oem_solution": full_solution,
        "severity": sev_from_db if sev_from_db else severity
    })

output = {"dtc_solutions": solutions}
outpath = "android/app/src/main/assets/dtc_offline_solutions.json"
with open(outpath, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

print(f"Generated {len(solutions)} total repair guides -> {outpath}")
