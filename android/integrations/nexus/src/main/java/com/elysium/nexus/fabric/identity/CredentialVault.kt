package com.elysium.nexus.fabric.identity

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.elysium.nexus.fabric.canonical.DeviceId
import com.elysium.nexus.fabric.canonical.Protocol

/**
 * §70 Credential Vault.
 *
 * The vault stores protocol-specific credentials
 * (pairing tokens, API keys, certificates) in
 * the Android Keystore. Room stores only
 * *references* (alias + metadata), never the
 * secrets themselves.
 *
 * ## Security model
 *
 * - Secrets live in the Android Keystore (hardware-
 *   backed on devices with TEE/SE).
 * - Room stores: alias, protocol, deviceId,
 *   createdAt, expiresAt, label.
 * - The vault never logs secrets.
 * - The vault never serializes secrets to JSON/disk.
 * - Process death: Keystore survives; Room references
 *   are re-resolved on next access.
 *
 * ## Why a sealed hierarchy
 *
 * Different protocols require different credential
 * shapes. A sealed hierarchy gives exhaustive-when
 * checking: if a new protocol needs credentials,
 * the compiler forces the vault to handle it.
 */
sealed class Credential {

    /** The protocol this credential belongs to. */
    abstract val protocol: Protocol

    /** The target device. */
    abstract val deviceId: DeviceId

    /** When this credential was created. */
    abstract val createdAtMs: Long

    /** Optional expiration (null = never expires). */
    abstract val expiresAtMs: Long?

    /** Whether this credential is currently valid. */
    val isValid: Boolean
        get() {
            if (expiresAtMs == null) return true
            return System.currentTimeMillis() < expiresAtMs!!
        }

    // ── Protocol-specific credentials ─────────────

    /**
     * Matter pairing code + salt.
     */
    data class MatterPairing(
        override val deviceId: DeviceId,
        val pairingCode: String,
        val salt: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Matter
        init {
            require(pairingCode.isNotBlank()) { "Matter pairing code must be non-blank." }
        }
    }

    /**
     * Zigbee network key + install code.
     */
    data class ZigbeeNetwork(
        override val deviceId: DeviceId,
        val networkKey: ByteArray,
        val installCode: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Zigbee
    }

    /**
     * Z-Wave S2 authentication + DSK.
     */
    data class ZWaveS2(
        override val deviceId: DeviceId,
        val authKey: ByteArray,
        val dsk: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.ZWave
        init {
            require(dsk.isNotBlank()) { "Z-Wave DSK must be non-blank." }
        }
    }

    /**
     * BLE bonding key.
     */
    data class BleBonding(
        override val deviceId: DeviceId,
        val bondKey: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Ble
    }

    /**
     * WiFi WPA2/WPA3 credential.
     */
    data class WiFiCredential(
        override val deviceId: DeviceId,
        val ssid: String,
        val psk: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.WiFi
        init {
            require(ssid.isNotBlank()) { "WiFi SSID must be non-blank." }
            require(psk.isNotBlank()) { "WiFi PSK must be non-blank." }
        }
    }

    /**
     * MQTT username/password.
     */
    data class MqttAuth(
        override val deviceId: DeviceId,
        val username: String,
        val password: String,
        val clientId: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Mqtt
    }

    /**
     * ONVIF username/password.
     */
    data class OnvifAuth(
        override val deviceId: DeviceId,
        val username: String,
        val password: String,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.Onvif
    }

    /**
     * Vendor API token (REST / WebSocket).
     */
    data class VendorToken(
        override val deviceId: DeviceId,
        val vendorProtocol: Protocol,
        val token: String,
        val refreshToken: String? = null,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = vendorProtocol
        init {
            require(token.isNotBlank()) { "Vendor token must be non-blank." }
        }
    }

    /**
     * Elysium Link pairing key.
     */
    data class ElysiumLinkKey(
        override val deviceId: DeviceId,
        val pairingKey: ByteArray,
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        override val protocol: Protocol = Protocol.ElysiumLink
    }

