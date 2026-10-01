package com.elysium369.meet.communications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CommunicationMediaPayload(val version: Int = 1, val mime: String, val data: String, val durationMs: Long = 0)

internal object CommunicationMediaCodec {
    const val MAX_BYTES = 512 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(bytes: ByteArray, mime: String, durationMs: Long = 0): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
        require(mime in setOf("image/jpeg", "audio/mp4"))
        require(durationMs in 0..300_000)
        return json.encodeToString(CommunicationMediaPayload(mime = mime, data = Base64.encodeToString(bytes, Base64.NO_WRAP), durationMs = durationMs))
    }
    fun decode(text: String, type: String): Pair<CommunicationMediaPayload, ByteArray> {
        require(text.length <= 750_000 && type in setOf("AUDIO", "IMAGE"))
        val payload = json.decodeFromString<CommunicationMediaPayload>(text)
        require(payload.version == 1 && payload.durationMs in 0..300_000)
        require(payload.mime == if (type == "IMAGE") "image/jpeg" else "audio/mp4")
        val bytes = Base64.decode(payload.data, Base64.NO_WRAP)
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES)
        if (type == "IMAGE") require(bytes.size > 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte())
        else require(bytes.size > 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp")
        return payload to bytes
    }
    fun file(context: Context, owner: String, event: String, type: String): File {
        require(runCatching { java.util.UUID.fromString(event) }.isSuccess)
        val namespace = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "communication_media/$namespace").apply { mkdirs() }
        return File(directory, "$event.${if(type == "IMAGE") "jpg" else "m4a"}")
    }
    fun materialize(context: Context, owner: String, event: String, type: String, text: String): File {
        val (_, bytes) = decode(text, type)
        val target = file(context, owner, event, type)
        if (!target.isFile) {
            val temporary = File(target.parentFile, target.name + ".tmp")
            temporary.writeBytes(bytes)
            check(temporary.renameTo(target))
        }
        return target
    }
    fun readImage(context: Context, uri: Uri): ByteArray {
        val original = context.contentResolver.openInputStream(uri)?.use { stream ->
            val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) { val count=stream.read(buffer);if(count<0) break;check(output.size()+count<=12*1024*1024);output.write(buffer,0,count) }
            output.toByteArray()
        } ?: error("IMAGE_UNAVAILABLE")
        require(original.size <= 12 * 1024 * 1024)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 120_000_000L)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(original,0,original.size,BitmapFactory.Options().apply { inSampleSize = sample }))
        try {
            for (quality in listOf(85,70,55,40,25)) {
                val output = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                if(output.size() <= MAX_BYTES) return output.toByteArray()
            }
            error("IMAGE_TOO_LARGE")
        } finally { bitmap.recycle() }
    }
}
