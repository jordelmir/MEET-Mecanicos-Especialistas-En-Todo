package com.elysium369.meet.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.elysium369.meet.ui.ObdViewModel
import com.elysium369.meet.ui.theme.MeetColors
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.pulseOnHover
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DashcamScreen(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewAndOverlay(
                navController = navController,
                viewModel = viewModel
            )
        } else {
            // Permission Request Card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PERMISO DE CÁMARA REQUERIDO",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "MEET necesita acceder a la cámara trasera para poder grabar la carretera y superponer la telemetría en tiempo real sobre el video.",
                    color = MeetColors.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen)
                ) {
                    Text("CONCEDER PERMISO", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewAndOverlay(
    navController: NavController,
    viewModel: ObdViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val liveData by viewModel.liveData.collectAsState()
    val performanceSnapshot by viewModel.performanceSnapshot.collectAsState()
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    val isRecordingDashcam by viewModel.isRecordingDashcam.collectAsState()
    val isTranscodingVideo by viewModel.isTranscodingVideo.collectAsState()
    val transcodingProgress by viewModel.transcodingProgress.collectAsState()

    var recordingTimerSeconds by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    var pendingStopReason by remember { mutableStateOf<String?>(null) }
    var isStopping by remember { mutableStateOf(false) }

    val lateralG by viewModel.lateralG.collectAsState()
    val longitudinalG by viewModel.longitudinalG.collectAsState()
    val totalG = kotlin.math.sqrt(lateralG * lateralG + longitudinalG * longitudinalG)
    val activeDtcs by viewModel.activeDtcs.collectAsState()

    // Crash detection: G-Force impact
    LaunchedEffect(isRecordingDashcam, totalG) {
        if (isRecordingDashcam && !isStopping && totalG > 2.5f) {
            pendingStopReason = "IMPACT"
            isStopping = true
            delay(5000L) // 5s aftermath recording
            activeRecording?.stop()
        }
    }

    // Crash detection: Critical DTCs
    LaunchedEffect(isRecordingDashcam, activeDtcs) {
        if (isRecordingDashcam && !isStopping && activeDtcs.isNotEmpty()) {
            pendingStopReason = "DTC_SEVERE"
            isStopping = true
            delay(5000L)
            activeRecording?.stop()
        }
    }

    // Monitoring coolant temp trigger
    val currentCoolant = liveData["0105"] ?: liveData["coolant"] ?: 0f
    LaunchedEffect(isRecordingDashcam, currentCoolant) {
        if (isRecordingDashcam && !isStopping && currentCoolant > 115f) {
            pendingStopReason = "CRITICAL_TEMP"
            isStopping = true
            delay(5000L)
            activeRecording?.stop()
        }
    }

    fun startRecordingHelper() {
        val capture = videoCapture ?: return
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val videoName = "MEET_Dash_${sdf.format(java.util.Date())}"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, videoName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MEET_Dashcam")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        viewModel.startDashcamRecording()
        val pendingRecording = capture.output.prepareRecording(context, mediaStoreOutputOptions)
        val recording = if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled()
        } else {
            pendingRecording
        }.start(mainExecutor) { recordEvent: VideoRecordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    Log.d("DashcamScreen", "Recording started")
                }
                is VideoRecordEvent.Finalize -> {
                    Log.d("DashcamScreen", "Recording finalized, error=${recordEvent.error}")
                    val uri = recordEvent.outputResults.outputUri
                    val stopReason = pendingStopReason

                    // Compile evidence for crash/alert triggers
                    if (stopReason != null && stopReason != "MANUAL") {
                        var evidencePath: String? = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val projection = arrayOf(MediaStore.Video.Media.DATA)
                            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    evidencePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                                }
                            }
                        } else {
                            evidencePath = uri.path
                        }
                        evidencePath?.let { videoPath ->
                            scope.launch {
                                try {
                                    val evidence = com.elysium369.meet.core.blackbox.EvidenceCompiler.compilePackage(
                                        context = context,
                                        vehicleId = viewModel.selectedVehicle.value?.id ?: "generic",
                                        eventType = stopReason,
                                        gpsLocation = "$lateralG,$longitudinalG",
                                        videoFile = java.io.File(videoPath),
                                        audioFile = null,
                                        telemetryJson = Json.encodeToString(liveData),
                                        dtcsList = activeDtcs
                                    )
                                    viewModel.saveEvidencePackage(evidence)
                                } catch (e: Exception) {
                                    Log.e("DashcamScreen", "Failed to compile evidence", e)
                                }
                            }
                        }
                    }

                    // Process video: burn telemetry overlay via URI (Scoped Storage safe)
                    viewModel.stopDashcamRecording(uri)

                    // Reset all flags
                    pendingStopReason = null
                    isStopping = false
                    activeRecording = null
                }
            }
            }
        activeRecording = recording
    }

    val currentRpm = liveData["RPM"] ?: liveData["rpm"] ?: 0f
    val currentSpeed = liveData["SPEED"] ?: liveData["speed"] ?: 0f
    val currentThrottle = liveData["THROTTLE"] ?: liveData["throttle"] ?: liveData["0111"] ?: 0f
    val currentLoad = liveData["LOAD"] ?: liveData["load"] ?: liveData["0104"] ?: 0f
    val currentGForce = performanceSnapshot?.gForce ?: 0f

    // Camera Preview View
    val previewView = remember { PreviewView(context) }

    // Bind CameraX
    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview UseCase
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Video Capture UseCase
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            val cap = VideoCapture.withOutput(recorder)
            videoCapture = cap

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    cap
                )
            } catch (e: Exception) {
                Log.e("DashcamScreen", "Use case binding failed", e)
            }
        }, mainExecutor)
    }

    // Recording duration timer
    LaunchedEffect(isRecordingDashcam) {
        if (isRecordingDashcam) {
            recordingTimerSeconds = 0
            while (isRecordingDashcam) {
                delay(1000L)
                recordingTimerSeconds++
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Viewfinder
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlay to improve contrast of the telemetry widgets
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
        )

        // ─── TELEMETRY HUD OVERLAYS ───
        
        // 1. Top Bar: Title and Recording status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }

            if (isRecordingDashcam) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .border(1.dp, MeetColors.error.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "recBlink")
                    val recAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "blink"
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MeetColors.error.copy(alpha = recAlpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REC ${String.format("%02d:%02d", recordingTimerSeconds / 60, recordingTimerSeconds % 60)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Text(
                    text = "TELEMETRÍA HUD",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                enabled = !isRecordingDashcam
            ) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Girar Cámara", tint = Color.White)
            }
        }

        // 2. Center Widgets: Tacómetro HUD y Velocímetro
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2 * 0.9f

                // RPM Arc (Semi-circle at the top)
                drawArc(
                    color = Color.White.copy(alpha = 0.15f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )

                // Current RPM Sweep
                val maxRpm = 7000f
                val rpmSweep = (currentRpm.coerceIn(0f, maxRpm) / maxRpm) * 180f
                val rpmColor = when {
                    currentRpm > 5500f -> MeetColors.error
                    currentRpm > 4000f -> MeetColors.warning
                    else -> MeetColors.neonGreen
                }
                drawArc(
                    color = rpmColor,
                    startAngle = 180f,
                    sweepAngle = rpmSweep.coerceAtLeast(1f),
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Speed and Load values inside the arc
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = String.format("%.0f", currentSpeed),
                    color = Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.offset(y = (-10).dp)
                )
                Text(
                    text = "KM/H",
                    color = MeetColors.cyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.offset(y = (-18).dp)
                )
                Text(
                    text = "RPM: ${currentRpm.toInt()}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.offset(y = (-10).dp)
                )
            }
        }

        // 3. Bottom HUD Details (Pedals and G-Force)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Pedals (Throttle & Load)
            Column(
                modifier = Modifier
                    .width(130.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudPedalBar(label = "ACEL (THR)", value = currentThrottle, color = MeetColors.neonGreen)
                HudPedalBar(label = "CARGA (LOD)", value = currentLoad, color = MeetColors.electricBlue)
            }

            // G-Force Mini-Radar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Text(
                    "FUERZA G: ${String.format("%.2fG", currentGForce)}",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val radius = size.minDimension / 2
                        drawCircle(color = Color.White.copy(alpha = 0.15f), radius = radius, style = Stroke(0.5.dp.toPx()))
                        drawCircle(color = Color.White.copy(alpha = 0.1f), radius = radius * 0.5f, style = Stroke(0.5.dp.toPx()))

                        // Pointer vector
                        val maxG = 1.0f
                        val gPos = center.y - (currentGForce / maxG).coerceIn(-1f, 1f) * radius
                        drawCircle(color = MeetColors.neonGreen, radius = 3.dp.toPx(), center = Offset(center.x, gPos))
                        drawLine(color = MeetColors.neonGreen.copy(alpha = 0.3f), start = center, end = Offset(center.x, gPos), strokeWidth = 1.dp.toPx())
                    }
                }
            }
        }

        // 4. Large Recording Trigger Button and SOS Overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecordingDashcam) {
                Button(
                    onClick = {
                        if (!isStopping) {
                            pendingStopReason = "SOS"
                            isStopping = true
                            scope.launch {
                                delay(5000L)
                                activeRecording?.stop()
                            }
                        }
                    },
                    enabled = !isStopping,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red,
                        disabledContainerColor = Color.Red.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .border(1.dp, Color.White.copy(alpha = if (isStopping) 0.1f else 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Text("EMERGENCIA SOS", color = Color.White.copy(alpha = if (isStopping) 0.3f else 1f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            IconButton(
                onClick = {
                    if (isRecordingDashcam && !isStopping) {
                        // Manual stop: immediately stop recording
                        pendingStopReason = "MANUAL"
                        isStopping = true
                        activeRecording?.stop()
                    } else if (!isRecordingDashcam && !isTranscodingVideo) {
                        // Start new recording
                        pendingStopReason = null
                        isStopping = false
                        startRecordingHelper()
                    }
                },
                enabled = !isTranscodingVideo,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(2.dp, when {
                        isStopping -> MeetColors.warning
                        isRecordingDashcam -> MeetColors.error
                        else -> Color.White
                    }, CircleShape)
                    .pulseOnHover()
            ) {
                Icon(
                    imageVector = if (isRecordingDashcam) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = "Grabar",
                    tint = when {
                        isStopping -> MeetColors.warning
                        isRecordingDashcam -> MeetColors.error
                        else -> Color.White
                    },
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // 5. Video Transcoding / Baking Progress HUD
        if (isTranscodingVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {}, // Intercept clicks
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    // Cyberpunk Glowing Circular Indicator
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { transcodingProgress / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = MeetColors.neonGreen,
                            strokeWidth = 6.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                        Text(
                            text = "$transcodingProgress%",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "BAKING TELEMETRY VIDEO OVERLAYS",
                        color = MeetColors.neonGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Incrustando RPM, velocidad, fuerza G y acelerador en cada fotograma del video. Por favor no cierres la pantalla.",
                        color = MeetColors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HudPedalBar(
    label: String,
    value: Float,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text("${value.toInt()}%", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        ) {
            val ratio = (value / 100f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
