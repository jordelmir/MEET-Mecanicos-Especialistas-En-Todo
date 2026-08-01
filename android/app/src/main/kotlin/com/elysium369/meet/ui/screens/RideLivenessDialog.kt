package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elysium369.meet.ui.theme.MeetColors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.abs

private enum class BlinkPhase { FIND_FACE, OPEN, CLOSED, VERIFIED }

@Composable
fun RideLivenessDialog(
    onVerified: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { hasPermission = it }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var phase by remember { mutableStateOf(BlinkPhase.FIND_FACE) }
    var evidenceHash by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdownNow() } }

    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF071019),
        title = {
            Column {
                Text("PRUEBA DE PRESENCIA", color = MeetColors.cyberCyan, fontWeight = FontWeight.Black)
                Text("Se solicita al iniciar el día y vence a las 12 horas.", color = MeetColors.textMuted, fontSize = 10.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (phase) {
                        BlinkPhase.FIND_FACE -> "Mira de frente a la cámara"
                        BlinkPhase.OPEN -> "Ahora parpadea naturalmente"
                        BlinkPhase.CLOSED -> "Abre los ojos para completar"
                        BlinkPhase.VERIFIED -> "Presencia confirmada"
                    },
                    color = if (phase == BlinkPhase.VERIFIED) MeetColors.neonGreen else Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (hasPermission) {
                    AndroidView(
                        factory = { viewContext ->
                            PreviewView(viewContext).also { previewView ->
                                val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                                providerFuture.addListener({
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val detector = FaceDetection.getClient(
                                        FaceDetectorOptions.Builder()
                                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                                            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                                            .enableTracking()
                                            .build(),
                                    )
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    analysis.setAnalyzer(cameraExecutor) { proxy ->
                                        analyzeBlinkFrame(proxy, detector, phase) { next, hash ->
                                            phase = next
                                            if (hash != null) evidenceHash = hash
                                        }
                                    }
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        preview,
                                        analysis,
                                    )
                                }, ContextCompat.getMainExecutor(viewContext))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp)
                            .background(Color.Black, RoundedCornerShape(16.dp))
                            .border(2.dp, MeetColors.cyberCyan, RoundedCornerShape(16.dp)),
                    )
                } else {
                    Text("Se requiere permiso de cámara para validar presencia.", color = MeetColors.warning)
                }
                Text(
                    "El análisis ocurre en el dispositivo. Se conserva un hash de evidencia, no una plantilla facial reutilizable.",
                    color = MeetColors.textMuted,
                    fontSize = 9.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { evidenceHash?.let(onVerified) },
                enabled = phase == BlinkPhase.VERIFIED && evidenceHash != null,
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
            ) { Text("EMPEZAR A CONDUCIR", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("VOLVER A PASAJERO") }
        },
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyzeBlinkFrame(
    proxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    phase: BlinkPhase,
    onPhase: (BlinkPhase, String?) -> Unit,
) {
    val mediaImage = proxy.image ?: run { proxy.close(); return }
    detector.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
        .addOnSuccessListener { faces ->
            val face = faces.singleOrNull()
            if (face == null || abs(face.headEulerAngleY) > 20f || abs(face.headEulerAngleX) > 20f) {
                onPhase(BlinkPhase.FIND_FACE, null)
                return@addOnSuccessListener
            }
            val left = face.leftEyeOpenProbability ?: return@addOnSuccessListener
            val right = face.rightEyeOpenProbability ?: return@addOnSuccessListener
            when (phase) {
                BlinkPhase.FIND_FACE -> if (left > 0.72f && right > 0.72f) onPhase(BlinkPhase.OPEN, null)
                BlinkPhase.OPEN -> if (left < 0.28f && right < 0.28f) onPhase(BlinkPhase.CLOSED, null)
                BlinkPhase.CLOSED -> if (left > 0.65f && right > 0.65f) {
                    onPhase(BlinkPhase.VERIFIED, proxy.frameEvidenceSha256())
                }
                BlinkPhase.VERIFIED -> Unit
            }
        }
        .addOnCompleteListener { proxy.close() }
}

private fun ImageProxy.frameEvidenceSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    planes.forEach { plane ->
        val buffer = plane.buffer.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        digest.update(bytes)
    }
    digest.update(imageInfo.timestamp.toString().toByteArray())
    return digest.digest().joinToString("") { "%02x".format(it) }
}
