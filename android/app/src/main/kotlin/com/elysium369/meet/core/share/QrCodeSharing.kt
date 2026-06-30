package com.elysium369.meet.core.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.elysium369.meet.data.local.entities.GaugeConfig
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Serializable
data class GaugeQrEnvelope(
    val app: String = QrCodeSharing.APP_ID,
    val kind: String = QrCodeSharing.KIND_GAUGE,
    val schemaVersion: Int = QrCodeSharing.SCHEMA_VERSION,
    val shareId: String,
    val createdAt: Long,
    val displayName: String,
    val sourceGaugeId: String? = null,
    val sourceMarketplaceId: String? = null,
    val sourcePublished: Boolean = false,
    val config: GaugeConfig,
    val checksum: String
)

data class GaugeQrExport(
    val qrText: String,
    val envelope: GaugeQrEnvelope,
    val warnings: List<String>
)

data class GaugeQrImport(
    val displayName: String,
    val config: GaugeConfig,
    val fingerprint: String,
    val sourceGaugeId: String?,
    val sourceMarketplaceId: String?,
    val sourcePublished: Boolean,
    val importedFromLegacyFormat: Boolean,
    val warnings: List<String>
)

object QrCodeSharing {
    const val APP_ID = "elysium-vanguard-meet"
    const val KIND_GAUGE = "meet.gauge.diy"
    const val SCHEMA_VERSION = 1

