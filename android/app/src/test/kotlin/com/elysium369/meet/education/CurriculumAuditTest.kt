package com.elysium369.meet.education

import com.elysium369.meet.education.data.CurriculumTrack
import com.elysium369.meet.education.data.NationalCurriculumCatalogSeed
import org.junit.Test

class CurriculumAuditTest {

    @Test
    fun auditAllTracks() {
        println("==================================================")
        println("AUDITORÍA DE TODOS LOS CURSOS EN ELYSIUM EDUCATION")
        println("==================================================")

        for (track in CurriculumTrack.values()) {
            val units = NationalCurriculumCatalogSeed.getUnitsForTrack(track)
            val totalConcepts = units.sumOf { it.concepts.size }
            val totalTasks = units.sumOf { u -> u.concepts.sumOf { c -> c.tasks.size } }
            val transferTasks = units.sumOf { u -> u.concepts.sumOf { c -> c.tasks.count { it.isTransferTask } } }

            println("Track: ${track.name.padEnd(30)} | Unidades: ${units.size.toString().padStart(2)} | Conceptos: ${totalConcepts.toString().padStart(2)} | Tareas: ${totalTasks.toString().padStart(2)} | Transferencias: $transferTasks")
        }
        println("==================================================")
    }
}
