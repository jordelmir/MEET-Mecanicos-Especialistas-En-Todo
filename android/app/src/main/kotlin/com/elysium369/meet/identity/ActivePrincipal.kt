package com.elysium369.meet.identity

import android.content.Context
import android.provider.Settings
import com.elysium369.meet.data.remote.SupabaseModule
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ActivePrincipal private constructor(
    val id: String,
    val isAuthenticated: Boolean,
) {
    val canSyncToCloud: Boolean
        get() = isAuthenticated && id != OfflineOwnership.OWNER_UNKNOWN_LEGACY

    companion object {
        fun authenticated(userId: String): ActivePrincipal {
            require(userId.isNotBlank()) { "Authenticated principal id cannot be blank" }
            return ActivePrincipal(userId, isAuthenticated = true)
        }

        fun local(deviceId: String): ActivePrincipal {
            require(deviceId.isNotBlank()) { "Local device id cannot be blank" }
            return ActivePrincipal("local_device_$deviceId", isAuthenticated = false)
        }

        fun legacyUnknown(): ActivePrincipal =
            ActivePrincipal(OfflineOwnership.OWNER_UNKNOWN_LEGACY, isAuthenticated = false)
    }
}

object OfflineOwnership {
    const val OWNER_UNKNOWN_LEGACY = "OWNER_UNKNOWN_LEGACY"
    const val PERSONAL_TENANT = "PERSONAL"

    fun canSync(ownerPrincipalId: String, activePrincipal: ActivePrincipal): Boolean =
        activePrincipal.canSyncToCloud &&
            ownerPrincipalId == activePrincipal.id &&
            ownerPrincipalId != OWNER_UNKNOWN_LEGACY &&
            !ownerPrincipalId.startsWith("local_device_")
}

@Singleton
class ActivePrincipalKernel @Inject constructor(
    @ApplicationContext context: Context,
    scope: CoroutineScope,
) {
    val localDeviceId: String = stableDeviceId(context)
    private val localPrincipal = ActivePrincipal.local(localDeviceId)

    val activePrincipal: StateFlow<ActivePrincipal> =
        SupabaseModule.client.auth.sessionStatus
            .map { status -> status.toPrincipal(localPrincipal) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, currentOrLocal(localPrincipal))

    fun current(): ActivePrincipal = activePrincipal.value

    private fun currentOrLocal(fallback: ActivePrincipal): ActivePrincipal =
        SupabaseModule.client.auth.currentUserOrNull()?.id
            ?.takeIf(String::isNotBlank)
            ?.let(ActivePrincipal::authenticated)
            ?: fallback

    private fun SessionStatus.toPrincipal(fallback: ActivePrincipal): ActivePrincipal = when (this) {
        is SessionStatus.Authenticated -> session.user?.id
            ?.takeIf(String::isNotBlank)
            ?.let(ActivePrincipal::authenticated)
            ?: currentOrLocal(fallback)
        is SessionStatus.NotAuthenticated -> fallback
        SessionStatus.LoadingFromStorage,
        SessionStatus.NetworkError -> currentOrLocal(fallback)
    }

    private companion object {
        fun stableDeviceId(context: Context): String {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            )?.takeIf(String::isNotBlank)
            if (androidId != null) return androidId

            val preferences = context.getSharedPreferences("meet_identity", Context.MODE_PRIVATE)
            return preferences.getString("local_principal_device_id", null)
                ?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString().also {
                    preferences.edit().putString("local_principal_device_id", it).apply()
                }
        }
    }
}