    /**
     * Generic credential for protocols not yet
     * enumerated. The vault stores the raw bytes;
     * the protocol adapter interprets them.
     */
    data class Generic(
        override val deviceId: DeviceId,
        override val protocol: Protocol,
        val keyAlias: String,
        val metadata: Map<String, String> = emptyMap(),
        override val createdAtMs: Long = System.currentTimeMillis(),
        override val expiresAtMs: Long? = null
    ) : Credential() {
        init {
            require(keyAlias.isNotBlank()) { "Generic credential keyAlias must be non-blank." }
        }
    }
}

/**
 * Reference stored in Room. Points to a Keystore
 * entry but contains no secrets.
 */
data class CredentialReference(
    val keyAlias: String,
    val protocol: Protocol,
    val deviceId: DeviceId,
    val label: String,
    val createdAtMs: Long,
    val expiresAtMs: Long?,
    val isExpired: Boolean = false
)

/**
 * V0.7 Phase 33 — AEAD AAD builder for credential
 * ciphertexts.
 *
 * Every encrypted credential is bound (via AEAD
 * associated data) to its canonical identity:
 * key alias, protocol, device, storage purpose and
 * schema version. Swapping a ciphertext row between
 * two credentials — even under the same master key —
 * fails authentication, because the decrypt side
 * rebuilds the AAD from the row's own identity.
 *
 * Pure JVM function: the Android Keystore vault and
 * any future Hub/Receiver secure-element vault share
 * this same binding contract.
 */
object CredentialAad {

    /** Purpose label used by the Android Keystore vault. */
    const val PURPOSE_STORAGE = "credential-storage"

    /** Current schema version of the encrypted envelope. */
    const val SCHEMA_VERSION = 1

    /** AAD envelope version prefix; bump only on a wire/format break. */
    private const val ENVELOPE_VERSION = "nexus-credential-v1"

    /**
     * Builds the AAD binding a credential row to its
     * identity. Deterministic: the same inputs always
     * produce the same bytes, so the decrypt side can
     * rebuild it from the persisted [CredentialReference].
     */
    fun build(
        keyAlias: String,
        protocol: Protocol,
        deviceId: DeviceId,
        purpose: String = PURPOSE_STORAGE,
        schemaVersion: Int = SCHEMA_VERSION
    ): ByteArray = (
        "$ENVELOPE_VERSION|alias=$keyAlias|protocol=${protocol.name}|" +
            "device=${deviceId.value}|purpose=$purpose|schema=$schemaVersion"
        ).toByteArray(Charsets.UTF_8)
}

/**
 * The Credential Vault interface. The Android
 * implementation uses the Android Keystore;
 * the Hub/Receiver use the secure element;
 * tests use an in-memory implementation.
 */
interface CredentialVault {
    /**
     * Store a credential securely. Returns a
     * [CredentialReference] for Room storage.
     */
    fun store(credential: Credential): CredentialReference

    /**
     * Retrieve a credential by its reference.
     * Returns null if the credential is missing
     * or expired.
     */
    fun retrieve(reference: CredentialReference): Credential?

    /**
     * Delete a credential. The Keystore entry
     * is permanently removed.
     */
    fun delete(reference: CredentialReference)

    /**
     * List all references for a device.
     */
    fun listForDevice(deviceId: DeviceId): List<CredentialReference>

    /**
     * List all references for a protocol.
     */
    fun listForProtocol(protocol: Protocol): List<CredentialReference>
}

/**
 * In-memory implementation for tests.
 */
class InMemoryCredentialVault : CredentialVault {
    private val store = mutableMapOf<String, Credential>()

    override fun store(credential: Credential): CredentialReference {
        val alias = "vault_${credential.protocol}_${credential.deviceId.value}_${System.currentTimeMillis()}"
        store[alias] = credential
        return CredentialReference(
            keyAlias = alias,
            protocol = credential.protocol,
            deviceId = credential.deviceId,
            label = credential::class.simpleName ?: "Unknown",
            createdAtMs = credential.createdAtMs,
            expiresAtMs = credential.expiresAtMs,
            isExpired = credential.isValid.not()
        )
    }

    override fun retrieve(reference: CredentialReference): Credential? {
        val credential = store[reference.keyAlias] ?: return null
        return if (credential.isValid) credential else null
    }

