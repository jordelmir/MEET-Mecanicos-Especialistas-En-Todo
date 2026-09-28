package com.elysium.nexus.databases.ir

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for learned IR commands.
 *
 * Singleton pattern matching [com.elysium.nexus.databases.profile.ProfileDatabase].
 * Schema version 1; destructive migration fallback
 * for development.
 */
@Database(
    entities = [LearnedIrCommandEntity::class],
    version = 1,
    exportSchema = false
)
abstract class IrCommandDatabase : RoomDatabase() {

    abstract fun learnedIrCommandDao(): LearnedIrCommandDao

    companion object {
        @Volatile
        private var INSTANCE: IrCommandDatabase? = null

        fun getInstance(context: Context): IrCommandDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        fun build(context: Context): IrCommandDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                IrCommandDatabase::class.java,
                "ir_commands.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
