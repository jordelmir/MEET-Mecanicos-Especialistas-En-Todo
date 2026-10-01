package com.elysium.nexus.core.motion

import com.elysium.nexus.core.model.MotionState
import kotlinx.coroutines.flow.Flow

/**
 * The §14 motion / IMU source.
 *
 * `MASTER_ORDER.md` §14 says the project shall
 * implement "gyro aim, air mouse, steering, tilt,
 * motion cursor, motion gestures, calibration, bias
 * estimation, drift indication, recenter,
 * sensibilidad por eje, inversión". The full §14
 * feature set is a Phase 4+ deliverable; Phase 1.4
 * ships the **first slice**: a sensor source that
 * emits a `MotionState` per IMU sample.
 *
 * The interface is **stateless**: each call to
 * [latest] returns the most recent sample (or
 * `null` if no sample has been received yet). The
 * production implementation
 * [AndroidMotionSensorSource] registers a
 * `SensorManager` listener at construction and
 * unregisters at [close]. The pipeline is a thin
 * layer that maps raw `SensorEvent` data to
 * `MotionState`; the consumer (the engine) decides
 * how to integrate the rates.
 *
 * ## Why an interface and not a concrete class
 *
 * The interface is the test surface. A unit test
 * can use a `FakeMotionSensorSource` that emits
 * a deterministic sequence of samples; the
 * production class is the Android adapter. The
 * agent-memory rule applies: the testable layer
 * (the interface) is the public API, the Android
 * impl is the implementation.
 *
 * ## Why `Flow<MotionState>` and not a callback
 *
 * The motion pipeline is a *stream* of samples,
 * not a request-response API. `Flow` is the
 * idiomatic Kotlin abstraction for a stream of
 * values. The consumer (the activity) collects
 * the flow and forwards each sample to the engine.
 * The `Flow` is cold — the actual `SensorManager`
 * listener is registered on the first `collect` and
 * unregistered when the collector's scope is
 * cancelled.
 */
interface MotionSensorSource : AutoCloseable {
    /**
     * A cold flow of motion samples. The flow
     * completes when [close] is called. Each
     * sample carries the gyro rates, accelerometer
     * readings, and the relative orientation
     * (roll / pitch / yaw) since the last
     * `recenter` event.
     */
    fun samples(): Flow<MotionState>

    /**
     * @return the most recent motion sample, or
     * `null` if no sample has been received yet.
     * The call is non-blocking; the value is read
     * from a single `volatile` field.
     */
    fun latest(): MotionState?

    /**
     * Reset the orientation reference. After a
     * recenter, the roll / pitch / yaw of the next
     * sample are reported as **relative to this
     * moment**. The §14 "recenter" feature is the
     * user-driven action (e.g. a button on the
     * controller surface) that compensates for
     * sensor drift.
     */
    fun recenter()

    /**
     * Release the underlying sensor listener. After
     * `close`, [samples] completes and [latest]
     * returns `null`.
     */
    override fun close()
}

/**
 * A no-op source for unit tests and previews. The
 * flow never emits; [latest] always returns `null`;
 * [recenter] is a no-op.
 */
class NullMotionSensorSource : MotionSensorSource {
    override fun samples(): Flow<MotionState> = kotlinx.coroutines.flow.emptyFlow()
    override fun latest(): MotionState? = null
    override fun recenter() { /* no-op */ }
    override fun close() { /* no-op */ }
}
