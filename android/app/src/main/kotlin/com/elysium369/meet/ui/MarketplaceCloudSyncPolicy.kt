package com.elysium369.meet.ui

/** Prevents a removed legacy endpoint from generating an error every ten seconds. */
internal object MarketplaceCloudSyncPolicy {
    fun isMissingLegacyServiceSchema(rawMessage: String?): Boolean {
        val message = rawMessage.orEmpty().lowercase()
        val namesLegacyServiceTable =
            "service_requests" in message || "service_bids" in message
        val reportsMissingSchema =
            "could not find the table" in message ||
                "schema cache" in message ||
                "pgrst205" in message ||
                ("relation" in message && "does not exist" in message)
        return namesLegacyServiceTable && reportsMissingSchema
    }
}
