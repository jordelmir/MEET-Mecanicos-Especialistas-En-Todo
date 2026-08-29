package com.elysium369.meet.data.remote

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

object SupabaseModule {
    const val PASSWORD_RECOVERY_REDIRECT: String =
        com.elysium369.meet.ui.screens.AuthRedirectPolicy.RECOVERY_REDIRECT_URL

    /**
     * ═══════════════════════════════════════════════════════════
     * Supabase credentials loaded from BuildConfig at compile time.
     * Set ELYSIUM_SUPABASE_URL and ELYSIUM_SUPABASE_KEY in local.properties
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
        install(Auth) {
            scheme = com.elysium369.meet.ui.screens.AuthRedirectPolicy.SCHEME
            host = com.elysium369.meet.ui.screens.AuthRedirectPolicy.HOST
        }
        install(Storage)
        install(Realtime)
    }
}
