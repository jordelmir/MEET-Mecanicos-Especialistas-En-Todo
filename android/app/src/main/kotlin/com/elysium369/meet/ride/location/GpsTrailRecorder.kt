package com.elysium369.meet.ride.location

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Records GPS forensic points locally during an active ride.
 * Points are stored in a JSON file per ride so they survive process death.
 * When the ride completes, the trail is finalized and can be exported as PDF.
 */
object GpsTrailRecorder {
    private const val TAG = "GpsTrailRecorder"
    private const val DIR_NAME = "gps_forensic_trails"
    private const val MAX_POINTS_PER_RIDE = 10_000

    private fun trailDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun trailFile(context: Context, rideId: String): File {
        val safeId = rideId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(100)
        return File(trailDir(context), "$safeId.json")
    }

    private fun activeRidePrefs(context: Context): SharedPreferences =
        context.getSharedPreferences("gps_forensic_active", Context.MODE_PRIVATE)

    /**
     * Start recording for a ride. Clears any previous trail for this ride.
     */
    fun startRecording(context: Context, rideId: String, driverId: String, passengerId: String) {
        val file = trailFile(context, rideId)
        val meta = JSONObject().apply {
            put("rideId", rideId)
            put("driverId", driverId)
            put("passengerId", passengerId)
            put("startedAtEpochMs", System.currentTimeMillis())
            put("completedAtEpochMs", 0L)
            put("points", JSONArray())
        }
        file.writeText(meta.toString())
        activeRidePrefs(context).edit().putString("active_ride_id", rideId).apply()
        Log.i(TAG, "Started recording trail for ride $rideId")
    }

    /**
     * Add a GPS point to the active trail. Called from RideLocationTrackingService.
     */
    suspend fun recordPoint(
        context: Context,
        rideId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        speedMetersPerSecond: Float?,
        headingDegrees: Int?,
        capturedAtEpochMs: Long,
    ) = withContext(Dispatchers.IO) {
        try {
            val file = trailFile(context, rideId)
            if (!file.exists()) return@withContext
            val json = JSONObject(file.readText())
            val points = json.getJSONArray("points")
            if (points.length() >= MAX_POINTS_PER_RIDE) return@withContext

            val point = JSONObject().apply {
                put("lat", latitude)
                put("lng", longitude)
                put("acc", accuracyMeters.toDouble())
                put("spd", speedMetersPerSecond?.toDouble() ?: JSONObject.NULL)
                put("hdg", headingDegrees ?: JSONObject.NULL)
                put("ts", capturedAtEpochMs)
                put("seq", points.length().toLong())
            }
            points.put(point)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record GPS point: ${e.message}")
        }
    }

    /**
     * Finalize the trail when the ride completes.
     */
    suspend fun stopRecording(context: Context, rideId: String) = withContext(Dispatchers.IO) {
        try {
            val file = trailFile(context, rideId)
            if (!file.exists()) return@withContext
            val json = JSONObject(file.readText())
            json.put("completedAtEpochMs", System.currentTimeMillis())
            file.writeText(json.toString())
            activeRidePrefs(context).edit().remove("active_ride_id").apply()
            Log.i(TAG, "Stopped recording trail for ride $rideId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop recording: ${e.message}")
        }
    }

    /**
     * Load a finalized trail and build the forensic trail object.
     */
    suspend fun loadTrail(context: Context, rideId: String): GpsForensicTrail? = withContext(Dispatchers.IO) {
        try {
            val file = trailFile(context, rideId)
            if (!file.exists()) return@withContext null
            val json = JSONObject(file.readText())
            val pointsArray = json.getJSONArray("points")
            val points = (0 until pointsArray.length()).map { i ->
                val p = pointsArray.getJSONObject(i)
                GpsForensicPoint(
                    latitude = p.getDouble("lat"),
                    longitude = p.getDouble("lng"),
                    accuracyMeters = p.getDouble("acc").toFloat(),
                    speedMetersPerSecond = if (p.isNull("spd")) null else p.getDouble("spd").toFloat(),
                    headingDegrees = if (p.isNull("hdg")) null else p.getInt("hdg"),
                    capturedAtEpochMs = p.getLong("ts"),
                    sequence = p.getLong("seq"),
                )
            }
            GpsForensicTrail.build(
                rideId = json.getString("rideId"),
                driverId = json.getString("driverId"),
                passengerId = json.getString("passengerId"),
                points = points,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load trail: ${e.message}")
            null
        }
    }

    /**
     * Check if there's an active recording for any ride.
     */
    fun hasActiveRecording(context: Context): Boolean =
        activeRidePrefs(context).contains("active_ride_id")

    fun getActiveRideId(context: Context): String? =
        activeRidePrefs(context).getString("active_ride_id", null)

    /**
     * List all trail files available for export.
     */
    fun listTrails(context: Context): List<String> =
        trailDir(context).listFiles()
            ?.filter { it.extension == "json" && it.length() > 100 }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

    /**
     * Delete a trail file after successful export.
     */
    fun deleteTrail(context: Context, rideId: String) {
        trailFile(context, rideId).delete()
    }
}
