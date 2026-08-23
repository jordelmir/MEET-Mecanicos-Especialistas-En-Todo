package com.elysium369.meet.communications

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates device-bound lookup tags for aliases that were already verified.
 * These tags support exact offline matching without storing raw email or phone
 * values in relationship rows. They are not a global contact-discovery scheme.
 */
@Singleton
class DeviceAliasMatcher @Inject constructor() {
    fun tag(medium: ContactDiscoveryMedium, normalizedAlias: String): String {
        require(medium == ContactDiscoveryMedium.EMAIL || medium == ContactDiscoveryMedium.PHONE)
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(getOrCreateKey())
        val input = "elysium-alias-v1|${medium.name}|$normalizedAlias"
        return Base64.encodeToString(
            mac.doFinal(input.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            ).setDigests(KeyProperties.DIGEST_SHA256).build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "elysium_communications_alias_match_v1"
    }
}
