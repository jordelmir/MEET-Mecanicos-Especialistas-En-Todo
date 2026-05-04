import json
import sqlite3
import itertools

db_file = "android/app/src/main/assets/huge_dtc_database.db"
old_json_file = "dtc_database.json" # the original old DB that had spanish translation!
output_json_es = "android/app/src/main/assets/dtc_database_es.json"
output_json_web = "dtc_database.json"

try:
    with open(old_json_file, 'r', encoding='utf-8') as f:
        old_data = json.load(f)
        
    old_dict = {}
    for item in old_data:
        old_dict[item["code"]] = item
except FileNotFoundError:
    old_dict = {}

conn = sqlite3.connect(db_file)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# Get all known codes from our 18k db
cursor.execute("SELECT code, manufacturer, description, type, is_generic FROM dtc_definitions WHERE locale='en'")
rows = cursor.fetchall()

known_dtcs = {}
for row in rows:
    code = row["code"].upper()
    if code not in known_dtcs:
        known_dtcs[code] = []
    known_dtcs[code].append({
        "manufacturer": row["manufacturer"],
        "description": row["description"],
        "is_generic": bool(row["is_generic"])
    })

letters = ['P', 'C', 'B', 'U']
hex_digits = '0123456789ABCDEF'

def get_system(letter):
    return {"P": "ENGINE", "C": "CHASSIS", "B": "BODY", "U": "NETWORK"}[letter]

def get_is_generic(letter, digit1):
    if digit1 == '0':
        return True
    if digit1 == '2' or digit1 == '3':
        if letter == 'P' and digit1 == '2': return True
        if letter == 'P' and digit1 == '3': return False
        if letter == 'U' and digit1 == '3': return True
    return False

def get_default_description_en(letter, digit1, digit2):
    generic = "Generic" if get_is_generic(letter, digit1) else "Manufacturer Specific"
    sys_name = {"P": "Powertrain", "C": "Chassis", "B": "Body", "U": "Network"}[letter]
    
    subsys = ""
    if letter == 'P':
        if digit2 in '012': subsys = " - Fuel and Air Metering"
        elif digit2 == '3': subsys = " - Ignition System or Misfire"
        elif digit2 == '4': subsys = " - Auxiliary Emission Controls"
        elif digit2 == '5': subsys = " - Vehicle Speed, Idle Control, and Auxiliary Inputs"
        elif digit2 == '6': subsys = " - Computer and Output Circuits"
        elif digit2 in '789': subsys = " - Transmission"
        elif digit2 in 'ABCDEF': subsys = " - Hybrid/Propulsion"
        
    return f"{generic} {sys_name} DTC{subsys}. Exact definition unavailable."

def get_default_description_es(letter, digit1, digit2):
    generic = "Genérico" if get_is_generic(letter, digit1) else "Específico del Fabricante"
    sys_name = {"P": "Motor/Transmisión", "C": "Chasis", "B": "Carrocería", "U": "Red/Comunicación"}[letter]
    
    subsys = ""
    if letter == 'P':
        if digit2 in '012': subsys = " - Medición de aire y combustible"
        elif digit2 == '3': subsys = " - Sistema de encendido o falla de cilindro"
        elif digit2 == '4': subsys = " - Controles auxiliares de emisiones"
        elif digit2 == '5': subsys = " - Control de velocidad, ralentí y entradas auxiliares"
        elif digit2 == '6': subsys = " - Computadora y circuitos de salida"
        elif digit2 in '789': subsys = " - Transmisión"
        elif digit2 in 'ABCDEF': subsys = " - Propulsión/Híbrido"
        
    return f"DTC {generic} de {sys_name}{subsys}. Definición exacta no disponible."

def infer_severity(letter, digit1):
    if letter in ('P', 'U') and digit1 in ('0', '2'): return "HIGH"
    return "MODERATE"

def infer_urgency(sev):
    if sev == "HIGH": return "STOP_DRIVING"
    return "CAUTION"

all_dtcs = []

# Generate ALL codes
for combo in itertools.product(letters, hex_digits, hex_digits, hex_digits, hex_digits):
    code = combo[0] + "".join(combo[1:])
    
    system = get_system(combo[0])
    is_gen = get_is_generic(combo[0], combo[1])
    severity = infer_severity(combo[0], combo[1])
    urgency = infer_urgency(severity)
    
    if code in known_dtcs:
        for entry in known_dtcs[code]:
            desc_en = entry["description"]
            desc_es = ""
            # Find translation if available
            if code in old_dict and entry["manufacturer"] in ("Generic", "All", "SAE"):
                desc_es = old_dict[code].get("descriptionEs", desc_en)
            elif code in old_dict:
                desc_es = old_dict[code].get("descriptionEs", desc_en)
            else:
                desc_es = desc_en
            
            # If the user's previous description had better possible causes, preserve it
            possible_causes = "Consultar manual de servicio específico del fabricante."
            if code in old_dict:
                possible_causes = old_dict[code].get("possibleCauses", possible_causes)
                severity = old_dict[code].get("severity", severity)
                urgency = old_dict[code].get("urgency", urgency)
                
            all_dtcs.append({
                "code": code,
                "manufacturer": entry["manufacturer"],
                "descriptionEn": desc_en,
                "descriptionEs": desc_es,
                "system": system,
                "severity": severity,
                "possibleCauses": possible_causes,
                "urgency": urgency,
                "isGeneric": entry["is_generic"]
            })
    else:
        # Fallback generated code (for ALL unknown codes)
        all_dtcs.append({
            "code": code,
            "manufacturer": "All",
            "descriptionEn": get_default_description_en(combo[0], combo[1], combo[2]),
            "descriptionEs": get_default_description_es(combo[0], combo[1], combo[2]),
            "system": system,
            "severity": severity,
            "possibleCauses": "Requiere escaneo profesional avanzado. / Requires advanced professional scan.",
            "urgency": urgency,
            "isGeneric": is_gen
        })

with open(output_json_es, "w", encoding="utf-8") as f:
    json.dump(all_dtcs, f, indent=2, ensure_ascii=False)

with open(output_json_web, "w", encoding="utf-8") as f:
    json.dump(all_dtcs, f, indent=2, ensure_ascii=False)

print(f"Successfully generated ALL HUMAN HISTORY {len(all_dtcs)} DTCs.")
