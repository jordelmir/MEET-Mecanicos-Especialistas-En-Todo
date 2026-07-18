package com.elysium369.meet.core.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-runtime parity test — Kotlin side.
 *
 * Mirror of `tests/parity/hash-parity.ts` from the TypeScript web build.
 * Reads the same JSON fixture, builds the equivalent [DiagnosticSnapshot],
 * SHA-256s the canonical string, and writes the result to a file the
 * `tests/parity/ci-verify.sh` wrapper can diff against the TS output.
 *
 * The expected output file path is fixed by `ci-verify.sh`:
 *
 *   android/app/build/reports/parity/snapshot-p0230.txt
 *
 * which maps to `app/build/reports/parity/snapshot-p0230.txt` from the
 * repo root. Gradle's `:app:testDebugUnitTest` task changes cwd to
 * `android/app/`, so writing to `build/reports/parity/...` lands in the
 * right place.
 *
 * If this test ever fails:
 *   - Either the Kotlin `computeHash` drifted (broken Kotlin side), or
 *   - Either the fixture's `expectedHash` changed without updating the
 *     Kotlin reference (broken TS side).
 * Both are hard breaks of the cross-runtime contract.
 */
class HashEngineParityTest {

    @Test
    fun `parity with TS for snapshot-p0230`() {
        val service = ReportHashingService()
        val result = service.p0230ParityDemo()

        // Capture stdout-style output so ci-verify.sh can diff with the TS side.
        // Trailing newline matches what the TS side writes via console.log.
        val expectedOut = buildString {
            appendLine("[OK] P0230 fuel-pump request (Hyundai Accent Verna 2005)")
            appendLine("  expected: ${result.expectedHash}")
            appendLine("  actual:   ${result.computedHash}")
        }

        // Write the output file. Path is fixed by ci-verify.sh.
        val outFile = File("build/reports/parity/snapshot-p0230.txt")
        outFile.parentFile?.mkdirs()
        outFile.writeText(expectedOut)

        // Hard assertions — drift = hard break of the contract.
        assertEquals(result.expectedHash, result.computedHash)
        assertTrue(
            "Parity broken — Kotlin produced ${result.computedHash}, TS expects ${result.expectedHash}",
            result.match,
        )
    }
}
