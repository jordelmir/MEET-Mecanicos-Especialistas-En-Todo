package com.elysium369.meet.mobility

import com.elysium369.meet.mobility.data.room.OutboxState
import com.elysium369.meet.mobility.domain.models.DispatchMode
import com.elysium369.meet.mobility.domain.models.DispatchOfferState
import com.elysium369.meet.mobility.domain.models.DriverOfferState
import com.elysium369.meet.mobility.domain.models.RideRequestState
import com.elysium369.meet.mobility.domain.models.RideStopType
import com.elysium369.meet.mobility.domain.models.TripState
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MobilitySchemaParityTest {

    @Test
    fun verifyStateEnumsMatchJsonContract() {
        val contractFile = File("../../contracts/mobility-states.json").takeIf { it.exists() }
            ?: File("contracts/mobility-states.json").takeIf { it.exists() }
            ?: File("../contracts/mobility-states.json")

        val json = Json.parseToJsonElement(contractFile.readText()).jsonObject
        val aggregates = json["aggregates"]!!.jsonObject

        fun check(key: String, kotlinNames: List<String>) {
            val contractNames = aggregates[key]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("Mismatch for enum $key", contractNames, kotlinNames)
        }

        check("RideRequestState", RideRequestState.entries.map { it.name })
        check("DispatchMode", DispatchMode.entries.map { it.name })
        check("RideStopType", RideStopType.entries.map { it.name })
        check("DriverOfferState", DriverOfferState.entries.map { it.name })
        check("DispatchOfferState", DispatchOfferState.entries.map { it.name })
        check("TripState", TripState.entries.map { it.name })
        check("OutboxState", OutboxState.entries.map { it.name })
    }
}
