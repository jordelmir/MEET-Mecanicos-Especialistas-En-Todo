package com.elysium369.meet.core.obd

/**
 * Detailed decoding status of a Mode $06 test record.
 * Guarantees metrological truth: no malformed or unknown format is ever silently assumed valid.
 */
enum class DecodeStatus {
    DECODED,
    UNKNOWN_UASID,
    UNSUPPORTED_FORMAT,
    MALFORMED,
    NO_LIMITS
}

/**
 * Standardized Mode $06 evaluation verdict according to SAE J1979 / ISO 15765-4.
 */
enum class Mode06Verdict {
    PASS,
    FAIL,
    UNKNOWN,
    NOT_APPLICABLE
}

/**
 * Protocol family for vehicle bus communications.
 */
enum class ProtocolFamily {
    CAN_11BIT,
    CAN_29BIT,
    ISO_K_LINE,
    J1850_PWM,
    J1850_VPW,
    UNKNOWN
}
