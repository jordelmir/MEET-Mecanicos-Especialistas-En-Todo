#!/usr/bin/env node

import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

interface PricingFixture {
  label: string;
  marketId: string;
  serviceCategoryId: string;
  rateCardVersion: number;
  currency: string;
  currencyDecimalPlaces: number;
  baseFareMinor: number;
  distanceRateMinorPerKm: number;
  timeRateMinorPerMinute: number;
  minimumFareMinor: number;
  bookingFeeMinor: number;
  platformCommissionBasisPoints: number;
}

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const fixturePath = process.argv[2] ?? 'tests/parity/fixtures/crc-ride-pricing-v2.json';
const fixture = JSON.parse(readFileSync(resolve(repoRoot, fixturePath), 'utf8')) as PricingFixture;

function source(path: string): string {
  return readFileSync(resolve(repoRoot, path), 'utf8');
}

function expectMatch(text: string, pattern: RegExp, description: string): RegExpMatchArray {
  const match = text.match(pattern);
  if (!match) throw new Error(`Pricing parity violation: ${description}`);
  return match;
}

function expectEqual(actual: string | number, expected: string | number, description: string): void {
  if (actual !== expected) {
    throw new Error(`Pricing parity violation: ${description}; expected=${expected}, actual=${actual}`);
  }
}

const currencySource = source('android/app/src/main/kotlin/com/elysium369/meet/core/money/CurrencyCode.kt');
const fareEngineSource = source('android/app/src/main/kotlin/com/elysium369/meet/ride/domain/RideFareEngine.kt');
const migrationsDir = resolve(repoRoot, 'supabase/migrations');
const v3Migrations = readdirSync(migrationsDir)
  .filter((name) => name.endsWith('.sql'))
  .map((name) => ({ name, sql: source(`supabase/migrations/${name}`) }))
  .filter(({ sql }) => sql.includes('create or replace function public.ride_create_request_v3('))
  .sort((a, b) => a.name.localeCompare(b.name));

if (v3Migrations.length === 0) throw new Error('Pricing parity violation: ride_create_request_v3 is missing');
const currentRideCreate = v3Migrations.at(-1)!;
const policyMigration = source('supabase/migrations/20260922090000_crc_ride_pricing_authority_parity.sql');
const commissionMigration = source('supabase/migrations/20260920110000_ride_authority_and_completion_audit.sql');

const crcDecimals = Number(expectMatch(currencySource, /CRC\("₡",\s*(\d+)\)/, 'CRC decimalPlaces is missing')[1]);
const androidDistance = Number(expectMatch(fareEngineSource, /CRC_DISTANCE_RATE_MINOR_PER_KM\s*=\s*(\d+)L/, 'Android distance rate is missing')[1]);
const androidTime = Number(expectMatch(fareEngineSource, /CRC_TIME_RATE_MINOR_PER_MINUTE\s*=\s*(\d+)L/, 'Android time rate is missing')[1]);
const serverDistance = Number(expectMatch(currentRideCreate.sql, /p_distance_rate_minor_per_km\s*<>\s*(\d+)/, 'server distance-rate guard is missing')[1]);
const serverTime = Number(expectMatch(currentRideCreate.sql, /p_time_rate_minor_per_minute\s*<>\s*(\d+)/, 'server time-rate guard is missing')[1]);

expectEqual(crcDecimals, fixture.currencyDecimalPlaces, 'CRC decimalPlaces drifted');
expectEqual(androidDistance, fixture.distanceRateMinorPerKm, 'Android distance rate drifted');
expectEqual(androidTime, fixture.timeRateMinorPerMinute, 'Android time rate drifted');
expectEqual(serverDistance, fixture.distanceRateMinorPerKm, `${currentRideCreate.name} distance rate drifted`);
expectEqual(serverTime, fixture.timeRateMinorPerMinute, `${currentRideCreate.name} time rate drifted`);

const policyMarker = [
  fixture.marketId,
  fixture.serviceCategoryId,
  fixture.rateCardVersion,
  fixture.currency,
  fixture.baseFareMinor,
  fixture.distanceRateMinorPerKm,
  fixture.timeRateMinorPerMinute,
  fixture.minimumFareMinor,
  fixture.bookingFeeMinor,
  fixture.platformCommissionBasisPoints,
].join('|');
expectMatch(policyMigration, new RegExp(`PRICING_PARITY_RATE_CARD=${policyMarker.replaceAll('|', '\\|')}`), 'canonical SQL rate-card marker drifted');
expectMatch(policyMigration, /UPDATE public\.mobility_pricing_policies[\s\S]*active = FALSE[\s\S]*market_id = 'CR_GAM'[\s\S]*service_category_id = 'STD_RIDE'/, 'legacy active STD_RIDE policies are not retired');
const normalizedPolicySql = policyMigration.replace(/\s+/g, ' ');
expectMatch(
  normalizedPolicySql,
  /VALUES \( 'CR_GAM', 'STD_RIDE', 2, 'CRC', 0, 300, 1000, 60, 60, 0, 0, 0, 0, 10000, TRUE \)/,
  'canonical SQL rate-card values drifted',
);
expectMatch(commissionMigration, new RegExp(`commission_basis_points',\\s*${fixture.platformCommissionBasisPoints}`), 'server commission drifted from canonical 5%');

const canonical = `currency=${fixture.currency};decimalPlaces=${crcDecimals};base=${fixture.baseFareMinor};distancePerKm=${androidDistance};timePerMinute=${androidTime};commissionBps=${fixture.platformCommissionBasisPoints};rateCardVersion=${fixture.rateCardVersion}`;
console.log(`[OK] ${fixture.label}`);
console.log(`  canonical: ${canonical}`);
