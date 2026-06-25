package com.elysium369.meet.domain.visualdiagnostics

interface VisualDiagnosticRepository {
    fun componentsForEngine(engineType: EngineType): List<DiagnosticComponent>
    fun findComponent(engineType: EngineType, componentId: String): DiagnosticComponent?
}

interface ObdLiveDataProvider {
    fun observePidValues(pids: Set<String>): kotlinx.coroutines.flow.Flow<Map<String, String>>
    fun observeActiveDtcs(): kotlinx.coroutines.flow.Flow<Set<String>>
    fun observeConnectionState(): kotlinx.coroutines.flow.Flow<Boolean>
}

