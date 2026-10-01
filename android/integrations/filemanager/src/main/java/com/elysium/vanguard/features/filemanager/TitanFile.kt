package com.elysium.vanguard.features.filemanager

import androidx.compose.ui.graphics.Color
import com.elysium.vanguard.core.util.FileCategory

data class TitanFile(
    val name: String,
    val isFolder: Boolean,
    val size: String,
    val path: String,
    val mimeType: String,
    val category: FileCategory,
    val thematicColor: Color,
    val lastModified: Long = 0L,
    val permissions: String = ""
)
