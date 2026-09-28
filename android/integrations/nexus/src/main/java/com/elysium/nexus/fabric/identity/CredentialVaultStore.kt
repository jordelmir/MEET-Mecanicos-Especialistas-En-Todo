package com.elysium.nexus.fabric.identity

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol

/**
 * V0.6.2 PR4 Phase 15 — Room entity for encrypted credential vault.
 *
 * Stores (keyAlias, protocol, deviceId, iv, ciphertext) — never plaintext.
 * The Android Keystore holds the AES-GCM key; this table is the durable
 * backing store for [CredentialVaultStore].
 */
@Entity(tableName = "credential_vault")
data class CredentialVaultEntity(
    @PrimaryKey
    @ColumnInfo(name = "key_alias")
    val keyAlias: String,

    @ColumnInfo(name = "protocol")
    val protocol: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,

    @ColumnInfo(name = "expires_at_ms")
    val expiresAtMs: Long?,

    @ColumnInfo(name = "is_expired")
    val isExpired: Boolean,

    @ColumnInfo(name = "iv")
    val iv: ByteArray,

    @ColumnInfo(name = "ciphertext")
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = keyAlias.hashCode()
}

@Dao
interface CredentialVaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CredentialVaultEntity)

    @Query("SELECT * FROM credential_vault WHERE key_alias = :keyAlias LIMIT 1")
    suspend fun getByAlias(keyAlias: String): CredentialVaultEntity?

    @Query("SELECT * FROM credential_vault ORDER BY created_at_ms DESC")
    suspend fun getAll(): List<CredentialVaultEntity>

    @Query("DELETE FROM credential_vault WHERE key_alias = :keyAlias")
    suspend fun deleteByAlias(keyAlias: String)

    @Query("DELETE FROM credential_vault")
    suspend fun deleteAll()
}

/**
 * Bridges [CredentialVaultDao] to [CredentialVaultStore].
 * Runs on IO dispatcher — caller must ensure coroutine context.
 */
class RoomCredentialVaultStore(
    private val dao: CredentialVaultDao
) : CredentialVaultStore {

    override fun save(ref: CredentialReference, iv: ByteArray, ciphertext: ByteArray) {
        val entity = CredentialVaultEntity(
            keyAlias = ref.keyAlias,
            protocol = ref.protocol.name,
            deviceId = ref.deviceId.value,
            label = ref.label,
            createdAtMs = ref.createdAtMs,
            expiresAtMs = ref.expiresAtMs,
            isExpired = ref.isExpired,
            iv = iv,
            ciphertext = ciphertext
        )
        kotlinx.coroutines.runBlocking { dao.insert(entity) }
    }

    override fun load(keyAlias: String): CredentialVaultEntry? {
        val entity = kotlinx.coroutines.runBlocking { dao.getByAlias(keyAlias) } ?: return null
        return CredentialVaultEntry(
            ref = CredentialReference(
                keyAlias = entity.keyAlias,
                protocol = Protocol.valueOf(entity.protocol),
                deviceId = DeviceId(entity.deviceId),
                label = entity.label,
                createdAtMs = entity.createdAtMs,
                expiresAtMs = entity.expiresAtMs,
                isExpired = entity.isExpired
            ),
            iv = entity.iv,
            ciphertext = entity.ciphertext
        )
    }

    override fun remove(keyAlias: String) {
        kotlinx.coroutines.runBlocking { dao.deleteByAlias(keyAlias) }
    }

    override fun listAll(): List<CredentialReference> {
        return kotlinx.coroutines.runBlocking { dao.getAll() }.map { entity ->
            CredentialReference(
                keyAlias = entity.keyAlias,
                protocol = Protocol.valueOf(entity.protocol),
                deviceId = DeviceId(entity.deviceId),
                label = entity.label,
                createdAtMs = entity.createdAtMs,
                expiresAtMs = entity.expiresAtMs,
                isExpired = entity.isExpired
            )
        }
    }
}
