package com.elysium.vanguard.features.filemanager

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.text.format.Formatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import com.elysium.vanguard.core.util.FileThematics
import com.elysium.vanguard.core.util.FileCategory

/**
 * TITAN FILE REPOSITORY (LIVE-WIRE IMPLEMENTATION)
 * Direct connection to MediaStore and Storage Access Framework.
 */
@Singleton
class FileManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun getFiles(path: String): Flow<List<TitanFile>> = flow {
        try {
            val directory = File(path)
            com.elysium.vanguard.core.util.SafeLog.d("TitanFileManager", "Scanning path: $path (Exists: ${directory.exists()}, IsDir: ${directory.isDirectory})")

            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles()
                if (files == null) {
                    com.elysium.vanguard.core.util.SafeLog.w("TitanFileManager", "listFiles() returned NULL for path: $path")
                    emit(emptyList())
                } else {
                    com.elysium.vanguard.core.util.SafeLog.d("TitanFileManager", "Found ${files.size} nodes in $path")
                    val titanFiles = files.map { file ->
                        val category = FileThematics.getCategory(file.name, file.isDirectory)
                        TitanFile(
                            name = file.name,
                            isFolder = file.isDirectory,
                            size = if (file.isDirectory) "${file.list()?.size ?: 0} items" else Formatter.formatFileSize(context, file.length()),
                            path = file.absolutePath,
                            mimeType = if (file.isDirectory) "resource/folder" else getMimeType(file),
                            category = category,
                            thematicColor = FileThematics.getCategoryColor(category),
                            lastModified = file.lastModified(),
                            permissions = getPermissionsString(file)
                        )
                    }.sortedWith(compareByDescending<TitanFile> { it.isFolder }.thenBy { it.name.lowercase() })
                    emit(titanFiles)
                }
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            com.elysium.vanguard.core.util.SafeLog.e("TitanFileManager", "Critical crash in getFiles: ${e.message}", e)
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun getPermissionsString(file: File): String {
        val r = if (file.canRead()) "r" else "-"
        val w = if (file.canWrite()) "w" else "-"
        val x = if (file.canExecute()) "x" else "-"
        return "[$r$w$x]"
    }

    /**
     * Detection Logic for the "Common Third-Party Folders" requirement.
     */
    fun getCommonShortcuts(): List<Pair<String, String>> {
        val base = "/storage/emulated/0"
        val possible = listOf(
            "Music" to "$base/Music",
            "Pictures" to "$base/Pictures",
            "Downloads" to "$base/Download",
            "DCIM" to "$base/DCIM",
            "Android" to "$base/Android",
            "WhatsApp" to "$base/Android/media/com.whatsapp/WhatsApp/Media",
            "Documents" to "$base/Documents",
            "Snaptube" to "$base/snaptube",
            "Tencent" to "$base/Tencent",
            "PSP" to "$base/PSP"
        )
        return possible.filter { File(it.second).exists() }
    }

    /**
     * INDUSTRIAL FILE OPERATIONS (MASTER ORDER)
     * Includes automatic conflict resolution (renaming if target exists).
     */
    private fun resolveConflictPath(destPath: String): String {
        var file = File(destPath)
        if (!file.exists()) return destPath

        val parent = file.parent
        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        val extDot = if (ext.isNotEmpty()) ".$ext" else ""

        var counter = 1
        var newFile = File(parent, "$nameWithoutExt ($counter)$extDot")
        while (newFile.exists()) {
            counter++
            newFile = File(parent, "$nameWithoutExt ($counter)$extDot")
        }
        return newFile.absolutePath
    }

    fun deleteFile(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    fun copyFile(sourcePath: String, destPath: String, autoRename: Boolean = true): Boolean {
        return try {
            val source = File(sourcePath)
            val finalDestPath = if (autoRename) resolveConflictPath(destPath) else destPath
            val dest = File(finalDestPath)

            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = !autoRename)
            } else {
                source.copyTo(dest, overwrite = !autoRename)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun moveFile(sourcePath: String, destPath: String, autoRename: Boolean = true): Boolean {
        return try {
            val source = File(sourcePath)
            val finalDestPath = if (autoRename) resolveConflictPath(destPath) else destPath
            val dest = File(finalDestPath)

            if (source.renameTo(dest)) {
                true
            } else {
                // Fallback if move across partitions
                if (copyFile(sourcePath, finalDestPath, autoRename = false)) {
                    deleteFile(sourcePath)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun renameFile(path: String, newName: String): Boolean {
        return try {
            val source = File(path)
            val dest = File(source.parent, newName)
            source.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }

    fun getStorageStats(): StorageStats {
        val internalPath = android.os.Environment.getExternalStorageDirectory()
        val internalStat = android.os.StatFs(internalPath.path)
        val blockSize = internalStat.blockSizeLong
        val totalBlocks = internalStat.blockCountLong
        val availableBlocks = internalStat.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val availableBytes = availableBlocks * blockSize
        val usedBytes = totalBytes - availableBytes

        return StorageStats(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            availableBytes = availableBytes,
            totalLabel = Formatter.formatShortFileSize(context, totalBytes),
            usedLabel = Formatter.formatShortFileSize(context, usedBytes),
            percentUsed = (usedBytes.toDouble() / totalBytes * 100).toInt()
        )
    }

    fun getFolderSizeRecursive(file: File): Long {
        if (!file.exists()) return 0L
        if (!file.isDirectory) return file.length()

        var size = 0L
        file.listFiles()?.forEach {
            size += if (it.isDirectory) getFolderSizeRecursive(it) else it.length()
        }
        return size
    }
}

data class StorageStats(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val totalLabel: String,
    val usedLabel: String,
    val percentUsed: Int
)
