package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
internal fun rememberVerificationPhotoCapture(
    onCaptured: (documentType: String, capturedPath: String) -> Unit,
): (
    ownerPrefix: String,
    documentType: String,
) -> Unit {
    val context = LocalContext.current
    val latestOnCaptured by rememberUpdatedState(onCaptured)
    var pendingCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDocumentType by rememberSaveable { mutableStateOf<String?>(null) }

    fun clearPendingCapture(deleteFile: Boolean) {
        if (deleteFile) {
            pendingCapturePath?.let(::File)?.takeIf(File::exists)?.delete()
        }
        pendingCapturePath = null
        pendingDocumentType = null
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { cameraReportedSuccess ->
        val file = pendingCapturePath?.let(::File)
        val documentType = pendingDocumentType
        if (file != null && documentType != null) {
            val capturedPath = VerificationCameraPolicy.completedCapturePath(
                cameraReportedSuccess = cameraReportedSuccess,
                file = file,
            )
            if (capturedPath != null) {
                latestOnCaptured(documentType, capturedPath)
            } else {
                file.takeIf(File::exists)?.delete()
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
        clearPendingCapture(deleteFile = false)
    }

    fun launchCamera(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            takePictureLauncher.launch(uri)
        } catch (error: Exception) {
            clearPendingCapture(deleteFile = true)
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
        val pendingFile = pendingCapturePath?.let(::File)
        if (granted && pendingFile != null) {
            launchCamera(pendingFile)
        } else {
            clearPendingCapture(deleteFile = true)
            Toast.makeText(
                context,
                "El permiso de cámara es necesario para capturar la evidencia. Puedes habilitarlo en Ajustes.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    return { ownerPrefix, documentType ->
        if (pendingCapturePath == null) {
            try {
                val file = VerificationCameraPolicy.newCaptureFile(
                    filesDir = context.filesDir,
                    ownerPrefix = ownerPrefix,
                    documentType = documentType,
                    timestampEpochMs = System.currentTimeMillis(),
                )
                pendingCapturePath = file.absolutePath
                pendingDocumentType = documentType
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
                    VerificationCameraAction.LAUNCH_CAMERA -> launchCamera(file)
                }
            } catch (error: Exception) {
                clearPendingCapture(deleteFile = true)
                Toast.makeText(
                    context,
                    "No se pudo preparar la captura: ${error.message ?: "error desconocido"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
