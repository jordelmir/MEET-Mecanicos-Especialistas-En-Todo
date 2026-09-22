package com.elysium369.meet.safety.crypto

object SafetyPayloadAad {

    fun report(
        principalId: String,
        reportId: String,
        payloadId: String,
    ): ByteArray {
        require(principalId.isNotBlank())
        require(reportId.isNotBlank())
        require(payloadId.isNotBlank())

        return "safety-report:v1:$principalId:$reportId:$payloadId"
            .toByteArray(Charsets.UTF_8)
    }
}
