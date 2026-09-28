package com.elysium.nexus.fabric.infrared.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * V06.3 Phase: Catalog signal integrity gate.
 *
 * Guards against the cmd/sub_device parameter swap bug where ALL parametric
 * signals had command_value=-1, causing NEC/SIRC/Samsung encoders to fail
 * with "command must be in [0, 255] (got -1)".
 *
 * Also guards against SIRC variant name mismatch where catalog stored lowercase
 * "sirc"/"sirc15"/"sirc20" but runtime expected "SIRC_12"/"SIRC_15"/"SIRC_20".
 *
 * Uses system sqlite3 CLI instead of JDBC to avoid dependency issues.
 */
class CatalogSignalIntegrityTest {

    private fun sqliteQuery(dbFile: File, sql: String): String {
        val pb = ProcessBuilder("sqlite3", dbFile.absolutePath, sql)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor(30, TimeUnit.SECONDS)
        assertEquals(
            "sqlite3 must exit cleanly for: $sql",
            0, proc.exitValue()
        )
        return output
    }

    private fun hasSqlite3(): Boolean {
        return try {
            val pb = ProcessBuilder("which", "sqlite3")
            pb.redirectErrorStream(true)
            val proc = pb.start()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    @Test
    fun allParametricSignalsHaveValidCommandValues() {
        assumeTrue("sqlite3 CLI must be available", hasSqlite3())
        val dbFile = PackagedCatalogTestAsset.decoded()
        assertTrue("ir_catalog.db must exist", dbFile.exists())

        val invalidCount = sqliteQuery(dbFile,
            "SELECT COUNT(*) FROM signals WHERE encoding_type = 'PARAMETRIC' AND (command_value < 0 OR command_value > 255);"
        ).trim().toIntOrNull() ?: -1

        assertEquals(
            "All PARAMETRIC signals must have command_value in [0, 255] " +
                "(cmd/sub_device parameter swap regression)",
            0, invalidCount
        )
    }

    @Test
    fun volumeUpCandidatesExist() {
        assumeTrue("sqlite3 CLI must be available", hasSqlite3())
        val dbFile = PackagedCatalogTestAsset.decoded()
        assertTrue("ir_catalog.db must exist", dbFile.exists())

        val count = sqliteQuery(dbFile,
            """
            SELECT COUNT(DISTINCT cs.id)
            FROM code_sets cs
            JOIN command_bindings cb ON cb.code_set_id = cs.id
            JOIN actions a ON cb.action_id = a.id
            JOIN signals sig ON cb.signal_id = sig.id
            WHERE a.canonical_key = 'VOLUME_UP'
              AND sig.command_value >= 0 AND sig.command_value <= 255;
            """.trimIndent()
        ).trim().toIntOrNull() ?: -1

        assertTrue(
            "Must have at least 100 valid VOLUME_UP candidates (got $count)",
            count >= 100
        )
    }

    @Test
    fun necProtocolSignalsHaveValidCommandValues() {
        assumeTrue("sqlite3 CLI must be available", hasSqlite3())
        val dbFile = PackagedCatalogTestAsset.decoded()
        assertTrue("ir_catalog.db must exist", dbFile.exists())

        val invalidCount = sqliteQuery(dbFile,
            """
            SELECT COUNT(*) FROM signals sig
            JOIN protocol_definitions pd ON sig.protocol_definition_id = pd.id
            WHERE sig.encoding_type = 'PARAMETRIC'
              AND pd.family_name = 'NEC'
              AND (sig.command_value < 0 OR sig.command_value > 255);
            """.trimIndent()
        ).trim().toIntOrNull() ?: -1

        assertEquals(
            "All NEC parametric signals must have valid command_value",
            0, invalidCount
        )
    }

    @Test
    fun protocolVariantNamesAreNormalized() {
        assumeTrue("sqlite3 CLI must be available", hasSqlite3())
        val dbFile = PackagedCatalogTestAsset.decoded()
        assertTrue("ir_catalog.db must exist", dbFile.exists())

        // All signal-bearing variants must use UPPERCASE_WITH_UNDERSCORES names
        // that match the runtime ProtocolCodecRegistry variant IDs.
        val badVariants = sqliteQuery(dbFile,
            """
            SELECT pv.variant_name || '|' || COUNT(DISTINCT sig.id)
            FROM protocol_variants pv
            JOIN signals sig ON sig.protocol_variant_id = pv.id
            WHERE sig.encoding_type = 'PARAMETRIC'
              AND sig.command_value >= 0 AND sig.command_value <= 255
              AND pv.variant_name NOT IN (
                'NEC_32', 'NEC_32_EXT', 'NECx_32', 'SAMSUNG_32', 'SAMSUNG_20',
                'SIRC_12', 'SIRC_15', 'SIRC_20', 'RC5_14', 'RC5X_16',
                'RC6_16', 'RC6_20', 'KASEIKYO_48', 'PIONEER_40', 'SHARP_42',
                'NEC_42', 'NEC_48', 'AIWA_42', 'SAMSUNG_36', 'APPLE_32',
                'JVC_16', 'MITSUBISHI_16', 'DENON_32'
              )
            GROUP BY pv.variant_name
            HAVING COUNT(DISTINCT sig.id) > 0;
            """.trimIndent()
        ).trim()

        assertEquals(
            "All signal-bearing protocol variants must have normalized names " +
                "(no lowercase like 'sirc', 'nec', 'rc5'). Found: ${badVariants.ifEmpty { "none" }}",
            "", badVariants
        )
    }
}
