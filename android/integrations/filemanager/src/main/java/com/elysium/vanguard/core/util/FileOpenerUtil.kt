package com.elysium.vanguard.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

object FileOpenerUtil {

    /**
     * Determines the MIME type by reading the first few bytes of the file.
     * This is critical for files from apps like WhatsApp that might have wrong extensions.
     */
    fun getMimeTypeFromMagicBytes(file: File): String {
        if (!file.exists() || file.isDirectory) return "*/*"

        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(8)
                val read = fis.read(header)
                if (read < 4) return "*/*"

                val hexHeader = header.take(4).joinToString("") { "%02X".format(it) }

                return when {
                    // PDF: %PDF
                    hexHeader == "25504446" -> "application/pdf"

                    // ZIP / Office Open XML (DOCX, XLSX, etc) / APK: PK..
                    hexHeader.startsWith("504B") -> {
                        // Further detection could check specific offsets for DOCX vs APK
                        val ext = file.extension.lowercase()
                        when (ext) {
                            "apk" -> "application/vnd.android.package-archive"
                            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                            else -> "application/zip"
                        }
                    }

                    // Images
                    hexHeader.startsWith("FFD8FF") -> "image/jpeg"
                    hexHeader == "89504E47" -> "image/png"
                    hexHeader == "47494638" -> "image/gif"
                    hexHeader.startsWith("424D") -> "image/bmp"

                    // Media
                    hexHeader == "1A45DFA3" -> "video/webm"
                    hexHeader.contains("66747970") -> "video/mp4" // MP4 header usually contains 'ftyp'

                    // Execution
                    hexHeader.startsWith("4D5A") -> "application/x-msdownload" // .exe

                    else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                }
            }
        } catch (e: Exception) {
            return "*/*"
        }
    }

    /**
     * Opens a file using the system intent chooser.
     */
    fun openFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val mimeType = getMimeTypeFromMagicBytes(file)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.filemanager.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, file: File) {
        if (!file.exists()) return
        try {
            val mimeType = getMimeTypeFromMagicBytes(file)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.filemanager.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                setDataAndType(uri, mimeType)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Share via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun editFile(context: Context, file: File) {
        if (!file.exists()) return
        try {
            val mimeType = getMimeTypeFromMagicBytes(file)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.filemanager.provider",
                file
            )

            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Edit with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No editor found: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun setWallpaper(context: Context, file: File) {
        if (!file.exists()) return
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.filemanager.provider",
                file
            )

            val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, "image/*")
                putExtra("mimeType", "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Set Wallpaper"))
        } catch (e: Exception) {
            Toast.makeText(context, "Wallpaper set failed", Toast.LENGTH_SHORT).show()
        }
    }
}
