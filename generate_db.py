import json
import sqlite3
import os

json_path = 'dtc_database.json'
db_path = 'android/app/src/main/assets/databases/meet_dtc.db'

# Ensure directory exists
os.makedirs(os.path.dirname(db_path), exist_ok=True)

# Remove existing db if exists
if os.path.exists(db_path):
    os.remove(db_path)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Create table matching DtcDefinitionEntity
cursor.execute('''
CREATE TABLE IF NOT EXISTS dtc_definitions (
    code TEXT PRIMARY KEY NOT NULL,
    descriptionEn TEXT NOT NULL,
    descriptionEs TEXT NOT NULL,
    system TEXT NOT NULL,
    severity TEXT NOT NULL,
    possibleCauses TEXT NOT NULL,
    urgency TEXT NOT NULL
)
''')

with open(json_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

# Deduplicate by code
unique_dtcs = {}
for item in data:
    code = item.get('code')
    if not code:
        continue
    # If multiple exist, maybe prefer one where manufacturer is 'OTHER' or just take the first one
    if code not in unique_dtcs:
        unique_dtcs[code] = item

print(f"Total unique DTCs: {len(unique_dtcs)}")

count = 0
for code, item in unique_dtcs.items():
    try:
        cursor.execute('''
            INSERT INTO dtc_definitions (code, descriptionEn, descriptionEs, system, severity, possibleCauses, urgency)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ''', (
            code,
            item.get('descriptionEn', ''),
            item.get('descriptionEs', ''),
            item.get('system', ''),
            item.get('severity', ''),
            item.get('possibleCauses', ''),
            item.get('urgency', '')
        ))
        count += 1
    except Exception as e:
        print(f"Error inserting {code}: {e}")

conn.commit()
conn.close()

print(f"Successfully inserted {count} DTC definitions into {db_path}")
