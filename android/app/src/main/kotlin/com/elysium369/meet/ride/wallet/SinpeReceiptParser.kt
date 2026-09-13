package com.elysium369.meet.ride.wallet

import java.text.NumberFormat
import java.util.Locale
import java.util.regex.Pattern

data class ParsedSinpeReceipt(
    val bank: SinpeBank,
    val referenceNumber: String,
    val amountCrc: Double,
    val senderPhoneOrName: String?,
    val recipientInfo: String?,
    val rawText: String,
    val isValid: Boolean = true
)

enum class SinpeBank(val displayName: String) {
    BAC("BAC Credomatic"),
    BNCR("Banco Nacional"),
    BCR("Banco de Costa Rica"),
    PROMERICA("Banco Promerica"),
    SCOTIABANK("Scotiabank"),
    DAVIVIENDA("Davivienda"),
    GENERIC("SINPE Móvil Costa Rica")
}

object SinpeReceiptParser {
    const val TARGET_ADMIN_EMAIL = "jordelmir@gmail.com"

    // Bank Patterns
    private val BAC_REF_REGEX = Pattern.compile("""(?:referencia|comprobante|número|num|ref)\s*[:#]?\s*([0-9A-Za-z]{6,32})""", Pattern.CASE_INSENSITIVE)
    private val BAC_AMOUNT_REGEX = Pattern.compile("""(?:monto|total|transferido|por)\s*[:#]?\s*(?:₡|CRC)?\s*([0-9]+(?:[.,][0-9]{2,3})*|[0-9]+)""", Pattern.CASE_INSENSITIVE)
    private val SENDER_REGEX = Pattern.compile("""(?:tel[eé]fono(?:\s+origen)?|origen|remitente|de|cliente)\s*[:#]\s*([0-9]{4}[-\s]?[0-9]{4}|[A-Za-zÀ-ÿ\s]{3,30})""", Pattern.CASE_INSENSITIVE)

    fun parse(text: String): ParsedSinpeReceipt? {
        val clean = text.trim()
        if (clean.length < 10) return null

        val bank = detectBank(clean)
        val ref = extractReference(clean, bank) ?: return null
        val amount = extractAmount(clean) ?: return null
        val sender = extractSender(clean)

        return ParsedSinpeReceipt(
            bank = bank,
            referenceNumber = ref,
            amountCrc = amount,
            senderPhoneOrName = sender,
            recipientInfo = TARGET_ADMIN_EMAIL,
            rawText = clean,
            isValid = amount > 0 && ref.isNotBlank()
        )
    }

    private fun detectBank(text: String): SinpeBank {
        val lower = text.lowercase(Locale.ROOT)
        return when {
            lower.contains("bac") || lower.contains("credomatic") -> SinpeBank.BAC
            lower.contains("nacional") || lower.contains("bncr") || lower.contains("bn móvil") -> SinpeBank.BNCR
            lower.contains("bcr") || lower.contains("banco de costa rica") -> SinpeBank.BCR
            lower.contains("promerica") -> SinpeBank.PROMERICA
            lower.contains("scotiabank") -> SinpeBank.SCOTIABANK
            lower.contains("davivienda") -> SinpeBank.DAVIVIENDA
            else -> SinpeBank.GENERIC
        }
    }

    private fun extractReference(text: String, bank: SinpeBank): String? {
        val matcher = BAC_REF_REGEX.matcher(text)
        if (matcher.find()) {
            val candidate = matcher.group(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate.length >= 6) {
                return candidate
            }
        }
        // Fallback: search for long digit string (typically 8 to 20 digits for Costa Rica bank vouchers)
        val digitMatch = Pattern.compile("""\b([0-9]{8,24})\b""").matcher(text)
        while (digitMatch.find()) {
            val candidate = digitMatch.group(1) ?: continue
            // Ignore common phone numbers or years
            if (!candidate.startsWith("506") || candidate.length > 8) {
                return candidate
            }
        }
        return null
    }

    private fun extractAmount(text: String): Double? {
        val matcher = BAC_AMOUNT_REGEX.matcher(text)
        while (matcher.find()) {
            val rawNum = matcher.group(1)?.trim() ?: continue
            val parsed = parseFormattedAmount(rawNum)
            if (parsed != null && parsed in 100.0..5_000_000.0) {
                return parsed
            }
        }

        // Fallback: look for ₡ followed by numbers
        val colonesMatcher = Pattern.compile("""(?:₡|CRC|COLONES)\s*([0-9.,]+)""", Pattern.CASE_INSENSITIVE).matcher(text)
        if (colonesMatcher.find()) {
            val raw = colonesMatcher.group(1)?.trim() ?: ""
            return parseFormattedAmount(raw)
        }

        return null
    }

    private fun parseFormattedAmount(raw: String): Double? {
        return try {
            // Remove common punctuation: handle 10,000.00 or 10.000,00 or 10000
            val normalized = if (raw.contains(",") && raw.contains(".")) {
                if (raw.indexOf(',') < raw.indexOf('.')) {
                    // English format: 10,000.00
                    raw.replace(",", "")
                } else {
                    // Spanish format: 10.000,00
                    raw.replace(".", "").replace(",", ".")
                }
            } else if (raw.contains(",")) {
                // If only comma, check decimal positions: 10000,00 vs 10,000
                val afterComma = raw.substringAfterLast(',')
                if (afterComma.length == 2) {
                    raw.replace(",", ".")
                } else {
                    raw.replace(",", "")
                }
            } else if (raw.contains(".")) {
                val afterDot = raw.substringAfterLast('.')
                if (afterDot.length == 2) {
                    raw
                } else {
                    raw.replace(".", "")
                }
            } else {
                raw
            }
            normalized.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun extractSender(text: String): String? {
        val matcher = SENDER_REGEX.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.trim()
        }
        return null
    }
}
