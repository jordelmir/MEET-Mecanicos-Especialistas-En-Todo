package dragoncore.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeuralVault @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "dragon_neural_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(provider: String, key: String) {
        sharedPrefs.edit().putString(scopedKey(provider), key).apply()
    }

    fun getApiKey(provider: String): String? {
        return sharedPrefs.getString(scopedKey(provider), null)
    }

    fun clearKey(provider: String) {
        sharedPrefs.edit().remove(scopedKey(provider)).apply()
    }

    private fun scopedKey(provider: String) =
        "${DragonAccountScope.storageKey()}_${provider.lowercase()}_api_key"
}
