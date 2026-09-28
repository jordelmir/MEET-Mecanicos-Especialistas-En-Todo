package cr.dragon.calc.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import cr.dragon.calc.ui.document.Cell
import cr.dragon.calc.ui.document.CellType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists the DragonDocument cells to local storage using JSON.
 * Ensures the notebook survives app restarts to create an infinite history experience.
 */
class DocumentRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dragon_docs_prefs", Context.MODE_PRIVATE)
    private val CELLS_KEY = "document_cells"

    fun saveCells(cells: List<Cell>) {
        val jsonArray = JSONArray()
        for (cell in cells) {
            val cellObject = JSONObject().apply {
                put("id", cell.id.toString())
                put("type", cell.type.name)
                put("text", cell.content.text)
                put("translatedContent", cell.translatedContent)
                put("evaluationSteps", JSONArray(cell.evaluationSteps))
            }
            jsonArray.put(cellObject)
        }
        prefs.edit().putString(CELLS_KEY, jsonArray.toString()).apply()
    }

    fun loadCells(): List<Cell> {
        val jsonString = prefs.getString(CELLS_KEY, null)
        if (jsonString.isNullOrEmpty()) {
            return DocumentSeed.getElVueloDelDragon()
        }

        val cells = mutableListOf<Cell>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val idStr = obj.optString("id")
                val typeStr = obj.optString("type", CellType.MATH.name)
                val text = obj.optString("text", "")
                val translatedContent = obj.optString("translatedContent", "")
                val stepsArray = obj.optJSONArray("evaluationSteps")
                val evaluationSteps = mutableListOf<String>()
                if (stepsArray != null) {
                    for (j in 0 until stepsArray.length()) {
                        evaluationSteps.add(stepsArray.getString(j))
                    }
                }

                val type = try { CellType.valueOf(typeStr) } catch(e: Exception) { CellType.MATH }
                val cellId = if (idStr.isNullOrEmpty()) UUID.randomUUID().toString() else idStr

                cells.add(
                    Cell(
                        id = cellId,
                        type = type,
                        content = TextFieldValue(text = text, selection = TextRange(text.length)),
                        translatedContent = translatedContent,
                        evaluationSteps = evaluationSteps
                    )
                )
            }
        } catch (_: Exception) {
        }
        return cells
    }
}
