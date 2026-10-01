package com.elysium.vanguard.recordshield.data.local

import android.content.Context
import android.content.SharedPreferences
import com.elysium.vanguard.recordshield.RecordShieldIdentity
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ============================================================================
 * SecureStorage — Hardware-Backed Encrypted Key-Value Store
 * ============================================================================
 *
 * Why EncryptedSharedPreferences over DataStore:
 *   EncryptedSharedPreferences uses Android Keystore + AES-256-GCM, meaning
 *   decryption keys are stored in hardware (TEE/StrongBox). Even if the
 *   device is rooted, the attacker cannot extract the master key.
 *
 * What's stored here:
 *   - Device API token (for authenticating with Vercel)
 *   - Device UUID
 *   - PIN hash (SHA-256 + salt, NOT the PIN itself)
 *   - Vercel API base URL
 *
 * Security Invariants:
 *   1. PIN is NEVER stored in plaintext — only salted hash
 *   2. API token is encrypted at rest by Android Keystore
 *   3. All reads/writes are synchronous (SharedPreferences API)
 *   4. Master key uses AES256_GCM with StrongBox preference
 * ============================================================================
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "SecureStorage"
        private const val PREFS_FILE = "record_shield_secure_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_IS_DEVICE_REGISTERED = "is_device_registered"
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_VERCEL_API_KEY = "vercel_api_key"
        private const val KEY_GOOGLE_DRIVE_ACCESS_TOKEN = "gdrive_access_token"
        private const val KEY_GOOGLE_DRIVE_REFRESH_TOKEN = "gdrive_refresh_token"
        private const val KEY_SELECTED_CLOUD_PROVIDER = "selected_cloud_provider"
        private const val KEY_CONSENT_GIVEN = "consent_given"
        private const val KEY_SELECTED_VIDEO_QUALITY = "selected_video_quality"
    }

    private val masterKey: MasterKey by lazy {
        runCatching {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        }.getOrElse {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
    }

    private val preferencesByPrincipal = java.util.concurrent.ConcurrentHashMap<String, SharedPreferences>()
    private val encryptedPrefs: SharedPreferences
        get() {
            val scope = RecordShieldIdentity.storageScope()
            return preferencesByPrincipal.computeIfAbsent(scope) {
                EncryptedSharedPreferences.create(
                    context,
                    "${PREFS_FILE}_$scope",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }

    // ========================================================================
    // DEVICE CREDENTIALS
    // ========================================================================

    var vercelApiKey: String?
        get() = encryptedPrefs.getString(KEY_VERCEL_API_KEY, null)
        set(value) = encryptedPrefs.edit().putString(KEY_VERCEL_API_KEY, value).apply()

    var deviceId: String?
        get() = encryptedPrefs.getString(KEY_DEVICE_ID, null)
        set(value) = encryptedPrefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var deviceToken: String?
        get() = encryptedPrefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = encryptedPrefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var isDeviceRegistered: Boolean
        get() = encryptedPrefs.getBoolean(KEY_IS_DEVICE_REGISTERED, false)
        set(value) = encryptedPrefs.edit().putBoolean(KEY_IS_DEVICE_REGISTERED, value).apply()

    var deviceAlias: String?
        get() = encryptedPrefs.getString(KEY_DEVICE_ALIAS, null)
        set(value) = encryptedPrefs.edit().putString(KEY_DEVICE_ALIAS, value).apply()

    var apiBaseUrl: String
        get() = encryptedPrefs.getString(KEY_API_BASE_URL, "https://your-project.vercel.app")
            ?: "https://your-project.vercel.app"
        set(value) = encryptedPrefs.edit().putString(KEY_API_BASE_URL, value).apply()

    // ========================================================================
    // GOOGLE DRIVE CREDENTIALS
    // ========================================================================

    var googleDriveAccessToken: String?
        get() = encryptedPrefs.getString(KEY_GOOGLE_DRIVE_ACCESS_TOKEN, null)
        set(value) = encryptedPrefs.edit().putString(KEY_GOOGLE_DRIVE_ACCESS_TOKEN, value).apply()

    var googleDriveRefreshToken: String?
        get() = encryptedPrefs.getString(KEY_GOOGLE_DRIVE_REFRESH_TOKEN, null)
        set(value) = encryptedPrefs.edit().putString(KEY_GOOGLE_DRIVE_REFRESH_TOKEN, value).apply()

    var selectedCloudProvider: String?
        get() = encryptedPrefs.getString(KEY_SELECTED_CLOUD_PROVIDER, "google_drive")
        set(value) = encryptedPrefs.edit().putString(KEY_SELECTED_CLOUD_PROVIDER, value).apply()

    // ========================================================================
    // CONSENT
    // ========================================================================

    var isConsentGiven: Boolean
        get() = encryptedPrefs.getBoolean(KEY_CONSENT_GIVEN, false)
        set(value) = encryptedPrefs.edit().putBoolean(KEY_CONSENT_GIVEN, value).apply()

    // ========================================================================
    // VIDEO QUALITY
    // ========================================================================

    /**
     * Selected video quality preset name (e.g., "SD", "HD", "FHD").
     * Falls back to "SD" if not set.
     */
    var selectedVideoQuality: String
        get() = encryptedPrefs.getString(KEY_SELECTED_VIDEO_QUALITY, "SD") ?: "SD"
        set(value) = encryptedPrefs.edit().putString(KEY_SELECTED_VIDEO_QUALITY, value).apply()

    // ========================================================================
    // PIN MANAGEMENT
    // ========================================================================

    /**
     * Set a new PIN by hashing it with PBKDF2 + AndroidKeyStore.
     * The PIN is NEVER stored — only its hash.
     */
    fun setPin(pin: String) {
        val salt = PinSecurity.generateSalt()
        val hash = PinSecurity.hashPin(pin, salt)
        encryptedPrefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .apply()
        Log.i(TAG, "PIN set with PBKDF2 + AndroidKeyStore")
    }

    /**
     * Verify a PIN attempt against the stored hash.
     * Uses constant-time comparison to prevent timing attacks.
     * Returns true if the PIN matches.
     */
    fun verifyPin(pinAttempt: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = encryptedPrefs.getString(KEY_PIN_SALT, null) ?: return false
        return PinSecurity.verifyPin(pinAttempt, salt, storedHash)
    }

    /**
     * Check if a PIN has been configured.
     */
    fun isPinSet(): Boolean {
        return encryptedPrefs.getString(KEY_PIN_HASH, null) != null
    }

    /**
     * Clear all secure storage (for device reset/unregister).
     */
    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
        Log.w(TAG, "All secure storage cleared")
    }
}
