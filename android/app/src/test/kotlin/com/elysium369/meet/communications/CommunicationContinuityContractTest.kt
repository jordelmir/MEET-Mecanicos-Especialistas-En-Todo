package com.elysium369.meet.communications

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationContinuityContractTest {
    private val projectDir: File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .flatMap { dir -> sequenceOf(dir, File(dir, "android")) }
        .first { candidate -> File(candidate, "app/src/main/kotlin").isDirectory }

    private fun source(relative: String) = File(projectDir, "app/src/main/kotlin/$relative").readText()

    @Test
    fun `voice note recorder is application scoped and navigation cannot stop it`() {
        val recorder = source("com/elysium369/meet/communications/VoiceNoteRecorder.kt")
        val screen = source("com/elysium369/meet/ui/screens/MessagesScreen.kt")
        assertTrue(recorder.contains("@Singleton"))
        assertTrue(recorder.contains("OperationOwner.APPLICATION_SCOPED"))
        assertFalse(screen.contains("onDispose") && screen.contains("cancelVoiceNote"))
    }

    @Test
    fun `typed events and replies are preserved in encrypted ledger`() {
        val repository = source("com/elysium369/meet/communications/ElysiumCommunicationRepository.kt")
        assertTrue(repository.contains("eventType = \"VOICE_NOTE\""))
        assertTrue(repository.contains("replyToEventId = replyToEventId"))
        assertTrue(repository.contains("cipher.encrypt("))
    }
}
