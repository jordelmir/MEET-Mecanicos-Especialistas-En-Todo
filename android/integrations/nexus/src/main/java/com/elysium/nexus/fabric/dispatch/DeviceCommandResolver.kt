package com.elysium.nexus.fabric.dispatch

import android.content.Context
import android.util.Log
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.UniversalAction
import com.elysium.nexus.fabric.infrared.IrProbeEngine
import com.elysium.nexus.fabric.infrared.database.IrCatalogRepository
import com.elysium.nexus.fabric.profile.InstalledIrProfileRepository

private const val TAG = "ElysiumNexus.DeviceCmdResolver"

/**
 * §4.7 Sealed resolution result — never returns null on missing profile.
 */
sealed interface CommandResolution {
    data class Resolved(
        val profileId: String,
        val codeSetId: String,
        val action: IrAction,
        val signalId: String,
        val physicalSha256: String,
        val signal: IrSignal
    ) : CommandResolution

    data class ProfileMissing(val profileId: String) : CommandResolution

    data class ActionNotInProfile(val profileId: String, val action: IrAction) : CommandResolution

    data class SignalMissing(val signalId: String, val profileId: String) : CommandResolution

    data class FingerprintMismatch(
        val signalId: String,
        val expected: String,
        val actual: String
    ) : CommandResolution
}

/**
 * §4.7 IR command resolution port. Implemented by [DeviceCommandResolver]
 * (Room + SQLite authoritative). Injectable so the dispatcher is testable
 * on JVM without an Android [Context].
 */
fun interface IrCommandResolver {
    suspend fun resolve(deviceId: DeviceId, action: UniversalAction): CommandResolution
}

/**
 * §4.7 Authoritative Device Command Resolver.
 *
 * Resolves [UniversalAction] and [DeviceId] to exact [CommandResolution.Resolved]
 * carrying database [signalId] and physical [IrSignal] loaded from SQLite/Room.
 * Zero manufactured signals. Zero brand fallbacks. Fail-closed on missing profile.
 */
class DeviceCommandResolver(
    private val context: Context
) : IrCommandResolver {
    private val profileRepository = InstalledIrProfileRepository(context)
    private val catalogRepository = IrCatalogRepository.getInstance(context)

    /**
     * §4.7 Resolve action to exact IR command.
     * NEVER falls back to first profile. Returns sealed [CommandResolution].
     * Verifies physical fingerprint before returning.
     */
    override suspend fun resolve(
        deviceId: DeviceId,
        action: UniversalAction
    ): CommandResolution {
        val profileId = deviceId.value.removePrefix("ir-")

        // §4.7 FAIL CLOSED: no fallback to first profile
        val profile = profileRepository.getProfileSuspend(profileId)
            ?: run {
                com.elysium.nexus.fabric.infrared.FileLog.d("RESOLVE_MISSING profile=$profileId")
                return CommandResolution.ProfileMissing(profileId)
            }

        val irAction = mapUniversalActionToIrAction(action)
            ?: return CommandResolution.ActionNotInProfile(profileId, IrAction.POWER_TOGGLE)

        val binding = profile.commands[irAction]
            ?: return CommandResolution.ActionNotInProfile(profileId, irAction)

        val signal = catalogRepository.resolveExecutableSignal(binding.signalId)
            ?: return CommandResolution.SignalMissing(binding.signalId, profileId)

        // §21 Fingerprint verification at domain layer
        val actualFingerprint = IrProbeEngine.fingerprintSignal(signal)
        com.elysium.nexus.fabric.infrared.FileLog.d("RESOLVE profile=$profileId action=$irAction signal=${binding.signalId} codeSet=${profile.codeSetId}")
        if (actualFingerprint != binding.physicalFingerprint) {
            com.elysium.nexus.fabric.infrared.FileLog.d("RESOLVE_FP_MISMATCH profile=$profileId expected=${binding.physicalFingerprint} actual=$actualFingerprint")
            Log.e(TAG, "FINGERPRINT MISMATCH profile=$profileId action=$irAction signalId=${binding.signalId}: expected=${binding.physicalFingerprint}, actual=$actualFingerprint")
            return CommandResolution.FingerprintMismatch(
                signalId = binding.signalId,
                expected = binding.physicalFingerprint,
                actual = actualFingerprint
            )
        }

        return CommandResolution.Resolved(
            profileId = profile.id,
            codeSetId = profile.codeSetId,
            action = irAction,
            signalId = binding.signalId,
            physicalSha256 = binding.physicalFingerprint,
            signal = signal
        )
    }

    private fun mapUniversalActionToIrAction(action: UniversalAction): IrAction? = when (action) {
        is UniversalAction.PowerOn -> IrAction.POWER_ON
        is UniversalAction.PowerOff -> IrAction.POWER_OFF
        is UniversalAction.PowerToggle -> IrAction.POWER_TOGGLE
        is UniversalAction.VolumeUp -> IrAction.VOLUME_UP
        is UniversalAction.VolumeDown -> IrAction.VOLUME_DOWN
        is UniversalAction.Mute -> IrAction.MUTE
        is UniversalAction.ChannelUp -> IrAction.CHANNEL_UP
        is UniversalAction.ChannelDown -> IrAction.CHANNEL_DOWN
        is UniversalAction.Ok -> IrAction.OK
        is UniversalAction.Back -> IrAction.BACK
        is UniversalAction.Home -> IrAction.HOME
        is UniversalAction.Menu -> IrAction.MENU
        is UniversalAction.MediaPlay -> IrAction.PLAY
        is UniversalAction.MediaPause -> IrAction.PAUSE
        is UniversalAction.MediaStop -> IrAction.STOP
        else -> null
    }
}
