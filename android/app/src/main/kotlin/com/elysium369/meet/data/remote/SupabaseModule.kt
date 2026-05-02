package com.elysium369.meet.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseModule {
    /**
     * ═══════════════════════════════════════════════════════════
     * Supabase credentials loaded from BuildConfig at compile time.
     * Set MEET_SUPABASE_URL and MEET_SUPABASE_KEY in local.properties
     * or as environment variables for CI/CD builds.
     * ═══════════════════════════════════════════════════════════
     */
    val SUPABASE_URL: String = com.elysium369.meet.BuildConfig.SUPABASE_URL
    val SUPABASE_KEY: String = com.elysium369.meet.BuildConfig.SUPABASE_KEY

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Auth)
        install(Storage)
    }
}
