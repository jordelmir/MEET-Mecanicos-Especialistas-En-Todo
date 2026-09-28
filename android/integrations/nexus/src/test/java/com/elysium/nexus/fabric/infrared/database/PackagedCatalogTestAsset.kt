package com.elysium.nexus.fabric.infrared.database

import java.io.File
import java.io.SequenceInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.zip.GZIPInputStream

/** Exercises the same three packaged parts that the APK installer consumes. */
internal object PackagedCatalogTestAsset {
    private val assetDir = File("src/main/assets/ir")
    val manifest: File get() = File(assetDir, "ir_catalog.manifest.json")

    private val unpacked by lazy {
        val result = File.createTempFile("meet-nexus-catalog-", ".db").apply { deleteOnExit() }
        val parts = (0..2).map { File(assetDir, "ir_catalog.db.gz.part-0$it").inputStream() }
        GZIPInputStream(SequenceInputStream(Collections.enumeration(parts))).use { input ->
            result.outputStream().use { input.copyTo(it) }
        }
        result
    }

    fun decoded(): File = unpacked

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65_536)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
