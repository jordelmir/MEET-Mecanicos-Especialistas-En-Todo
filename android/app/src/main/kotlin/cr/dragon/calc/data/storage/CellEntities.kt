package cr.dragon.calc.data.storage

import androidx.room.*
import java.util.UUID

/**
 * Entidad de base de datos para una celda de DragonDocs.
 * Diseñada para persistencia binaria segura via SQLCipher.
 */
@Entity(tableName = "cells")
data class CellEntity(
    @PrimaryKey val id: String, // UUID as String
    val type: String,           // Enum name
    val content: String,        // Raw input
    val result: String,         // Calculated output
    val isError: Boolean,
    val translatedContent: String,
    val physicsStateJson: String, // Serialized Map<String, Double>
    val chemState: String,
    val statsPayloadJson: String?,
    val aiExplanation: String?,  // [V2] Persisted AI explanation
    val displayOrder: Int        // Posicion en la lista
)

/**
 * Data Access Object para operaciones CRUD en el cuaderno.
 */
@Dao
interface CellDao {
    @Query("SELECT * FROM cells ORDER BY displayOrder ASC")
    suspend fun getAll(): List<CellEntity>

    @Upsert
    suspend fun insertAll(cells: List<CellEntity>)

    @Query("DELETE FROM cells")
    suspend fun deleteAll()

    @Transaction
    suspend fun updateDocument(cells: List<CellEntity>) {
        deleteAll()
        insertAll(cells)
    }
}
