package cr.dragon.calc.data.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CellEntity::class], version = 2, exportSchema = false)
abstract class DragonDatabase : RoomDatabase() {
    abstract fun cellDao(): CellDao

    companion object {
        @Volatile
        private var INSTANCE: DragonDatabase? = null
        private var currentScope: String? = null

        @Synchronized
        fun getInstance(context: Context): DragonDatabase {
            val scope = dragoncore.security.DragonAccountScope.storageKey()
            if (currentScope != scope) {
                INSTANCE?.close()
                INSTANCE = null
                currentScope = scope
            }
            return INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                DragonDatabase::class.java,
                "dragon_vault_$scope.db",
            ).build().also { INSTANCE = it }
        }
    }
}
