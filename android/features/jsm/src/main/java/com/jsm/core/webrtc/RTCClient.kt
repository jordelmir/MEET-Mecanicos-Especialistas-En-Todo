package com.jsm.core.webrtc

import android.content.Context
import android.content.Intent
import com.jsm.core.SafeLog as Log
import org.webrtc.*

/**
 * Cliente WebRTC completo para Android.
 * Gestiona PeerConnection, VideoTrack (MediaProjection), DataChannel e ICE candidates.
 */
class RTCClient(
    private val context: Context,
    private val observer: PeerConnection.Observer
) {
    private val rootEglBase: EglBase = EglBase.create()
    private var pcFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var screenCapturerHook = ScreenCapturerHook(context)
    private var dataChannel: DataChannel? = null

    // Callback para enviar ICE candidates al Mac via signaling
    var onLocalIceCandidate: ((IceCandidate) -> Unit)? = null

    // Callback para notificar cambios de estado de conexión
    var onConnectionStateChanged: ((PeerConnection.IceConnectionState) -> Unit)? = null

    init {
        initPeerConnectionFactory(context)
        pcFactory = createPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory(context: Context) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startStreaming(projectionData: Intent) {
        // Configuración LAN-first: sin servidores STUN/TURN para latencia mínima
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Forzar candidatos host para LAN pura
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.LOW_COST
        }

        // Wrapper del observer que intercepta ICE candidates
        val wrappedObserver = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Local ICE candidate generated: ${candidate.sdp.take(60)}...")
                onLocalIceCandidate?.invoke(candidate)
                observer.onIceCandidate(candidate)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
                state?.let { onConnectionStateChanged?.invoke(it) }
                observer.onIceConnectionChange(state)
            }
            override fun onAddStream(stream: MediaStream) = observer.onAddStream(stream)
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) = observer.onSignalingChange(p0)
            override fun onIceConnectionReceivingChange(p0: Boolean) = observer.onIceConnectionReceivingChange(p0)
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) = observer.onIceGatheringChange(p0)
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) = observer.onIceCandidatesRemoved(p0)
            override fun onRemoveStream(p0: MediaStream?) = observer.onRemoveStream(p0)
            override fun onDataChannel(dc: DataChannel?) {
                Log.d(TAG, "DataChannel received from remote")
                observer.onDataChannel(dc)
            }
            override fun onRenegotiationNeeded() = observer.onRenegotiationNeeded()
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) = observer.onAddTrack(p0, p1)
        }

        peerConnection = pcFactory?.createPeerConnection(rtcConfig, wrappedObserver)

        // Crear DataChannel para enviar metadatos (fold state, orientación, dimensiones)
        val dcInit = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = 3
        }
        dataChannel = peerConnection?.createDataChannel("jsm_meta", dcInit)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel state: ${dataChannel?.state()}")
            }
            override fun onMessage(buffer: DataChannel.Buffer) {}
        })
        Log.d(TAG, "DataChannel 'jsm_meta' created")

        // Añadir video track de screen capture
        val videoTrack = screenCapturerHook.createVideoTrack(
            pcFactory!!,
            rootEglBase.eglBaseContext,
            projectionData
        )

        videoTrack?.let {
            peerConnection?.addTrack(it, listOf("JSM_STREAM"))
            Log.d(TAG, "Video track added to PeerConnection")

            // ═══ BITRATE BOOST: 8 Mbps para calidad cristalina en LAN ═══
            peerConnection?.senders?.firstOrNull { sender ->
                sender.track()?.kind() == "video"
            }?.let { videoSender ->
                val params = videoSender.parameters
                if (params.encodings.isNotEmpty()) {
                    params.encodings[0].maxBitrateBps = 8_000_000  // 8 Mbps
                    params.encodings[0].minBitrateBps = 2_000_000  // 2 Mbps min
                    videoSender.parameters = params
                    Log.d(TAG, "⚡ Video bitrate set: 2-8 Mbps")
                }
            }
        }
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local SDP (Offer) set successfully")
                        callback(sdp)
                    }
                    override fun onSetFailure(s: String?) { Log.e(TAG, "Local SDP Error: $s") }
                    override fun onCreateSuccess(sdp: SessionDescription) {}
                    override fun onCreateFailure(s: String?) {}
                }, sdp)
            }
            override fun onCreateFailure(p0: String?) { Log.e(TAG, "Offer creation failed: $p0") }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { Log.d(TAG, "Remote Answer set successfully") }
            override fun onSetFailure(s: String?) { Log.e(TAG, "Remote SDP Error: $s") }
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sdp)
    }

    /**
     * Aplica un ICE candidate recibido del Mac via signaling.
     */
    fun addRemoteIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val iceCandidate = IceCandidate(sdpMid ?: "", sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCandidate)
        Log.d(TAG, "Remote ICE candidate applied: ${candidate.take(60)}...")
    }

    /**
     * Envía un mensaje de metadatos por el DataChannel (fold state, orientación, etc.)
     */
    fun sendDataChannelMessage(message: String) {
        if (dataChannel?.state() == DataChannel.State.OPEN) {
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(message.toByteArray(Charsets.UTF_8)),
                false
            )
            dataChannel?.send(buffer)
            Log.d(TAG, "DataChannel sent: $message")
        } else {
            Log.w(TAG, "DataChannel not open, cannot send: $message")
        }
    }

    /**
     * Adapta la resolución de captura dinámicamente cuando el dispositivo cambia de estado
     * (fold/unfold). Esto NO reinicia el PeerConnection ni el MediaProjection.
     * WebRTC internamente renegocia el codec si es necesario.
     */
    fun adaptCaptureResolution(width: Int, height: Int, fps: Int) {
        screenCapturerHook.changeCaptureFormat(width, height, fps)
        Log.d(TAG, "📐 Capture resolution adapted: ${width}x${height}@${fps}fps")
    }

    fun dispose() {
        try {
            screenCapturerHook.stopCapture()
            dataChannel?.close()
            peerConnection?.close()
            peerConnection?.dispose()
            pcFactory?.dispose()
            rootEglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during dispose", e)
        }
    }

    companion object {
        private const val TAG = "RTCClient"
    }
}
