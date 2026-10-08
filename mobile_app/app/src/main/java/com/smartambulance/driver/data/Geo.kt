package com.smartambulance.driver.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Great-circle maths.
 *
 * Used to turn two real coordinate pairs into a distance. This is only ever
 * applied when both positions are genuinely known; when either is missing the
 * caller keeps `null` and the UI shows a dash rather than an estimate.
 */
object Geo {

    private const val EARTH_RADIUS_KM = 6371.0088

    /** Straight-line distance in kilometres, or null when either point is unknown. */
    fun haversineKm(
        fromLat: Double?,
        fromLng: Double?,
        toLat: Double?,
        toLng: Double?
    ): Double? {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return null

        val dLat = Math.toRadians(toLat - fromLat)
        val dLng = Math.toRadians(toLng - fromLng)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Formats a distance the way the dashboards show it, or null when there is
     * nothing to show.
     */
    fun formatKm(km: Double?): String? = when {
        km == null -> null
        km < 1.0 -> "${(km * 1000).toInt()} m"
        km < 10.0 -> String.format("%.1f km", km)
        else -> "${km.toInt()} km"
    }
}
