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
                // Create a unique filename for the image
                val fileName = "receipt_${UUID.randomUUID()}.jpg"
                val destFile = File(context.filesDir, fileName)
                
                val outputStream = FileOutputStream(destFile)
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
