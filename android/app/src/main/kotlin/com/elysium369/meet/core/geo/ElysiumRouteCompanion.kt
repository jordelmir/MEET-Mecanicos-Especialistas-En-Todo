package com.elysium369.meet.core.geo

/**
 * ══════════════════════════════════════════════════════════════════════
 *  E L Y S I U M   R O U T E   C O M P A N I O N   ( L A Y A )
 *  ──────────────────────────────────────────────────────────────
 *  Copiloto inteligente de navegación y encuentro seguro:
 *  - Detección de proximidad y alerta de llegada inminente (< 3 min).
 *  - Recomendación de zonas de refugio y espera segura (Safe Staging Zones)
 *    usando la base de datos de Costa Rica (gasolineras con cámaras,
 *    supermercados con parqueo, hospitales).
 *  - Protocolo táctico de seguridad vial para conductor y cliente.
 * ══════════════════════════════════════════════════════════════════════
 */
object ElysiumRouteCompanion {

    data class SafetyStagingZone(
        val place: CostaRicaGisDatabase.GisPlace,
        val distanceKm: Double,
        val recommendationReason: String,
    )

    data class RouteCompanionState(
        val currentEtaMinutes: Int,
        val distanceKm: Double,
        val trafficLevel: LayaRouteEngine.TrafficLevel,
        val isArrivingSoon: Boolean,
        val safeStagingZones: List<SafetyStagingZone>,
        val safetyChecklist: List<String>,
        val advice: String,
    )

    /**
     * Evalúa el estado situacional del trayecto en tiempo real.
     */
    fun evaluateCompanionState(
        currentLatitude: Double,
        currentLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        isClientWaiting: Boolean = true,
    ): RouteCompanionState {
        val origin = GeoPoint(currentLatitude, currentLongitude)
        val dest = GeoPoint(destinationLatitude, destinationLongitude)
        val routeEstimate = LayaRouteEngine.calculateRoute(origin, dest)

        // Encuentra zonas seguras de espera cercanas (Gasolineras, Supermercados, Hospitales)
        val safePlaces = CostaRicaGisDatabase.nearest(
            latitude = currentLatitude,
            longitude = currentLongitude,
            limit = 3
        ).map { (place, dist) ->
            val reason = when (place.category) {
                CostaRicaGisDatabase.GisCategory.GAS_STATION -> "Estación iluminada con cámaras y asistencia 24/7."
                CostaRicaGisDatabase.GisCategory.SUPERMARKET -> "Parqueo seguro y alto tránsito público visible."
                CostaRicaGisDatabase.GisCategory.MALL -> "Centro comercial con vigilancia privada activa."
                CostaRicaGisDatabase.GisCategory.HOSPITAL_CLINIC -> "Zona hospitalaria con seguridad institucional."
                CostaRicaGisDatabase.GisCategory.POLICE_FIRE_EMERGENCY -> "Delegación o estación oficial de auxilio con protección institucional 24/7."
                else -> "Punto de referencia urbano seguro y concurrido."
            }
            SafetyStagingZone(
                place = place,
                distanceKm = kotlin.math.round(dist * 10.0) / 10.0,
                recommendationReason = reason
            )
        }

        val isArrivingSoon = routeEstimate.etaMinutes <= 3

        val checklist = if (isClientWaiting) {
            listOf(
                "Enciende luces de emergencia (intermitentes)",
                "Mantén los seguros de las puertas cerrados",
                "Verifica que el especialista te confirme el PIN Aura Sentinel",
                "No abordes vehículos sin placa coincidente en el reporte"
            )
        } else {
            listOf(
                "Portar chaleco reflectivo de seguridad vial",
                "Colocar triángulos reflectivos a 30m del vehículo",
                "Solicitar al cliente su PIN de verificación antes de intervenir",
                "Verificar suelo y derrame de fluidos antes de elevar con gata/grúa"
            )
        }

        val advice = if (isArrivingSoon) {
            "⚡ ¡LLEGADA INMINENTE! Especialista a menos de 3 minutos. Ten a mano tu PIN de seguridad."
        } else {
            routeEstimate.tacticalAdvice
        }

        return RouteCompanionState(
            currentEtaMinutes = routeEstimate.etaMinutes,
            distanceKm = routeEstimate.distanceKm,
            trafficLevel = routeEstimate.trafficLevel,
            isArrivingSoon = isArrivingSoon,
            safeStagingZones = safePlaces,
            safetyChecklist = checklist,
            advice = advice,
        )
    }
}
