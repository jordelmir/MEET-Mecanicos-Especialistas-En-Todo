package com.elysium369.meet.education.forge

import java.security.MessageDigest

data class ForgeEducationalEvidence(
    val learnerId: String,
    val conceptId: String,
    val taskId: String,
    val environmentContext: String,
    val isCorrect: Boolean,
    val isTransferTask: Boolean,
    val misconceptionCode: String?,
    val latencyMs: Int,
    val rawEvidenceHash: String,
)

object ForgeEducationBridge {

    /**
     * Bridges FORGE 3D interactive manipulation (e.g. placing objects behind the house,
     * or tightening a PVC compression joint) into verifiable educational evidence.
     */
    fun createEvidencePacket(
        learnerId: String,
        conceptId: String,
        taskId: String,
        environmentContext: String,
        isCorrect: Boolean,
        isTransferTask: Boolean,
        misconceptionCode: String? = null,
        latencyMs: Int = 1200,
        interactionPayload: String = "{}",
    ): ForgeEducationalEvidence {
        val digest = MessageDigest.getInstance("SHA-256")
        val rawInput = "$learnerId:$conceptId:$taskId:$environmentContext:$isCorrect:$interactionPayload"
        val hash = digest.digest(rawInput.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        return ForgeEducationalEvidence(
            learnerId = learnerId,
            conceptId = conceptId,
            taskId = taskId,
            environmentContext = environmentContext,
            isCorrect = isCorrect,
            isTransferTask = isTransferTask,
            misconceptionCode = misconceptionCode,
            latencyMs = latencyMs,
            rawEvidenceHash = hash,
        )
    }
}
