package com.elysium.vanguard.core.util

import androidx.compose.ui.graphics.Color
import com.elysium.vanguard.ui.theme.TitanColors
import java.util.Locale


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class FileCategory {
    AUDIO,
    VIDEO,
    DOCUMENT,
    SYSTEM, // APKs, Archives
    IMAGE,
    FOLDER,
    GENERIC,
    CODE // New category for source files
}

object FileThematics {

    fun getCategory(name: String, isDirectory: Boolean): FileCategory {
        if (isDirectory) return FileCategory.FOLDER

        val extension = name.substringAfterLast(".", "").lowercase(Locale.ROOT)

        // HEURISTIC: If no extension, check if it starts with "doc-" (common in WhatsApp docs)
        if (extension.isEmpty()) {
            if (name.startsWith("doc-", ignoreCase = true)) return FileCategory.DOCUMENT
            return FileCategory.GENERIC
        }

        return when (extension) {
            // Audio
            "mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "amr", "mid", "xmf", "mxmf", "wma", "pcm" ->
                FileCategory.AUDIO

            // Video
            "mp4", "mkv", "webm", "3gp", "ts", "avi", "mov", "flv", "wmv", "m4v" ->
                FileCategory.VIDEO

            // Documents
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "epub", "mobi", "html", "htm", "md", "json", "xml" ->
                FileCategory.DOCUMENT

            // System / Compressed
            "apk", "apks", "xapk", "zip", "rar", "7z", "tar", "gz", "iso", "bin" ->
                FileCategory.SYSTEM

            // Images
            "jpg", "jpeg", "png", "webp", "heif", "heic", "gif", "bmp", "dng", "raw", "svg", "ico" ->
                FileCategory.IMAGE

            // Code
            "kt", "java", "py", "js", "cpp", "c", "h", "cs", "swift", "gradle", "properties" ->
                FileCategory.CODE

            else -> FileCategory.GENERIC
        }
    }
    fun getCategoryColor(category: FileCategory): Color {
        return when (category) {
            FileCategory.AUDIO -> TitanColors.AcidLime // Acid Lime for Audio
            FileCategory.VIDEO -> TitanColors.ElectricBlue // Electric Blue/Purple for Video
            FileCategory.DOCUMENT -> TitanColors.NeonCyan // Cyan for Docs
            FileCategory.SYSTEM -> TitanColors.RadioactiveGreen // Green for System/APKs
            FileCategory.IMAGE -> TitanColors.UltraViolet // Violet for Images
            FileCategory.FOLDER -> TitanColors.NeonCyan
            FileCategory.GENERIC -> Color.Gray
            FileCategory.CODE -> TitanColors.NeonOrange
        }
    }

    fun getCategoryIcon(category: FileCategory): ImageVector {
        return when (category) {
            FileCategory.AUDIO -> Icons.Default.Audiotrack
            FileCategory.VIDEO -> Icons.Default.PlayCircle
            FileCategory.DOCUMENT -> Icons.Default.Description
            FileCategory.SYSTEM -> Icons.Default.Android // Or Settings/Build if not APK
            FileCategory.IMAGE -> Icons.Default.Image
            FileCategory.FOLDER -> Icons.Default.Folder
            FileCategory.GENERIC -> Icons.Default.InsertDriveFile
            FileCategory.CODE -> Icons.Default.Code
        }
    }
}
