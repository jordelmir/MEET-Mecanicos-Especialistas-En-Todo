package com.elysium369.meet.platform.marketos.data

import android.content.Context
import androidx.room.withTransaction
import com.elysium369.meet.data.local.MeetDatabase
import com.elysium369.meet.data.local.dao.MarketOsDao
import com.elysium369.meet.data.local.entities.FuelCouponProjectionEntity
import com.elysium369.meet.data.local.entities.LegalMatterProjectionEntity
import com.elysium369.meet.data.local.entities.MarketCommandOutboxEntity
import com.elysium369.meet.data.local.entities.MarketOrganizationProjectionEntity
import com.elysium369.meet.data.local.entities.PropertyListingProjectionEntity
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.platform.marketos.MarketCommandEnvelope
import com.elysium369.meet.platform.marketos.work.MarketOsSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonObject

sealed interface MarketEnqueueResult {
    data class Queued(val idempotencyKey: String) : MarketEnqueueResult
    data object AuthenticationRequired : MarketEnqueueResult
    data object Duplicate : MarketEnqueueResult
    data class Rejected(val reason: String) : MarketEnqueueResult
}

sealed interface MarketRefreshResult {
    data class Refreshed(val rowCount: Int) : MarketRefreshResult
    data object AuthenticationRequired : MarketRefreshResult
    data class Failed(val reason: String) : MarketRefreshResult
}

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MarketOsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MeetDatabase,
    private val dao: MarketOsDao,
    private val principalKernel: ActivePrincipalKernel,
    private val remoteGateway: MarketOsRemoteGateway,
) {
    val organizations: Flow<List<MarketOrganizationProjectionEntity>> = principalKernel.activePrincipal
        .flatMapLatest { dao.observeOrganizations(it.id) }

    val legalMatters: Flow<List<LegalMatterProjectionEntity>> = principalKernel.activePrincipal
        .flatMapLatest { dao.observeLegalMatters(it.id) }

    val propertyListings: Flow<List<PropertyListingProjectionEntity>> = principalKernel.activePrincipal
        .flatMapLatest { dao.observePropertyListings(it.id) }

    val fuelCoupons: Flow<List<FuelCouponProjectionEntity>> = principalKernel.activePrincipal
        .flatMapLatest { dao.observeFuelCoupons(it.id) }

    val pendingCommands: Flow<Int> = principalKernel.activePrincipal
        .flatMapLatest { principal ->
            if (principal.id.isBlank()) flowOf(0) else dao.observePendingCommandCount(principal.id)
        }

    suspend fun enqueue(
        aggregateType: String,
        aggregateId: String = UUID.randomUUID().toString(),
        commandType: String,
        expectedVersion: Long,
        payload: JsonObject,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): MarketEnqueueResult {
        val principal = principalKernel.current()
        if (!principal.canSyncToCloud) return MarketEnqueueResult.AuthenticationRequired
        val actorId = runCatching { UUID.fromString(principal.id) }.getOrElse {
            return MarketEnqueueResult.Rejected("INVALID_ACTIVE_PRINCIPAL")
        }
        val aggregateUuid = runCatching { UUID.fromString(aggregateId) }.getOrElse {
            return MarketEnqueueResult.Rejected("INVALID_AGGREGATE_ID")
        }
        val idempotencyUuid = runCatching { UUID.fromString(idempotencyKey) }.getOrElse {
            return MarketEnqueueResult.Rejected("INVALID_IDEMPOTENCY_KEY")
        }
        val now = System.currentTimeMillis()
        val envelope = MarketCommandEnvelope(
            commandId = UUID.randomUUID(),
            aggregateId = aggregateUuid,
            aggregateType = aggregateType,
            commandType = commandType,
            expectedVersion = expectedVersion,
            idempotencyKey = idempotencyUuid,
            actorPrincipalId = actorId,
            payloadCanonicalJson = payload.toString(),
            payloadVersion = 1,
            createdAtEpochMs = now,
        )
        val inserted = dao.enqueue(
            MarketCommandOutboxEntity(
                ownerPrincipalId = principal.id,
                idempotencyKey = envelope.idempotencyKey.toString(),
                commandId = envelope.commandId.toString(),
                aggregateId = envelope.aggregateId.toString(),
                aggregateType = envelope.aggregateType,
                commandType = envelope.commandType,
                expectedVersion = envelope.expectedVersion,
                canonicalDigest = envelope.canonicalDigest,
                payloadJson = envelope.payloadCanonicalJson,
                payloadVersion = envelope.payloadVersion,
                status = "PENDING",
                attemptCount = 0,
                nextAttemptAtEpochMs = now,
                lastErrorCode = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        if (inserted == -1L) return MarketEnqueueResult.Duplicate
        MarketOsSyncWorker.enqueueNow(context)
        return MarketEnqueueResult.Queued(idempotencyKey)
    }

    suspend fun refresh(): MarketRefreshResult {
        val principal = principalKernel.current()
        if (!principal.canSyncToCloud) return MarketRefreshResult.AuthenticationRequired
        return remoteGateway.fetchVisibleSnapshot(principal.id).fold(
            onSuccess = { snapshot ->
                database.withTransaction {
                    dao.replaceOrganizationProjection(principal.id, snapshot.organizations)
                    dao.replaceLegalProjection(principal.id, snapshot.legalMatters)
                    dao.replacePropertyProjection(principal.id, snapshot.propertyListings)
                    dao.replaceFuelProjection(principal.id, snapshot.fuelCoupons)
                }
                MarketRefreshResult.Refreshed(
                    snapshot.organizations.size + snapshot.legalMatters.size +
                        snapshot.propertyListings.size + snapshot.fuelCoupons.size,
                )
            },
            onFailure = { error ->
                MarketRefreshResult.Failed((error.message ?: "REMOTE_REFRESH_FAILED").take(240))
            },
        )
    }

    fun realtimeWakeUps(): Flow<Unit> = remoteGateway.realtimeWakeUps()

    suspend fun fetchCatalog(vertical: String): Result<List<MarketCatalogCategory>> =
        remoteGateway.fetchCatalog(vertical)
}
