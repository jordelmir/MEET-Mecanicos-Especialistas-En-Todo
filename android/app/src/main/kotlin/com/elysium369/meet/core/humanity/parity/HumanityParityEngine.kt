package com.elysium369.meet.core.humanity.parity

import com.elysium369.meet.core.humanity.CapabilityRecord
import com.elysium369.meet.core.humanity.EvidenceItem
import java.security.MessageDigest

object HumanityParityEngine {

    fun canonicalEvidenceString(item: EvidenceItem): String {
        return listOf(
            "EVIDENCE_V1",
            item.userId,
            item.skillId,
            item.missionId ?: "",
            item.evidenceType.name,
            item.executionTruth.name,
            item.evidencePayloadHash,
        ).joinToString("|")
    }

    fun canonicalCapabilityString(record: CapabilityRecord): String {
        return listOf(
            "CAPABILITY_V1",
            record.userId,
            record.skillId,
            record.currentLevel.name,
            record.demonstratedEvidenceCount.toString(),
            if (record.verifiedByExpert) "1" else "0",
        ).joinToString("|")
    }

    fun sha256Hex(content: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
