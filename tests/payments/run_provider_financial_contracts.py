#!/usr/bin/env python3
"""Local-only PostgreSQL contracts. Uses a fresh socket database; never remote credentials."""
import concurrent.futures
import pathlib
import subprocess
import uuid
root=pathlib.Path(__file__).resolve().parents[2]
db='meet_financial_contract_'+uuid.uuid4().hex[:12]
def command(*args):
    try:
        return subprocess.run(args,cwd=root,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
    except subprocess.CalledProcessError as error:
        print(error.stderr)
        raise
def sql(query):
    return command('psql','-h','/tmp','-v','ON_ERROR_STOP=1','-d',db,'-Atc',query).stdout.strip()
def file(path):
    command('psql','-h','/tmp','-v','ON_ERROR_STOP=1','-d',db,'-f',str(root/path))
command('createdb','-h','/tmp',db)
try:
    file('tests/payments/provider_financial_live_shape_fixture.sql')
    file('supabase/migrations/20260927125500_provider_trust_write_guard.sql')
    sql('create table universal_service_ratings(provider_id uuid,stars integer);')
    helper=(root/'supabase/migrations/20260927130000_commission_integer_authority.sql').read_text()
    helper=helper[helper.index('create or replace function public.elysium_commission_minor_v1'):helper.index('revoke all on function public.elysium_commission_minor_v1')]
    sql(helper)
    file('supabase/migrations/20260927212925_provider_global_financial_authority.sql')
    file('tests/payments/provider_financial_test_bootstrap.sql')
    file('tests/payments/provider_financial_authority_contract.sql')
    file('tests/payments/provider_trust_write_guard_contract.sql')
    # True concurrent grant attempts use a fifth principal with two verified roles.
    principal='00000000-0000-0000-0000-000000000005'
    sql(f"insert into auth.users values('{principal}'); insert into user_profiles(id,auth_user_id) values('{principal}','{principal}'); insert into provider_profiles(id,user_profile_id,is_active,is_verified,status,provider_type) values(gen_random_uuid(),'{principal}',true,true,'active','mechanic'),(gen_random_uuid(),'{principal}',true,true,'active','service_provider');")
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as executor:
        list(executor.map(lambda _:sql(f"select elysium_provider_wallet_ensure_v1('{principal}')"),range(100)))
    assert sql(f"select count(*)||':'||sum(amount_minor) from ride_wallet_ledger where driver_id='{principal}' and entry_type='PROMOTIONAL_GRANT'")=='1:5000'
    # Two independent transactions compete for precisely one 5000 commission.
    customer='00000000-0000-0000-0000-000000000001'
    request_ids=[str(uuid.uuid4()),str(uuid.uuid4())]
    offer_ids=[str(uuid.uuid4()),str(uuid.uuid4())]
    for request,offer in zip(request_ids,offer_ids):
        sql(f"insert into universal_service_requests(id,client_id,currency,state,intake,service_definition_id) values('{request}','{customer}','CRC','OPEN','{{\"payment_method\":\"CASH\"}}','mechanic'); insert into universal_service_offers(id,request_id,provider_id,price_minor,currency,state) values('{offer}','{request}','{principal}',100000,'CRC','PENDING');")
    def accept(pair):
        request,offer=pair
        try:
            sql(f"select set_config('request.jwt.claim.sub','{customer}',false); select universal_service_transition_v1('{request}','ACCEPT','{offer}');")
            return 'assigned'
        except subprocess.CalledProcessError as error:
            assert 'INSUFFICIENT_PROVIDER_COMMISSION_BALANCE' in error.stderr,error.stderr
            return 'insufficient'
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        assert sorted(executor.map(accept,zip(request_ids,offer_ids)))==['assigned','insufficient']
    assert sql(f"select elysium_provider_balance_v1('{principal}')->>'available_minor'")=='0'
    # Migration replay must neither grant again nor rewrite captured history.
    file('supabase/migrations/20260927212925_provider_global_financial_authority.sql')
    assert sql(f"select count(*)||':'||sum(amount_minor) from ride_wallet_ledger where driver_id='{principal}' and entry_type='PROMOTIONAL_GRANT'")=='1:5000'
    print('PASS: live-shaped authority SQL, 100 concurrent grants, multi-role uniqueness, historical 15000 preservation, external-payment customer, reserve/capture/release, capture retries, two competing accepts, fake SINPE/trust/acceptance-starter rejection, privileges, journal balance, migration replay.')
finally:
    command('dropdb','-h','/tmp',db)
