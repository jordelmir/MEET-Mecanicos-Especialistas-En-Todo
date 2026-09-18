package com.elysium369.meet.communications

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RealtimeLiveAudioEngine — Low-latency, bidirectional PCM voice streaming engine.
 *
 * Captures 16kHz 16-bit mono audio with hardware Acoustic Echo Cancellation (AEC)
 * and Noise Suppression (NS) when supported, and outputs through STREAM_VOICE_CALL.
 */
@Singleton
class RealtimeLiveAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "RealtimeLiveAudioEngine"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 40ms audio chunks: 16000 samples/sec * 2 bytes/sample * 0.040 sec = 1280 bytes
        const val CHUNK_SIZE = 1280
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(
        scope: CoroutineScope,
        onAudioChunk: (ByteArray) -> Unit,
    ): Boolean {
        if (isRecording.get()) return true

        return runCatching {
            val minRecordBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
            ).coerceAtLeast(CHUNK_SIZE * 4)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                minRecordBufferSize,
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                record.release()
                return false
            }

            // Enable hardware echo cancellation & noise suppression
            val audioSessionId = record.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = true
                }
            }

            // Setup AudioTrack for playback
            val minTrackBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT,
            ).coerceAtLeast(CHUNK_SIZE * 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build(),
                )
                .setBufferSizeInBytes(minTrackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack initialization failed")
                record.release()
                track.release()
                return false
            }

            audioRecord = record
            audioTrack = track

            // Configure audio routing for speaker / earpiece call
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true

            track.play()
            isPlaying.set(true)

            record.startRecording()
            isRecording.set(true)

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE)
                while (isActive && isRecording.get()) {
                    val readBytes = record.read(buffer, 0, CHUNK_SIZE)
                    if (readBytes > 0) {
                        if (!isMuted.get()) {
                            val chunk = buffer.copyOf(readBytes)
                            onAudioChunk(chunk)
                        }
                    }
                }
            }
            Log.i(TAG, "Realtime Live Audio Engine started successfully")
            true
        }.getOrElse { error ->
            Log.e(TAG, "Error starting Realtime Live Audio Engine", error)
            stop()
            false
        }
    }

    /**
     * Plays an incoming audio chunk from the remote peer.
     */
    fun playAudioChunk(chunk: ByteArray) {
        val track = audioTrack ?: return
        if (!isPlaying.get()) return
        runCatching {
            track.write(chunk, 0, chunk.size)
        }.onFailure { error ->
            Log.w(TAG, "Error writing audio chunk to AudioTrack", error)
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun isMuted(): Boolean = isMuted.get()

    @Synchronized
    fun stop() {
        isRecording.set(false)
        isPlaying.set(false)
        recordingJob?.cancel()
        recordingJob = null

        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null

        runCatching {
            audioTrack?.stop()
            audioTrack?.flush()
            audioTrack?.release()
        }
        audioTrack = null

        runCatching { echoCanceler?.release() }
        echoCanceler = null

        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null

        runCatching { gainControl?.release() }
        gainControl = null

        runCatching {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        }
        Log.i(TAG, "Realtime Live Audio Engine stopped")
    }
}
