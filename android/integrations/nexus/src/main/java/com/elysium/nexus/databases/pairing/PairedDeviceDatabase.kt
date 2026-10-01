package com.elysium.nexus.databases.pairing

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elysium.nexus.fabric.identity.CredentialVaultDao
import com.elysium.nexus.fabric.identity.CredentialVaultEntity

@Database(
    entities = [PairedDeviceEntity::class, CredentialVaultEntity::class],
    version = 3,
    exportSchema = true
)
abstract class PairedDeviceDatabase : RoomDatabase() {

    abstract fun pairedDeviceDao(): PairedDeviceDao

    abstract fun credentialVaultDao(): CredentialVaultDao

    companion object {
        @Volatile
        private var INSTANCE: PairedDeviceDatabase? = null

        /** V06 §4: add stable_identity column + index. Never use IP as identity. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paired_devices ADD COLUMN stable_identity TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_paired_devices_stableIdentity ON paired_devices(stable_identity)")
            }
        }

        /**
         * V0.6.2 PR4 Phase 15: create credential_vault table + scrub plaintext.
         *
         * §70: Room stores only credential references, never secrets.
         * Existing plaintext `pairing_token` and `client_key` are zeroed.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create the encrypted credential vault table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS credential_vault (
                        key_alias TEXT NOT NULL PRIMARY KEY,
                        protocol TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        label TEXT NOT NULL,
                        created_at_ms INTEGER NOT NULL,
                        expires_at_ms INTEGER,
                        is_expired INTEGER NOT NULL DEFAULT 0,
                        iv BLOB NOT NULL,
                        ciphertext BLOB NOT NULL
                    )
                """.trimIndent())

                // 2. Scrub existing plaintext credentials from paired_devices
                // §70: plaintext secrets must never persist in Room.
                db.execSQL("UPDATE paired_devices SET pairing_token = NULL, client_key = NULL")
            }
        }

        fun getInstance(context: Context): PairedDeviceDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        fun build(context: Context): PairedDeviceDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PairedDeviceDatabase::class.java,
                "paired_devices.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
