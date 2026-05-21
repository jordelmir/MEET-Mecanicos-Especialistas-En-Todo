package com.elysium369.meet.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.IOException

class VoicePlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressHandler: Handler? = null
    private var progressRunnable: Runnable? = null
    
    // Callback interfaces for playback status updates
    private var onProgressCallback: ((currentMs: Int, totalMs: Int) -> Unit)? = null
    private var onCompleteCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /**
     * Start playing an audio file from a local path or remote URL.
     */
    fun play(
        sourcePath: String,
        onProgress: (currentMs: Int, totalMs: Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Stop any active playback first
        stop()

        onProgressCallback = onProgress
        onCompleteCallback = onComplete
        onErrorCallback = onError

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            try {
                if (sourcePath.startsWith("http://") || sourcePath.startsWith("https://")) {
                    setDataSource(context, Uri.parse(sourcePath))
                } else {
                    setDataSource(sourcePath)
                }
                
                setOnPreparedListener { mp ->
                    mp.start()
                    startProgressTracker()
                }

                setOnCompletionListener {
                    stopProgressTracker()
                    onCompleteCallback?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    stopProgressTracker()
                    onErrorCallback?.invoke("MediaPlayer error: what=$what, extra=$extra")
                    true
                }

                prepareAsync()
            } catch (e: IOException) {
                android.util.Log.e("VoicePlayer", "Failed to prepare media source: $sourcePath", e)
                onError("Failed to load audio source: ${e.localizedMessage}")
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                stopProgressTracker()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                startProgressTracker()
            }
        }
    }

    fun stop() {
        stopProgressTracker()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                // Ignore release/stop crashes
            }
        }
        mediaPlayer = null
        onProgressCallback = null
        onCompleteCallback = null
        onErrorCallback = null
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressHandler = Handler(Looper.getMainLooper())
        progressRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        try {
                            val current = mp.currentPosition
                            val total = mp.duration
                            onProgressCallback?.invoke(current, total)
                        } catch (e: Exception) {
                            // ignore in case player transitioned state rapidly
                        }
                        progressHandler?.postDelayed(this, 100)
                    }
                }
            }
        }
        progressRunnable?.let { progressHandler?.post(it) }
    }

    private fun stopProgressTracker() {
        progressRunnable?.let { progressHandler?.removeCallbacks(it) }
        progressHandler = null
        progressRunnable = null
    }
}
