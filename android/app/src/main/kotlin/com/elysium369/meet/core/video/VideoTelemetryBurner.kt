package com.elysium369.meet.core.video

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Effects
import com.elysium369.meet.ui.DashcamTelemetryFrame
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * VideoTelemetryBurner — Impregna la telemetría OBD2 directamente en los cuadros del video grabado.
 * Utiliza Android Jetpack Media3 Transformer para una codificación de hardware óptima y sin lags.
 */
@OptIn(UnstableApi::class)
class VideoTelemetryBurner(private val context: Context) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    fun burnTelemetry(
        inputUri: android.net.Uri,
        outputFile: File,
        telemetryFrames: List<DashcamTelemetryFrame>,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        scope.launch {
            try {
                // Base dimensions for the overlay bitmap
                val overlayWidth = 1280
                val overlayHeight = 720

                // Implementamos BitmapOverlay para dibujar la telemetría dinámicamente en cada frame
                val bitmapOverlay = object : BitmapOverlay() {
                    
                    // Colores de la temática Elysium (Neon Green, Electric Blue, Cyber Cyan, Red Alert)
                    private val greenColor = Color.parseColor("#00FFD4")
                    private val blueColor = Color.parseColor("#BB00FF")
                    private val cyanColor = Color.parseColor("#00E5FF")
                    private val warningColor = Color.parseColor("#FFD700")
                    private val errorColor = Color.parseColor("#FF1744")
                    
                    private val paintText = Paint().apply {
                        isAntiAlias = true
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    }
                    
                    private val paintGlow = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                    }

                    // Pre-asignamos el bitmap y el canvas del frame para evitar presiones en el recolector de basura (GC)
                    private val frameBitmap = Bitmap.createBitmap(overlayWidth, overlayHeight, Bitmap.Config.ARGB_8888)
                    private val frameCanvas = Canvas(frameBitmap)

                    override fun getBitmap(presentationTimeUs: Long): Bitmap {
                        // Limpiar el bitmap (hacerlo transparente para el fondo)
                        frameBitmap.eraseColor(Color.TRANSPARENT)

                        val w = overlayWidth.toFloat()
                        val h = overlayHeight.toFloat()
                        
                        // Tiempo en ms
                        val timeMs = presentationTimeUs / 1000L
                        
                        // Buscar el frame de telemetría más cercano
                        val frame = telemetryFrames.minByOrNull { abs(it.timestampMs - timeMs) }
                            ?: return frameBitmap

                        val scale = 1.0f // Mapeo directo a escala 1280x720

                        // ── Dibujar Banner de Encabezado Superior ──
                        val headerHeight = 45f * scale
                        frameCanvas.drawRect(0f, 0f, w, headerHeight, Paint().apply {
                            color = Color.BLACK
                            alpha = 150
                        })
                        
                        // Línea divisora inferior
                        frameCanvas.drawLine(0f, headerHeight, w, headerHeight, Paint().apply {
                            color = cyanColor
                            strokeWidth = 1.5f * scale
                            alpha = 180
                        })
                        
                        // Títulos del encabezado
                        paintText.color = greenColor
                        paintText.textSize = 14f * scale
                        frameCanvas.drawText("MEET ELITE TELEMETRY SYSTEMS [UDS ACTIVE LINK]", 30f * scale, 28f * scale, paintText)
                        
                        // Timer de grabación
                        val elapsedSeconds = frame.timestampMs / 1000
                        val timerStr = String.format("ELAPSED: %02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
                        paintText.color = Color.WHITE
                        paintText.textSize = 13f * scale
                        frameCanvas.drawText(timerStr, w - (200f * scale), 28f * scale, paintText)
                        
                        // ── Dibujar Tacómetro y Velocímetro ──
                        val cx = w / 2f
                        val cy = h - (120f * scale)
                        val radius = 90f * scale
                        
                        // Fondo del arco de RPM
                        paintGlow.style = Paint.Style.STROKE
                        paintGlow.strokeWidth = 6f * scale
                        paintGlow.strokeCap = Paint.Cap.ROUND
                        paintGlow.color = Color.WHITE
                        paintGlow.alpha = 50
                        frameCanvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 180f, 180f, false, paintGlow)
                        
                        // Arco activo de RPM
                        val maxRpm = 7000f
                        val rpmSweep = (frame.rpm.coerceIn(0f, maxRpm) / maxRpm) * 180f
                        val rpmColor = when {
                            frame.rpm > 5500f -> errorColor
                            frame.rpm > 4000f -> warningColor
                            else -> greenColor
                        }
                        paintGlow.color = rpmColor
                        paintGlow.alpha = 255
                        frameCanvas.drawArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 180f, rpmSweep.coerceAtLeast(1f), false, paintGlow)
                        
                        // Velocímetro digital
                        paintText.color = Color.WHITE
                        paintText.textSize = 48f * scale
                        paintText.textAlign = Paint.Align.CENTER
                        frameCanvas.drawText(String.format("%.0f", frame.speedKph), cx, cy + (10f * scale), paintText)
                        
                        paintText.color = cyanColor
                        paintText.textSize = 11f * scale
                        frameCanvas.drawText("KM/H", cx, cy + (25f * scale), paintText)
                        
                        paintText.color = Color.WHITE
                        paintText.textSize = 12f * scale
                        frameCanvas.drawText("${frame.rpm.toInt()} RPM", cx, cy - (20f * scale), paintText)
                        paintText.textAlign = Paint.Align.LEFT // Reset align

                        // ── Dibujar Barras de Aceleración y Carga (Esquina Inferior Izquierda) ──
                        val pedalX = 40f * scale
                        val pedalY = h - (120f * scale)
                        val pedalW = 160f * scale
                        val pedalH = 8f * scale
                        
                        // 1. Acelerador (Throttle)
                        drawPedalBar(frameCanvas, pedalX, pedalY, pedalW, pedalH, scale, "THR (ACEL)", frame.throttle, greenColor)
                        
                        // 2. Carga Motor (Load)
                        drawPedalBar(frameCanvas, pedalX, pedalY + (35f * scale), pedalW, pedalH, scale, "LOD (CARGA)", frame.load, cyanColor)

                        // ── Dibujar Radar de Fuerza G (Esquina Inferior Derecha) ──
                        val gRadarSize = 60f * scale
                        val gRadarX = w - gRadarSize - (60f * scale)
                        val gRadarY = h - gRadarSize - (30f * scale)
                        val gCenterX = gRadarX + (gRadarSize / 2f)
                        val gCenterY = gRadarY + (gRadarSize / 2f)
                        
                        // Fondo del radar
                        frameCanvas.drawCircle(gCenterX, gCenterY, gRadarSize / 2f, Paint().apply {
                            color = Color.BLACK
                            alpha = 100
                        })
                        
                        paintGlow.color = Color.WHITE
                        paintGlow.strokeWidth = 1f * scale
                        paintGlow.alpha = 40
                        frameCanvas.drawCircle(gCenterX, gCenterY, gRadarSize / 2f, paintGlow)
                        frameCanvas.drawCircle(gCenterX, gCenterY, gRadarSize / 4f, paintGlow)
                        
                        // Ejes cartesianos cruzados
                        frameCanvas.drawLine(gCenterX - (gRadarSize / 2f), gCenterY, gCenterX + (gRadarSize / 2f), gCenterY, paintGlow)
                        frameCanvas.drawLine(gCenterX, gCenterY - (gRadarSize / 2f), gCenterX, gCenterY + (gRadarSize / 2f), paintGlow)
                        
                        // Vector de Fuerza G actual (máximo mapeado a 1.0G)
                        val maxG = 1.0f
                        val gRatio = (frame.gForce / maxG).coerceIn(-1f, 1f)
                        val pointerY = gCenterY - (gRatio * (gRadarSize / 2f))
                        
                        // Línea conectora
                        frameCanvas.drawLine(gCenterX, gCenterY, gCenterX, pointerY, Paint().apply {
                            color = greenColor
                            strokeWidth = 1.5f * scale
                            alpha = 150
                            isAntiAlias = true
                        })
                        
                        // Punto del vector
                        frameCanvas.drawCircle(gCenterX, pointerY, 4f * scale, Paint().apply {
                            color = greenColor
                            isAntiAlias = true
                        })
                        
                        // Texto Fuerza G
                        paintText.color = Color.WHITE
                        paintText.textSize = 9f * scale
                        frameCanvas.drawText(String.format("FORCE G: %.2fG", frame.gForce), gRadarX - (10f * scale), gRadarY - (10f * scale), paintText)

                        return frameBitmap
                    }

                    private fun drawPedalBar(
                        canvas: Canvas,
                        x: Float,
                        y: Float,
                        width: Float,
                        height: Float,
                        scale: Float,
                        label: String,
                        value: Float,
                        colorVal: Int
                    ) {
                        // Fondo de la barra
                        canvas.drawRoundRect(RectF(x, y, x + width, y + height), 3f * scale, 3f * scale, Paint().apply {
                            color = Color.BLACK
                            alpha = 120
                        })
                        
                        // Relleno proporcional
                        val fillW = width * (value / 100f).coerceIn(0f, 1f)
                        if (fillW > 0) {
                            canvas.drawRoundRect(RectF(x, y, x + fillW, y + height), 3f * scale, 3f * scale, Paint().apply {
                                color = colorVal
                                isAntiAlias = true
                            })
                        }
                        
                        // Textos descriptivos
                        paintText.color = Color.WHITE
                        paintText.textSize = 9f * scale
                        canvas.drawText(label, x, y - (4f * scale), paintText)
                        
                        paintText.color = colorVal
                        canvas.drawText("${value.toInt()}%", x + width - (30f * scale), y - (4f * scale), paintText)
                    }
                }

                // 2. Configurar la composición del MediaItem con los efectos (URI soporta content:// para Scoped Storage)
                val mediaItem = MediaItem.fromUri(inputUri)
                val overlayEffect = OverlayEffect(ImmutableList.of(bitmapOverlay))
                val effects = Effects(ImmutableList.of(), ImmutableList.of<Effect>(overlayEffect))
                
                val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                    .setEffects(effects)
                    .setRemoveAudio(false) // Preservar audio de micrófono de la cabina
                    .build()

                // 3. Configurar el exportador / transcodificador
                val transformer = Transformer.Builder(context)
                    .build()
                
                var progressJob: Job? = null
                
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressJob?.cancel()
                        onProgress(100)
                        Log.d("TelemetryBurner", "Transformation completed successfully")
                        onComplete()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressJob?.cancel()
                        Log.e("TelemetryBurner", "Transformation failed", exportException)
                        onError(exportException)
                    }
                })

                // 4. Iniciar la transformación
                withContext(Dispatchers.Main) {
                    transformer.start(editedMediaItem, outputFile.absolutePath)
                }

                // 5. Iniciar la corrutina de monitoreo de progreso
                progressJob = scope.launch {
                    val progressHolder = ProgressHolder()
                    while (isActive) {
                        val progressState = transformer.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val progressPercent = progressHolder.progress
                            withContext(Dispatchers.Main) {
                                onProgress(progressPercent)
                            }
                        }
                        if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                            break
                        }
                        delay(200L)
                    }
                }

            } catch (e: Exception) {
                Log.e("TelemetryBurner", "Failed to setup video telemetry burner", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
}
