package com.elysium369.meet.ride.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SinpeReceiptParserTest {

    @Test
    fun testBacReceiptParsing() {
        val email = """
            Notificación de Transferencia SINPE Móvil BAC San José
            Estimado cliente, ha transferido fondos exitosamente.
            Monto: ₡ 15,000.00
            Número de comprobante: 20260913998877
            Teléfono origen: 8888-1111
            Destino: jordelmir@gmail.com
            Fecha: 13/09/2026 10:30:00
        """.trimIndent()

        val parsed = SinpeReceiptParser.parse(email)
        assertNotNull(parsed)
        assertEquals(SinpeBank.BAC, parsed!!.bank)
        assertEquals("20260913998877", parsed.referenceNumber)
        assertEquals(15000.0, parsed.amountCrc, 0.01)
        assertEquals("8888-1111", parsed.senderPhoneOrName)
        assertEquals("jordelmir@gmail.com", parsed.recipientInfo)
        assertTrue(parsed.isValid)
    }

    @Test
    fun testBncrReceiptParsing() {
        val sms = """
            BN Móvil: Transferencia SINPE Móvil enviada por ₡5000 a 87654321. Ref: BN987654321.
        """.trimIndent()

        val parsed = SinpeReceiptParser.parse(sms)
        assertNotNull(parsed)
        assertEquals(SinpeBank.BNCR, parsed!!.bank)
        assertEquals("BN987654321", parsed.referenceNumber)
        assertEquals(5000.0, parsed.amountCrc, 0.01)
        assertTrue(parsed.isValid)
    }

    @Test
    fun testBcrReceiptParsing() {
        val receipt = """
            Comprobante SINPE BCR
            Monto transferido: 25,500.00 CRC
            Referencia: BCR20260913001
            De: Carlos Ramirez
        """.trimIndent()

        val parsed = SinpeReceiptParser.parse(receipt)
        assertNotNull(parsed)
        assertEquals(SinpeBank.BCR, parsed!!.bank)
        assertEquals("BCR20260913001", parsed.referenceNumber)
        assertEquals(25500.0, parsed.amountCrc, 0.01)
        assertEquals("Carlos Ramirez", parsed.senderPhoneOrName)
        assertTrue(parsed.isValid)
    }
}
