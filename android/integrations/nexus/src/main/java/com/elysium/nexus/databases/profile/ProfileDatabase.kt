package com.elysium.nexus.databases.profile

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room database for the §15 profile store.
 *
 * The database is a singleton; the activity / service
 * that hosts it calls [getInstance] once and shares the
 * resulting instance. The schema is two tables
 * ([ProfileEntity] in `profile`,
 * [ProfileControlEntity] in `profile_control`) with a
 * single foreign key (CASCADE) and one composite index.
 *
 * ## Schema version
 *
 * The schema is at version 1. Future migrations land in
 * `databases/profile/MIGRATIONS.md` (and as
 * `Migration(from, to)` instances added to the
 * `addMigrations(...)` call below). Bumping the
 * version without a migration is a release-blocker:
 * Room refuses to open the database.
 *
 * ## Why `fallbackToDestructiveMigration`
 *
 * Phase 1.2 ships the first version of this database.
 * There is no prior version to migrate from. When the
 * schema is bumped in a future phase the migration is
 * added in the same change; the destructive fallback
 * is the safety net for development only (a user
 * running a Phase 1.2 build on a device that previously
 * ran a Phase 1.0 build has no profile data, because
 * the Phase 1.0 build did not persist any).
 */
@Database(
    entities = [ProfileEntity::class, ProfileControlEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ProfileConverters::class)
abstract class ProfileDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        /** The single instance per process. */
        @Volatile
        private var INSTANCE: ProfileDatabase? = null

        /**
         * @return the singleton database instance, building
         *   it on first call. The activity / service
         *   that hosts the database calls this in
         *   `onCreate` (or in a Hilt module in 1.x+).
         */
        fun getInstance(context: Context): ProfileDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        /**
         * Build a fresh database instance. Used by
         * [getInstance] and by tests that need a
         * per-test instance.
         */
        fun build(context: Context): ProfileDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ProfileDatabase::class.java,
                "profile.db"
            )
                .fallbackToDestructiveMigration()
                .build()
    }
}
