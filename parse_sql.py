import re
import json
import os

sql_file = "supabase/migrations/20260427050909_seed_5000_codes.sql"
json_file = "android/app/src/main/assets/dtc_database_es.json"

with open(sql_file, "r", encoding="utf-8") as f:
    content = f.read()

# Match tuples like ('P0000', 'Reserved', 'Reserved', 'ENGINE', 'LOW', 'Diagn...', 'MONITOR')
# Since descriptions might contain escaped quotes or commas, we can use ast.literal_eval or a more careful regex.
# Actually, looking at the SQL, they are all single quotes.
# Let's find all instances of `('...', '...', '...', '...', '...', '...', '...')`

pattern = re.compile(r"\('(.*?)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)',\s*'(.*?)'\)")
matches = pattern.findall(content)

dtcs = []
for m in matches:
    dtcs.append({
        "code": m[0].replace("''", "'"),
        "descriptionEs": m[1].replace("''", "'"),
        "descriptionEn": m[2].replace("''", "'"),
        "system": m[3],
        "severity": m[4],
        "possibleCauses": m[5].replace("''", "'"),
        "urgency": m[6]
    })

print(f"Found {len(dtcs)} codes.")

with open(json_file, "w", encoding="utf-8") as f:
    json.dump(dtcs, f, indent=2, ensure_ascii=False)

print("Wrote JSON file.")
