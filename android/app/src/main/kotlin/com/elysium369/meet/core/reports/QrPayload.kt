package com.elysium369.meet.core.reports

/**
 * Minimal QR payload for a certified report.
 *
 * The QR is intentionally tiny and **never contains** full VIN, plate,
 * or phone — only the 6 fields below, which are enough to verify the
 * report anywhere without leaking PII. The full report contents are
 * fetched from the local DB or the verifier endpoint after the QR is
 * scanned.
 *
 * Schema (byte-exact on the wire):
 *   v1|reportId|integrityHash|vehicleId|generatedAt|reportType|verifierUrl
 *
 * Why the `v1|` prefix:
 *   - lets future versions extend the payload without breaking the
 *     existing scanners;
 *   - gives the verifier a hard failure mode if a malformed string is
 *     fed in instead of silently treating garbage as data.
 *
 * Field separator is `|` to match the canonical hash chain delimiter;
 * the verifier never URL-decodes the payload, it just splits on `|`.
 */
data class QrPayload(
    val reportId: String,
    val integrityHash: String,
    val vehicleId: String,
    val generatedAt: Long,
    val reportType: ReportType,
    val verifierUrl: String?,
) {
    fun encode(): String = buildString {
        append("v1|")
        append(reportId).append('|')
        append(integrityHash).append('|')
        append(vehicleId).append('|')
        append(generatedAt).append('|')
        append(reportType.wireValue).append('|')
        append(verifierUrl ?: "")
    }

    companion object {
        /**
         * Decodes a QR payload string. Returns null on any structural
         * problem — the verifier UI should treat null as "QR not
         * recognized" and refuse to render any report details.
         */
        fun decode(raw: String): QrPayload? {
            val parts = raw.split('|')
            if (parts.size != 7) return null
            if (parts[0] != "v1") return null
            val generatedAt = parts[4].toLongOrNull() ?: return null
            val reportType = ReportType.fromWire(parts[5]) ?: return null
            return QrPayload(
                reportId = parts[1],
                integrityHash = parts[2],
                vehicleId = parts[3],
                generatedAt = generatedAt,
                reportType = reportType,
                verifierUrl = parts[6].takeIf { it.isNotEmpty() },
            )
        }
    }
}