package com.elysium369.meet.core.copilot

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EventBus — Centralized reactive bus for vehicle events.
 * Enables decoupling between RuleEngine (producers) and Speech/Notification services (consumers).
 */
@Singleton
class EventBus @Inject constructor() {
    private val _events = MutableSharedFlow<CopilotEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun publish(event: CopilotEvent) {
        _events.tryEmit(event)
    }
}
