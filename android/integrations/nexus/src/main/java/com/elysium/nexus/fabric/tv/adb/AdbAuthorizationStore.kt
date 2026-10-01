package com.elysium.nexus.fabric.tv.adb

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for the ADB RSA identity.
 *
 * A commercial controller pairs with each TV ONCE;
 * the TV remembers the RSA key in its `adb_keys`
 * store, so every later connection must use the SAME
 * private key or the "Allow USB debugging" dialog
 * reappears. This store keeps the PEM on disk
 * (SharedPreferences) across app installs/updates.
 *
 * JVM-safe: the abstract contract is backed by
 * [MemoryAdbAuthorizationStore] in unit tests.
 */
interface AdbAuthorizationStore {
    fun load(): String?
    /** Returns true when the PEM was persisted. */
    fun save(pem: String): Boolean
    fun clear()
}

/** Keep the private key in a [SharedPreferences] blob. */
class SharedPrefsAdbAuthorizationStore(
    private val prefs: SharedPreferences
) : AdbAuthorizationStore {
    override fun load(): String? = prefs.getString(KEY_ADB_PEM, null)
    override fun save(pem: String): Boolean =
        prefs.edit().putString(KEY_ADB_PEM, pem).commit()
    override fun clear() { prefs.edit().remove(KEY_ADB_PEM).apply() }

    companion object {
        private const val KEY_ADB_PEM = "adb_key_pem"
        fun of(context: Context): AdbAuthorizationStore =
            SharedPrefsAdbAuthorizationStore(
                context.getSharedPreferences("elysium_adb_auth", Context.MODE_PRIVATE)
            )
    }
}

/** In-memory store for JVM tests. */
class MemoryAdbAuthorizationStore : AdbAuthorizationStore {
    private var pem: String? = null
    override fun load(): String? = pem
    override fun save(pem: String): Boolean { this.pem = pem; return true }
    override fun clear() { this.pem = null }
}

/**
 * Load the persisted key or generate + persist a new
 * one (first run). The returned identity is stable for
 * the lifetime of the app — the TV only shows the
 * pairing dialog for the very first contact.
 */
fun AdbAuthorizationStore.resolveIdentity(): AdbAuthorization {
    val stored = load()
    val existing = stored?.let { AdbAuthorization.loadFromPem(it) }
    if (existing != null) return existing
    val fresh = AdbAuthorization.generate()
    save(fresh.toPem())
    return fresh
}