package com.elysium369.meet.fulfillment.adapters

import com.elysium369.meet.core.geo.adapters.TowMapAdapter
import com.elysium369.meet.core.services.kernel.ProviderType
import com.elysium369.meet.core.services.kernel.ServiceVertical
import com.elysium369.meet.core.services.tow.TowCustodyCheckpoint
import com.elysium369.meet.core.services.tow.TowJob
import com.elysium369.meet.core.services.tow.TowState
import com.elysium369.meet.fulfillment.domain.*

object TowFulfillmentAdapter : FulfillmentPresentationAdapter<TowJob> {

    override fun toFulfillmentProjection(source: TowJob): FulfillmentProjection {
        val phase: FulfillmentPhase = when (source.state) {
            TowState.REQUESTED -> FulfillmentPhase.Configuring
            TowState.MATCHING -> FulfillmentPhase.Searching
            TowState.ASSIGNED -> FulfillmentPhase.Matched
            TowState.EN_ROUTE -> FulfillmentPhase.ProviderEnRoute
            TowState.ARRIVED -> FulfillmentPhase.ProviderArrived
            TowState.LOADING, TowState.LOADED, TowState.IN_TRANSIT,
            TowState.ARRIVED_DESTINATION, TowState.UNLOADING -> FulfillmentPhase.InProgress
            TowState.DELIVERED -> FulfillmentPhase.Completing
            TowState.COMPLETED -> FulfillmentPhase.Completed
            TowState.CANCELLED -> FulfillmentPhase.Cancelled("Servicio cancelado")
            TowState.DISPUTED -> FulfillmentPhase.Disputed("En revisión por el equipo de soporte")
        }

        val providerInfo = source.assignedUnit?.let { unit ->
            FulfillmentProviderInfo(
                id = unit.towUnitId,
                name = source.assignedOperatorName ?: "Operador de Grúa",
                rating = source.assignedOperatorRating,
                totalJobs = source.assignedOperatorCompletedJobs,
                phone = source.assignedOperatorPhone,
                vehicleDescription = "${unit.brandModel} (${unit.capabilities.joinToString { it.displayName }})",
                licensePlate = unit.licensePlate,
                providerType = ProviderType.TOW_PROVIDER,
                etaMinutes = null,
                distanceMeters = null,
                currentPoint = source.operatorLocation
            )
        }

        val pricing = when {
            source.finalSettlement != null -> FulfillmentPricing.FinalSettlement(
                base = source.finalSettlement,
                extras = com.elysium369.meet.core.services.kernel.Money.zero(source.finalSettlement.currency),
                taxes = com.elysium369.meet.core.services.kernel.Money.zero(source.finalSettlement.currency),
                total = source.finalSettlement,
                ledgerAttestationHash = source.custodyRecords.lastOrNull()?.evidenceHash
            )
            source.authorizedPrice != null -> FulfillmentPricing.AuthorizedAmount(
                amount = source.authorizedPrice,
                authorizationId = "AUTH_${source.jobId}_v${source.serverVersion}"
            )
            source.quotedPrice != null -> FulfillmentPricing.Quote(
                amount = source.quotedPrice,
                breakdown = listOf(PricingItem("Servicio de Remolque", source.quotedPrice))
            )
            source.estimatedPrice != null -> FulfillmentPricing.EstimatedRange(
                min = source.estimatedPrice,
                max = source.estimatedPrice
            )
            else -> null
        }

        val isCancelledOrDisputed = source.state == TowState.CANCELLED || source.state == TowState.DISPUTED
        val assignedCompleted = !isCancelledOrDisputed && source.state in setOf(
            TowState.ASSIGNED, TowState.EN_ROUTE, TowState.ARRIVED, TowState.LOADING,
            TowState.LOADED, TowState.IN_TRANSIT, TowState.ARRIVED_DESTINATION,
            TowState.UNLOADING, TowState.DELIVERED, TowState.COMPLETED
        )
        val enRouteCompleted = !isCancelledOrDisputed && source.state in setOf(
            TowState.EN_ROUTE, TowState.ARRIVED, TowState.LOADING,
            TowState.LOADED, TowState.IN_TRANSIT, TowState.ARRIVED_DESTINATION,
            TowState.UNLOADING, TowState.DELIVERED, TowState.COMPLETED
        )
        val loadedCompleted = !isCancelledOrDisputed && source.state in setOf(
            TowState.LOADED, TowState.IN_TRANSIT, TowState.ARRIVED_DESTINATION,
            TowState.UNLOADING, TowState.DELIVERED, TowState.COMPLETED
        )
        val deliveredCompleted = !isCancelledOrDisputed && source.state in setOf(
            TowState.DELIVERED, TowState.COMPLETED
        )

        val timelineEvents = listOf(
            FulfillmentTimelineEvent(
                phase = "REQUESTED",
                title = "Solicitud Creada",
                description = "Vehículo: ${source.vehicleSummary}",
                timestampEpochMs = source.createdAtEpochMs,
                isCompleted = true,
                isCurrent = source.state == TowState.REQUESTED
            ),
            FulfillmentTimelineEvent(
                phase = "ASSIGNED",
                title = "Grúa Asignada",
                description = source.assignedOperatorName ?: "Esperando operador compatible",
                timestampEpochMs = source.updatedAtEpochMs,
                isCompleted = assignedCompleted,
                isCurrent = source.state == TowState.ASSIGNED
            ),
            FulfillmentTimelineEvent(
                phase = "EN_ROUTE",
                title = "En Camino al Vehículo",
                description = "Hacia ${source.pickupAddress}",
                timestampEpochMs = source.updatedAtEpochMs,
                isCompleted = enRouteCompleted,
                isCurrent = source.state == TowState.EN_ROUTE
            ),
            FulfillmentTimelineEvent(
                phase = "LOADED",
                title = "Vehículo Cargado y Asegurado",
                description = source.custodyRecords.firstOrNull { it.checkpoint == TowCustodyCheckpoint.LOADED_SECURED }?.evidenceHash ?: "Inspección y anclaje",
                timestampEpochMs = source.updatedAtEpochMs,
                isCompleted = loadedCompleted,
                isCurrent = source.state == TowState.LOADED || source.state == TowState.LOADING
            ),
            FulfillmentTimelineEvent(
                phase = "DELIVERED",
                title = "Entrega en Destino",
                description = source.destinationAddress ?: "Destino final",
                timestampEpochMs = source.updatedAtEpochMs,
                isCompleted = deliveredCompleted,
                isCurrent = source.state == TowState.DELIVERED || source.state == TowState.UNLOADING
            ),
            FulfillmentTimelineEvent(
                phase = "COMPLETED",
                title = "Servicio Finalizado",
                description = "Custodia completada",
                timestampEpochMs = source.updatedAtEpochMs,
                isCompleted = source.state == TowState.COMPLETED,
                isCurrent = source.state == TowState.COMPLETED
            )
        )

        val evidenceSnapshots = source.custodyRecords.map { rec ->
            FulfillmentEvidenceSnapshot(
                evidenceId = rec.canonicalEvidenceId?.toString() ?: "custody_${rec.checkpoint.name}_${rec.recordedAtEpochMs}",
                label = rec.checkpoint.displayName,
                sha256Hash = rec.evidenceHash,
                capturedAtEpochMs = rec.recordedAtEpochMs,
                verificationLevel = if (rec.canonicalEvidenceId != null) "SERVER_ATTESTED" else "UNVERIFIED"
            )
        }

        val mapState = TowMapAdapter.buildMapState(
            vehicleOrigin = source.pickupLocation,
            towTruckPoint = source.operatorLocation,
            destinationPoint = source.destinationLocation,
            driverName = source.assignedOperatorName
        )

        return FulfillmentProjection(
            reference = FulfillmentReference(
                vertical = ServiceVertical.TOW,
                aggregateId = source.jobId
            ),
            mode = FulfillmentMode.PICKUP_AND_DELIVERY,
            phase = phase,
            vertical = ServiceVertical.TOW,
            serviceName = "Auxilio Vial y Grúas",
            serviceDescription = "Remolque y rescate vehicular con custodia certificada",
            userLocation = source.pickupLocation,
            targetLocation = source.pickupLocation,
            destinationLocation = source.destinationLocation,
            provider = providerInfo,
            pricing = pricing,
            timeline = timelineEvents,
            evidenceSnapshots = evidenceSnapshots,
            mapState = mapState,
            canCancel = source.state in setOf(TowState.REQUESTED, TowState.MATCHING, TowState.ASSIGNED),
            canMessage = source.assignedOperatorName != null,
            canCall = source.assignedOperatorPhone != null,
            canPTT = source.assignedOperatorName != null
        )
    }
}
