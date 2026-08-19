package com.elysium369.meet.ride.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VanguardEventSchemaContractTest {

    private fun projectFile(path: String): File = listOf(
        File(path),
        File("app/$path"),
        File("android/app/$path"),
        File("../$path"),
        File("../../$path"),
    ).firstOrNull(File::isFile) ?: error("Missing project file $path")

    @Test
    fun `authoritative repair migration contains meet_emit_vanguard_event_v2 with exact columns`() {
        val migrationFile = projectFile("supabase/migrations/20260819000000_repair_v2_authoritative_state_machine.sql")
        val content = migrationFile.readText()

        assertTrue(
            "Must define meet_emit_vanguard_event_v2 helper",
            content.contains("CREATE OR REPLACE FUNCTION public.meet_emit_vanguard_event_v2")
        )
        assertTrue(
            "Must insert event_id column",
            content.contains("event_id,")
        )
        assertTrue(
            "Must insert aggregate_type column",
            content.contains("aggregate_type,")
        )
        assertTrue(
            "Must insert aggregate_id column",
            content.contains("aggregate_id,")
        )
        assertTrue(
            "Must insert occurred_at_ms column",
            content.contains("occurred_at_ms,")
        )
        assertTrue(
            "Must insert payload_json column",
            content.contains("payload_json,")
        )
        assertTrue(
            "Must check expected_version optimistic locking",
            content.contains("p_expected_version") && content.contains("STALE_COMMAND")
        )
        assertTrue(
            "Must guard against idempotency equivocation",
            content.contains("IDEMPOTENCY_EQUIVOCATION")
        )
    }
}
