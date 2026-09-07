package com.elysium369.meet.mobility.data.protocol

class ProtocolViolation(
    val field: String,
    val violation: String,
) : RuntimeException(
    "Protocol violation: field=$field reason=$violation"
)
