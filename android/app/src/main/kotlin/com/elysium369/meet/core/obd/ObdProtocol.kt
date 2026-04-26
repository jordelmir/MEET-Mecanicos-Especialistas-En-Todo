package com.elysium369.meet.core.obd

enum class ObdProtocol(val atspCode: String, val displayName: String) {
    AUTO("0", "Auto-Detect"),
    J1850_PWM("1", "SAE J1850 PWM"),
    J1850_VPW("2", "SAE J1850 VPW"),
    ISO9141("3", "ISO 9141-2"),
    KWP2000("4", "ISO 14230-4 KWP"),
    KWP2000_FAST("5", "ISO 14230-4 KWP (fast)"),
    CAN_11BIT_500K("6", "ISO 15765-4 CAN 11bit 500K"),
    CAN_29BIT_500K("7", "ISO 15765-4 CAN 29bit 500K"),
    CAN_11BIT_250K("8", "ISO 15765-4 CAN 11bit 250K"),
    CAN_29BIT_250K("9", "ISO 15765-4 CAN 29bit 250K"),
    CAN_ISO15765("6", "CAN ISO 15765 (detectado)")
}
