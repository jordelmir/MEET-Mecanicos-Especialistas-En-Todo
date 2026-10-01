package com.elysium.vanguard.features.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.elysium.vanguard.ui.theme.TitanColors
import com.elysium.vanguard.ui.theme.premiumGlass
import java.io.File
import java.io.FileOutputStream

@Composable
fun BasicImageEditor(
    filePath: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableStateOf(0f) }

    LaunchedEffect(filePath) {
        bitmap = BitmapFactory.decodeFile(filePath)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TitanColors.AbsoluteBlack),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { b ->
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentScale = ContentScale.Fit
            )
        }

        // Toolbar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .premiumGlass(cornerRadius = 20.dp)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TitanColors.NeonRed)
            }

            IconButton(onClick = {
                bitmap?.let { b ->
                    val matrix = Matrix().apply { postRotate(90f) }
                    bitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, matrix, true)
                }
            }) {
                Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = TitanColors.NeonCyan)
            }

            IconButton(onClick = {
                bitmap?.let { b ->
                    val file = File(filePath)
                    val out = FileOutputStream(file)
                    b.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()
                    onSave(filePath)
                }
            }) {
                Icon(Icons.Default.Check, contentDescription = "Save", tint = TitanColors.RadioactiveGreen)
            }
        }
    }
}
