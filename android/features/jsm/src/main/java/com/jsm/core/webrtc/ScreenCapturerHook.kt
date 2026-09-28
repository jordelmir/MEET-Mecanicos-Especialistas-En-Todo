package com.jsm.core.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.webrtc.EglBase
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.PeerConnectionFactory

/**
 * Puente ultra-eficiente entre la API nativa de Android MediaProjection y el flujo WebRTC.
 * Elimina la sobrecarga de software render delegando al SurfaceTextureHelper vinculado al EGL Context HW.
 */
class ScreenCapturerHook(private val context: Context) {

    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    /**
     * @param mediaProjectionPermissionResultData El intent onActivityResult esperado tras solicitar permiso de pantalla.
     */
    fun createVideoTrack(
        peerConnectionFactory: PeerConnectionFactory,
        eglBaseContext: EglBase.Context,
        mediaProjectionPermissionResultData: Intent,
        targetWidth: Int = 2560,
        targetHeight: Int = 1440,
        targetFps: Int = 60
    ): VideoTrack? {
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                // Cleanup: Manejar pérdida de permisos o desconexión
            }
        }

        videoCapturer = ScreenCapturerAndroid(
            mediaProjectionPermissionResultData,
            callback
        )

        val videoSource: VideoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)

        // El SurfaceTextureHelper empalmará el thread de UI directamente a OpenGLES
        surfaceTextureHelper = SurfaceTextureHelper.create("JSM_CaptureThread", eglBaseContext)

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            videoSource.capturerObserver
        )

        // Forzamos target resolutions
        videoCapturer?.startCapture(targetWidth, targetHeight, targetFps)

        return peerConnectionFactory.createVideoTrack("JSM_ANDROID_STREAM_TRACK", videoSource)
    }

    /**
     * Adapta la resolución de captura en tiempo real sin reiniciar el pipeline.
     * WebRTC's ScreenCapturerAndroid soporta esto nativamente vía changeCaptureFormat().
     * Esto se activa cuando el Honor Magic V2 cambia entre fold/unfold.
     */
    fun changeCaptureFormat(width: Int, height: Int, fps: Int) {
        try {
            videoCapturer?.changeCaptureFormat(width, height, fps)
        } catch (e: Exception) {
            // Si el capturer no soporta cambio dinámico, ignorar silenciosamente
            // El stream seguirá con la resolución anterior sin interrupción
        }
    }

    fun stopCapture() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            surfaceTextureHelper?.dispose()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
