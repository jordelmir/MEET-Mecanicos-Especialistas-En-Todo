package com.elysium.vanguard.recordshield.data.cloud

import android.util.Size
import androidx.camera.video.Quality

/**
 * Video quality presets for chunk recording.
 *
 * FHD/HD/SD use CameraX QualitySelector (hardware-optimized).
 * LOW/VLOW use MediaRecorder directly with custom resolution.
 *
 * Chunk size estimates (10s chunks, typical encoder):
 *   FHD:  ~100-150 MB  (10-15 Mbps)
 *   HD:   ~50-80 MB    (5-8 Mbps)
 *   SD:   ~25-40 MB    (2.5-4 Mbps)
 *   LOW:  ~10-20 MB    (1-2 Mbps)
 *   VLOW: ~5-10 MB     (0.5-1 Mbps)
 */
enum class VideoQualityPreset(
    val label: String,
    val resolution: String,
    val width: Int,
    val height: Int,
    val videoBitrate: Int,
    val estimatedBitrateMbps: Int,
    val description: String,
    val cameraXQuality: Quality? = null,
    val useMediaRecorder: Boolean = false
) {
    FHD(
        label = "1080p Full HD",
        resolution = "1920x1080",
        width = 1920,
        height = 1080,
        videoBitrate = 12_000_000,
        estimatedBitrateMbps = 12,
        description = "Maxima calidad (~120MB/10s)",
        cameraXQuality = Quality.FHD
    ),
    HD(
        label = "720p HD",
        resolution = "1280x720",
        width = 1280,
        height = 720,
        videoBitrate = 6_000_000,
        estimatedBitrateMbps = 6,
        description = "Balance calidad/tamaño (~60MB/10s)",
        cameraXQuality = Quality.HD
    ),
    SD(
        label = "480p SD",
        resolution = "640x480",
        width = 640,
        height = 480,
        videoBitrate = 3_000_000,
        estimatedBitrateMbps = 3,
        description = "Calidad baja (~30MB/10s)",
        cameraXQuality = Quality.SD
    ),
    LOW(
        label = "360p Low",
        resolution = "640x360",
        width = 640,
        height = 360,
        videoBitrate = 1_000_000,
        estimatedBitrateMbps = 1,
        description = "Internet lento (~15MB/10s)",
        useMediaRecorder = true
    ),
    VLOW(
        label = "240p Ultra Low",
        resolution = "426x240",
        width = 426,
        height = 240,
        videoBitrate = 500_000,
        estimatedBitrateMbps = 0,
        description = "Conexion critica (~7MB/10s)",
        useMediaRecorder = true
    );

    companion object {
        val DEFAULT = SD
    }
}
