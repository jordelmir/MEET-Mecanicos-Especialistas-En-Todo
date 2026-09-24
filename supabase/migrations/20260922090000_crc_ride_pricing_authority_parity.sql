-- Migration: 20260922090000_crc_ride_pricing_authority_parity.sql
-- Purpose: retire the legacy x100 CRC STD_RIDE seed and make the currently
-- enforced 300 CRC/km + 60 CRC/min contract explicit in the canonical policy
-- table. This does not activate the proposed 900/350/80 pilot rate card.
-- PRICING_PARITY_RATE_CARD=CR_GAM|STD_RIDE|2|CRC|0|300|60|0|0|500

UPDATE public.mobility_pricing_policies
SET active = FALSE,
    valid_until = COALESCE(valid_until, clock_timestamp())
WHERE market_id = 'CR_GAM'
  AND service_category_id = 'STD_RIDE'
  AND active = TRUE;

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
    2,
    'CRC',
    0,
    300,
    1000,
    60,
    60,
    0,
    0,
    0,
    0,
    10000,
    TRUE
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
    surge_max_basis_points = EXCLUDED.surge_max_basis_points,
    valid_from = clock_timestamp(),
    valid_until = NULL,
    active = TRUE;
