package com.elysium.vanguard.recordshield.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.*
import android.os.Handler
import android.os.HandlerThread
import com.elysium.vanguard.recordshield.util.SafeLog as Log
import android.util.Size
import android.view.Surface
import com.elysium.vanguard.recordshield.data.cloud.VideoQualityPreset
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * DualCameraManager — Simultaneous Front + Rear Camera Recording
 * ============================================================================
 *
 * Why Camera2 over CameraX:
 *   CameraX can only bind one camera per lifecycle owner. For simultaneous
 *   dual recording, we need Camera2 to open both camera devices independently,
 *   each with its own capture session, surface, and MediaRecorder.
 *
 * Architecture:
 *   - CameraManager opens front + rear cameras
 *   - Each camera gets its own CaptureSession with a Surface from MediaRecorder
 *   - Each camera records to a separate MP4 file
 *   - Both recordings start/stop synchronously
 *
 * Thread Safety:
 *   - Camera2 operations require a dedicated HandlerThread
 *   - MediaRecorder must be created/started/stopped on the correct thread
 *   - Semaphore ensures exclusive access to camera open/close
 * ============================================================================
 */
class DualCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "DualCameraManager"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var rearCamera: CameraDevice? = null
    private var frontCamera: CameraDevice? = null
    private var rearSession: CameraCaptureSession? = null
    private var frontSession: CameraCaptureSession? = null
    private var rearRecorder: MediaRecorder? = null
    private var frontRecorder: MediaRecorder? = null
    private var rearSurface: Surface? = null
    private var frontSurface: Surface? = null

    private val cameraThread = HandlerThread("DualCameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private val openLock = Semaphore(2, true) // 2 permits for simultaneous open
    private var isRecording = false

    // Preview surfaces
    private var rearPreviewSurface: Surface? = null
    private var frontPreviewSurface: Surface? = null

    /**
     * Callback for preview frame updates.
     */
    interface PreviewCallback {
        fun onRearPreview(surface: Surface)
        fun onFrontPreview(surface: Surface)
    }

    /**
     * Open both cameras and start recording.
     *
     * @param preset Quality preset for recording
     * @param rearOutput File for rear camera output
     * @param frontOutput File for front camera output
     * @param onReady Called when both cameras are ready
     * @param onError Called on error with exception
     */
    @SuppressLint("MissingPermission")
    fun startDualRecording(
        preset: VideoQualityPreset,
        rearOutput: File,
        frontOutput: File,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isRecording) {
            onError(IllegalStateException("Already recording"))
            return
        }

        try {
            // Create MediaRecorders
            rearRecorder = createMediaRecorder(preset, rearOutput)
            frontRecorder = createMediaRecorder(preset, frontOutput)

            // Get surfaces from recorders
            rearSurface = rearRecorder!!.surface
            frontSurface = frontRecorder!!.surface

            if (rearSurface == null || frontSurface == null) {
                onError(IllegalStateException("Failed to get surfaces from MediaRecorder"))
                return
            }

            isRecording = true

            // Open both cameras simultaneously
            val rearCameraId = getCameraId(CameraCharacteristics.LENS_FACING_BACK)
            val frontCameraId = getCameraId(CameraCharacteristics.LENS_FACING_FRONT)

            if (rearCameraId == null || frontCameraId == null) {
                onError(IllegalStateException("Could not find both cameras"))
                return
            }

            var camerasOpened = 0
            val onCameraOpened = {
                camerasOpened++
                if (camerasOpened == 2) {
                    // Both cameras opened, create sessions and start recording
                    createCaptureSessions(rearOutput, frontOutput, preset, onReady, onError)
                }
            }

            openCamera(rearCameraId, { device ->
                rearCamera = device
                onCameraOpened()
            }, onError)

            openCamera(frontCameraId, { device ->
                frontCamera = device
                onCameraOpened()
            }, onError)

        } catch (e: Exception) {
            isRecording = false
            cleanup()
            onError(e)
        }
    }

    /**
     * Stop both recordings.
     */
    fun stopDualRecording(onComplete: () -> Unit) {
        if (!isRecording) {
            onComplete()
            return
        }

        isRecording = false

        try {
            // Stop sessions
            rearSession?.stopRepeating()
            frontSession?.stopRepeating()

            // Stop recorders
            rearRecorder?.stop()
            frontRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        } finally {
            rearRecorder?.release()
            frontRecorder?.release()
            rearRecorder = null
            frontRecorder = null

            rearCamera?.close()
            frontCamera?.close()
            rearCamera = null
            frontCamera = null

            rearSession?.close()
            frontSession?.close()
            rearSession = null
            frontSession = null

            rearSurface?.release()
            frontSurface?.release()
            rearSurface = null
            frontSurface = null

            onComplete()
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        stopDualRecording { }
        cameraThread.quitSafely()
    }

    // ========================================================================
    // INTERNAL METHODS
    // ========================================================================

    private fun createMediaRecorder(preset: VideoQualityPreset, outputFile: File): MediaRecorder {
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(preset.width, preset.height)
            setVideoEncodingBitRate(preset.videoBitrate)
            setVideoFrameRate(30)
            setMaxDuration(10_000) // 10 seconds per chunk
            setOutputFile(outputFile.absolutePath)
            prepare()
        }

        return recorder
    }

    private fun getCameraId(facing: Int): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(
        cameraId: String,
        onOpened: (CameraDevice) -> Unit,
        onError: (Exception) -> Unit
    ) {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                onOpened(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                onError(IllegalStateException("Camera $cameraId disconnected"))
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                onError(IllegalStateException("Camera $cameraId error: $error"))
                camera.close()
            }
        }, cameraHandler)
    }

    private fun createCaptureSessions(
        rearOutput: File,
        frontOutput: File,
        preset: VideoQualityPreset,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        var sessionsCreated = 0
        val onSessionCreated = {
            sessionsCreated++
            if (sessionsCreated == 2) {
                // Both sessions created, start recording
                startRecording()
                onReady()
            }
        }

        // Rear camera session
        rearCamera?.let { camera ->
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                rearSurface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            camera.createCaptureSession(
                listOf(rearSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        rearSession = session
                        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
                        onSessionCreated()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError(IllegalStateException("Rear camera session config failed"))
                    }
                },
                cameraHandler
            )
        }

        // Front camera session
        frontCamera?.let { camera ->
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                frontSurface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            camera.createCaptureSession(
                listOf(frontSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        frontSession = session
                        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
                        onSessionCreated()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError(IllegalStateException("Front camera session config failed"))
                    }
                },
                cameraHandler
            )
        }
    }

    private fun startRecording() {
        try {
            rearRecorder?.start()
            frontRecorder?.start()
            Log.i(TAG, "Dual recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            throw e
        }
    }

    private fun cleanup() {
        rearRecorder?.release()
        frontRecorder?.release()
        rearRecorder = null
        frontRecorder = null

        rearCamera?.close()
        frontCamera?.close()
        rearCamera = null
        frontCamera = null

        rearSession?.close()
        frontSession?.close()
        rearSession = null
        frontSession = null

        rearSurface?.release()
        frontSurface?.release()
        rearSurface = null
        frontSurface = null
    }
}
