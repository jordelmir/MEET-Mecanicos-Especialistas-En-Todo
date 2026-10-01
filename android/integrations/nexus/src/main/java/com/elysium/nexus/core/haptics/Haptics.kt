package com.elysium.nexus.core.haptics

/**
 * The §27 haptics abstraction.
 *
 * `MASTER_ORDER.md` §27 says the project shall
 * support "Haptics locales" — local haptic
 * feedback for button presses, stick limits,
 * trigger clicks, errors, connection events,
 * profile changes, recentering. Phase 1.6 ships
 * the abstraction; the Android adapter (which
 * uses the Android `Vibrator` API and the
 * `HapticFeedbackConstants` constants) lands in
 * the same phase.
 *
 * ## Why an interface, not a class
 *
 * Per the agent-memory rule, the haptics
 * abstraction is split into a testable interface
 * [Haptics] and the Android adapter
 * [AndroidHaptics]. The interface is a
 * no-side-effect API (it fires an event); the
 * Android adapter translates the event into
 * a `Vibrator` call. A unit test uses a
 * [FakeHaptics] that records the events.
 *
 * ## Why "tap" / "long press" / "error" rather
 * than "duration" + "amplitude"
 *
 * The §27 spec describes haptics in terms of
 * *user-visible events*, not low-level
 * parameters. "Button tap" is what the editor's
 * UX cares about; the Android adapter maps it
 * to a specific `Vibrator` call (e.g. a 20ms
 * vibration with default amplitude). The
 * interface is the semantic layer; the adapter
 * is the implementation layer. A future haptic
 * device (e.g. the Nexus Receiver's rumble
 * motors) can implement [Haptics] without
 * changing the editor.
 */
interface Haptics {
    /**
     * Fire a haptic event. The implementation
     * is responsible for mapping the [HapticEvent]
     * to a platform-specific call.
     */
    fun fire(event: HapticEvent)
}

/**
 * The closed set of haptic events the project
 * emits. Each event maps to a platform-specific
 * vibration (Android: `Vibrator`; future:
 * Nexus Receiver's motors).
 */
sealed class HapticEvent {
    /** A button was tapped (press + release). */
    object ButtonTap : HapticEvent()
    /** A button was long-pressed. */
    object ButtonLongPress : HapticEvent()
    /** A stick reached the edge of its range. */
    object StickEdge : HapticEvent()
    /** A trigger reached the digital click point. */
    object TriggerClick : HapticEvent()
    /** An error occurred (e.g. invalid binding). */
    object Error : HapticEvent()
    /** The transport connected. */
    object TransportConnected : HapticEvent()
    /** The transport disconnected. */
    object TransportDisconnected : HapticEvent()
    /** The active profile changed. */
    object ProfileChanged : HapticEvent()
    /** The orientation was recentered. */
    object Recentered : HapticEvent()
}

/**
 * A no-op haptics for unit tests and previews.
 * `fire` is a no-op; no events are recorded.
 */
class NullHaptics : Haptics {
    override fun fire(event: HapticEvent) { /* no-op */ }
}

/**
 * A recording haptics for unit tests. Every
 * `fire` call appends to [events] for later
 * assertion.
 */
class FakeHaptics : Haptics {
    private val recorded: MutableList<HapticEvent> = mutableListOf()
    fun events(): List<HapticEvent> = recorded.toList()
    fun count(): Int = recorded.size
    override fun fire(event: HapticEvent) {
        recorded.add(event)
    }
}
