package com.elysium369.meet.ride.traffic

/**
 * Encodes a privacy-conscious coarse geohash for traffic aggregation.
 *
 * Precision six is intentionally less exact than the raw incident coordinate.
 * It is suitable for regional querying; road matching still belongs to the
 * routing engine and must not be inferred from this cell alone.
 */
object RideGeoCell {
    private const val PRECISION = 6
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun encode(latitude: Double, longitude: Double): String {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)

        var latitudeRange = -90.0 to 90.0
        var longitudeRange = -180.0 to 180.0
        var bit = 0
        var character = 0
        var refineLongitude = true
        val result = StringBuilder(PRECISION)

        while (result.length < PRECISION) {
            val range = if (refineLongitude) longitudeRange else latitudeRange
            val coordinate = if (refineLongitude) longitude else latitude
            val midpoint = (range.first + range.second) / 2.0
            if (coordinate >= midpoint) {
                character = character or (1 shl (4 - bit))
                if (refineLongitude) {
                    longitudeRange = midpoint to range.second
                } else {
                    latitudeRange = midpoint to range.second
                }
            } else if (refineLongitude) {
                longitudeRange = range.first to midpoint
            } else {
                latitudeRange = range.first to midpoint
            }

            refineLongitude = !refineLongitude
            if (bit < 4) {
                bit += 1
            } else {
                result.append(BASE32[character])
                bit = 0
                character = 0
            }
        }
        return result.toString()
    }
}
