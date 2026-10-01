package com.elysium.nexus.core.haptics

import com.elysium.nexus.core.settings.AppSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * A [Haptics] that respects the §15
 * `AppSettings.hapticsEnabled` knob.
 *
 * The wrapper is a thin decorator: it forwards
 * every event to the inner [Haptics] when the
 * settings say "haptics on", and silently drops
 * every event when the settings say "haptics
 * off". The wrapper is the only [Haptics] the
 * engine and the editor should depend on; the
 * raw [AndroidHaptics] is an implementation
 * detail of the wrapper.
 *
 * ## Why a decorator and not a flag on [AndroidHaptics]
 *
 * Tying the "haptics enabled" flag to the
 * concrete Android implementation would force
 * the test-friendly [FakeHaptics] and the
 * production [AndroidHaptics] to share the
 * same flag, and would mix the §27 spec
 * (haptics as a feature) with the §15 spec
 * (settings as a feature). The decorator is a
 * one-class addition that does not pollute
 * either side.
 *
 * ## Why a `StateFlow<AppSettings>` and not a
 * `() -> Boolean` callback
 *
 * The settings are a [StateFlow] (the activity
 * already collects them). The wrapper reads the
 * flow's `.value`; the flow is the source of
 * truth, and the wrapper is just a consumer.
 * Using a callback would force the activity to
 * remember to update the wrapper on every
 * settings change, which is exactly the kind of
 * "forget to update" bug the flow is designed
 * to prevent.
 */
class SettingsAwareHaptics(
    private val inner: Haptics,
    private val settingsFlow: StateFlow<AppSettings>
) : Haptics {

    override fun fire(event: HapticEvent) {
        if (settingsFlow.value.hapticsEnabled) {
            inner.fire(event)
        }
    }
}
