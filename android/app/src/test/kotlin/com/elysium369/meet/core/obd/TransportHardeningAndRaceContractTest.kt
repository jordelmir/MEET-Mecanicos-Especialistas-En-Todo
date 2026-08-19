package com.elysium369.meet.core.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransportHardeningAndRaceContractTest {

    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `ble transport rejects question mark in elm readiness check`() {
        val bleFile = projectFile("src/main/kotlin/com/elysium369/meet/core/transport/BleTransport.kt")
        val content = bleFile.readText()

        assertTrue("BleTransport must enforce gattOperationMutex queue", content.contains("gattOperationMutex"))
        assertTrue("BleTransport must record droppedResponseCount", content.contains("droppedResponseCount"))
        assertTrue("BleTransport must record accumulatorOverflowCount", content.contains("accumulatorOverflowCount"))
        assertTrue("BleTransport must explicitly reject question mark", content.contains("!probeStr.contains(\"?\")"))
        assertFalse("BleTransport must not accept question mark as readiness", content.contains("probeStr.contains(\"?\")\n                    )"))
    }

    @Test
    fun `obd session protects against transport target race conditions`() {
        val obdSessionFile = projectFile("src/main/kotlin/com/elysium369/meet/core/obd/ObdSession.kt")
        val content = obdSessionFile.readText()

        assertTrue("ObdSession must use transportGenerationId", content.contains("transportGenerationId"))
        assertTrue("ObdSession must use transportLifecycleMutex", content.contains("transportLifecycleMutex"))
        assertTrue("ObdSession must abort stale connect attempt on generation mismatch", content.contains("currentGen != transportGenerationId.get()"))
    }
}
