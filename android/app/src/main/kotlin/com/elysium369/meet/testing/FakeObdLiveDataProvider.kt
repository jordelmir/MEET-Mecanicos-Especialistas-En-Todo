package com.elysium369.meet.testing

import com.elysium369.meet.domain.visualdiagnostics.ObdLiveDataProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeObdLiveDataProvider(
    initialPidValues: Map<String, String> = emptyMap(),
    initialDtcs: Set<String> = emptySet(),
    initiallyConnected: Boolean = false
) : ObdLiveDataProvider {
    private val pidValues = MutableStateFlow(initialPidValues)
    private val dtcs = MutableStateFlow(initialDtcs)
    private val connected = MutableStateFlow(initiallyConnected)

    override fun observePidValues(pids: Set<String>): Flow<Map<String, String>> {
        return pidValues.map { values -> values.filterKeys { pids.contains(it) } }
    }

    override fun observeActiveDtcs(): Flow<Set<String>> = dtcs

    override fun observeConnectionState(): Flow<Boolean> = connected

    fun setPidValues(values: Map<String, String>) {
        pidValues.value = values
    }

    fun setActiveDtcs(values: Set<String>) {
        dtcs.value = values
    }

    fun setConnected(value: Boolean) {
        connected.value = value
    }
}

