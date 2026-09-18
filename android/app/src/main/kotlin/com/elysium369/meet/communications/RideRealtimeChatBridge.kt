package com.elysium369.meet.communications

import android.content.Context
import android.util.Base64
import android.util.Log
import com.elysium369.meet.data.local.entities.RideChatMessageEntity
import com.elysium369.meet.data.remote.SupabaseModule
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RideChatBroadcastPayload(
    val messageId: String,
    val rideRequestId: String,
    val senderId: String,
    val senderName: String,
    val senderRole: String,
    val messageType: String,
    val textContent: String? = null,
    val mediaBase64: String? = null,
    val mediaMimeType: String? = null,
    val audioDurationMs: Long? = null,
    val remoteMediaPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * RideRealtimeChatBridge — Bidirectional real-time chat & media bridge.
 *
 * Transmits text, preset messages, photos, and voice notes instantly via:
 * 1. Supabase Realtime broadcast channel (`chat_ride_$requestId`)
 * 2. Local UDP Mesh broadcast on port 42424 (`255.255.255.255`)
 */
@Singleton
class RideRealtimeChatBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "RideRealtimeChatBridge"
        private const val MAGIC_0: Byte = 0x45 // 'E'
        private const val MAGIC_1: Byte = 0x56 // 'V'
        private const val TYPE_CHAT: Byte = 0x10
        private const val MAX_INLINE_MEDIA_BYTES = 500 * 1024 // 500 KB limit for inline broadcast
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val deliveredMessageIds = Collections.newSetFromMap(
        object : java.util.LinkedHashMap<String, Boolean>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > 256
            }
        },
    )

    /**
     * Broadcasts a chat message over Cloud Realtime and Local UDP Mesh.
     */
    suspend fun broadcastMessage(
        message: RideChatMessageEntity,
        mediaBytes: ByteArray? = null,
    ) {
        val payload = createPayload(message, mediaBytes)

        // 1. Broadcast via Supabase Realtime
        runCatching {
            val channelName = "chat_ride_${message.rideRequestId}"
            val channel = SupabaseModule.client.channel(channelName)
            channel.subscribe()
            channel.broadcast(
                event = "new_message",
                message = payload,
            )
        }.onFailure { err ->
            Log.w(TAG, "Failed cloud realtime broadcast for message ${message.messageId}", err)
        }

        // 2. Broadcast via Local UDP Mesh
        runCatching {
            sendUdpChatMessage(payload)
        }.onFailure { err ->
            Log.w(TAG, "Failed mesh UDP broadcast for message ${message.messageId}", err)
        }
    }

    /**
     * Subscribes to real-time incoming messages for an active ride request.
     */
    fun startObserving(
        scope: CoroutineScope,
        rideRequestId: String,
        localRole: String,
        onIncomingMessage: suspend (RideChatMessageEntity) -> Unit,
    ): Job {
        return scope.launch(Dispatchers.IO) {
            val channelName = "chat_ride_$rideRequestId"
            val channel = SupabaseModule.client.channel(channelName)

            // Cloud Realtime Listener
            val cloudJob = launch {
                runCatching {
                    channel.broadcastFlow<RideChatBroadcastPayload>("new_message").collect { payload ->
                        if (payload.rideRequestId == rideRequestId && payload.senderRole != localRole) {
                            handleIncomingPayload(payload, onIncomingMessage)
                        }
                    }
                }.onFailure { e ->
                    if (e !is CancellationException) {
                        Log.w(TAG, "Cloud chat flow error for $rideRequestId", e)
                    }
                }
            }

            // Local UDP Mesh Listener
            val meshJob = launch {
                listenUdpChat(rideRequestId, localRole, onIncomingMessage)
            }

            try {
                channel.subscribe()
                Log.i(TAG, "Subscribed to realtime chat for ride $rideRequestId")
                // Keep active until cancelled
                while (isActive) {
                    kotlinx.coroutines.delay(10_000)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.e(TAG, "Error in realtime chat subscription", e)
            } finally {
                cloudJob.cancel()
                meshJob.cancel()
                runCatching { channel.unsubscribe() }
                Log.i(TAG, "Unsubscribed from realtime chat for ride $rideRequestId")
            }
        }
    }

    private suspend fun handleIncomingPayload(
        payload: RideChatBroadcastPayload,
        onIncomingMessage: suspend (RideChatMessageEntity) -> Unit,
    ) {
        val isNew = synchronized(deliveredMessageIds) {
            deliveredMessageIds.add(payload.messageId)
        }
        if (!isNew) return

        var localAudioPath: String? = null
        var localImagePath: String? = null

        if (!payload.mediaBase64.isNullOrBlank()) {
            runCatching {
                val decoded = Base64.decode(payload.mediaBase64, Base64.NO_WRAP)
                if (payload.messageType == "AUDIO") {
                    val audioDir = File(context.filesDir, "meet_rides_audio").apply { mkdirs() }
                    val file = File(audioDir, "audio_${payload.messageId}.m4a")
                    file.writeBytes(decoded)
                    localAudioPath = file.absolutePath
                } else if (payload.messageType == "IMAGE") {
                    val imageDir = File(context.filesDir, "meet_rides_images").apply { mkdirs() }
                    val file = File(imageDir, "img_${payload.messageId}.jpg")
                    file.writeBytes(decoded)
                    localImagePath = file.absolutePath
                }
            }.onFailure { err ->
                Log.e(TAG, "Failed to decode media for message ${payload.messageId}", err)
            }
        }

        val entity = RideChatMessageEntity(
            messageId = payload.messageId,
            rideRequestId = payload.rideRequestId,
            senderId = payload.senderId,
            senderName = payload.senderName,
            senderRole = payload.senderRole,
            messageType = payload.messageType,
            textContent = payload.textContent,
            audioFilePath = localAudioPath,
            imageFilePath = localImagePath,
            remoteMediaPath = payload.remoteMediaPath,
            mediaMimeType = payload.mediaMimeType,
            audioDurationMs = payload.audioDurationMs,
            syncState = "SYNCED",
            createdAt = payload.createdAt,
        )

        onIncomingMessage(entity)
    }

    private fun createPayload(
        message: RideChatMessageEntity,
        mediaBytes: ByteArray?,
    ): RideChatBroadcastPayload {
        var base64: String? = null
        if (mediaBytes != null && mediaBytes.size <= MAX_INLINE_MEDIA_BYTES) {
            base64 = Base64.encodeToString(mediaBytes, Base64.NO_WRAP)
        } else {
            val localPath = message.imageFilePath ?: message.audioFilePath
            if (localPath != null) {
                val f = File(localPath)
                if (f.exists() && f.length() <= MAX_INLINE_MEDIA_BYTES) {
                    base64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                }
            }
        }

        return RideChatBroadcastPayload(
            messageId = message.messageId,
            rideRequestId = message.rideRequestId,
            senderId = message.senderId,
            senderName = message.senderName,
            senderRole = message.senderRole,
            messageType = message.messageType,
            textContent = message.textContent,
            mediaBase64 = base64,
            mediaMimeType = message.mediaMimeType,
            audioDurationMs = message.audioDurationMs,
            remoteMediaPath = message.remoteMediaPath,
            createdAt = message.createdAt,
        )
    }

    private fun sendUdpChatMessage(payload: RideChatBroadcastPayload) {
        val payloadJson = json.encodeToString(payload)
        val jsonBytes = payloadJson.toByteArray(Charsets.UTF_8)
        if (jsonBytes.size > 60_000) return // Skip oversized UDP packets

        val totalSize = 2 + 1 + 4 + jsonBytes.size
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.put(MAGIC_0)
        buffer.put(MAGIC_1)
        buffer.put(TYPE_CHAT)
        buffer.putInt(payload.rideRequestId.hashCode())
        buffer.put(jsonBytes)

        val socket = DatagramSocket()
        socket.broadcast = true
        val broadcastAddr = InetAddress.getByName("255.255.255.255")
        val packet = DatagramPacket(buffer.array(), totalSize, broadcastAddr, RideLiveVoiceBridge.UDP_PORT)
        socket.send(packet)
        socket.close()
    }

    private suspend fun listenUdpChat(
        rideRequestId: String,
        localRole: String,
        onIncomingMessage: suspend (RideChatMessageEntity) -> Unit,
    ) {
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(RideLiveVoiceBridge.UDP_PORT))
            }
        }.getOrNull() ?: return

        val buffer = ByteArray(65535)
        val packet = DatagramPacket(buffer, buffer.size)
        val expectedHash = rideRequestId.hashCode()

        try {
            while (true) {
                socket.receive(packet)
                if (packet.length < 8) continue

                val byteBuffer = ByteBuffer.wrap(packet.data, 0, packet.length)
                val m0 = byteBuffer.get()
                val m1 = byteBuffer.get()
                val type = byteBuffer.get()
                val rideHash = byteBuffer.getInt()

                if (m0 != MAGIC_0 || m1 != MAGIC_1 || type != TYPE_CHAT || rideHash != expectedHash) {
                    continue
                }

                val jsonLength = packet.length - 7
                val jsonBytes = ByteArray(jsonLength)
                byteBuffer.get(jsonBytes)
                val jsonString = String(jsonBytes, Charsets.UTF_8)

                runCatching {
                    val payload = json.decodeFromString<RideChatBroadcastPayload>(jsonString)
                    if (payload.rideRequestId == rideRequestId && payload.senderRole != localRole) {
                        handleIncomingPayload(payload, onIncomingMessage)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            socket.close()
        }
    }
}
