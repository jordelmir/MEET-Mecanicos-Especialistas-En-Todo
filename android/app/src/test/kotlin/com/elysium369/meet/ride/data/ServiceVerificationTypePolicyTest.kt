package com.elysium369.meet.ride.data

import com.elysium369.meet.ride.data.remote.ServiceVerificationTypePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceVerificationTypePolicyTest {
    @Test
    fun `local provider aliases become server canonical identifiers`() {
        assertEquals("MECHANIC", ServiceVerificationTypePolicy.canonicalLegacyType("mechanic"))
        assertEquals("TOW_TRUCK", ServiceVerificationTypePolicy.canonicalLegacyType("tow_provider"))
        assertEquals("PARTS_STORE", ServiceVerificationTypePolicy.canonicalLegacyType("parts_store"))
        assertEquals("RIDE_DRIVER", ServiceVerificationTypePolicy.canonicalLegacyType("driver"))
        assertEquals("SERVICE_PROVIDER", ServiceVerificationTypePolicy.canonicalLegacyType("service_provider"))
    }

    @Test
    fun `unknown or privileged-looking values cannot enter legacy submission`() {
        assertNull(ServiceVerificationTypePolicy.canonicalLegacyType("platform_owner"))
        assertNull(ServiceVerificationTypePolicy.canonicalLegacyType("lawyer"))
        assertNull(ServiceVerificationTypePolicy.canonicalLegacyType(""))
    }

    @Test
    fun `every server capability has an explicit client identifier`() {
        assertEquals(13, ServiceVerificationTypePolicy.capabilityTypes.size)
        assertTrue("WORKSHOP" in ServiceVerificationTypePolicy.capabilityTypes)
        assertTrue("AUTO_LOCKSMITH" in ServiceVerificationTypePolicy.capabilityTypes)
        assertTrue("LAWYER" in ServiceVerificationTypePolicy.capabilityTypes)
        assertTrue("NOTARY" in ServiceVerificationTypePolicy.capabilityTypes)
        assertTrue("PROPERTY_BROKER" in ServiceVerificationTypePolicy.capabilityTypes)
        assertTrue("PROPERTY_SELLER" in ServiceVerificationTypePolicy.capabilityTypes)
        assertTrue("FUEL_STATION_STAFF" in ServiceVerificationTypePolicy.capabilityTypes)
        assertTrue("FLEET_OPERATOR" in ServiceVerificationTypePolicy.capabilityTypes)
    }

    @Test
    fun `every provider registration alias enters the unified review queue`() {
        assertEquals("WORKSHOP", ServiceVerificationTypePolicy.canonicalSubmissionType("workshop"))
        assertEquals(
            "AUTO_LOCKSMITH",
            ServiceVerificationTypePolicy.canonicalSubmissionType("auto_locksmith"),
        )
        assertEquals("TOW_TRUCK", ServiceVerificationTypePolicy.canonicalSubmissionType("tow_provider"))
        assertNull(ServiceVerificationTypePolicy.canonicalSubmissionType("platform_owner"))
    }
}
