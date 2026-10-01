package com.elysium.nexus.fabric.infrared.database

import com.elysium.nexus.core.device.CodeProvenance
import com.elysium.nexus.core.device.IrAction
import com.elysium.nexus.core.device.IrCodeSet
import com.elysium.nexus.core.device.IrSignal
import com.elysium.nexus.core.device.VerificationStatus
import com.elysium.nexus.fabric.infrared.IrProtocol
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Universal sweep contract (§43): the "Control Universal" first card must
 * sweep every production-approved TV code set regardless of brand.
 * [IrCatalog.getAllCandidates] returns all candidates carrying [IrAction.VOLUME_UP].
 */
class UniversalCatalogSweepTest {

    private fun codeSet(id: String, brand: String, address: Int): IrCodeSet = IrCodeSet(
        id = id,
        brand = brand,
        modelPatterns = setOf("TV"),
        remoteModels = emptySet(),
        commands = mapOf(
            IrAction.VOLUME_UP to IrSignal.Encoded(carrierHz = 38_000, protocol = IrProtocol.Nec, address = address, command = 0x07),
            IrAction.VOLUME_DOWN to IrSignal.Encoded(carrierHz = 38_000, protocol = IrProtocol.Nec, address = address, command = 0x0A)
        ),
        provenance = CodeProvenance(brand, "http://example.com/$brand", "MIT"),
        verification = VerificationStatus.UNVERIFIED
    )

    @Test
    fun `getAllCandidates sweeps every brand in the catalog`() = runBlocking {
        val catalog = InMemoryIrCatalog(
            candidateMap = mapOf(
                "Sankey" to listOf(codeSet("s1", "Sankey", 0x00), codeSet("s2", "Sankey", 0x04)),
                "Kintech" to listOf(codeSet("k1", "Kintech", 0x00)),
                "Honor" to listOf(codeSet("h1", "Honor", 0x07)),
                "NoVol" to listOf(
                    IrCodeSet(
                        id = "nv1",
                        brand = "NoVol",
                        modelPatterns = setOf("X"),
                        remoteModels = emptySet(),
                        commands = mapOf(
                            IrAction.POWER_TOGGLE to IrSignal.Encoded(carrierHz = 38_000, protocol = IrProtocol.Nec, address = 0x00, command = 0x02)
                        ),
                        provenance = CodeProvenance("NoVol", "http://no", "MIT"),
                        verification = VerificationStatus.UNVERIFIED
                    )
                )
            )
        )

        val all = catalog.getAllCandidates(deviceType = "TV", action = IrAction.VOLUME_UP)
        assertEquals("Universal sweep flattens every brand with VOLUME_UP", 4, all.size)
        assertTrue(all.any { it.brand == "Kintech" })
        assertTrue(all.any { it.brand == "Honor" })
        assertTrue(all.none { it.id == "nv1" })
    }

    @Test
    fun `universal sweep honors the requested limit to stay responsive`() = runBlocking {
        val catalog = InMemoryIrCatalog(
            candidateMap = (listOf("A", "B", "C", "D", "E", "F")).associateWith { brand ->
                (1..20).map { codeSet("$brand$it", brand, it) }
            }
        )
        val all = catalog.getAllCandidates(deviceType = "TV", action = IrAction.VOLUME_UP, limit = 100)
        assertEquals("Universal sweep caps at the requested limit", 100, all.size)
    }
}