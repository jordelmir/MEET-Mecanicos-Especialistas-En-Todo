package com.elysium369.meet.communications

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.elysium369.meet.operations.ActiveOperation
import com.elysium369.meet.operations.ActiveOperationsRegistry
import com.elysium369.meet.operations.OperationOwner
import com.elysium369.meet.operations.OperationRecoverability
import com.elysium369.meet.operations.OperationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceNoteRecordingState {
    data object Idle : VoiceNoteRecordingState
    data class Recording(val conversationId: String, val startedAtEpochMs: Long) : VoiceNoteRecordingState
    data class Failed(val safeCode: String) : VoiceNoteRecordingState
}

data class VoiceNoteDraft(val conversationId: String, val file: File, val durationMs: Long)

/** Application-scoped recorder: destination disposal is not a stop command. */
@Singleton
class VoiceNoteRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operationsRegistry: ActiveOperationsRegistry,
) {
    private val mutableState = MutableStateFlow<VoiceNoteRecordingState>(VoiceNoteRecordingState.Idle)
    val state: StateFlow<VoiceNoteRecordingState> = mutableState.asStateFlow()
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtEpochMs: Long = 0L
    private var conversationId: String? = null

    @Synchronized
    fun start(conversationId: String): Boolean {
        require(conversationId.isNotBlank())
        if (recorder != null) return true
        return runCatching {
            val directory = File(context.filesDir, "communication_voice_notes").apply { mkdirs() }
            val file = File(directory, "voice-${UUID.randomUUID()}.m4a")
            val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(96_000)
            next.setAudioSamplingRate(44_100)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            recorder = next
            outputFile = file
            startedAtEpochMs = System.currentTimeMillis()
            this.conversationId = conversationId
            mutableState.value = VoiceNoteRecordingState.Recording(conversationId, startedAtEpochMs)
            operationsRegistry.upsert(
                ActiveOperation(
                    operationId = OPERATION_ID,
                    type = "COMMUNICATION_VOICE_NOTE",
                    vehicleId = null,
                    startedAtEpochMs = startedAtEpochMs,
                    state = OperationState.RUNNING,
                    progress = null,
                    owner = OperationOwner.APPLICATION_SCOPED,
                    recoverability = OperationRecoverability.ACTIVITY_RECREATION,
                    lastHeartbeatEpochMs = startedAtEpochMs,
                ),
            )
            true
        }.getOrElse {
            release(deleteOutput = true)
            mutableState.value = VoiceNoteRecordingState.Failed("RECORDER_START_FAILED")
            false
        }
    }

    @Synchronized
    fun stop(): VoiceNoteDraft? {
        val active = recorder ?: return null
        val file = outputFile
        val targetConversationId = conversationId
        val duration = (System.currentTimeMillis() - startedAtEpochMs).coerceAtLeast(0L)
        return runCatching {
            active.stop()
            active.release()
            recorder = null
            outputFile = null
            conversationId = null
            mutableState.value = VoiceNoteRecordingState.Idle
            operationsRegistry.complete(OPERATION_ID)
            if (targetConversationId == null) null else file
                ?.takeIf { it.isFile && it.length() > 0L }
                ?.let { VoiceNoteDraft(targetConversationId, it, duration) }
        }.getOrElse {
            release(deleteOutput = true)
            mutableState.value = VoiceNoteRecordingState.Failed("RECORDER_STOP_FAILED")
            null
        }
    }

    @Synchronized
    fun cancel() = release(deleteOutput = true)

    private fun release(deleteOutput: Boolean) {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        if (deleteOutput) runCatching { outputFile?.delete() }
        outputFile = null
        conversationId = null
        startedAtEpochMs = 0L
        mutableState.value = VoiceNoteRecordingState.Idle
        operationsRegistry.complete(OPERATION_ID)
    }

    private companion object { const val OPERATION_ID = "communication-voice-note" }
}
