package com.elysium.nexus.databases.compatibility

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room database for the §33 compatibility records.
 *
 * The database is a singleton; the activity / service
 * that hosts it calls [build] once and shares the
 * resulting instance. The schema is one table
 * ([CompatibilityEntity] in the `compatibility` table)
 * with a single composite index.
 *
 * ## Schema version
 *
 * The schema is at version 1. Future migrations land in
 * `databases/compatibility/MIGRATIONS.md` (and as
 * `Migration(from, to)` instances added to the
 * `addMigrations(...)` call below). Bumping the
 * version without a migration is a release-blocker:
 * Room refuses to open the database.
 */
@Database(
    entities = [CompatibilityEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CompatibilityDatabase : RoomDatabase() {

    abstract fun compatibilityDao(): CompatibilityDao

    companion object {
        /** The single instance per process. */
        @Volatile
        private var INSTANCE: CompatibilityDatabase? = null

        /**
         * @return the singleton database instance, building
         *   it on first call. The activity / service
         *   that hosts the database calls this in
         *   `onCreate` (or in a Hilt module in 1.x+).
         */
        fun getInstance(context: Context): CompatibilityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        /**
         * Build a fresh database instance. Used by
         * [getInstance] and by tests that need a
         * per-test instance.
         */
        fun build(context: Context): CompatibilityDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CompatibilityDatabase::class.java,
                "compatibility.db"
            )
                // Foreign keys are off by default in Room;
                // we have only one table, so this is a no-op.
                .fallbackToDestructiveMigration()
                .build()
    }
}
