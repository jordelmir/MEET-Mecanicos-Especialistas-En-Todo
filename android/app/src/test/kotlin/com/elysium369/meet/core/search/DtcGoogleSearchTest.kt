package com.elysium369.meet.core.search

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcGoogleSearchTest {
    @Test
    fun `without active vehicle query contains only canonical dtc`() {
        val request = requireNotNull(DtcGoogleSearch.buildRequest(" p0303 ", null))

        assertEquals("P0303", request.dtc)
        assertEquals("P0303", request.query)
        assertEquals("P0303", decodedQuery(request.googleUrl))
    }

    @Test
    fun `active automatic vehicle adds exact safe technical fields`() {
        val request = requireNotNull(
            DtcGoogleSearch.buildRequest(
                "P0303",
                DtcGoogleSearchVehicleContext(
                    make = "Hyundai",
                    model = "Accent Verna",
                    year = 2005,
                    transmissionType = "Automatic",
                    displacementCc = 1600
                )
            )
        )

        assertEquals(
            "P0303 Hyundai Accent Verna 2005 automático 1600 cc",
            request.query
        )
    }

    @Test
    fun `manual transmission is normalized in spanish`() {
        val request = requireNotNull(
            DtcGoogleSearch.buildRequest(
                "p0700",
                vehicle(transmission = "6MT")
            )
        )

        assertTrue(request.query.contains(" manual "))
        assertFalse(request.query.contains("automático"))
    }

    @Test
    fun `cvt dct dsg and at are automatic transmissions`() {
        listOf("CVT", "DCT", "DSG", "6AT", "A/T").forEach { transmission ->
            val request = requireNotNull(
                DtcGoogleSearch.buildRequest("P0700", vehicle(transmission))
            )

            assertTrue("$transmission should be automatic", request.query.contains("automático"))
        }
    }

    @Test
    fun `unknown or absent vehicle fields are omitted without placeholders`() {
        val request = requireNotNull(
            DtcGoogleSearch.buildRequest(
                "U0100",
                DtcGoogleSearchVehicleContext(
                    make = " ",
                    model = "\n",
                    year = 0,
                    transmissionType = "unknown",
                    displacementCc = 0
                )
            )
        )

        assertEquals("U0100", request.query)
        assertFalse(request.query.contains("unknown", ignoreCase = true))
        assertFalse(request.query.contains("genérico", ignoreCase = true))
    }

    @Test
    fun `google origin and path are fixed while query is encoded`() {
        val request = requireNotNull(
            DtcGoogleSearch.buildRequest(
                "C1234",
                DtcGoogleSearchVehicleContext(
                    make = "Škoda",
                    model = "Octavia RS",
                    year = 2024,
                    transmissionType = "Automático",
                    displacementCc = 1984
                )
            )
        )
        val uri = URI(request.googleUrl)

        assertEquals("https", uri.scheme)
        assertEquals("www.google.com", uri.host)
        assertEquals("/search", uri.path)
        assertEquals(request.query, decodedQuery(request.googleUrl))
    }

    @Test
    fun `control characters are collapsed and cannot alter google url`() {
        val request = requireNotNull(
            DtcGoogleSearch.buildRequest(
                "B0001",
                DtcGoogleSearchVehicleContext(
                    make = "Toyota\r\nhttps://evil.example?x=1&q=owned",
                    model = "Corolla\tSedan",
                    year = 2014,
                    transmissionType = "Manual",
                    displacementCc = 1798
                )
            )
        )
        val uri = URI(request.googleUrl)

        assertEquals("www.google.com", uri.host)
        assertEquals("/search", uri.path)
        assertFalse(uri.rawQuery.substringAfter("q=").contains("&q="))
        assertFalse(request.query.contains('\n'))
        assertFalse(request.query.contains('\r'))
        assertFalse(request.query.contains('\t'))
    }

    @Test
    fun `invalid or ambiguous dtc fails closed`() {
        listOf(
            "",
            "P030",
            "P03030",
            "X0303",
            "P03G3",
            "P0303 causes",
            "https://example.com"
        ).forEach { raw ->
            assertNull("Expected invalid DTC: $raw", DtcGoogleSearch.buildRequest(raw, null))
        }
    }

    @Test
    fun `vehicle context has no private identity fields`() {
        val publicFields = DtcGoogleSearchVehicleContext::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()

        assertEquals(
            setOf("make", "model", "year", "transmissionType", "displacementCc"),
            publicFields
        )
        assertFalse(publicFields.contains("vin"))
        assertFalse(publicFields.contains("plate"))
        assertFalse(publicFields.contains("userId"))
    }

    private fun vehicle(transmission: String) = DtcGoogleSearchVehicleContext(
        make = "Toyota",
        model = "Corolla",
        year = 2014,
        transmissionType = transmission,
        displacementCc = 1798
    )

    private fun decodedQuery(url: String): String {
        val rawQuery = URI(url).rawQuery
        val encoded = rawQuery.substringAfter("q=")
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
    }
}
