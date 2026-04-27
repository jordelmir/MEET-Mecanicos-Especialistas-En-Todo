import { createClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://kluumjhzncitjayvvwtj.supabase.co';
const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtsdXVtamh6bmNpdGpheXZ2d3RqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzcyMzI4NzUsImV4cCI6MjA5MjgwODg3NX0.0GnwAhTBTk93EcM3mxYdgI0j4pUM0O-Squ0N6b7N7MA';

export const supabase = createClient(supabaseUrl, supabaseKey);
