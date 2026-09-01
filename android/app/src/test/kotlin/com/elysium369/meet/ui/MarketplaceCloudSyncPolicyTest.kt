package com.elysium369.meet.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceCloudSyncPolicyTest {
    @Test
    fun `missing legacy service schema disables only its background poll`() {
        assertTrue(
            MarketplaceCloudSyncPolicy.isMissingLegacyServiceSchema(
                "Could not find the table 'public.service_requests' in the schema cache",
            ),
        )
        assertTrue(
            MarketplaceCloudSyncPolicy.isMissingLegacyServiceSchema(
                "PGRST205 relation public.service_bids does not exist",
            ),
        )
        assertFalse(
            MarketplaceCloudSyncPolicy.isMissingLegacyServiceSchema(
                "java.net.SocketTimeoutException: timed out",
            ),
        )
        assertFalse(
            MarketplaceCloudSyncPolicy.isMissingLegacyServiceSchema(
                "Could not find the table public.part_requests",
            ),
        )
    }
}
