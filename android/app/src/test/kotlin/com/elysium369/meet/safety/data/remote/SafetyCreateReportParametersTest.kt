package com.elysium369.meet.safety.data.remote

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SafetyCreateReportParametersTest {
    @Test fun forwardsMapProvenanceAndReportedDemographicsWithoutPromotingTruth() {
        val payload = buildJsonObject {
            put("locationSource","MAP_SELECTION"); put("latitude",9.9); put("longitude",-84.1)
            put("reportedVictimCount",3); put("reportedVictimFemale",1); put("reportedVictimMale",1)
        }
        val result = safetyCreateReportParameters("report","command","digest",payload)
        assertEquals("MAP_SELECTION",result["p_location_source"]?.jsonPrimitive?.content)
        assertEquals(3,result["p_reported_victim_count"]?.jsonPrimitive?.int)
        assertEquals(payload["latitude"],result["p_latitude"])
        assertFalse(result.containsKey("public_latitude"))
        assertFalse(result.containsKey("documented_victim_count"))
    }
    @Test fun unknownCountsStayNullRatherThanBecomingZero() {
        val result = safetyCreateReportParameters("report","command","digest",buildJsonObject {})
        assertEquals(JsonNull,result["p_reported_victim_count"])
        assertEquals(JsonNull,result["p_reported_victim_female"])
        assertEquals(JsonNull,result["p_reported_victim_male"])
        assertEquals("NONE",result["p_location_source"]?.jsonPrimitive?.content)
    }
}
