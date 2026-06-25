package com.elysium369.meet.core.share

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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import android.util.Base64

object QrCodeSharing {

    /** Compress a string using GZIP and encode it to Base64 */
    fun compressText(text: String): String {
        return try {
            val bos = ByteArrayOutputStream()
            val gos = GZIPOutputStream(bos)
            gos.write(text.toByteArray(Charsets.UTF_8))
            gos.close()
            Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT or Base64.NO_WRAP)
        } catch (e: Exception) {
            text
        }
    }

    /** Decode and decompress a GZIP Base64 encoded string */
    fun decompressText(compressed: String): String {
        return try {
            val bytes = Base64.decode(compressed, Base64.DEFAULT)
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
}

@Composable
fun QrCodeImage(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    qrColor: Color = Color.Black
) {
    val bitmap = remember(text) {
        try {
            val size = 512
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) qrColor.toArgb() else backgroundColor.toArgb())
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
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
