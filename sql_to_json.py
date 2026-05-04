import json
import re

files = [
    "supabase/migrations/20260427045845_create_dtc_database.sql",
    "supabase/migrations/20260427050909_seed_5000_codes.sql"
]
json_file = "android/app/src/main/assets/dtc_database_es.json"
web_json_file = "dtc_database.json"

dtcs = []

# Regex to match the tuple values
# Example: ('P0000', 'Reserved', 'Reserved', 'ENGINE', 'LOW', 'Diagnóstico requerido (Código Extendido).', 'MONITOR')
# Since it's SQL, we need to handle single quotes properly.
pattern = re.compile(r"\('([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*'([^']*)'\)")

for sql_file in files:
    with open(sql_file, "r", encoding="utf-8") as f:
        for line in f:
            match = pattern.search(line)
            if match:
                code, desc_es, desc_en, system, severity, possible_causes, urgency = match.groups()
                # Deduplicate by code
                if not any(d["code"] == code for d in dtcs):
                    dtcs.append({
                        "code": code,
                        "descriptionEs": desc_es,
                        "descriptionEn": desc_en,
                        "system": system,
                        "severity": severity,
                        "possibleCauses": possible_causes,
                        "urgency": urgency
                    })

with open(json_file, "w", encoding="utf-8") as f:
    json.dump(dtcs, f, indent=2, ensure_ascii=False)

with open(web_json_file, "w", encoding="utf-8") as f:
    json.dump(dtcs, f, indent=2, ensure_ascii=False)

print(f"Successfully converted {len(dtcs)} DTCs to {json_file} and {web_json_file}")
