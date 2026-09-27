#!/usr/bin/env python3
"""Transactional, disposable-database authority verification (no production target)."""
import argparse
import pathlib
import subprocess
p=argparse.ArgumentParser()
p.add_argument('--database',required=True)
a=p.parse_args()
if not a.database.startswith('meet_') or 'contract' not in a.database:
    p.error('Use a disposable meet_*contract* database, never production')
root=pathlib.Path(__file__).resolve().parents[3]
folder=pathlib.Path(__file__).resolve().parent
migration=(root/'supabase/migrations/20260927001000_unified_service_transition_authority.sql').read_text()
migration=migration.removeprefix('begin;').removesuffix('\n').removesuffix('commit;')
sql=(folder/'unified_fixture.sql').read_text()+migration+(folder/'unified_authority_contract.sql').read_text()+'\nrollback;\n'
subprocess.run(['psql','-h','/tmp','-p','5432','-d',a.database,'-v','ON_ERROR_STOP=1'],input=sql,text=True,check=True)
