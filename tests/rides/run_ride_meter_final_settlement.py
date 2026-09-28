#!/usr/bin/env python3
"""Isolated local PostgreSQL exercise; never uses remote project credentials."""
from pathlib import Path
import subprocess
import uuid

root = Path(__file__).resolve().parents[2]
db = 'meet_meter_contract_' + uuid.uuid4().hex[:12]

def run(*args):
    return subprocess.run(args,cwd=root,text=True,check=True,capture_output=True)

run('createdb','-h','/tmp',db)
try:
    for path in (
        'tests/rides/ride_meter_final_settlement_fixture.sql',
        'supabase/migrations/20260928120000_ride_meter_final_settlement.sql',
        'tests/rides/ride_meter_final_settlement_contract.sql',
    ):
        run('psql','-h','/tmp','-v','ON_ERROR_STOP=1','-d',db,'-f',str(root/path))
    print('PASS: missing GPS blocked; downstream failure rolled back; validated meter settled; shared final total confirmed')
finally:
    run('dropdb','-h','/tmp',db)
