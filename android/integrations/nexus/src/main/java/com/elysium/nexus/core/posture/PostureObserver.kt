package com.elysium.nexus.core.posture

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The §16 foldable posture observer.
 *
 * `MASTER_ORDER.md` §16 calls for the project to
 * support foldable postures (open, half-folded,
 * tabletop) and the cover screen. The posture is
 * the orientation of the device's hinge relative
 * to the rest of the chassis; the editor's layout
 * adapts to the posture (e.g. a tabletop posture
 * uses the top half for the dashboard and the
 * bottom half for the controls).
 *
 * The interface is the testable surface. A unit
 * test uses a `FakePostureObserver` that emits a
 * deterministic sequence; the production
 * implementation [AndroidPostureObserver] wraps
 * `androidx.window.layout.FoldingFeature` from
 * Jetpack WindowManager.
 *
 * ## Why an interface and not a class
 *
 * `androidx.window.layout.WindowInfoTracker` is
 * the Jetpack API. It is Android-specific. The
 * interface decouples the editor's posture logic
 * from the Android runtime; the activity wires
 * the production class, the JVM tests use the
 * fake.
 *
 * ## Why `Flow<Posture>` and not a callback
 *
 * The posture can change at any time (the user
 * folds / unfolds the device). `Flow` is the
 * idiomatic Kotlin abstraction for a stream of
 * values; the consumer (the editor) collects the
 * flow and recomposes on each new posture.
 */
interface PostureObserver {
    /**
     * A cold flow of posture changes. The flow
     * completes when [close] is called. The first
     * emission is the *current* posture; subsequent
     * emissions are the new posture after each
     * change.
     */
    fun postures(): Flow<Posture>

    /**
     * @return the current posture, or `null` if no
     * posture has been observed yet. The call is
     * non-blocking; the value is read from a
     * single `volatile` field.
     */
    fun current(): Posture?

    /**
     * Release the underlying observer. After
     * [close], [postures] completes and [current]
     * returns `null`.
     */
    fun close()
}

/**
 * The closed set of foldable postures the editor
 * cares about.
 *
 * The values mirror the §16 spec:
 *  - [CLOSED]: the device is folded shut (e.g. a
 *    closed clamshell). The cover screen is the
 *    primary surface.
 *  - [OPEN]: the device is fully unfolded. The
 *    main screen is the primary surface.
 *  - [HALF_OPENED]: the device is partially
 *    unfolded (e.g. a laptop posture or a
 *    tabletop posture). The editor may split the
 *    content across the hinge.
 *  - [FLAT]: the device is fully unfolded and
 *    lying flat (180° hinge angle). Treated as
 *    a separate case from [OPEN] so the editor
 *    can use the full surface for a tabletop
 *    layout.
 *  - [UNKNOWN]: the posture cannot be determined
 *    (e.g. a non-foldable device, or a foldable
 *    device in an unrecognised state).
 *
 * The enum is JVM-testeable; the production
 * mapping from `FoldingFeature.State` to [Posture]
 * lives in the Android adapter.
 */
enum class Posture {
    CLOSED,
    OPEN,
    HALF_OPENED,
    FLAT,
    UNKNOWN
}

/**
 * A no-op source for unit tests. The flow never
 * emits; [current] always returns `null`.
 */
class NullPostureObserver : PostureObserver {
    override fun postures(): Flow<Posture> = flowOf()
    override fun current(): Posture? = null
    override fun close() { /* no-op */ }
}
