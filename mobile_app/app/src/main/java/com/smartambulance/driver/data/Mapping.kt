package com.smartambulance.driver.data

/**
 * Realtime Database map -> typed model.
 *
 * Deliberately pure: these take the `Map<String, Any?>` that a `DataSnapshot`
 * exposes and return a model, with no Firebase types involved. That keeps the
 * one part of the app most likely to be wrong about real data under unit test.
 */
object Mapping {

    // -- single records ----------------------------------------------------

    fun ambulance(id: String, raw: Map<String, Any?>?): AmbulanceState {
        val location = raw?.get("lastLocation").asMap()
        return AmbulanceState(
            ambulanceId = id,
            driverId = raw?.get("driverId").asString(),
            rfidTagId = raw?.get("rfidTagId").asString(),
            status = raw?.get("status").asString(),
            emergencyActive = raw?.get("emergencyActive").asBool(),
            severity = raw?.get("severity").asString(),
            destinationHospitalId = raw?.get("destinationHospitalId").asString(),
            lat = location?.get("lat").asDouble(),
            lng = location?.get("lng").asDouble(),
            locationSource = location?.get("source").asString(),
            updatedAt = location?.get("updatedAt").asLong() ?: raw?.get("updatedAt").asLong()
        )
    }

    /**
     * LoRa fields, preferring the node the roadside receiver writes
     * (`loraTelemetry/$junction/$ambulance`) and falling back to whatever the
     * ambulance unit last published on its own record.
     */
    fun loRaTelemetry(
        receiverNode: Map<String, Any?>?,
        ambulanceFallback: Map<String, Any?>?
    ): LoRaTelemetry {
        val source = receiverNode ?: ambulanceFallback ?: return LoRaTelemetry.EMPTY
        return LoRaTelemetry(
            lat = source["lat"].asDouble(),
            lng = source["lng"].asDouble(),
            speedKmph = source["speedKmph"].asDouble(),
            headingDeg = source["headingDeg"].asDouble(),
            distanceMeters = source["distanceMeters"].asDouble(),
            bearingToJunctionDeg = source["bearingToJunctionDeg"].asDouble(),
            rssi = source["rssi"].asInt(),
            gpsFix = source["gpsFix"].asBool(),
            approaching = source["approaching"].asBoolOrNull(),
            preemptionEligible = source["preemptionEligible"].asBoolOrNull(),
            updatedAt = source["updatedAt"].asLong()
        )
    }

    fun junction(id: String, raw: Map<String, Any?>?): JunctionState = JunctionState(
        junctionId = id,
        name = raw?.get("name").asString(),
        signalState = raw?.get("signalState").asString(),
        activeLane = raw?.get("activeLane").asString(),
        updatedAt = raw?.get("updatedAt").asLong()
    )

    fun rfidTag(id: String, raw: Map<String, Any?>?): RfidTag = RfidTag(
        rfidTagId = id,
        ambulanceId = raw?.get("ambulanceId").asString(),
        authorized = raw?.get("authorized").asBool(),
        active = raw?.get("active").asBool(),
        updatedAt = raw?.get("updatedAt").asLong()
    )

    fun junctionEvent(id: String, raw: Map<String, Any?>?): JunctionEvent = JunctionEvent(
        eventId = id,
        junctionId = raw?.get("junctionId").asString(),
        junctionName = raw?.get("junctionName").asString(),
        ambulanceId = raw?.get("ambulanceId").asString(),
        rfidTagId = raw?.get("rfidTagId").asString(),
        lane = raw?.get("lane").asString(),
        eventType = raw?.get("eventType").asString(),
        preemptionMode = raw?.get("preemptionMode").asString(),
        distanceMeters = raw?.get("distanceMeters").asDouble(),
        rssi = raw?.get("rssi").asInt(),
        dwellTimeMs = raw?.get("dwellTime").asDurationMs() ?: raw?.get("dwellTimeMs").asDurationMs(),
        timestamp = raw?.get("timestamp").asLong() ?: raw?.get("updatedAt").asLong()
    )

