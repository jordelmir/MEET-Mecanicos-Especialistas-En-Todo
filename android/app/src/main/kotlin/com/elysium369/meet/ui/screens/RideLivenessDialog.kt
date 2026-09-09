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
import android.os.SystemClock
import com.elysium369.meet.ride.domain.RidePresenceChallenge
import java.util.concurrent.atomic.AtomicBoolean


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
    val challenge = remember { RidePresenceChallenge() }
    var challengeState by remember { mutableStateOf(challenge.reset()) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val previewView = remember { PreviewView(context) }
    var evidenceHash by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }
    DisposableEffect(hasPermission, retry, lifecycleOwner) {
        val disposed = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        var analysis: ImageAnalysis? = null
        var preview: Preview? = null
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking()
                .build(),
        )
        if (hasPermission) {
            cameraError = null
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (!disposed.get()) {
                    try {
                        val cameraProvider = future.get()
                        provider = cameraProvider
                        val cameraPreview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        preview = cameraPreview
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis = imageAnalysis
                        imageAnalysis.setAnalyzer(cameraExecutor) { proxy ->
                            analyzeBlinkFrame(proxy, detector, disposed, challenge) { next, hash, error ->
                                challengeState = next
                                if (hash != null) evidenceHash = hash
                                cameraError = error
                            }
                        }
                        cameraProvider.bindToLifecycle(lifecycleOwner,
                            CameraSelector.DEFAULT_FRONT_CAMERA, cameraPreview, imageAnalysis)
                    } catch (_: Exception) {
                        cameraError = "No se pudo iniciar la cámara frontal. Cierra otras cámaras y reintenta."
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            disposed.set(true)
            analysis?.clearAnalyzer()
            val ownedUseCases = listOfNotNull(preview, analysis).toTypedArray()
            if (ownedUseCases.isNotEmpty()) provider?.unbind(*ownedUseCases)
            detector.close()
        }
    }

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
                    cameraError ?: challengeState.instruction,
                    color = if (challengeState.phase == RidePresenceChallenge.Phase.VERIFIED) MeetColors.neonGreen else Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (hasPermission) {
                    AndroidView(
                        factory = { previewView },
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
                    "Esta prueba detecta presencia; no verifica tu identidad. El análisis ocurre en el dispositivo y se conserva un hash, no una plantilla facial.",
                    color = MeetColors.textMuted,
                    fontSize = 9.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { evidenceHash?.let(onVerified) },
                enabled = challengeState.phase == RidePresenceChallenge.Phase.VERIFIED && evidenceHash != null,
                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
            ) { Text("EMPEZAR A CONDUCIR", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    evidenceHash = null
                    challengeState = challenge.reset()
                    cameraError = null
                    if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
                    retry++
                }) { Text("REINTENTAR PRUEBA") }
                TextButton(onClick = onCancel) { Text("VOLVER A PASAJERO") }
            }
        },
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyzeBlinkFrame(
    proxy: ImageProxy,
    detector: com.google.mlkit.vision.face.FaceDetector,
    disposed: AtomicBoolean,
    challenge: RidePresenceChallenge,
    onState: (RidePresenceChallenge.State, String?, String?) -> Unit,
) {
    if (disposed.get()) { proxy.close(); return }
    val mediaImage = proxy.image ?: run { proxy.close(); return }
    try {
        detector.process(InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { faces ->
                if (!disposed.get() && !challenge.isComplete) {
                    val face = faces.singleOrNull()
                    val state = challenge.accept(RidePresenceChallenge.Observation(
                        faceCount = faces.size,
                        trackingId = face?.trackingId,
                        yaw = face?.headEulerAngleY ?: 0f,
                        pitch = face?.headEulerAngleX ?: 0f,
                        leftEye = face?.leftEyeOpenProbability,
                        rightEye = face?.rightEyeOpenProbability,
                    ), SystemClock.elapsedRealtime())
                    val hash = if (state.phase == RidePresenceChallenge.Phase.VERIFIED) proxy.frameEvidenceSha256() else null
                    onState(state, hash, null)
                }
            }
            .addOnFailureListener {
                if (!disposed.get() && !challenge.isComplete) onState(challenge.reset(), null,
                    "No se pudo analizar la cámara. Reintenta la prueba con buena iluminación.")
            }
            .addOnCompleteListener { proxy.close() }
    } catch (_: Exception) {
        proxy.close()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (!disposed.get() && !challenge.isComplete) onState(challenge.reset(), null,
                "No se pudo analizar la cámara. Reintenta la prueba.")
        }
    }
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
