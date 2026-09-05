package com.elysium369.meet.communications

/**
 * MessageState — Canonical message delivery state machine.
 *
 * LOCAL_PENDING → UPLOADING → SERVER_ACCEPTED → DELIVERED → READ
 *                   ↓              ↓
 *              FAILED_RETRYABLE  FAILED_PERMANENT
 *
 * Laws:
 * - MESSAGE SENT != MESSAGE DELIVERED
 * - MESSAGE DELIVERED != MESSAGE READ
 * - Offline messages queue locally; retry on connectivity.
 * - Failed permanent messages stay in dead-letter for audit.
 */
enum class MessageDeliveryState {
    /** Message created locally, not yet sent to server. */
    LOCAL_PENDING,
    /** Upload in progress to server. */
    UPLOADING,
    /** Server accepted the message, will deliver to recipients. */
    SERVER_ACCEPTED,
    /** At least one recipient device received the message. */
    DELIVERED,
    /** At least one recipient read the message. */
    READ,
    /** Upload failed but retryable (network, timeout). */
    FAILED_RETRYABLE,
    /** Upload failed permanently (invalid conversation, blocked, etc.). */
    FAILED_PERMANENT;

    val isActive: Boolean get() = this in listOf(LOCAL_PENDING, UPLOADING, FAILED_RETRYABLE)
    val isTerminal: Boolean get() = this in listOf(SERVER_ACCEPTED, DELIVERED, READ, FAILED_PERMANENT)
    val canRetry: Boolean get() = this == FAILED_RETRYABLE
    val isSent: Boolean get() = this in listOf(SERVER_ACCEPTED, DELIVERED, READ)
}

/**
 * ReceiptType — Message read/delivery receipt semantics.
 *
 * MESSAGE_SENT != MESSAGE DELIVERED
 * MESSAGE DELIVERED != MESSAGE READ
 */
enum class ReceiptType {
    /** Server confirmed receipt of the message. */
    SENT,
    /** Message was delivered to the recipient's device (push delivered). */
    DELIVERED,
    /** Recipient opened/read the message. */
    READ;

    val isAtLeast: (ReceiptType) -> Boolean = { other ->
        ordinal >= other.ordinal
    }
}

/**
 * ConversationType — Universal conversation taxonomy.
 * One conversation type per domain, no duplicate chat systems.
 */
enum class ConversationType {
    DIRECT,
    GROUP,
    SERVICE_REQUEST,
    WORK_ORDER,
    RIDE,
    MARKETPLACE,
    VEHICLE,
    FLEET,
    LEGAL_CASE,
    SUPPORT,
    PROPERTY;

    val isServiceLike: Boolean get() = this in listOf(SERVICE_REQUEST, WORK_ORDER, SUPPORT)
}

/**
 * MessageType — Payload type taxonomy for messages.
 * Versioned payloads; each type has a schema version.
 */
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    VOICE_NOTE,
    DOCUMENT,
    LOCATION,
    CONTACT,
    VEHICLE,
    DIAGNOSTIC_REPORT,
    DTC_REPORT,
    QUOTE,
    INVOICE,
    PART,
    SERVICE_REQUEST,
    WORK_ORDER,
    RIDE_LOCATION,
    PROPERTY,
    LEGAL_EVIDENCE_REFERENCE,
    SYSTEM_EVENT;

    val isMedia: Boolean get() = this in listOf(IMAGE, VIDEO, AUDIO, VOICE_NOTE, DOCUMENT)
    val requiresUpload: Boolean get() = isMedia
}
