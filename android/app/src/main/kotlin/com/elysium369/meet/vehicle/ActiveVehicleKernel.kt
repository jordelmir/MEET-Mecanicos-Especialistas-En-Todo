package com.elysium369.meet.vehicle

import android.content.Context
import android.util.Log
import com.elysium369.meet.data.local.dao.ActiveVehicleSelectionDao
import com.elysium369.meet.data.local.entities.ActiveVehicleSelectionEntity
import com.elysium369.meet.data.supabase.Vehicle
import com.elysium369.meet.data.supabase.VehicleRepository
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.observability.MeetTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveVehicleChangeReason {
    USER_SELECTED,
    USER_CREATED,
    VERIFIED_ECU_BINDING,
    VEHICLE_DELETED,
    ACCESS_REVOKED,
    OWNER_CHANGED,
    RESTORED,
    LEGACY_MIGRATION,
}

/** Application-scoped authority for the user's active vehicle intent. */
@Singleton
class ActiveVehicleKernel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val principalKernel: ActivePrincipalKernel,
    private val vehicleRepository: VehicleRepository,
    private val selectionDao: ActiveVehicleSelectionDao,
    private val applicationScope: CoroutineScope,
) {
    private val mutableActiveVehicle = MutableStateFlow<Vehicle?>(null)
    val activeVehicle: StateFlow<Vehicle?> = mutableActiveVehicle.asStateFlow()

    init {
        applicationScope.launch {
            principalKernel.activePrincipal
                .map { it.id }
                .distinctUntilChanged()
                .collect(::restoreForOwner)
        }
    }

    fun select(vehicle: Vehicle, reason: ActiveVehicleChangeReason) {
        val ownerId = principalKernel.current().id
        require(vehicle.user_id == ownerId) { "Active vehicle must belong to the active principal" }
        val previousVehicleId = mutableActiveVehicle.value?.id
        mutableActiveVehicle.value = vehicle
        record(previousVehicleId, vehicle.id, reason, resultCode = "SELECTED")
        applicationScope.launch(Dispatchers.IO) {
            selectionDao.upsert(
                ActiveVehicleSelectionEntity(
                    ownerPrincipalId = ownerId,
                    vehicleId = vehicle.id,
                    reason = reason.name,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateIfActive(vehicle: Vehicle) {
        if (mutableActiveVehicle.value?.id == vehicle.id) mutableActiveVehicle.value = vehicle
    }

    fun clearIfDeleted(vehicle: Vehicle) {
        val ownerId = principalKernel.current().id
        if (mutableActiveVehicle.value?.id != vehicle.id) return
        mutableActiveVehicle.value = null
        record(vehicle.id, null, ActiveVehicleChangeReason.VEHICLE_DELETED, resultCode = "CLEARED")
        applicationScope.launch(Dispatchers.IO) {
            selectionDao.deleteIfSelected(ownerId, vehicle.id)
        }
    }

    private suspend fun restoreForOwner(ownerId: String) {
        val previousOwner = mutableActiveVehicle.value?.user_id
        if (previousOwner != null && previousOwner != ownerId) {
            val previousVehicleId = mutableActiveVehicle.value?.id
            mutableActiveVehicle.value = null
            record(previousVehicleId, null, ActiveVehicleChangeReason.OWNER_CHANGED, resultCode = "OWNER_BOUNDARY")
        }

        val durable = withContext(Dispatchers.IO) { selectionDao.get(ownerId) }
        val legacyId = context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
            .getString("selected_vehicle_id", null)
        val candidateId = durable?.vehicleId ?: legacyId
        if (candidateId == null) return

        val vehicle = withContext(Dispatchers.IO) {
            vehicleRepository.getVehicleById(ownerId, candidateId)
        }
        if (vehicle == null) {
            // Local absence can be temporary while cloud reconciliation runs.
            // Preserve the durable pointer and never auto-select a replacement.
            Log.w(TAG, "Active vehicle unavailable locally; durable intent retained")
            record(null, candidateId, ActiveVehicleChangeReason.RESTORED, resultCode = "LOCAL_ABSENCE")
            return
        }

        mutableActiveVehicle.value = vehicle
        val reason = if (durable == null) {
            selectionDao.upsert(
                ActiveVehicleSelectionEntity(
                    ownerPrincipalId = ownerId,
                    vehicleId = vehicle.id,
                    reason = ActiveVehicleChangeReason.LEGACY_MIGRATION.name,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            context.getSharedPreferences("meet_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("selected_vehicle_id")
                .apply()
            ActiveVehicleChangeReason.LEGACY_MIGRATION
        } else {
            ActiveVehicleChangeReason.RESTORED
        }
        record(null, vehicle.id, reason, resultCode = "RESTORED")
    }

    private fun record(
        previousVehicleId: String?,
        newVehicleId: String?,
        reason: ActiveVehicleChangeReason,
        resultCode: String,
    ) {
        Log.i(
            TAG,
            "active vehicle transition previous=${previousVehicleId ?: "NONE"} " +
                "new=${newVehicleId ?: "NONE"} reason=${reason.name} result=$resultCode",
        )
        MeetTelemetry.event(
            name = "vehicle.active.transition",
            attributes = mapOf(
                "previousVehicleId" to (previousVehicleId ?: "NONE"),
                "newVehicleId" to (newVehicleId ?: "NONE"),
                "operation" to reason.name,
                "resultCode" to resultCode,
            ),
        )
    }

    private companion object { const val TAG = "ActiveVehicleKernel" }
}
