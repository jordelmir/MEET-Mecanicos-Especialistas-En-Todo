import sqlite3
import os

def main():
    db_path = 'android/app/src/main/assets/databases/meet_dtc.db'
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    if os.path.exists(db_path):
        os.remove(db_path)
        
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Matches DtcDefinitionEntity exactly
    cursor.execute("""
    CREATE TABLE dtc_definitions (
        code TEXT PRIMARY KEY NOT NULL,
        descriptionEs TEXT NOT NULL,
        descriptionEn TEXT NOT NULL,
        system TEXT NOT NULL,
        severity TEXT NOT NULL,
        possibleCauses TEXT NOT NULL,
        urgency TEXT NOT NULL
    );
    """)
    
    cursor.execute("""
    CREATE TABLE temp_dtc_codes (
        code TEXT NOT NULL UNIQUE,
        description_es TEXT NOT NULL,
        description_en TEXT NOT NULL,
        system TEXT NOT NULL,
        severity TEXT NOT NULL,
        possible_causes TEXT NOT NULL,
        urgency TEXT NOT NULL
    );
    """)
    
    files = [
        'supabase/migrations/20260427045845_create_dtc_database.sql',
        'supabase/migrations/20260427050909_seed_5000_codes.sql'
    ]
    
    for filename in files:
        if not os.path.exists(filename):
            continue
            
        with open(filename, 'r', encoding='utf-8') as f:
            content = f.read()
            
        statements = content.split(';')
        for stmt in statements:
            stmt = stmt.strip()
            if stmt.startswith('INSERT INTO public.dtc_codes'):
                stmt = stmt.replace('public.dtc_codes', 'temp_dtc_codes')
                try:
                    cursor.execute(stmt)
                except Exception as e:
                    pass
                    
    cursor.execute("SELECT code, description_es, description_en, system, severity, possible_causes, urgency FROM temp_dtc_codes")
    rows = cursor.fetchall()
    
    for row in rows:
        code, desces, descen, sys, sev, caus, urg = row
        try:
            cursor.execute('''
                INSERT INTO dtc_definitions (code, descriptionEs, descriptionEn, system, severity, possibleCauses, urgency)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            ''', (code, desces, descen, sys, sev, caus, urg))
        except Exception as e:
            pass # ignore duplicates
            
    cursor.execute("DROP TABLE temp_dtc_codes")
    
    cursor.execute("SELECT count(*) FROM dtc_definitions")
    print(f"Total DTC codes inserted: {cursor.fetchone()[0]}")
    
    conn.commit()
    conn.close()
    
if __name__ == '__main__':
    main()