    fun alert(key: String, raw: Map<String, Any?>?): AlertRecord = AlertRecord(
        key = key,
        tripId = raw?.get("tripId").asString(),
        ambulanceId = raw?.get("ambulanceId").asString(),
        destinationHospitalId = raw?.get("destinationHospitalId").asString(),
        severity = raw?.get("severity").asString(),
        message = raw?.get("message").asString(),
        eta = raw?.get("eta").asString(),
        status = raw?.get("status").asString(),
        preemptionMode = raw?.get("preemptionMode").asString(),
        distanceMeters = raw?.get("distanceMeters").asDouble(),
        timestamp = raw?.get("timestamp").asLong() ?: raw?.get("updatedAt").asLong()
    )

    fun trip(id: String, raw: Map<String, Any?>?): TripRecord = TripRecord(
        tripId = id,
        ambulanceId = raw?.get("ambulanceId").asString(),
        driverId = raw?.get("driverId").asString(),
        severity = raw?.get("severity").asString(),
        destinationHospitalId = raw?.get("destinationHospitalId").asString(),
        status = raw?.get("status").asString(),
        startedAt = raw?.get("startedAt").asLong() ?: raw?.get("startTime").asLong(),
        endedAt = raw?.get("endedAt").asLong() ?: raw?.get("endTime").asLong()
    )

    fun hospital(id: String, raw: Map<String, Any?>?): HospitalRecord = HospitalRecord(
        hospitalId = id,
        name = raw?.get("name").asString() ?: id,
        bedsAvailable = raw?.get("bedsAvailable").asInt(),
        phone = raw?.get("phone").asString() ?: raw?.get("contact").asString(),
        emergencyAvailable = raw?.get("emergencyAvailable").asBoolOrNull(),
        distance = raw?.get("distance").asString(),
        eta = raw?.get("eta").asString(),
        lat = raw?.get("latitude").asDouble() ?: raw?.get("lat").asDouble(),
        lng = raw?.get("longitude").asDouble() ?: raw?.get("lng").asDouble()
    )

    // -- whole nodes -------------------------------------------------------

    private inline fun <T> node(raw: Map<String, Any?>?, parse: (String, Map<String, Any?>?) -> T): List<T> =
        raw.orEmpty().map { (key, value) -> parse(key, value.asMap()) }

    fun ambulances(raw: Map<String, Any?>?): List<AmbulanceState> =
        node(raw) { id, child -> ambulance(id, child) }.sortedBy { it.ambulanceId }

    fun junctions(raw: Map<String, Any?>?): List<JunctionState> =
        node(raw) { id, child -> junction(id, child) }.sortedBy { it.junctionId }

    fun rfidTags(raw: Map<String, Any?>?): List<RfidTag> =
        node(raw) { id, child -> rfidTag(id, child) }.sortedBy { it.rfidTagId }

    fun junctionEvents(raw: Map<String, Any?>?): List<JunctionEvent> =
        node(raw) { id, child -> junctionEvent(id, child) }
            .sortedByDescending { it.timestamp ?: 0L }

    fun alerts(raw: Map<String, Any?>?): List<AlertRecord> =
        node(raw) { key, child -> alert(key, child) }
            .sortedByDescending { it.timestamp ?: 0L }

    fun trips(raw: Map<String, Any?>?): List<TripRecord> =
        node(raw) { id, child -> trip(id, child) }
            .sortedByDescending { it.startedAt ?: 0L }

    fun hospitals(raw: Map<String, Any?>?): List<HospitalRecord> =
        node(raw) { id, child -> hospital(id, child) }.sortedBy { it.hospitalId }

    /**
     * Hospitals the driver app can offer as a destination, in the shape its
     * picker expects. Distance and ETA are deliberately absent: only the
     * location-aware discovery service can compute those, and a placeholder
     * number here would be indistinguishable from a real one.
     */
    fun hospitalOptions(raw: Map<String, Any?>?): List<HospitalRecord> = hospitals(raw)
}
