package com.elysium369.meet.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseModule {
    const val SUPABASE_URL = "https://kluumjhzncitjayvvwtj.supabase.co"
    const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtsdXVtamh6bmNpdGpheXZ2d3RqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzcyMzI4NzUsImV4cCI6MjA5MjgwODg3NX0.0GnwAhTBTk93EcM3mxYdgI0j4pUM0O-Squ0N6b7N7MA"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Auth)
        install(Storage)
    }
}
