-- Geospatial authority for nearby tow, mechanic and universal-service search.
-- Supabase recommends keeping PostGIS objects outside the exposed public schema.

create extension if not exists postgis with schema extensions;
