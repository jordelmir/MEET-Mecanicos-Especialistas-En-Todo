package com.elysium369.meet.core.monetization

import android.content.Context
import android.util.Log
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class EntitlementLocalCache(private val context: Context) {
    private val prefs = context.getSharedPreferences("meet_entitlements_cache", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DATA = "cache_data"
        private const val KEY_SIGNATURE = "cache_signature"
        private const val SALT = "ElysiumVanguardCommerceTrustCoreSalt"
    }

    fun getEntitlements(): List<Entitlement> {
        val data = prefs.getString(KEY_DATA, null) ?: return emptyList()
        val signature = prefs.getString(KEY_SIGNATURE, null) ?: return emptyList()

        if (signature != calculateSignature(data)) {
            Log.w("EntitlementLocalCache", "Local cache signature mismatch! Discarding cache.")
            clear()
            return emptyList()
        }

        return try {
            val array = JSONArray(data)
            val list = mutableListOf<Entitlement>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(deserializeEntitlement(obj))
            }
            list
        } catch (e: Exception) {
            Log.e("EntitlementLocalCache", "Failed to deserialize entitlements from cache", e)
            emptyList()
        }
    }

    fun saveEntitlements(entitlements: List<Entitlement>) {
        try {
            val array = JSONArray()
            entitlements.forEach { entitlement ->
                array.put(serializeEntitlement(entitlement))
            }
            val dataString = array.toString()
            val signature = calculateSignature(dataString)

            prefs.edit()
                .putString(KEY_DATA, dataString)
                .putString(KEY_SIGNATURE, signature)
                .apply()
        } catch (e: Exception) {
            Log.e("EntitlementLocalCache", "Failed to save entitlements to cache", e)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun calculateSignature(data: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest((data + SALT).toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun serializeEntitlement(e: Entitlement): JSONObject {
        val obj = JSONObject()
        obj.put("id", e.id)
        obj.put("userId", e.userId ?: JSONObject.NULL)
        obj.put("entitlementKey", e.entitlementKey.name)
        obj.put("source", e.source.name)
        obj.put("state", e.state.name)
        obj.put("expiresAt", e.expiresAt ?: JSONObject.NULL)
        obj.put("purchaseTokenHash", e.purchaseTokenHash ?: JSONObject.NULL)
        obj.put("productId", e.productId ?: JSONObject.NULL)
        obj.put("createdAt", e.createdAt)
        obj.put("updatedAt", e.updatedAt)
        return obj
    }

    private fun deserializeEntitlement(obj: JSONObject): Entitlement {
        return Entitlement(
            id = obj.getString("id"),
            userId = if (obj.isNull("userId")) null else obj.getString("userId"),
            entitlementKey = EntitlementKey.valueOf(obj.getString("entitlementKey")),
            source = EntitlementSource.valueOf(obj.getString("source")),
            state = EntitlementState.valueOf(obj.getString("state")),
            expiresAt = if (obj.isNull("expiresAt")) null else obj.getLong("expiresAt"),
            purchaseTokenHash = if (obj.isNull("purchaseTokenHash")) null else obj.getString("purchaseTokenHash"),
            productId = if (obj.isNull("productId")) null else obj.getString("productId"),
            createdAt = obj.getLong("createdAt"),
            updatedAt = obj.getLong("updatedAt")
        )
    }
}
