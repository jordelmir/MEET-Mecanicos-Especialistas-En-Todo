package cr.dragon.calc.data.storage

import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellType
import java.util.UUID

/**
 * Extensiones para mapear entre el dominio UI y la persistencia cifrada.
 */
fun CellEntity.toCell(): Cell {
    return Cell(
        id = id,
        type = try { CellType.valueOf(type) } catch (e: Exception) { CellType.MATH },
        content = androidx.compose.ui.text.input.TextFieldValue(content),
        result = result,
        isError = isError,
        translatedContent = translatedContent,
        chemState = chemState,
        aiExplanation = aiExplanation
    )
}

fun Cell.toEntity(order: Int): CellEntity {
    return CellEntity(
        id = id.toString(),
        type = type.name,
        content = content.text,
        result = result,
        isError = isError,
        translatedContent = translatedContent,
        physicsStateJson = "{}", // TODO: Implementar serialización en DragonStorage (Fase II)
        chemState = chemState,
        statsPayloadJson = null,
        aiExplanation = aiExplanation,
        displayOrder = order
    )
}
