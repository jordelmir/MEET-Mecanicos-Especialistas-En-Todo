package com.elysium369.meet.safety.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SafetyLocationSample(val latitude: Double, val longitude: Double, val accuracyMeters: Float, val capturedAtEpochMs: Long)

/** One explicit request, no background tracking and no fabricated or stale fallback. */
class FusedSafetyLocationProvider @Inject constructor(@ApplicationContext private val context: Context) {
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): SafetyLocationSample = withTimeout(25_000) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        check(fine || coarse) { "Permiso de ubicación no concedido. Puedes continuar sin ubicación." }
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }
            val request = CurrentLocationRequest.Builder()
                .setPriority(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(15_000).setDurationMillis(20_000).build()
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    val ageMs = location?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
                    if (location == null || ageMs == null || ageMs !in 0..30_000 || !location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy <= 0 || !location.latitude.isFinite() || !location.longitude.isFinite()) {
                        continuation.resumeWithException(IllegalStateException("No se obtuvo una ubicación reciente. Puedes reintentar o continuar sin ubicación."))
                    } else {
                        continuation.resume(SafetyLocationSample(location.latitude, location.longitude, location.accuracy, location.time))
                    }
                }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                .addOnCanceledListener { continuation.cancel() }
        }
    }
}
