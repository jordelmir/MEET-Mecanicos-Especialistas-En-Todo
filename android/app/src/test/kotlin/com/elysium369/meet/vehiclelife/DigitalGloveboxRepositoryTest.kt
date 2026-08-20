package com.elysium369.meet.vehiclelife

import com.elysium369.meet.vehiclelife.documents.DefaultDigitalGloveboxRepository
import com.elysium369.meet.vehiclelife.documents.DocumentType
import com.elysium369.meet.vehiclelife.documents.VehicleDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DigitalGloveboxRepositoryTest {

    @Test
    fun testSaveAndExpiringDocumentDetection() = runBlocking {
        val repo = DefaultDigitalGloveboxRepository()
        val now = System.currentTimeMillis()
        val in10Days = now + (10L * 24 * 60 * 60 * 1000)
        val in60Days = now + (60L * 24 * 60 * 60 * 1000)

        val docExpiringSoon = VehicleDocument(
            documentId = "DOC_INS_01",
            vehicleId = "V-001",
            ownerPrincipalId = "USR-01",
            type = DocumentType.INSURANCE_POLICY,
            title = "Póliza Todo Riesgo",
            issuer = "INS",
            issueDateUtc = now - (300L * 24 * 60 * 60 * 1000),
            expiryDateUtc = in10Days,
            fileUri = null,
            fileSha256 = null
        )

        val docExpiringLater = VehicleDocument(
            documentId = "DOC_RTV_01",
            vehicleId = "V-001",
            ownerPrincipalId = "USR-01",
            type = DocumentType.TECHNICAL_INSPECTION,
            title = "Dekra 2026",
            issuer = "Dekra",
            issueDateUtc = now - (100L * 24 * 60 * 60 * 1000),
            expiryDateUtc = in60Days,
            fileUri = null,
            fileSha256 = null
        )

        repo.saveDocument(docExpiringSoon)
        repo.saveDocument(docExpiringLater)

        val allDocs = repo.getDocumentsForVehicle("V-001")
        assertEquals(2, allDocs.size)

        val expiringWithin30Days = repo.getExpiringDocuments("V-001", withinDays = 30)
        assertEquals(1, expiringWithin30Days.size)
        assertEquals("DOC_INS_01", expiringWithin30Days.first().documentId)
    }
}
