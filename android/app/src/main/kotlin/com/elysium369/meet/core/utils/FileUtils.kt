package com.elysium369.meet.core.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object FileUtils {
    /**
     * Copies a file from a given Uri to the app's internal filesDir and returns the absolute local path.
     * This avoids using cloud space and ensures privacy.
     */
    suspend fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                
                // Extract file extension dynamically based on the MIME type
                val extension = context.contentResolver.getType(uri)?.let { mimeType ->
                    android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                } ?: "jpg"
                
                val fileName = "receipt_${UUID.randomUUID()}.$extension"
                val destFile = File(context.filesDir, fileName)
                
                val outputStream = FileOutputStream(destFile)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            } catch (e: Exception) {
                android.util.Log.e("FileUtils", "Error copying Uri to internal storage", e)
                null
            }
        }
    }
}
