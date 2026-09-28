package com.jsm.core.signaling

import android.util.Base64
import com.jsm.core.SafeLog as Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Servidor de Signaling TCP persistente para intercambio WebRTC completo.
 *
 * Protocolo: JSON delimitado por newlines sobre TCP (puerto 9999).
 * Tipos de mensaje:
 *   {"type":"offer","sdp":"<base64>"}
 *   {"type":"answer","sdp":"<base64>"}
 *   {"type":"ice","candidate":"...","sdpMid":"...","sdpMLineIndex":0}
 *
 * Reconexión:
 *   - El accept loop corre indefinidamente.
 *   - Si una Mac se desconecta, la vieja sesión se limpia y el server
 *     queda listo para aceptar una nueva conexión inmediatamente.
 *   - Solo un cliente a la vez (el más reciente gana).
 */
class SignalingServer(
    private val port: Int,
    private val onConnectionRequested: (String) -> Boolean,
    private val onOfferRequested: (sendOffer: (String) -> Unit) -> Unit,
    private val onAnswerReceived: (String) -> Unit,
    private val onRemoteIceCandidate: (candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit,
    private val onMacDisconnected: (() -> Unit)? = null
) {
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isRunning = false
    private var clientWriter: PrintWriter? = null
    private var activeClientSocket: Socket? = null
    private val writerLock = Any()

    fun start() {
        isRunning = true
        thread(name = "SignalingServer") {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Listening on port $port")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept()
                    if (clientSocket != null && isRunning) {
                        val address = clientSocket.inetAddress
                        if ((!address.isSiteLocalAddress && !address.isLoopbackAddress) ||
                            !onConnectionRequested(address.hostAddress ?: "dispositivo local")) {
                            clientSocket.close()
                            continue
                        }
                        // Cerrar conexión anterior si existe (solo 1 Mac a la vez)
                        closeActiveClient()
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Server error", e)
                }
            }
        }
    }

    /**
     * Cierra la conexión activa del cliente anterior.
     * Llamado antes de aceptar un nuevo cliente (reconexión limpia).
     */
    private fun closeActiveClient() {
        synchronized(writerLock) {
            clientWriter = null
            try { activeClientSocket?.close() } catch (_: Exception) {}
            activeClientSocket = null
        }
    }

    private fun handleClient(socket: Socket) {
        synchronized(writerLock) {
            activeClientSocket = socket
        }

        thread(name = "SignalingClient-Handler") {
            try {
                socket.tcpNoDelay = true // Nagle off — latencia mínima
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                synchronized(writerLock) {
                    clientWriter = writer
                }

                Log.d(TAG, "Local screen-mirror connection approved")

                // Paso 1: Crear y enviar la Offer al Mac
                onOfferRequested { sdpOffer ->
                    sendMessage(JSONObject().apply {
                        put("type", "offer")
                        put("sdp", Base64.encodeToString(
                            sdpOffer.toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP
                        ))
                    })
                }

                // Paso 2: Loop de lectura persistente para Answer + ICE del Mac
                while (isRunning && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    try {
                        val json = JSONObject(line)
                        when (json.getString("type")) {
                            "answer" -> {
                                val sdpB64 = json.getString("sdp")
                                val sdpAnswer = String(
                                    Base64.decode(sdpB64, Base64.DEFAULT),
                                    Charsets.UTF_8
                                )
                                Log.d(TAG, "SDP Answer received from Mac (${sdpAnswer.length} chars)")
                                onAnswerReceived(sdpAnswer)
                            }
                            "ice" -> {
                                val candidate = json.getString("candidate")
                                val sdpMid = if (json.has("sdpMid")) json.getString("sdpMid") else null
                                val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                                Log.d(TAG, "ICE candidate received from Mac")
                                onRemoteIceCandidate(candidate, sdpMid, sdpMLineIndex)
                            }
                            else -> {
                                Log.w(TAG, "Unknown message type: ${json.optString("type")}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Invalid signaling message")
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Client handling error", e)
                }
            } finally {
                synchronized(writerLock) {
                    // Solo limpiar si este socket sigue siendo el activo
                    // (evita borrar el writer de una nueva conexión)
                    if (activeClientSocket === socket) {
                        clientWriter = null
                        activeClientSocket = null
                    }
                }
                try { socket.close() } catch (_: Exception) {}
                Log.d(TAG, "Mac disconnected. Waiting for reconnection...")
                onMacDisconnected?.invoke()
            }
        }
    }

    /**
     * Envía un ICE candidate local al Mac via el socket persistente.
     */
    fun sendIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        sendMessage(JSONObject().apply {
            put("type", "ice")
            put("candidate", candidate)
            put("sdpMid", sdpMid ?: "")
            put("sdpMLineIndex", sdpMLineIndex)
        })
    }

    private fun sendMessage(json: JSONObject) {
        synchronized(writerLock) {
            clientWriter?.let { writer ->
                writer.println(json.toString())
                if (writer.checkError()) {
                    Log.e(TAG, "Error writing to signaling socket")
                }
            } ?: Log.w(TAG, "No client connected, cannot send: ${json.optString("type")}")
        }
    }

    fun stop() {
        isRunning = false
        closeActiveClient()
        try { serverSocket?.close() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "SignalingServer"
    }
}
