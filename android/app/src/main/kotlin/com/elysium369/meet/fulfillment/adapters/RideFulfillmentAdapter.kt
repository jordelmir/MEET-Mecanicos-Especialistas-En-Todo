package com.elysium369.meet.fulfillment.adapters

import com.elysium369.meet.core.geo.GeoMarker
import com.elysium369.meet.core.geo.GeoMarkerRole
import com.elysium369.meet.core.geo.GeoPoint
import com.elysium369.meet.core.geo.GeoRoute
import com.elysium369.meet.core.geo.CommonMapState
import com.elysium369.meet.core.services.kernel.CurrencyCode
import com.elysium369.meet.core.services.kernel.Money
import com.elysium369.meet.core.services.kernel.ProviderType
import com.elysium369.meet.core.services.kernel.ServiceVertical
import com.elysium369.meet.fulfillment.domain.*
import com.elysium369.meet.ride.domain.RideState
import com.elysium369.meet.ui.screens.ride.ActiveRideViewState
import java.util.UUID

object RideFulfillmentAdapter : FulfillmentPresentationAdapter<ActiveRideViewState> {

    override fun toFulfillmentProjection(source: ActiveRideViewState): FulfillmentProjection {
        val phase: FulfillmentPhase = when (source.state) {
            RideState.DRAFT, RideState.SEARCHING, RideState.OFFERED -> FulfillmentPhase.Searching
            RideState.ASSIGNED -> FulfillmentPhase.Matched
            RideState.DRIVER_EN_ROUTE -> FulfillmentPhase.ProviderEnRoute
            RideState.ARRIVED -> FulfillmentPhase.ProviderArrived
            RideState.PASSENGER_ONBOARD, RideState.IN_PROGRESS -> FulfillmentPhase.InProgress
            RideState.COMPLETED -> FulfillmentPhase.Completed
            RideState.CANCELLED -> FulfillmentPhase.Cancelled("Cancelado")
            RideState.EXPIRED -> FulfillmentPhase.Failed("Tiempo de espera agotado")
            RideState.DISPUTED -> FulfillmentPhase.Disputed("En disputa")
        }

        val providerInfo = source.driver?.let { drv ->
            FulfillmentProviderInfo(
                id = drv.driverId,
                name = drv.name,
                rating = drv.rating,
                totalJobs = drv.totalTrips,
                avatarUrl = drv.photoUrl,
                phone = drv.phone,
                vehicleDescription = drv.vehicle,
                licensePlate = drv.plate,
                providerType = ProviderType.RIDE_DRIVER,
                etaMinutes = drv.etaMinutes,
                distanceMeters = drv.distanceMeters?.toLong(),
                currentPoint = source.driverLocation?.let { GeoPoint(it.latitude, it.longitude) }
            )
        }

        val currency = CurrencyCode.fromStringOrNull(source.fareQuote.currency)
            ?: throw IllegalArgumentException("Unknown or unsupported currency: ${source.fareQuote.currency}")
        val pricing = FulfillmentPricing.Quote(
            amount = Money(source.fareQuote.totalFare, currency),
            breakdown = listOf(
                PricingItem("Tarifa base", Money(source.fareQuote.baseFare, currency)),
                PricingItem("Distancia (${source.fareQuote.estimatedDistanceKm} km)", Money(source.fareQuote.distanceFare, currency)),
                PricingItem("Tiempo (${source.fareQuote.estimatedDurationMin} min)", Money(source.fareQuote.timeFare, currency))
            )
        )

        val markers = mutableListOf<GeoMarker>()
        markers.add(
            GeoMarker(
                id = "pickup",
                role = GeoMarkerRole.USER_LOCATION,
                point = GeoPoint(source.pickup.latitude, source.pickup.longitude),
                label = source.pickup.displayName,
                isHighlighted = true
            )
        )
        source.dropoff?.let {
            markers.add(
                GeoMarker(
                    id = "dropoff",
                    role = GeoMarkerRole.DESTINATION,
                    point = GeoPoint(it.latitude, it.longitude),
                    label = it.displayName
                )
            )
        }
        source.driverLocation?.let {
            markers.add(
                GeoMarker(
                    id = "driver",
                    role = GeoMarkerRole.PROVIDER_LIVE,
                    point = GeoPoint(it.latitude, it.longitude),
                    label = source.driver?.name ?: "Conductor"
                )
            )
        }

        // Straight lines are not road routes; routes remain empty until navigation engine provides a polyline
        val mapState = CommonMapState(markers = markers, routes = emptyList())

        val aggregateId = runCatching { UUID.fromString(source.rideId) }.getOrElse { UUID.nameUUIDFromBytes(source.rideId.toByteArray()) }

        return FulfillmentProjection(
            reference = FulfillmentReference(
                vertical = ServiceVertical.RIDE,
                aggregateId = aggregateId
            ),
            mode = FulfillmentMode.ON_DEMAND_MOBILE,
            phase = phase,
            vertical = ServiceVertical.RIDE,
            serviceName = "Movilidad y Viajes",
            serviceDescription = "Transporte de personas puerta a puerta",
            userLocation = GeoPoint(source.pickup.latitude, source.pickup.longitude),
            targetLocation = GeoPoint(source.pickup.latitude, source.pickup.longitude),
            destinationLocation = source.dropoff?.let { GeoPoint(it.latitude, it.longitude) },
            provider = providerInfo,
            pricing = pricing,
            timeline = emptyList(),
            evidenceSnapshots = emptyList(),
            mapState = mapState,
            canCancel = source.state in setOf(RideState.DRAFT, RideState.SEARCHING, RideState.OFFERED, RideState.ASSIGNED, RideState.DRIVER_EN_ROUTE),
            canMessage = source.driver != null,
            canCall = source.driver?.phone != null,
            canPTT = false // Not enabled until floor lease is verified
        )
    }
}
