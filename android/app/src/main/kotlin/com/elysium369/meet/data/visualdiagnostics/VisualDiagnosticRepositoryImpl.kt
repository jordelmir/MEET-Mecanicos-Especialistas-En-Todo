package com.elysium369.meet.data.visualdiagnostics

import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent
import com.elysium369.meet.domain.visualdiagnostics.EngineType
import com.elysium369.meet.domain.visualdiagnostics.VisualBomAtlas
import com.elysium369.meet.domain.visualdiagnostics.VisualBomNode
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

    override fun bomAtlas(): List<VisualBomNode> = VisualBomAtlas.nodes()

    override fun findBomNode(query: String): VisualBomNode? = VisualBomAtlas.find(query)

    override fun bomNodesForDtc(code: String): List<VisualBomNode> = VisualBomAtlas.byDtc(code)

    override fun componentsForBomNode(engineType: EngineType, nodeId: String): List<DiagnosticComponent> {
        val node = findBomNode(nodeId) ?: return emptyList()
        val ids = node.componentIds.toSet()
        return componentsForEngine(engineType).filter { component ->
            component.id in ids || component.meshKey == node.meshKey || ids.contains(component.meshKey)
        }
    }
}
