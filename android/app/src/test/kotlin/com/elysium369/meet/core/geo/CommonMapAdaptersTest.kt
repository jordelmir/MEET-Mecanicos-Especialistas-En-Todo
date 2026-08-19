package com.elysium369.meet.core.geo

import com.elysium369.meet.core.geo.adapters.PartsMapAdapter
import com.elysium369.meet.core.geo.adapters.RepairMapAdapter
import com.elysium369.meet.core.geo.adapters.RideMapAdapter
import com.elysium369.meet.core.geo.adapters.TowMapAdapter
import com.elysium369.meet.core.geo.adapters.UniversalServiceMapAdapter
import com.elysium369.meet.ride.map.RideGeoPoint
import com.elysium369.meet.ride.map.RideMapMarker
import com.elysium369.meet.ride.map.RideMapState
import com.elysium369.meet.ride.map.RideMarkerRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonMapAdaptersTest {

    @Test
    fun testRideMapAdapterConversion() {
        val rideState = RideMapState(
            markers = listOf(
                RideMapMarker(
                    id = "p1",
                    role = RideMarkerRole.PASSENGER_GPS,
                    point = RideGeoPoint(9.9333, -84.0833, 5f, 1000L),
                    label = "Pasajero",
                ),
                RideMapMarker(
                    id = "d1",
                    role = RideMarkerRole.DRIVER,
                    point = RideGeoPoint(9.9350, -84.0850, 4f, 1000L),
                    label = "Conductor",
                ),
            ),
            route = listOf(
                RideGeoPoint(9.9333, -84.0833, null, 1000L),
                RideGeoPoint(9.9350, -84.0850, null, 1000L),
            ),
        )

        val commonState = RideMapAdapter.toCommonState(rideState)
        assertEquals(2, commonState.markers.size)
        assertEquals(1, commonState.routes.size)
        assertEquals(GeoMarkerRole.USER_LOCATION, commonState.marker(GeoMarkerRole.USER_LOCATION)?.role)
        assertEquals(GeoMarkerRole.PROVIDER_LIVE, commonState.marker(GeoMarkerRole.PROVIDER_LIVE)?.role)
    }

    @Test
    fun testRepairMapAdapter() {
        val state = RepairMapAdapter.buildMapState(
            userPoint = GeoPoint(9.93, -84.08),
            vehiclePoint = GeoPoint(9.94, -84.09),
            mechanicPoint = GeoPoint(9.95, -84.10),
            workshopPoint = null,
            mechanicName = "Carlos Pro",
        )
        assertEquals(3, state.markers.size)
        assertNotNull(state.marker(GeoMarkerRole.VEHICLE_ORIGIN))
        assertEquals(true, state.marker(GeoMarkerRole.VEHICLE_ORIGIN)?.isHighlighted)
    }

    @Test
    fun testTowMapAdapter() {
        val state = TowMapAdapter.buildMapState(
            vehicleOrigin = GeoPoint(9.93, -84.08),
            towTruckPoint = GeoPoint(9.94, -84.09),
            destinationPoint = GeoPoint(9.98, -84.15),
            driverName = "Grúas Atlas #04",
        )
        assertEquals(3, state.markers.size)
        assertNotNull(state.marker(GeoMarkerRole.TOW_TRUCK))
    }

    @Test
    fun testPartsMapAdapter() {
        val state = PartsMapAdapter.buildMapState(
            storePoint = GeoPoint(9.93, -84.08),
            deliveryCourierPoint = GeoPoint(9.94, -84.09),
            customerDestination = GeoPoint(9.95, -84.10),
            storeName = "Repuestos Central",
        )
        assertEquals(3, state.markers.size)
        assertNotNull(state.marker(GeoMarkerRole.STORE_LOCATION))
    }

    @Test
    fun testUniversalServiceMapAdapter() {
        val state = UniversalServiceMapAdapter.buildMapState(
            clientPoint = GeoPoint(9.93, -84.08),
            providerPoint = GeoPoint(9.94, -84.09),
            serviceName = "Cerrajería Vanguard",
        )
        assertEquals(2, state.markers.size)
    }
}
