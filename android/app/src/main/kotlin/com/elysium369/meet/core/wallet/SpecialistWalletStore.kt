package com.elysium369.meet.core.wallet

import android.content.Context
import androidx.core.content.edit
import com.elysium369.meet.ride.data.remote.PlatformTrustCenterGateway
import com.elysium369.meet.ride.data.remote.RideWalletTopup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * World-Class Specialist Wallet Store.
 * Provides unified balance management across ALL services:
 * Viajes, Grúa, Mecánicos, Repuestos, and Servicios Elysium.
 *
 * Core Guarantees:
 * Wallet amounts are a cached projection only. The server ledger is the sole
 * authority for credit, commission and completed-job balances.
 */

@Serializable
data class SpecialistTopup(
    val id: String,
    val specialistId: String,
    val serviceVertical: String,
    val amountCrc: Double,
    val referenceNumber: String,
    val senderPhoneOrName: String? = null,
    val proofLocalPath: String? = null,
    val status: String = "PENDING_REVIEW", // PENDING_REVIEW, APPROVED, REJECTED
    val submittedAtEpochMs: Long = System.currentTimeMillis(),
    val decisionReason: String? = null,
)

@Serializable
data class SpecialistWalletState(
    val specialistId: String,
    val serviceVertical: String,
    val balanceCrc: Double = 0.0,
    val starterGiftCrc: Double = 0.0,
    val commissionPercent: Double = 0.0,
    val totalEarningsCrc: Double = 0.0,
    val totalCommissionsPaidCrc: Double = 0.0,
    val completedJobsCount: Int = 0,
    val topups: List<SpecialistTopup> = emptyList(),
)

data class JobCommissionSplit(
    val grossCrc: Double,
    val commissionRate: Double = 0.0,
    val platformFeeCrc: Double = grossCrc * commissionRate,
    val specialistNetCrc: Double = grossCrc - platformFeeCrc,
)

object SpecialistWalletStore {
    const val SINPE_PHONE = "63194029"
    const val SINPE_RECIPIENT_NAME = "Jorge David Del Valle Miranda"
    const val SINPE_EMAIL = "jordelmir@gmail.com"

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val memoryFlows = mutableMapOf<String, MutableStateFlow<SpecialistWalletState>>()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Get or create a reactive StateFlow for a specialist wallet.
     */
    fun getWalletFlow(
        context: Context,
        specialistId: String,
        serviceVertical: String = "ALL",
    ): StateFlow<SpecialistWalletState> {
        val key = "${specialistId}_$serviceVertical"
        synchronized(memoryFlows) {
            return memoryFlows.getOrPut(key) {
                val initialState = loadState(context, specialistId, serviceVertical)
                MutableStateFlow(initialState)
            }.asStateFlow()
        }
    }

    /**
     * Synchronize the local projection with the authoritative Supabase ledger.
     */
    fun syncWithTrustCenter(context: Context, specialistId: String, serviceVertical: String = "ALL") {
        scope.launch {
            runCatching {
                PlatformTrustCenterGateway.ensureStarterCredit()
                val remoteBal = PlatformTrustCenterGateway.walletBalance()
                val remoteTopups = PlatformTrustCenterGateway.loadOwnWalletTopups()

                val current = getWalletFlow(context, specialistId, serviceVertical).value
                val combinedBalance = remoteBal.availableMinor.toDouble()

                val mappedRemoteTopups = remoteTopups.map { rt ->
                    SpecialistTopup(
                        id = rt.id,
                        specialistId = specialistId,
                        serviceVertical = serviceVertical,
                        amountCrc = rt.amountMinor.toDouble(),
                        referenceNumber = rt.transferReference ?: "SINPE-${rt.id.take(8)}",
                        senderPhoneOrName = rt.senderPhone,
                        status = rt.status,
                        decisionReason = rt.decisionReason,
                    )
                }

                val mergedTopups = (current.topups + mappedRemoteTopups)
                    .distinctBy { it.id }
                    .sortedByDescending { it.submittedAtEpochMs }

                updateState(
                    context,
                    current.copy(
                        balanceCrc = combinedBalance,
                        topups = mergedTopups,
                    )
                )
            }
        }
    }

    /**
     * Submit a new SINPE Top-Up for Trust Center approval.
     */
    suspend fun submitTopup(
        context: Context,
        specialistId: String,
        serviceVertical: String,
        amountCrc: Double,
        referenceNumber: String,
        senderPhoneOrName: String?,
        proofFile: File?,
    ): SpecialistTopup {
        val topupId = UUID.randomUUID().toString()
        val topup = SpecialistTopup(
            id = topupId,
            specialistId = specialistId,
            serviceVertical = serviceVertical,
            amountCrc = amountCrc,
            referenceNumber = referenceNumber.ifBlank { "SINPE-${System.currentTimeMillis().toString().takeLast(6)}" },
            senderPhoneOrName = senderPhoneOrName,
            proofLocalPath = proofFile?.absolutePath,
            status = "PENDING_REVIEW",
            submittedAtEpochMs = System.currentTimeMillis(),
        )

        // 1. Persist in local specialist wallet
        val current = getWalletFlow(context, specialistId, serviceVertical).value
        val updatedTopups = listOf(topup) + current.topups.filterNot { it.id == topup.id }
        updateState(context, current.copy(topups = updatedTopups))

        // 2. Also record into global pending topups queue for Trust Center review
        recordInGlobalTrustQueue(context, topup)

        // 3. Attempt Supabase upload if available
        if (proofFile != null && proofFile.exists()) {
            runCatching {
                PlatformTrustCenterGateway.submitWalletTopup(
                    localProof = proofFile.absolutePath,
                    amountMinor = amountCrc.toLong(),
                    senderPhone = senderPhoneOrName,
                    transferReference = referenceNumber,
                )
            }
        }

        return topup
    }

