package com.smartambulance.driver.data

/**
 * The signed-in operator.
 *
 * There is no PIN or password field: credentials are held by Firebase Auth and
 * never leave it. What remains here is the authorisation profile that decides
 * which dashboard opens and which junction, hospital or ambulance it is scoped to.
 */
data class AppUser(
    val userId: String,
    val name: String,
    val role: String,
    val ambulanceId: String? = null,
    val assignedJunctionId: String? = null,
    val hospitalId: String? = null
)

/**
 * A hospital as offered to the driver as a destination.
 *
 * `distance` and `eta` are nullable on purpose. Bed count is a fact read from
 * the database; distance and travel time are either a stored registry value or a
 * straight-line estimate from the phone's own fix. When neither exists the picker
 * shows a dash rather than a made-up figure.
 */
data class HospitalOption(
    val id: String,
    val name: String,
    val beds: Int? = null,
    /** Display-ready, e.g. "2.4 km". Stored value first, computed straight-line fallback. */
    val distance: String? = null,
    val eta: String? = null
)

/** Compact registry listing shown on the admin overview. */
data class AdminSummary(
    val users: List<String>,
    val ambulances: List<String>,
    val hospitals: List<String>,
    val rfidTags: List<String>,
    val junctions: List<String>
)

/**
 * Typed views of the Realtime Database nodes.
 *
 * Everything the UI renders comes from one of these; nothing is invented on the
 * client. Fields are nullable because the database genuinely has no value for
 * them until the relevant device reports in, and the UI is expected to show that
 * absence rather than substitute a plausible number.
 */

/** `ambulances/$ambulanceId` */
data class AmbulanceState(
    val ambulanceId: String,
    val driverId: String? = null,
    val rfidTagId: String? = null,
    val status: String? = null,
    val emergencyActive: Boolean = false,
    val severity: String? = null,
    val destinationHospitalId: String? = null,
    /** Phone GPS, published by the driver app: `lastLocation`. */
    val lat: Double? = null,
    val lng: Double? = null,
    val locationSource: String? = null,
    val updatedAt: Long? = null
) {
    val hasLocation: Boolean get() = lat != null && lng != null
    val isOnEmergency: Boolean get() = emergencyActive
}

/** `loraTelemetry/$junctionId/$ambulanceId` — written by the roadside receiver. */
data class LoRaTelemetry(
    val lat: Double? = null,
    val lng: Double? = null,
    val speedKmph: Double? = null,
    val headingDeg: Double? = null,
    val distanceMeters: Double? = null,
    val bearingToJunctionDeg: Double? = null,
    val rssi: Int? = null,
    val gpsFix: Boolean = false,
    val approaching: Boolean? = null,
    val preemptionEligible: Boolean? = null,
    val updatedAt: Long? = null
) {
    val hasFix: Boolean get() = gpsFix && lat != null && lng != null

    companion object {
        val EMPTY = LoRaTelemetry()
    }
}

/** `junctions/$junctionId` */
data class JunctionState(
    val junctionId: String,
    val name: String? = null,
    val signalState: String? = null,
    val activeLane: String? = null,
    val updatedAt: Long? = null
)

/** `rfidTags/$rfidTagId` */
data class RfidTag(
    val rfidTagId: String,
    val ambulanceId: String? = null,
    val authorized: Boolean = false,
    val active: Boolean = false,
    val updatedAt: Long? = null
)

/**
 * `junctionEvents/$eventId` — the roadside controller's log.
 *
 * `eventType` carries the meaning: `gps_preempt_started` and `rssi_preempt_started`
 * open a corridor, `rfid_clearance` is the stop-line release, `timeout_restore` and
 * `manual_reset` close it, and `entry`/`exit` are mere presence detection.
 */
data class JunctionEvent(
    val eventId: String,
    val junctionId: String? = null,
    val junctionName: String? = null,
    val ambulanceId: String? = null,
    val rfidTagId: String? = null,
    val lane: String? = null,
    val eventType: String? = null,
    val preemptionMode: String? = null,
    val distanceMeters: Double? = null,
    val rssi: Int? = null,
    val dwellTimeMs: Long? = null,
    val timestamp: Long? = null
) {
    /** A corridor was opened for this unit. */
    val openedCorridor: Boolean
        get() = eventType?.contains("preempt", ignoreCase = true) == true

    /** The stop-line reader released the junction. */
    val clearedAtStopLine: Boolean
        get() = eventType?.contains("clearance", ignoreCase = true) == true ||
            eventType?.contains("clear", ignoreCase = true) == true

    /** A recorded hand-back of normal signal control. */
    val restored: Boolean
        get() = eventType?.contains("restore", ignoreCase = true) == true ||
            eventType?.contains("reset", ignoreCase = true) == true
}