    override fun delete(reference: CredentialReference) {
        store.remove(reference.keyAlias)
    }

    override fun listForDevice(deviceId: DeviceId): List<CredentialReference> =
        store.entries
            .filter { it.value.deviceId == deviceId }
            .map { (alias, credential) ->
                CredentialReference(
                    keyAlias = alias,
                    protocol = credential.protocol,
                    deviceId = credential.deviceId,
                    label = credential::class.simpleName ?: "Unknown",
                    createdAtMs = credential.createdAtMs,
                    expiresAtMs = credential.expiresAtMs,
                    isExpired = credential.isValid.not()
                )
            }

    override fun listForProtocol(protocol: Protocol): List<CredentialReference> =
        store.entries
            .filter { it.value.protocol == protocol }
            .map { (alias, credential) ->
                CredentialReference(
                    keyAlias = alias,
                    protocol = credential.protocol,
                    deviceId = credential.deviceId,
                    label = credential::class.simpleName ?: "Unknown",
                    createdAtMs = credential.createdAtMs,
                    expiresAtMs = credential.expiresAtMs,
                    isExpired = credential.isValid.not()
                )
            }
}

// ═══════════════════════════════════════════════════════════════════════════
// V0.6.2 PR4 Phase 15: Android Keystore Credential Vault (§70)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Android Keystore-backed [CredentialVault].
 *
 * Uses AES-256-GCM with key material from the Android Keystore
 * (hardware-backed on devices with TEE/SE). Each credential is
 * serialized to JSON, encrypted with a unique AES key, and stored
 * in a [CredentialVaultStore].
 *
 * ## Architecture: Master Key with AEAD AAD Binding
 *
 * - A **Master Keystore key** (`KEYSTORE_ALIAS`) is generated once in Android KeyStore
 *   and never leaves hardware-backed storage (TEE/SE).
 * - All credential entries are encrypted with AES-256-GCM using this master key.
 * - AEAD Associated Authenticated Data (AAD) binds the ciphertext to the schema version,
 *   preventing cross-credential ciphertext substitution attacks.
 *
 * ## Security contract
 *
 * - [store] encrypts before persisting — plaintext never touches disk.
 * - [retrieve] decrypts in memory only — plaintext never leaves the vault.
 * - [delete] permanently destroys the credential entry in storage.
 * - If the Android Keystore is unavailable, [store] throws [IllegalStateException] —
 *   **never** falls back to plaintext storage.
 */
