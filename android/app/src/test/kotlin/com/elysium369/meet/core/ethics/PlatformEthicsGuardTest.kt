package com.elysium369.meet.core.ethics

import org.junit.Assert.*
import org.junit.Test

class PlatformEthicsGuardTest {

    @Test
    fun `CONSTITUTIONAL — max fee is 5 percent`() {
        assertEquals(5.0, PlatformEthicsGuard.MAX_PLATFORM_FEE_PERCENT, 0.001)
    }

    @Test
    fun `micro transactions under 10 dollars are FREE`() {
        val fee = PlatformEthicsGuard.calculateFee(8.0)
        assertEquals(0.0, fee.feeAmount, 0.001)
        assertEquals(0.0, fee.feePercent, 0.001)
        assertEquals(8.0, fee.providerReceives, 0.001)
        assertTrue(fee.isMicroTransaction)
    }

    @Test
    fun `small transaction gets 5 percent fee`() {
        val fee = PlatformEthicsGuard.calculateFee(40.0)
        assertEquals(5.0, fee.feePercent, 0.001)
        assertEquals(2.0, fee.feeAmount, 0.001)
        assertEquals(38.0, fee.providerReceives, 0.001)
    }

    @Test
    fun `large transaction gets progressive lower fee`() {
        val fee = PlatformEthicsGuard.calculateFee(800.0)
        assertTrue(fee.feePercent < 5.0) // Progressive: lower for bigger
        assertTrue(fee.feePercent <= 3.0) // $800 → 2.5%
    }

    @Test
    fun `absolute fee cap at 50 dollars`() {
        val fee = PlatformEthicsGuard.calculateFee(5000.0)
        assertTrue(fee.feeAmount <= PlatformEthicsGuard.MAX_ABSOLUTE_FEE_USD)
    }

    @Test
    fun `fee disclosure includes constitutional limit`() {
        val fee = PlatformEthicsGuard.calculateFee(100.0)
        assertTrue(fee.disclosure.contains("5% máximo"))
        assertTrue(fee.disclosure.contains("Usted recibe"))
    }

    @Test
    fun `audit detects fee exceeding constitutional limit`() {
        val result = PlatformEthicsGuard.auditTransaction(100.0, 10.0)
        assertFalse(result.isCompliant)
        assertTrue(result.violations.any {
            it.code == "FEE_EXCEEDS_CONSTITUTIONAL_LIMIT"
        })
        assertTrue(result.violations.any {
            it.severity == EthicsViolationSeverity.CONSTITUTIONAL
        })
    }

    @Test
    fun `audit passes for compliant transaction`() {
        val result = PlatformEthicsGuard.auditTransaction(100.0, 4.0)
        assertTrue(result.isCompliant)
    }

    @Test
    fun `data export includes all 8 sections`() {
        val export = PlatformEthicsGuard.generateDataExport("user-1")
        assertEquals(8, export.sections.size)
        assertTrue(export.sections.any { it.key == "economic_passport" })
        assertTrue(export.sections.any { it.key == "trust_graph" })
        assertTrue(export.portabilityNotice.contains("TUYOS"))
    }

    @Test
    fun `provider always receives more than platform`() {
        // For any non-micro transaction, provider gets at least 95%
        listOf(20.0, 50.0, 100.0, 500.0, 1000.0, 5000.0).forEach { amount ->
            val fee = PlatformEthicsGuard.calculateFee(amount)
            assertTrue(
                "Provider should get >= 95% of $amount but got ${fee.providerReceives}",
                fee.providerReceives >= amount * 0.95
            )
        }
    }
}
