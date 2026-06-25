package com.elysium369.meet.data.visualdiagnostics

import com.elysium369.meet.domain.visualdiagnostics.DiagnosticComponent

object VisualDiagnosticMapper {
    fun toDto(component: DiagnosticComponent): VisualDiagnosticDto {
        return VisualDiagnosticDto(
            id = component.id,
            engineType = component.engineType.name,
            name = component.name,
            category = component.category.name,
            payloadJson = "{}"
        )
    }
}

