package com.elysium369.meet.core.realtime

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    DEGRADED,
    RECONNECTING,
}

@Singleton
class ElysiumRealtimeClient @Inject constructor() {

    private val _connectionState = MutableSharedFlow<RealtimeConnectionState>(replay = 1)
    val connectionState: SharedFlow<RealtimeConnectionState> = _connectionState.asSharedFlow()

    private val _events = MutableSharedFlow<RealtimeEventEnvelope>(extraBufferCapacity = 256)
    val events: SharedFlow<RealtimeEventEnvelope> = _events.asSharedFlow()

    private val activeSubscriptions = ConcurrentHashMap<String, Long>()

    init {
        _connectionState.tryEmit(RealtimeConnectionState.DISCONNECTED)
    }

    fun subscribe(channel: String, lastCursor: Long? = null) {
        activeSubscriptions[channel] = lastCursor ?: 0L
        Log.i("ElysiumRealtime", "Subscribed to channel: $channel at cursor ${lastCursor ?: 0}")
    }

    fun unsubscribe(channel: String) {
        activeSubscriptions.remove(channel)
        Log.i("ElysiumRealtime", "Unsubscribed from channel: $channel")
    }

    suspend fun publishEventForTest(event: RealtimeEventEnvelope) {
        _events.emit(event)
    }

    fun getActiveSubscriptionCount(): Int = activeSubscriptions.size
}