/** An alert row under `policeAlerts/$junctionId` or `hospitalAlerts/$hospitalId`. */
data class AlertRecord(
    val key: String,
    val tripId: String? = null,
    val ambulanceId: String? = null,
    val destinationHospitalId: String? = null,
    val severity: String? = null,
    val message: String? = null,
    val eta: String? = null,
    val status: String? = null,
    val preemptionMode: String? = null,
    val distanceMeters: Double? = null,
    val timestamp: Long? = null
)

/** `emergencyTrips/$tripId` */
data class TripRecord(
    val tripId: String,
    val ambulanceId: String? = null,
    val driverId: String? = null,
    val severity: String? = null,
    val destinationHospitalId: String? = null,
    val status: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null
) {
    val isActive: Boolean get() = endedAt == null
}

/** `hospitals/$hospitalId` */
data class HospitalRecord(
    val hospitalId: String,
    val name: String,
    val bedsAvailable: Int? = null,
    val phone: String? = null,
    val emergencyAvailable: Boolean? = null,
    /** Stored by the registry, e.g. "2.4 km" — shown in preference to any estimate. */
    val distance: String? = null,
    /** Stored travel time, e.g. "6 min". */
    val eta: String? = null,
    val lat: Double? = null,
    val lng: Double? = null
)

// ---------------------------------------------------------------------------
// Realtime Database value coercion
//
// RTDB hands back loosely typed maps: integers arrive as Long, decimals as
// Double, and a "42" written by firmware can arrive as a String. These helpers
// normalise that once so no screen has to guess.
// ---------------------------------------------------------------------------

internal fun Any?.asString(): String? = when (this) {
    null -> null
    is String -> this.ifBlank { null }
    else -> toString()
}

internal fun Any?.asDouble(): Double? = when (this) {
    null -> null
    is Double -> this
    is Long -> this.toDouble()
    is Int -> this.toDouble()
    is Float -> this.toDouble()
    is Number -> this.toDouble()
    is String -> this.trim().toDoubleOrNull()
    else -> null
}

internal fun Any?.asLong(): Long? = when (this) {
    null -> null
    is Long -> this
    is Int -> this.toLong()
    is Double -> this.toLong()
    is Number -> this.toLong()
    is String -> this.trim().toLongOrNull()
    else -> null
}

/**
 * The roadside controller writes `dwellTime` as a human duration such as "7s" or
 * "1m30s", but the ambulance unit writes raw milliseconds. Both are accepted so
 * the stop-line log reports a real figure rather than silently showing nothing.
 */
internal fun Any?.asDurationMs(): Long? = when (this) {
    null -> null
    is Number -> this.toLong()
    is String -> {
        val text = this.trim()
        text.toLongOrNull() ?: run {
            val match = Regex("^(?:(\\d+)m)?(?:(\\d+)s)?$").find(text)
            if (match == null || (match.groupValues[1].isEmpty() && match.groupValues[2].isEmpty())) {
                null
            } else {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                minutes * 60_000L + seconds * 1_000L
            }
        }
    }
    else -> null
}

internal fun Any?.asInt(): Int? = when (this) {
    null -> null
    is Int -> this
    is Long -> this.toInt()
    is Double -> this.toInt()
    is Number -> this.toInt()
    is String -> this.trim().toDoubleOrNull()?.toInt()
    else -> null
}

internal fun Any?.asBool(): Boolean = when (this) {
    null -> false
    is Boolean -> this
    is String -> this.equals("true", ignoreCase = true) || this == "1"
    is Number -> this.toInt() != 0
    else -> false
}

internal fun Any?.asBoolOrNull(): Boolean? = when (this) {
    null -> null
    is Boolean -> this
    is String -> when {
        this.equals("true", ignoreCase = true) || this == "1" -> true
        this.equals("false", ignoreCase = true) || this == "0" -> false
        else -> null
    }
    is Number -> this.toInt() != 0
    else -> null
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>
