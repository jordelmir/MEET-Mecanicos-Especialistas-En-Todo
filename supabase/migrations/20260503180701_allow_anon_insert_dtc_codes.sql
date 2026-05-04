CREATE POLICY "Enable insert for anon role" ON public.dtc_codes FOR INSERT TO anon WITH CHECK (true);
CREATE POLICY "Enable update for anon role" ON public.dtc_codes FOR UPDATE TO anon USING (true) WITH CHECK (true);
