package com.elysium369.meet.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class PartRepairWorkflowTest {
    @Test
    fun `literal procedure language maps to repair phases`() {
        assertEquals(
            PartRepairPhase.DISCOVERY,
            PartRepairWorkflowBuilder.classify("Verificar alimentación y medir continuidad"),
        )
        assertEquals(
            PartRepairPhase.REPAIR_OR_REPLACE,
            PartRepairWorkflowBuilder.classify("Sustituir si presenta fisuras"),
        )
        assertEquals(
            PartRepairPhase.VALIDATION,
            PartRepairWorkflowBuilder.classify("Realizar prueba de carretera y post scan"),
        )
    }
}
