package dragoncore.security

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import java.security.MessageDigest

/** Keeps local DragonCalc notebooks and user-provided keys within the active account. */
object DragonAccountScope {
    fun principalId(): String? = SupabaseModule.client.auth.currentUserOrNull()?.id

    fun storageKey(): String = storageKeyFor(principalId())

    fun storageKeyFor(principal: String?): String {
        if (principal == null) return "guest"
        val digest = MessageDigest.getInstance("SHA-256").digest(principal.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
