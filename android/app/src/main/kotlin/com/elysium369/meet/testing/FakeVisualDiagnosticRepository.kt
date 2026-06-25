package com.elysium369.meet.testing

import com.elysium369.meet.data.visualdiagnostics.VisualDiagnosticSeedData
import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import com.elysium369.meet.domain.visualdiagnostics.VisualDiagnosticRepository

class FakeVisualDiagnosticRepository(
    private val components: List<DiagnosticComponent> = VisualDiagnosticSeedData.components(EngineType.L4)
) : VisualDiagnosticRepository {
    override fun componentsForEngine(engineType: EngineType): List<DiagnosticComponent> = components

    override fun findComponent(engineType: EngineType, componentId: String): DiagnosticComponent? {
        return components.firstOrNull { it.id == componentId }
    }
}

