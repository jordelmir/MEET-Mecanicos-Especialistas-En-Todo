package com.elysium.nexus.core.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The store contract for [AppSettings].
 *
 * The store is a thin layer over a *single*
 * settings document. The contract is:
 *
 *  - [current] returns the latest settings; the
 *    value is the same across threads.
 *  - [updates] is a hot [StateFlow] of the
 *    settings; the UI collects to recompose.
 *  - [update] replaces the document atomically.
 *    The store is the only writer; the editor's
 *    "save" button is the only call site.
 *
 * The contract is intentionally narrow: there is
 * no per-field setter, no diff API, no undo. A
 * document is replaced as a whole. The settings
 * are a small bag of values; the granularity of
 * "save" is the whole bag.
 *
 * ## Why an interface and not a class
 *
 * The store has two implementations:
 * [InMemoryAppSettingsStore] (the test-friendly
 * default) and the Android-backed store (Phase
 * 1.18's [AndroidAppSettingsStore], backed by
 * SharedPreferences). The interface lets the
 * activity depend on the contract, not on a
 * specific backing store, and lets the test
 * suite swap in the in-memory store.
 */
interface AppSettingsStore {
    val current: AppSettings
    val updates: StateFlow<AppSettings>
    fun update(settings: AppSettings)
}

/**
 * A test-friendly [AppSettingsStore] backed by an
 * in-memory document.
 *
 * The store is the JVM-testeable half of the
 * settings story. The Android-backed
 * [AndroidAppSettingsStore] is its production
 * counterpart.
 */
class InMemoryAppSettingsStore(
    initial: AppSettings = AppSettings()
) : AppSettingsStore {
    private val state = MutableStateFlow(initial)

    override val current: AppSettings
        get() = state.value

    override val updates: StateFlow<AppSettings>
        get() = state.asStateFlow()

    override fun update(settings: AppSettings) {
        state.value = settings
    }
}
