package com.elysium369.meet.vehiclelife.timeline

import com.elysium369.meet.core.vehiclelife.VehicleLifeEvent
import com.elysium369.meet.core.vehiclelife.VehicleLifeEventType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class TimelineCategoryFilter(val title: String, val types: Set<VehicleLifeEventType>) {
    ALL("Todo", VehicleLifeEventType.entries.toSet()),
    DIAGNOSTICS("Diagnósticos & Fallas", setOf(VehicleLifeEventType.DIAGNOSTIC, VehicleLifeEventType.FINDING)),
    MAINTENANCE("Mantenimiento", setOf(VehicleLifeEventType.MAINTENANCE)),
    REPAIRS_PARTS("Reparaciones & Piezas", setOf(VehicleLifeEventType.REPAIR, VehicleLifeEventType.PART_INSTALLED, VehicleLifeEventType.PART_REMOVED)),
    INSPECTIONS("Inspecciones & Peritajes", setOf(VehicleLifeEventType.INSPECTION)),
    DOCUMENTS("Documentos & Guantera", setOf(VehicleLifeEventType.DOCUMENT, VehicleLifeEventType.OWNERSHIP)),
    ACCIDENTS("Incidentes", setOf(VehicleLifeEventType.ACCIDENT)),
    COSTS("Costos & Facturas", setOf(VehicleLifeEventType.COST)),
    ACCESS("Accesos & Llaves", setOf(VehicleLifeEventType.ACCESS))
}

interface VehicleTimelineRepository {
    val events: StateFlow<List<VehicleLifeEvent>>
    suspend fun recordEvent(event: VehicleLifeEvent)
    suspend fun getEventsForVehicle(vehicleId: String, filter: TimelineCategoryFilter = TimelineCategoryFilter.ALL): List<VehicleLifeEvent>
}

@Singleton
class DefaultVehicleTimelineRepository @Inject constructor() : VehicleTimelineRepository {
    private val _events = MutableStateFlow<List<VehicleLifeEvent>>(emptyList())
    override val events: StateFlow<List<VehicleLifeEvent>> = _events.asStateFlow()

    override suspend fun recordEvent(event: VehicleLifeEvent) {
        _events.value = listOf(event) + _events.value.filter { it.eventId != event.eventId }
    }

    override suspend fun getEventsForVehicle(
        vehicleId: String,
        filter: TimelineCategoryFilter
    ): List<VehicleLifeEvent> {
        return _events.value
            .filter { it.vehicleId == vehicleId && it.type in filter.types }
            .sortedByDescending { it.occurredAtUtc }
    }
}
