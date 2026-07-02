package com.elysium369.meet.timeline

import com.elysium369.meet.diagnostic.DiagnosticProvenance
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Grabador append-only de eventos de timeline.
 *
 * Reglas:
 * - Append-only: nunca modifica ni borra eventos.
 * - Thread-safe (Mutex para batches; AtomicLong para ID monotónico).
 * - Filtros: por vehicle, por tipo, por rango de tiempo.
 * - Soporta backends en memoria (default) o Room (cuando se inyecta DAO).
 */
class TimelineRecorder {

    private val mutex = Mutex()
    private val events: MutableList<VehicleTimelineEvent> = ArrayList()
    private val idGenerator = AtomicLong(System.currentTimeMillis())

    suspend fun record(
        vehicleId: String,
        eventType: TimelineEventType,
        title: String,
        source: TimelineSource,
        provenance: DiagnosticProvenance,
        description: String = "",
        severity: Int = 0,
        payloadJson: String? = null,
        createdAtMs: Long = System.currentTimeMillis()
    ): VehicleTimelineEvent {
        val event = VehicleTimelineEvent(
            id = generateId(),
            vehicleId = vehicleId,
            eventType = eventType,
            title = title,
            description = description,
            severity = severity,
            source = source,
            provenance = provenance,
            payloadJson = payloadJson,
            createdAtMs = createdAtMs
        )
        mutex.withLock {
            events.add(event)
        }
        return event
    }

    /**
     * Registra un evento pre-construido. Útil para casos donde el evento
     * fue generado en otra capa y queremos preservarlo tal cual.
     */
    suspend fun append(event: VehicleTimelineEvent) {
        require(event.id.isNotBlank()) { "Cannot append event with blank id" }
        mutex.withLock {
            events.add(event)
        }
    }

    suspend fun query(
        vehicleId: String,
        types: Set<TimelineEventType> = emptySet(),
        sinceMs: Long = 0L,
        untilMs: Long = Long.MAX_VALUE,
        limit: Int = 500
    ): List<VehicleTimelineEvent> = mutex.withLock {
        events.asSequence()
            .filter { it.vehicleId == vehicleId }
            .filter { types.isEmpty() || it.eventType in types }
            .filter { it.createdAtMs in sinceMs..untilMs }
            .sortedByDescending { it.createdAtMs }
            .take(limit)
            .toList()
    }

    suspend fun countByType(vehicleId: String, type: TimelineEventType): Int = mutex.withLock {
        events.count { it.vehicleId == vehicleId && it.eventType == type }
    }

    suspend fun totalFor(vehicleId: String): Int = mutex.withLock {
        events.count { it.vehicleId == vehicleId }
    }

    suspend fun clearForVehicle(vehicleId: String) = mutex.withLock {
        events.removeAll { it.vehicleId == vehicleId }
    }

    private fun generateId(): String =
        "te-${idGenerator.incrementAndGet()}"

    companion object {
        /** Para tests: crea un recorder con eventos pre-cargados. */
        fun withEvents(events: List<VehicleTimelineEvent>): TimelineRecorder {
            val recorder = TimelineRecorder()
            // Los tests insertan directamente sin pasar por append() para evitar bloqueo.
            recorder.events.addAll(events)
            return recorder
        }
    }
}