    private const val QR_PREFIX = "MEET-GAUGE-QR:"
    private const val MAX_QR_CHARS = 12_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Compress a string using GZIP and encode it to Base64 */
    fun compressText(text: String): String {
        return try {
            val bos = ByteArrayOutputStream()
            val gos = GZIPOutputStream(bos)
            gos.write(text.toByteArray(Charsets.UTF_8))
            gos.close()
            Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray())
        } catch (e: Exception) {
            text
        }
    }

    /** Decode and decompress a GZIP Base64 encoded string */
    fun decompressText(compressed: String): String {
        return try {
            val decoder = Base64.getUrlDecoder()
            val normalized = compressed.trim()
            val padding = (4 - normalized.length % 4) % 4
            val bytes = decoder.decode(normalized + "=".repeat(padding))
            val bis = java.io.ByteArrayInputStream(bytes)
            val gis = GZIPInputStream(bis)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(gis, Charsets.UTF_8))
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            compressed // Fallback
        }
    }

    fun createGaugeQrExport(
        config: GaugeConfig,
        sourceGaugeId: String? = null,
        sourceMarketplaceId: String? = null,
        sourcePublished: Boolean = false,
        createdAt: Long = System.currentTimeMillis()
    ): GaugeQrExport {
        val sanitized = sanitizeGaugeConfig(config)
        val checksum = fingerprintFor(sanitized.config)
        val envelope = GaugeQrEnvelope(
            shareId = UUID.randomUUID().toString(),
            createdAt = createdAt,
            displayName = sanitized.config.name.ifBlank { "MEET Gauge" },
            sourceGaugeId = sourceGaugeId,
            sourceMarketplaceId = sourceMarketplaceId,
            sourcePublished = sourcePublished,
            config = sanitized.config,
            checksum = checksum
        )
        val envelopeJson = json.encodeToString(envelope)
        return GaugeQrExport(
            qrText = QR_PREFIX + compressText(envelopeJson),
            envelope = envelope,
            warnings = sanitized.warnings
        )
    }

    fun decodeGaugeQrText(scannedText: String): Result<GaugeQrImport> = runCatching {
        val raw = scannedText.trim()
        require(raw.isNotBlank()) { "QR vacío" }
        require(raw.length <= MAX_QR_CHARS) { "QR demasiado grande para un gauge" }

        val payload = if (raw.startsWith(QR_PREFIX)) {
            decompressText(raw.removePrefix(QR_PREFIX))
        } else {
            decompressText(raw)
        }

        val envelope = runCatching {
            json.decodeFromString(GaugeQrEnvelope.serializer(), payload)
        }.getOrNull()

        if (envelope != null) {
            require(envelope.app == APP_ID) { "QR de otra aplicación" }
            require(envelope.kind == KIND_GAUGE) { "QR no corresponde a un gauge" }
            require(envelope.schemaVersion in 1..SCHEMA_VERSION) { "Versión QR no soportada" }

            val sanitized = sanitizeGaugeConfig(envelope.config)
            val expectedChecksum = fingerprintFor(sanitized.config)
            require(envelope.checksum == expectedChecksum) { "QR alterado o corrupto" }

            GaugeQrImport(
                displayName = envelope.displayName.ifBlank { sanitized.config.name.ifBlank { "MEET Gauge" } },
                config = sanitized.config,
                fingerprint = expectedChecksum,
                sourceGaugeId = envelope.sourceGaugeId,
                sourceMarketplaceId = envelope.sourceMarketplaceId,
                sourcePublished = envelope.sourcePublished,
                importedFromLegacyFormat = false,
                warnings = sanitized.warnings
            )
        } else {
            val legacyConfig = json.decodeFromString(GaugeConfig.serializer(), payload)
            val sanitized = sanitizeGaugeConfig(legacyConfig)
            GaugeQrImport(
                displayName = sanitized.config.name.ifBlank { "MEET Gauge" },
                config = sanitized.config,
                fingerprint = fingerprintFor(sanitized.config),
                sourceGaugeId = null,
                sourceMarketplaceId = null,
                sourcePublished = false,
                importedFromLegacyFormat = true,
                warnings = sanitized.warnings + "Formato QR antiguo aceptado; se guardará como copia nueva."
            )
        }
    }

    fun fingerprintFor(config: GaugeConfig): String {
        val sanitized = sanitizeGaugeConfig(config).config
        val canonical = json.encodeToString(sanitized)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("MEET_GAUGE_QR_V1|$canonical".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generateQrBitmap(
        text: String,
        size: Int = 512,
        backgroundArgb: Int = android.graphics.Color.WHITE,
        qrArgb: Int = android.graphics.Color.BLACK
    ): Bitmap? {
        return try {
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size)
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bmp ->
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bmp.setPixel(x, y, if (bitMatrix.get(x, y)) qrArgb else backgroundArgb)
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun shareQrAsImage(
        context: Context,
        qrText: String,
        title: String
    ): Result<Unit> = runCatching {
        val bitmap = generateQrBitmap(qrText, size = 1024)
            ?: error("No se pudo generar la imagen QR")
        val dir = File(context.cacheDir, "gauge_qr_shares").apply { mkdirs() }
        val safeTitle = title.ifBlank { "meet_gauge" }
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "meet_gauge" }
            .take(48)
        val file = File(dir, "$safeTitle.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Gauge MEET: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir gauge QR"))
    }

    private fun sanitizeGaugeConfig(config: GaugeConfig): SanitizedGaugeConfig {
        val warnings = mutableListOf<String>()
        val name = config.name.trim().ifBlank { "MEET Gauge" }.take(48)
        val bgType = when (config.bgType) {
            0, 1 -> config.bgType
            2 -> {
                warnings += "Las imágenes locales no viajan por QR; se usó fondo preset."
                1
            }
            else -> {
                warnings += "Tipo de fondo ajustado a un valor compatible."
                0
            }
        }

        return SanitizedGaugeConfig(
            config = config.copy(
                name = name,
                bgType = bgType,
                bgPresetIndex = config.bgPresetIndex.coerceIn(0, 59),
                bezelStyle = config.bezelStyle.coerceIn(0, 19),
                needleStyle = config.needleStyle.coerceIn(0, 14),
                ticksStyle = config.ticksStyle.coerceIn(0, 14),
                glowIntensity = config.glowIntensity.coerceIn(0f, 1f),
                imageOpacity = config.imageOpacity.coerceIn(0f, 1f),
                animationIndex = config.animationIndex.coerceIn(0, 9),
                typographyIndex = config.typographyIndex.coerceIn(0, 9)
            ),
            warnings = warnings
        )
    }

    private data class SanitizedGaugeConfig(
        val config: GaugeConfig,
        val warnings: List<String>
    )
}

@Composable
fun QrCodeImage(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    qrColor: Color = Color.Black
) {
    val bitmap = remember(text) {
        QrCodeSharing.generateQrBitmap(
            text = text,
            backgroundArgb = backgroundColor.toArgb(),
            qrArgb = qrColor.toArgb()
        )
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(12.dp)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Código QR",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun Color.toArgb(): Int {
    return (this.alpha * 255.0f + 0.5f).toInt() shl 24 or
           ((this.red * 255.0f + 0.5f).toInt() shl 16) or
           ((this.green * 255.0f + 0.5f).toInt() shl 8) or
           (this.blue * 255.0f + 0.5f).toInt()
}
