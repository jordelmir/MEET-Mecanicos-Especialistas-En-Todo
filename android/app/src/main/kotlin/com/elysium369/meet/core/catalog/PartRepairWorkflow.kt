package com.elysium369.meet.core.catalog

enum class PartRepairPhase(val title: String) {
    SYMPTOMS("Síntomas y contexto"),
    DISCOVERY("Descubrir y confirmar la falla"),
    SAFETY("Preparación y seguridad"),
    REMOVAL("Desmontaje"),
    INSPECTION("Inspección fuera del vehículo"),
    REPAIR_OR_REPLACE("Reparar o sustituir"),
    INSTALLATION("Instalación y ajustes"),
    VALIDATION("Validación posterior"),
}

data class PartRepairEvidence(
    val blockId: String,
    val sourceOrder: Int,
    val sourceTextHash: String,
    val text: String,
)

data class PartRepairPhaseCard(
    val phase: PartRepairPhase,
    val universalChecklist: String,
    val evidence: List<PartRepairEvidence>,
) {
    val hasLiteralEvidence: Boolean get() = evidence.isNotEmpty()
}

object PartRepairWorkflowBuilder {
    fun build(blocks: List<ProprietarySourceBlock>): List<PartRepairPhaseCard> {
        val evidenceByPhase = PartRepairPhase.entries.associateWith { mutableListOf<PartRepairEvidence>() }
        blocks.forEach { block ->
            val text = block.rows
                ?.flatten()
                ?.filter(String::isNotBlank)
                ?.joinToString(" | ")
                ?.takeIf(String::isNotBlank)
                ?: block.text
            val phase = classify(text) ?: return@forEach
            evidenceByPhase.getValue(phase) += PartRepairEvidence(
                blockId = block.blockId,
                sourceOrder = block.order,
                sourceTextHash = block.textHash,
                text = text,
            )
        }
        return PartRepairPhase.entries.map { phase ->
            PartRepairPhaseCard(
                phase = phase,
                universalChecklist = universalChecklist(phase),
                evidence = evidenceByPhase.getValue(phase)
                    .distinctBy(PartRepairEvidence::blockId)
                    .sortedBy(PartRepairEvidence::sourceOrder),
            )
        }
    }

    internal fun classify(text: String): PartRepairPhase? {
        val normalized = text.normalizedCatalogText()
        return when {
            tokens(normalized, "validar", "confirmar reparacion", "prueba de carretera", "borrar codigos", "post scan") ->
                PartRepairPhase.VALIDATION
            tokens(normalized, "reparar", "reemplazar", "sustituir", "rectificar", "soldar") ->
                PartRepairPhase.REPAIR_OR_REPLACE
            tokens(normalized, "sintoma", "falla", "ruido", "vibracion", "fuga", "no funciona") ->
                PartRepairPhase.SYMPTOMS
            tokens(normalized, "diagnost", "probar", "prueba", "medir", "verificar", "comprobar", "inspeccion visual") ->
                PartRepairPhase.DISCOVERY
            tokens(normalized, "seguridad", "precaucion", "desconectar", "proteccion", "elevar vehiculo") ->
                PartRepairPhase.SAFETY
            tokens(normalized, "desmont", "retirar", "remover", "extraer", "aflojar") ->
                PartRepairPhase.REMOVAL
            tokens(normalized, "inspeccionar", "desgaste", "holgura", "corrosion", "fisura", "limpiar") ->
                PartRepairPhase.INSPECTION
            tokens(normalized, "instalar", "montar", "apretar", "torque", "calibrar", "purgar", "ajustar") ->
                PartRepairPhase.INSTALLATION
            else -> null
        }
    }

    private fun tokens(text: String, vararg values: String): Boolean = values.any(text::contains)

    private fun universalChecklist(phase: PartRepairPhase): String = when (phase) {
        PartRepairPhase.SYMPTOMS ->
            "Registrar el síntoma exacto, cuándo ocurre y evidencia previa. No asumir que esta pieza es la causa."
        PartRepairPhase.DISCOVERY ->
            "Confirmar alimentación, señales, condición mecánica y piezas relacionadas con pruebas apropiadas antes de desmontar."
        PartRepairPhase.SAFETY ->
            "Aplicar el procedimiento de seguridad del sistema y del fabricante. Si falta, marcarlo como pendiente antes de intervenir."
        PartRepairPhase.REMOVAL ->
            "Documentar conectores, fijaciones y orientación. No usar un orden ni torque no respaldado por la fuente."
        PartRepairPhase.INSPECTION ->
            "Comparar desgaste, contaminación, daño, juego y superficies de contacto con especificación verificable."
        PartRepairPhase.REPAIR_OR_REPLACE ->
            "Decidir con evidencia. Confirmar VIN, OEM, conector, foto y medidas antes de comprar o instalar."
        PartRepairPhase.INSTALLATION ->
            "Restituir conexiones y ajustes siguiendo datos verificables; registrar las piezas realmente utilizadas."
        PartRepairPhase.VALIDATION ->
            "Realizar prueba funcional y escaneo posterior, comparar antes/después y adjuntar evidencia al historial."
    }
}
