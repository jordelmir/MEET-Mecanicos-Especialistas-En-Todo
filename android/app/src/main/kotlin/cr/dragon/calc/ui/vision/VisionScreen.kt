package cr.dragon.calc.ui.vision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import cr.dragon.calc.ui.DragonBlack
import cr.dragon.calc.ui.DragonGreen
import cr.dragon.calc.ui.DragonMidGray
import cr.dragon.calc.ui.DragonWhite
import cr.dragon.calc.vision.MathRecognizer
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun VisionScreen(
    onDismiss: () -> Unit,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = DragonBlack,
        topBar = {
            TopAppBar(
                title = { Text("The Dragon's Eye", color = DragonWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = DragonWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DragonBlack.copy(alpha = 0.5f))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasCameraPermission) {
                CameraView(onResult = onResult)
                ScannerOverlay()
                Text(
                    text = "Alinea SOLO la fórmula aquí",
                    color = DragonGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center).offset(y = (-80).dp)
                )
            } else {
                Text(
                    text = "Se requiere permiso de cámara para escanear ecuaciones.",
                    color = DragonWhite,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraView(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // We use a single thread executor for camera operations
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Keep reference to ImageCapture to trigger photos
    val imageCapture = remember { ImageCapture.Builder().build() }

    var isProcessing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Capture Button Wrapper
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(color = DragonGreen)
            } else {
                Button(
                    onClick = {
                        isProcessing = true
                        imageCapture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    try {
                                        val bitmap = image.toBitmap()

                                        val matrix = android.graphics.Matrix()
                                        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                                        val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                        )

                                        val cropW = (rotatedBitmap.width * 0.8f).toInt()
                                        val cropH = (rotatedBitmap.height * 0.35f).toInt()
                                        val startX = (rotatedBitmap.width - cropW) / 2
                                        val startY = (rotatedBitmap.height - cropH) / 2

                                        val croppedBitmap = android.graphics.Bitmap.createBitmap(
                                            rotatedBitmap, startX, startY, cropW, cropH
                                        )

                                        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                                        coroutineScope.launch {
                                            val mathString = MathRecognizer.recognizeMath(inputImage)
                                            onResult(mathString)
                                            isProcessing = false
                                        }
                                    } catch (_: Exception) {
                                        isProcessing = false
                                    } finally {
                                        image.close()
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isProcessing = false
                                }
                            }
                        )
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = DragonWhite)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(DragonMidGray) // Inner circle logic if desired, or leave solid
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sweepAngle = 90f
        val cornerLength = 40.dp.toPx()
        val cornerThickness = 4.dp.toPx()

        val boxWidth = size.width * 0.8f
        val boxHeight = 120.dp.toPx() // Wide rectangle for equations

        val left = (size.width - boxWidth) / 2
        val top = (size.height - boxHeight) / 2
        val right = left + boxWidth
        val bottom = top + boxHeight

        // Draw 4 corners
        val color = DragonGreen

        // Top-Left
        drawLine(color, Offset(left, top), Offset(left + cornerLength, top), strokeWidth = cornerThickness, cap = StrokeCap.Round)
        drawLine(color, Offset(left, top), Offset(left, top + cornerLength), strokeWidth = cornerThickness, cap = StrokeCap.Round)

        // Top-Right
        drawLine(color, Offset(right, top), Offset(right - cornerLength, top), strokeWidth = cornerThickness, cap = StrokeCap.Round)
        drawLine(color, Offset(right, top), Offset(right, top + cornerLength), strokeWidth = cornerThickness, cap = StrokeCap.Round)

        // Bottom-Left
        drawLine(color, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth = cornerThickness, cap = StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth = cornerThickness, cap = StrokeCap.Round)

        // Bottom-Right
        drawLine(color, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth = cornerThickness, cap = StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth = cornerThickness, cap = StrokeCap.Round)

        // Darken the background outside the box
        drawRect(Color.Black.copy(alpha = 0.5f), size = Size(size.width, top))
        drawRect(Color.Black.copy(alpha = 0.5f), size = Size(size.width, size.height - bottom), topLeft = Offset(0f, bottom))
        drawRect(Color.Black.copy(alpha = 0.5f), size = Size(left, boxHeight), topLeft = Offset(0f, top))
        drawRect(Color.Black.copy(alpha = 0.5f), size = Size(size.width - right, boxHeight), topLeft = Offset(right, top))
    }
}
