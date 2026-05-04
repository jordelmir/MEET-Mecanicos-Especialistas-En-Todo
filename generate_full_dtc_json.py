import json
import sqlite3
import re

db_file = "android/app/src/main/assets/huge_dtc_database.db"
old_json_file = "dtc_database.json"
output_json_es = "android/app/src/main/assets/dtc_database_es.json"
output_json_web = "dtc_database.json"

# Load the old translations and metadata
try:
    with open(old_json_file, 'r', encoding='utf-8') as f:
        old_data = json.load(f)
        
    old_dict = {}
    for item in old_data:
        old_dict[item["code"]] = item
except FileNotFoundError:
    old_dict = {}

def infer_system(code):
    if not code: return "GENERAL"
    c = code[0].upper()
    if c == 'P': return "ENGINE" # Powertrain
    if c == 'C': return "CHASSIS"
    if c == 'B': return "BODY"
    if c == 'U': return "NETWORK"
    return "GENERAL"

def infer_severity(code):
    if code in old_dict:
        return old_dict[code].get("severity", "LOW")
    if not code: return "LOW"
    c = code[0].upper()
    if c == 'P': return "HIGH" if code[1] == '0' else "MODERATE"
    if c == 'U': return "HIGH"
    return "MODERATE"

def infer_urgency(code):
    if code in old_dict:
        return old_dict[code].get("urgency", "LOW")
    sev = infer_severity(code)
    if sev == "HIGH": return "STOP_DRIVING"
    if sev == "MODERATE": return "CAUTION"
    return "MONITOR"

def infer_possible_causes(code, description):
    if code in old_dict:
        return old_dict[code].get("possibleCauses", "Consultar manual de servicio del fabricante.")
    return "Consultar manual de servicio específico del fabricante."

conn = sqlite3.connect(db_file)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# Get all unique codes. We will try to group by code and manufacturer.
# The user wants "all codes from human history".
# But wait, DtcDefinitionEntity currently uses @PrimaryKey val code: String.
# We must include the manufacturer in the JSON so we can update the Android Room DB to use a composite key or a unique ID.
# Let's query all en codes
cursor.execute("SELECT code, manufacturer, description, type, is_generic FROM dtc_definitions WHERE locale='en'")
rows = cursor.fetchall()

dtcs = []

for row in rows:
    code = row["code"]
    manufacturer = row["manufacturer"]
    desc_en = row["description"]
    is_generic = row["is_generic"]
    
    # Try to find a Spanish translation
    desc_es = ""
    if code in old_dict and manufacturer in ("Generic", "All", "SAE"): 
        # Only use the generic Spanish description for generic codes (or if the manufacturer is generic)
        # Actually, let's just use it if available, better than nothing
        desc_es = old_dict[code].get("descriptionEs", desc_en)
    elif code in old_dict:
         desc_es = old_dict[code].get("descriptionEs", desc_en)
    else:
        desc_es = desc_en # Fallback to English if no Spanish is available

    system = infer_system(code)
    severity = infer_severity(code)
    urgency = infer_urgency(code)
    possible_causes = infer_possible_causes(code, desc_en)
    
    dtcs.append({
        "code": code,
        "manufacturer": manufacturer,
        "descriptionEs": desc_es,
        "descriptionEn": desc_en,
        "system": system,
        "severity": severity,
        "possibleCauses": possible_causes,
        "urgency": urgency,
        "isGeneric": bool(is_generic)
    })

# Write to android assets
with open(output_json_es, "w", encoding="utf-8") as f:
    json.dump(dtcs, f, indent=2, ensure_ascii=False)

# Write to web app
with open(output_json_web, "w", encoding="utf-8") as f:
    json.dump(dtcs, f, indent=2, ensure_ascii=False)

print(f"Successfully exported {len(dtcs)} DTCs to JSON formats.")