class AndroidKeystoreCredentialVault(
    private val keystoreAlias: String = KEYSTORE_ALIAS,
    private val store: CredentialVaultStore
) : CredentialVault {

    companion object {
        private const val KEYSTORE_ALIAS = "elysium.credential"
        private const val AES_KEY_SIZE = 256
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val KEYSTORE_VERSION = 1
    }

    // ── Keystore key management ──────────────────────

    private fun getOrCreateSecretKey(alias: String): javax.crypto.SecretKey {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getEntry(alias, null)?.let { entry ->
            return (entry as java.security.KeyStore.SecretKeyEntry).secretKey
        }
        // V0.6.3 Phase 21: Fixed KeyGenerator initialization.
        // AES/GCM/NoPadding is a Cipher transformation, not a KeyGenerator algorithm.
        // KeyGenerator must use "AES" only; GCM parameters are set on Cipher, not KeyGenerator.
        val keyGen = javax.crypto.KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(AES_KEY_SIZE)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun getKeyAlias(): String = "${keystoreAlias}.v${KEYSTORE_VERSION}"

    // ── CredentialVault interface ──────────────────────

    override fun store(credential: Credential): CredentialReference {
        val secretKey = getOrCreateSecretKey(getKeyAlias())
        val cipher = javax.crypto.Cipher.getInstance(AES_GCM)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val plaintext = serializeCredential(credential)
        val ref = CredentialReference(
            keyAlias = "${getKeyAlias()}_${credential.protocol}_${credential.deviceId.value}",
            protocol = credential.protocol,
            deviceId = credential.deviceId,
            label = credential::class.simpleName ?: "Unknown",
            createdAtMs = credential.createdAtMs,
            expiresAtMs = credential.expiresAtMs,
            isExpired = credential.isValid.not()
        )
        // Phase 33: bind the ciphertext to the credential's
        // canonical identity before sealing (AAD).
        cipher.updateAAD(
            CredentialAad.build(
                keyAlias = ref.keyAlias,
                protocol = ref.protocol,
                deviceId = ref.deviceId
            )
        )
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        store.save(ref, iv, ciphertext)
        return ref
    }

    override fun retrieve(reference: CredentialReference): Credential? {
        if (reference.isExpired) return null
        val entry = store.load(reference.keyAlias) ?: return null
        val secretKey = try {
            getOrCreateSecretKey(getKeyAlias())
        } catch (e: Exception) {
            return null
        }
        return try {
            val cipher = javax.crypto.Cipher.getInstance(AES_GCM)
            val spec = javax.crypto.spec.GCMParameterSpec(TAG_SIZE, entry.iv)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, spec)
            // Phase 33: rebuild the AAD from the row's own identity.
            // A ciphertext moved under another credential's alias fails here.
            cipher.updateAAD(
                CredentialAad.build(
                    keyAlias = reference.keyAlias,
                    protocol = reference.protocol,
                    deviceId = reference.deviceId
                )
            )
            val plaintext = String(cipher.doFinal(entry.ciphertext), Charsets.UTF_8)
            deserializeCredential(plaintext, reference.protocol, reference.deviceId)
        } catch (e: Exception) {
            android.util.Log.e("CredentialVault", "Decrypt failed: ${e.message}")
            null
        }
    }

    override fun delete(reference: CredentialReference) {
        store.remove(reference.keyAlias)
    }

    override fun listForDevice(deviceId: DeviceId): List<CredentialReference> =
        store.listAll().filter { it.deviceId == deviceId }

    override fun listForProtocol(protocol: Protocol): List<CredentialReference> =
        store.listAll().filter { it.protocol == protocol }

    // ── Serialization (JSON) ─────────────────────

    private fun serializeCredential(credential: Credential): String {
        val json = org.json.JSONObject()
        json.put("type", credential::class.simpleName)
        json.put("protocol", credential.protocol.name)
        json.put("deviceId", credential.deviceId.value)
        json.put("createdAtMs", credential.createdAtMs)
        json.put("expiresAtMs", credential.expiresAtMs ?: org.json.JSONObject.NULL)

        when (credential) {
            is Credential.MatterPairing -> {
                json.put("pairingCode", credential.pairingCode)
                json.put("salt", android.util.Base64.encodeToString(credential.salt, android.util.Base64.NO_WRAP))
            }
            is Credential.ZigbeeNetwork -> {
                json.put("networkKey", android.util.Base64.encodeToString(credential.networkKey, android.util.Base64.NO_WRAP))
                json.put("installCode", android.util.Base64.encodeToString(credential.installCode, android.util.Base64.NO_WRAP))
            }
            is Credential.ZWaveS2 -> {
                json.put("authKey", android.util.Base64.encodeToString(credential.authKey, android.util.Base64.NO_WRAP))
                json.put("dsk", credential.dsk)
            }
            is Credential.BleBonding -> {
                json.put("bondKey", android.util.Base64.encodeToString(credential.bondKey, android.util.Base64.NO_WRAP))
            }
            is Credential.WiFiCredential -> {
                json.put("ssid", credential.ssid)
                json.put("psk", credential.psk)
            }
            is Credential.MqttAuth -> {
                json.put("username", credential.username)
                json.put("password", credential.password)
                json.put("clientId", credential.clientId)
            }
            is Credential.OnvifAuth -> {
                json.put("username", credential.username)
                json.put("password", credential.password)
            }
            is Credential.VendorToken -> {
                json.put("token", credential.token)
                json.put("refreshToken", credential.refreshToken ?: "")
                json.put("vendorProtocol", credential.vendorProtocol.name)
            }
            is Credential.ElysiumLinkKey -> {
                json.put("pairingKey", android.util.Base64.encodeToString(credential.pairingKey, android.util.Base64.NO_WRAP))
            }
            is Credential.Generic -> {
                json.put("keyAlias", credential.keyAlias)
                val meta = org.json.JSONObject()
                credential.metadata.forEach { (k, v) -> meta.put(k, v) }
                json.put("metadata", meta)
            }
        }
        return json.toString()
    }

    private fun deserializeCredential(json: String, protocol: Protocol, deviceId: DeviceId): Credential? {
        return try {
            val obj = org.json.JSONObject(json)
            val createdAt = obj.optLong("createdAtMs", System.currentTimeMillis())
            val expiresAt = if (obj.isNull("expiresAtMs")) null else obj.optLong("expiresAtMs")

            when (protocol) {
                Protocol.Matter -> Credential.MatterPairing(
                    deviceId = deviceId,
                    pairingCode = obj.getString("pairingCode"),
                    salt = android.util.Base64.decode(obj.getString("salt"), android.util.Base64.NO_WRAP),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                Protocol.Zigbee -> Credential.ZigbeeNetwork(
                    deviceId = deviceId,
                    networkKey = android.util.Base64.decode(obj.getString("networkKey"), android.util.Base64.NO_WRAP),
                    installCode = android.util.Base64.decode(obj.getString("installCode"), android.util.Base64.NO_WRAP),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                Protocol.ZWave -> Credential.ZWaveS2(
                    deviceId = deviceId,
                    authKey = android.util.Base64.decode(obj.getString("authKey"), android.util.Base64.NO_WRAP),
                    dsk = obj.getString("dsk"),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                Protocol.Ble -> Credential.BleBonding(
                    deviceId = deviceId,
                    bondKey = android.util.Base64.decode(obj.getString("bondKey"), android.util.Base64.NO_WRAP),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                Protocol.WiFi -> Credential.WiFiCredential(
                    deviceId = deviceId,
                    ssid = obj.getString("ssid"),
                    psk = obj.getString("psk"),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                Protocol.Mqtt -> Credential.MqttAuth(
                    deviceId = deviceId,
                    username = obj.getString("username"),
                    password = obj.getString("password"),
                    clientId = obj.getString("clientId"),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                Protocol.Onvif -> Credential.OnvifAuth(
                    deviceId = deviceId,
                    username = obj.getString("username"),
                    password = obj.getString("password"),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                Protocol.ElysiumLink -> Credential.ElysiumLinkKey(
                    deviceId = deviceId,
                    pairingKey = android.util.Base64.decode(obj.getString("pairingKey"), android.util.Base64.NO_WRAP),
                    createdAtMs = createdAt,
                    expiresAtMs = expiresAt
                )
                else -> {
                    val refreshToken = obj.optString("refreshToken", "")
                    if (refreshToken.isNotEmpty()) {
                        Credential.VendorToken(
                            deviceId = deviceId,
                            vendorProtocol = protocol,
                            token = obj.getString("token"),
                            refreshToken = refreshToken,
                            createdAtMs = createdAt,
                            expiresAtMs = expiresAt
                        )
                    } else {
                        val metadata = mutableMapOf<String, String>()
                        val metaObj = obj.optJSONObject("metadata")
                        metaObj?.keys()?.forEach { k -> metadata[k] = metaObj.getString(k) }
                        Credential.Generic(
                            deviceId = deviceId,
                            protocol = protocol,
                            keyAlias = obj.optString("keyAlias", ""),
                            metadata = metadata,
                            createdAtMs = createdAt,
                            expiresAtMs = expiresAt
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CredentialVault", "Deserialize failed: ${e.message}")
            null
        }
    }
}

/**
 * Storage backend for encrypted credential entries.
 * Room stores (ref, iv, ciphertext) — never plaintext.
 */
interface CredentialVaultStore {
    fun save(ref: CredentialReference, iv: ByteArray, ciphertext: ByteArray)
    fun load(keyAlias: String): CredentialVaultEntry?
    fun remove(keyAlias: String)
    fun listAll(): List<CredentialReference>
}

data class CredentialVaultEntry(
    val ref: CredentialReference,
    val iv: ByteArray,
    val ciphertext: ByteArray
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = ref.keyAlias.hashCode()
}
