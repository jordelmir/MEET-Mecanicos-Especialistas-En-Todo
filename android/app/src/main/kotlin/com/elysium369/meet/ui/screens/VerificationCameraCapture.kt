package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

internal enum class VerificationCameraAction {
    REQUEST_PERMISSION,
    LAUNCH_CAMERA,
}

internal object VerificationCameraPolicy {
    fun nextAction(cameraPermissionGranted: Boolean): VerificationCameraAction =
        if (cameraPermissionGranted) {
            VerificationCameraAction.LAUNCH_CAMERA
        } else {
            VerificationCameraAction.REQUEST_PERMISSION
        }

    fun completedCapturePath(
        cameraReportedSuccess: Boolean,
        file: File,
    ): String? =
        file.absolutePath.takeIf {
            cameraReportedSuccess && file.isFile && file.length() > 0L
        }

    fun newCaptureFile(
        filesDir: File,
        ownerPrefix: String,
        documentType: String,
        timestampEpochMs: Long,
    ): File {
        val verificationDirectory = File(filesDir, "meet_verifications")
        check(verificationDirectory.isDirectory || verificationDirectory.mkdirs()) {
            "No se pudo preparar el almacenamiento privado de verificación"
        }
        val owner = ownerPrefix.safeFileToken()
        val type = documentType.safeFileToken()
        return File(
            verificationDirectory,
            "${owner}_${type}_${timestampEpochMs.coerceAtLeast(0L)}.jpg",
        )
    }

    private fun String.safeFileToken(): String =
        lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "photo" }
}

private data class PendingVerificationCapture(
    val file: File,
    val onCaptured: (String) -> Unit,
)

@Composable
internal fun rememberVerificationPhotoCapture(): (
    ownerPrefix: String,
    documentType: String,
    onCaptured: (String) -> Unit,
) -> Unit {
    val context = LocalContext.current
    var pendingCapture by remember {
        mutableStateOf<PendingVerificationCapture?>(null)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { cameraReportedSuccess ->
        val pending = pendingCapture
        if (pending != null) {
            val capturedPath = VerificationCameraPolicy.completedCapturePath(
                cameraReportedSuccess = cameraReportedSuccess,
                file = pending.file,
            )
            if (capturedPath != null) {
                pending.onCaptured(capturedPath)
            } else {
                pending.file.takeIf { it.exists() }?.delete()
                Toast.makeText(
                    context,
                    if (cameraReportedSuccess) {
                        "La cámara no guardó una imagen válida. Intenta de nuevo."
                    } else {
                        "Captura cancelada; no se guardó ninguna imagen."
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        pendingCapture = null
    }

    fun launchCamera(pending: PendingVerificationCapture) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pending.file,
            )
            takePictureLauncher.launch(uri)
        } catch (error: Exception) {
            pendingCapture = null
            pending.file.takeIf { it.exists() }?.delete()
            Toast.makeText(
                context,
                "No se pudo abrir la cámara: ${error.message ?: "error desconocido"}",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingCapture
        if (granted && pending != null) {
            launchCamera(pending)
        } else {
            pendingCapture = null
            Toast.makeText(
                context,
                "El permiso de cámara es necesario para capturar la evidencia. Puedes habilitarlo en Ajustes.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    return { ownerPrefix, documentType, onCaptured ->
        if (pendingCapture == null) {
            try {
                val pending = PendingVerificationCapture(
                    file = VerificationCameraPolicy.newCaptureFile(
                        filesDir = context.filesDir,
                        ownerPrefix = ownerPrefix,
                        documentType = documentType,
                        timestampEpochMs = System.currentTimeMillis(),
                    ),
                    onCaptured = onCaptured,
                )
                pendingCapture = pending
                when (
                    VerificationCameraPolicy.nextAction(
                        cameraPermissionGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED,
                    )
                ) {
                    VerificationCameraAction.REQUEST_PERMISSION ->
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    VerificationCameraAction.LAUNCH_CAMERA -> launchCamera(pending)
                }
            } catch (error: Exception) {
                pendingCapture = null
                Toast.makeText(
                    context,
                    "No se pudo preparar la captura: ${error.message ?: "error desconocido"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
