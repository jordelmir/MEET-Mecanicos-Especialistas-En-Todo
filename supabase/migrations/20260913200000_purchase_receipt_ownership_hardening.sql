-- Migration: 20260913200000_purchase_receipt_ownership_hardening.sql
-- P0 audit item: google_play_purchase_receipts.user_id nullable = ownership gap.
-- Table has 0 rows in production. Edge function always provides user_id from JWT.
-- NOT NULL prevents unauthenticated receipt injection.

ALTER TABLE public.google_play_purchase_receipts
    ALTER COLUMN user_id SET NOT NULL;
