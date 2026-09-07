package com.elysium369.meet.core.channel

import kotlinx.serialization.Serializable

/**
 * ASTRA V6 §48 — Multi-Channel Communication Platform.
 * Extracted from OpenClaw's channel plugin architecture.
 *
 * ELYSIUM can communicate with users/providers/clients through multiple
 * channels: in-app, WhatsApp Business, SMS, email, push notifications.
 * Each channel is a registered plugin following the OpenClaw pattern.
 */

// ─── Channel Types ───

enum class ChannelType(val label: String, val supportsMedia: Boolean) {
    IN_APP("Notificación en app", true),
    WHATSAPP_BUSINESS("WhatsApp Business", true),
    SMS("SMS", false),
    EMAIL("Correo electrónico", true),
    PUSH_NOTIFICATION("Push notification", false),
    TELEGRAM("Telegram", true),
    SIGNAL("Signal", true),
    WEBHOOK("Webhook HTTP", false),
}

enum class MessageDirection {
    INBOUND,   // From user/provider to ELYSIUM
    OUTBOUND,  // From ELYSIUM to user/provider
}

enum class MessageStatus {
    QUEUED, SENT, DELIVERED, READ, FAILED, EXPIRED,
}

// ─── Channel Message ───

@Serializable
data class ChannelMessage(
    val messageId: String,
    val channelType: ChannelType,
    val direction: MessageDirection,
    val fromId: String,
    val toId: String,
    val body: String,
    val mediaUrls: List<String> = emptyList(),
    val status: MessageStatus = MessageStatus.QUEUED,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val replyToMessageId: String? = null,
)

// ─── Channel Provider Interface ───

/**
 * Abstract channel provider. Each messaging platform implements this.
 * Following OpenClaw's pattern: channels register capabilities and
 * the bus routes messages to the appropriate channel.
 */
interface ChannelProvider {
    val channelType: ChannelType
    val isAvailable: Boolean

    suspend fun sendMessage(message: ChannelMessage): MessageStatus
    suspend fun getMessageStatus(messageId: String): MessageStatus
    fun supportsFeature(feature: ChannelFeature): Boolean
}

enum class ChannelFeature {
    RICH_TEXT,
    MEDIA_ATTACHMENTS,
    TEMPLATES,
    INTERACTIVE_BUTTONS,
    LOCATION_SHARING,
    READ_RECEIPTS,
    GROUP_MESSAGING,
    SCHEDULING,
}

// ─── Channel Registry ───

class ChannelRegistry {

    private val providers = mutableMapOf<ChannelType, ChannelProvider>()

    fun register(provider: ChannelProvider) {
        providers[provider.channelType] = provider
    }

    fun unregister(channelType: ChannelType) {
        providers.remove(channelType)
    }

    fun getProvider(channelType: ChannelType): ChannelProvider? {
        return providers[channelType]
    }

    val availableChannels: List<ChannelType>
        get() = providers.values
            .filter { it.isAvailable }
            .map { it.channelType }

    fun channelsWithFeature(feature: ChannelFeature): List<ChannelType> {
        return providers.values
            .filter { it.isAvailable && it.supportsFeature(feature) }
            .map { it.channelType }
    }

    val registeredCount: Int get() = providers.size
}

// ─── WhatsApp Business Stub ───

/**
 * WhatsApp Business API channel provider stub.
 * Full implementation requires Meta Business API credentials.
 */
class WhatsAppBusinessProvider(
    private val apiToken: String = "",
    private val phoneNumberId: String = "",
) : ChannelProvider {

    override val channelType = ChannelType.WHATSAPP_BUSINESS
    override val isAvailable: Boolean get() = apiToken.isNotBlank()

    override suspend fun sendMessage(message: ChannelMessage): MessageStatus {
        if (!isAvailable) return MessageStatus.FAILED
        // TODO: Meta Cloud API POST /messages
        return MessageStatus.QUEUED
    }

    override suspend fun getMessageStatus(messageId: String): MessageStatus {
        // TODO: Meta webhook status callback
        return MessageStatus.SENT
    }

    override fun supportsFeature(feature: ChannelFeature): Boolean {
        return feature in setOf(
            ChannelFeature.RICH_TEXT,
            ChannelFeature.MEDIA_ATTACHMENTS,
            ChannelFeature.TEMPLATES,
            ChannelFeature.INTERACTIVE_BUTTONS,
            ChannelFeature.LOCATION_SHARING,
            ChannelFeature.READ_RECEIPTS,
        )
    }
}

// ─── In-App Notification Provider ───

class InAppNotificationProvider : ChannelProvider {
    override val channelType = ChannelType.IN_APP
    override val isAvailable = true

    private val sentMessages = mutableListOf<ChannelMessage>()

    override suspend fun sendMessage(message: ChannelMessage): MessageStatus {
        sentMessages.add(message.copy(status = MessageStatus.DELIVERED))
        return MessageStatus.DELIVERED
    }

    override suspend fun getMessageStatus(messageId: String): MessageStatus {
        return sentMessages.firstOrNull { it.messageId == messageId }?.status
            ?: MessageStatus.FAILED
    }

    override fun supportsFeature(feature: ChannelFeature): Boolean {
        return feature in setOf(
            ChannelFeature.RICH_TEXT,
            ChannelFeature.MEDIA_ATTACHMENTS,
            ChannelFeature.INTERACTIVE_BUTTONS,
            ChannelFeature.READ_RECEIPTS,
        )
    }

    val deliveredMessages: List<ChannelMessage> get() = sentMessages.toList()
}
