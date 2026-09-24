-- Migration: 20260923020000_seed_mobility_pricing_pilot_v3.sql
-- Description: PR-3: Seed Pilot Rate Card V3 for Costa Rica GAM
-- (Base ₡900, Distancia ₡350/km, Tiempo ₡80/min, Mínima ₡1200, Comisión 5%).
-- Note: Staged with active = FALSE so canonical v2 remains active for general requests,
-- while pilot rides requesting rate_card_version = 3 resolve authoritatively to V3.

INSERT INTO public.mobility_pricing_policies (
    market_id,
    service_category_id,
    version,
    currency_code,
    base_fare_minor,
    per_meter_numerator,
    per_meter_denominator,
    per_second_numerator,
    per_second_denominator,
    minimum_fare_minor,
    booking_fee_minor,
    cancellation_fee_minor,
    tax_basis_points,
    surge_max_basis_points,
    active
) VALUES (
    'CR_GAM',
    'STD_RIDE',
    3,
    'CRC',
    900,
    350,
    1000,
    80,
    60,
    1200,
    0,
    900,
    0,
    10000,
    FALSE
)
ON CONFLICT (market_id, service_category_id, version) DO UPDATE SET
    currency_code = EXCLUDED.currency_code,
    base_fare_minor = EXCLUDED.base_fare_minor,
    per_meter_numerator = EXCLUDED.per_meter_numerator,
    per_meter_denominator = EXCLUDED.per_meter_denominator,
    per_second_numerator = EXCLUDED.per_second_numerator,
    per_second_denominator = EXCLUDED.per_second_denominator,
    minimum_fare_minor = EXCLUDED.minimum_fare_minor,
    booking_fee_minor = EXCLUDED.booking_fee_minor,
    cancellation_fee_minor = EXCLUDED.cancellation_fee_minor,
    tax_basis_points = EXCLUDED.tax_basis_points,
    surge_max_basis_points = EXCLUDED.surge_max_basis_points;
