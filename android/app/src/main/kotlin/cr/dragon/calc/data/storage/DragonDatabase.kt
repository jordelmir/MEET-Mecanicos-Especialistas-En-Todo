package cr.dragon.calc.data.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CellEntity::class], version = 2, exportSchema = false)
abstract class DragonDatabase : RoomDatabase() {
    abstract fun cellDao(): CellDao

    companion object {
        private const val DB_NAME = "dragon_vault.db"

        @Volatile
        private var INSTANCE: DragonDatabase? = null

        fun getInstance(context: Context): DragonDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context, DragonDatabase::class.java, DB_NAME)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