    /**
     * Returns a display-only split. It never changes a wallet balance; the
     * authoritative service ledger must project the completed job first.
     */
    fun recordCompletedJob(
        context: Context,
        specialistId: String,
        serviceVertical: String,
        grossCrc: Double,
    ): JobCommissionSplit {
        val split = JobCommissionSplit(grossCrc = grossCrc)
        return split
    }

    /**
     * Trust Center Decision (Owner validates the real bank transfer).
     */
    fun decideTopup(
        context: Context,
        topupId: String,
        approved: Boolean,
        decisionReason: String,
    ) {
        val pendingList = getAllGlobalTopups(context)
        val target = pendingList.firstOrNull { it.id == topupId } ?: return

        val newStatus = if (approved) "APPROVED" else "REJECTED"
        val updatedTarget = target.copy(
            status = newStatus,
            decisionReason = decisionReason,
        )

        // Update in global queue
        val updatedGlobal = pendingList.map { if (it.id == topupId) updatedTarget else it }
        saveGlobalTopups(context, updatedGlobal)

        // Update in specific specialist wallet
        val specWallet = getWalletFlow(context, target.specialistId, target.serviceVertical).value
        val updatedTopups = specWallet.topups.map { if (it.id == topupId) updatedTarget else it }
        updateState(
            context,
            specWallet.copy(
                topups = updatedTopups,
            )
        )
    }

    /**
     * Retrieve all topups across all specialists for Trust Center display.
     */
    fun getAllGlobalTopups(context: Context): List<SpecialistTopup> {
        val prefs = context.getSharedPreferences("meet_trust_center_global_topups", Context.MODE_PRIVATE)
        val raw = prefs.getString("topups_json", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SpecialistTopup>>(raw) }.getOrDefault(emptyList())
    }

    fun getPendingGlobalTopups(context: Context): List<SpecialistTopup> {
        return getAllGlobalTopups(context).filter { it.status == "PENDING_REVIEW" }
    }

    fun mapToRideWalletTopups(specialistTopups: List<SpecialistTopup>): List<RideWalletTopup> {
        return specialistTopups.map { st ->
            RideWalletTopup(
                id = st.id,
                driverId = "${st.serviceVertical}:${st.specialistId}",
                amountMinor = st.amountCrc.toLong(),
                currency = "CRC",
                senderPhone = st.senderPhoneOrName,
                transferReference = st.referenceNumber,
                proofStoragePath = st.proofLocalPath ?: "local/voucher-${st.id.take(8)}.jpg",
                status = st.status,
                submittedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(st.submittedAtEpochMs)),
                decisionReason = st.decisionReason,
            )
        }
    }

    private fun loadState(context: Context, specialistId: String, serviceVertical: String): SpecialistWalletState {
        val prefs = context.getSharedPreferences("elysium_wallet_${specialistId}_$serviceVertical", Context.MODE_PRIVATE)
        val raw = prefs.getString("wallet_json", null)
        if (raw != null) {
            val parsed = runCatching { json.decodeFromString<SpecialistWalletState>(raw) }.getOrNull()
            if (parsed != null) {
                return parsed
            }
        }
        // A new local cache has no money until the authoritative ledger arrives.
        return SpecialistWalletState(
            specialistId = specialistId,
            serviceVertical = serviceVertical,
        )
    }

    private fun updateState(context: Context, state: SpecialistWalletState) {
        val key = "${state.specialistId}_${state.serviceVertical}"
        synchronized(memoryFlows) {
            val flow = memoryFlows.getOrPut(key) { MutableStateFlow(state) }
            flow.value = state
        }
        val prefs = context.getSharedPreferences("elysium_wallet_${state.specialistId}_${state.serviceVertical}", Context.MODE_PRIVATE)
        prefs.edit {
            putString("wallet_json", json.encodeToString(state))
        }
    }

    private fun recordInGlobalTrustQueue(context: Context, topup: SpecialistTopup) {
        val existing = getAllGlobalTopups(context)
        val updated = (listOf(topup) + existing.filterNot { it.id == topup.id })
            .sortedByDescending { it.submittedAtEpochMs }
        saveGlobalTopups(context, updated)
    }

    private fun saveGlobalTopups(context: Context, list: List<SpecialistTopup>) {
        val prefs = context.getSharedPreferences("meet_trust_center_global_topups", Context.MODE_PRIVATE)
        prefs.edit {
            putString("topups_json", json.encodeToString(list))
        }
    }
}
