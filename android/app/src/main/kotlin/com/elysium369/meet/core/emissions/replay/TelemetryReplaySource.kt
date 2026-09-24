package com.elysium369.meet.core.emissions.replay

import com.elysium369.meet.core.emissions.domain.EmissionFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow

interface TelemetryReplaySource {
    val totalFrames: Int
    fun streamFrames(playbackSpeedMultiplier: Double = 1.0): Flow<EmissionFrame>
}

class InMemoryTelemetryReplaySource(
    private val frames: List<EmissionFrame>
) : TelemetryReplaySource {
    override val totalFrames: Int get() = frames.size

    override fun streamFrames(playbackSpeedMultiplier: Double): Flow<EmissionFrame> = flow {
        for (frame in frames) {
            emit(frame)
        }
    }
}
