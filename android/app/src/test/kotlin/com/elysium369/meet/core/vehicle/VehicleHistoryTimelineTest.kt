package com.elysium369.meet.core.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleHistoryTimelineTest {
    @Test fun `add events with chain hashing`() {
        val e = VehicleHistoryTimeline()
        val ev1 = e.addEvent("v1", VehicleEventType.OBD_SCAN, "Escaneo inicial", "Sin DTCs", mileageKm = 50000)
        val ev2 = e.addEvent("v1", VehicleEventType.REPAIR_COMPLETED, "Cambio de aceite", "Aceite 5W-30", mileageKm = 50000)
        assertEquals(ev1.eventHash, ev2.prevHash)
        assertTrue(ev1.eventHash.length == 64)
    }
    @Test fun `chain verification passes for intact chain`() {
        val e = VehicleHistoryTimeline()
        e.addEvent("v1", VehicleEventType.OBD_SCAN, "Scan 1", "OK")
        e.addEvent("v1", VehicleEventType.REPAIR_COMPLETED, "Repair 1", "Done")
        val v = e.verifyChain("v1")
        assertTrue(v.isValid)
        assertEquals(2, v.verifiedEvents)
    }
    @Test fun `summary counts events correctly`() {
        val e = VehicleHistoryTimeline()
        e.addEvent("v1", VehicleEventType.OBD_SCAN, "Scan", "OK")
        e.addEvent("v1", VehicleEventType.REPAIR_COMPLETED, "Repair", "Done")
        e.addEvent("v1", VehicleEventType.PART_REPLACED, "Part", "New filter")
        e.addEvent("v1", VehicleEventType.INCIDENT, "Accidente", "Leve")
        val s = e.getSummary("v1")
        assertEquals(4, s.totalEvents)
        assertEquals(1, s.totalRepairs)
        assertEquals(1, s.totalScans)
        assertEquals(1, s.totalParts)
        assertEquals(1, s.totalIncidents)
    }
    @Test fun `search events by query`() {
        val e = VehicleHistoryTimeline()
        e.addEvent("v1", VehicleEventType.DTC_FOUND, "DTC P0301", "Misfire cyl 1", relatedDtcCodes = listOf("P0301"))
        e.addEvent("v1", VehicleEventType.REPAIR_COMPLETED, "Cambio bujías", "Sparkplugs replaced")
        val results = e.searchEvents("v1", "P0301")
        assertEquals(1, results.size)
    }
    @Test fun `share text contains key info`() {
        val e = VehicleHistoryTimeline()
        e.addEvent("v1", VehicleEventType.OBD_SCAN, "Scan", "OK", mileageKm = 75000)
        val text = e.generateShareText("v1")
        assertTrue(text.contains("ELYSIUM"))
        assertTrue(text.contains("1 eventos"))
    }
    @Test fun `clean history means no incidents`() {
        val e = VehicleHistoryTimeline()
        e.addEvent("v1", VehicleEventType.MAINTENANCE, "Oil change", "Done")
        assertTrue(e.getSummary("v1").cleanHistory)
    }
}
