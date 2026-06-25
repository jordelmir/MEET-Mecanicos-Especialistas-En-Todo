package com.elysium369.meet.data.visualdiagnostics

import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import com.elysium369.meet.domain.visualdiagnostics.VisualDiagnosticRepository

class VisualDiagnosticRepositoryImpl : VisualDiagnosticRepository {
    override fun componentsForEngine(engineType: EngineType): List<DiagnosticComponent> {
        return VisualDiagnosticSeedData.components(engineType)
    }

    override fun findComponent(engineType: EngineType, componentId: String): DiagnosticComponent? {
        return componentsForEngine(engineType).firstOrNull {
            it.id == componentId || componentId.startsWith(it.meshKey) || it.meshKey == componentId
        }
    }
}

