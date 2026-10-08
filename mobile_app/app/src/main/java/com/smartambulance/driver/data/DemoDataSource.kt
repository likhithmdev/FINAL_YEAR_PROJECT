package com.smartambulance.driver.data

/**
 * Canned dataset for the **offline demonstration mode**.
 *
 * This is the only place in the app where invented values are allowed to exist.
 * It is never read on a live path: [SaptcsRepository] consults it only when it
 * was constructed with `offline = true`, and never writes anything to the
 * database in that mode. Screens are expected to badge themselves while this is
 * active so no one mistakes these numbers for a real corridor.
 *
 * The PINs below are demonstration credentials for this dataset alone. They are
 * not, and must never become, credentials for the live Firebase project.
 */
object DemoDataSource {

    private val demoUsers = listOf(
        AppUser("driver_001", "Demo Driver", "ambulance_driver", ambulanceId = "AMB001"),
        AppUser("police_001", "Demo Junction Officer", "police", assignedJunctionId = "JNC001"),
        AppUser("hospital_001", "Demo Receiving Desk", "hospital", hospitalId = "HOSP001"),
        AppUser("admin_001", "Demo Administrator", "admin")
    )

    private val demoPins = mapOf(
        "driver_001" to "1111",
        "police_001" to "2222",
        "hospital_001" to "3333",
        "admin_001" to "0000"
    )

    /** Returns the matching demo operator, or null when nothing matches. */
    fun signIn(operatorIdOrEmail: String, pin: String): AppUser? {
        val id = SaptcsRepository.operatorIdFor(operatorIdOrEmail)
        return demoUsers.firstOrNull { it.userId == id && demoPins[id] == pin.trim() }
    }

    /** The operator IDs the offline mode accepts, for the sign-in screen hint. */
    val operatorIds: List<String> get() = demoUsers.map { it.userId }

    val hospitals = listOf(
        HospitalRecord("HOSP001", "City Care Hospital", bedsAvailable = 8, emergencyAvailable = true),
        HospitalRecord("HOSP002", "Metro Emergency Centre", bedsAvailable = 3, emergencyAvailable = true),
        HospitalRecord("HOSP003", "St. Mark Trauma Unit", bedsAvailable = 11, emergencyAvailable = true)
    )

    val junctions = listOf(
        JunctionState("JNC001", name = "Main Road Junction", signalState = "green", activeLane = "north"),
        JunctionState("JNC002", name = "High Street Junction", signalState = "preemption", activeLane = "north"),
        JunctionState("JNC003", name = "Central Avenue", signalState = "normal", activeLane = "normal"),
        JunctionState("JNC004", name = "North Junction", signalState = "normal", activeLane = "normal")
    )

    val ambulances = listOf(
        AmbulanceState(
            ambulanceId = "AMB001",
            driverId = "driver_001",
            rfidTagId = "RFID_TAG_001",
            status = "emergency_active",
            emergencyActive = true,
            severity = "Critical",
            destinationHospitalId = "HOSP001",
            lat = 12.9716,
            lng = 77.5946,
            locationSource = "demo"
        ),
        AmbulanceState(
            ambulanceId = "AMB002",
            driverId = "driver_002",
            rfidTagId = "RFID_TAG_002",
            status = "available",
            emergencyActive = false,
            lat = 12.9352,
            lng = 77.6245,
            locationSource = "demo"
        )
    )

    val rfidTags = listOf(
        RfidTag("RFID_TAG_001", "AMB001", authorized = true, active = true),
        RfidTag("RFID_TAG_002", "AMB002", authorized = true, active = true)
    )

    // Event names mirror the roadside controller's own vocabulary so the offline
    // sample exercises the same classification the live log does.
    val junctionEvents = listOf(
        JunctionEvent(
            eventId = "-demo1",
            junctionId = "JNC001",
            junctionName = "Main Road Junction",
            ambulanceId = "AMB001",
            eventType = "gps_preempt_started",
            preemptionMode = "gps",
            distanceMeters = 480.0,
            timestamp = 1735689000000L
        ),
        JunctionEvent(
            eventId = "-demo2",
            junctionId = "JNC001",
            junctionName = "Main Road Junction",
            ambulanceId = "AMB001",
            rfidTagId = "RFID_TAG_001",
            lane = "north",
            eventType = "rfid_clearance",
            dwellTimeMs = 4200L,
            timestamp = 1735689600000L
        )
    )

    val trips = listOf(
        TripRecord(
            tripId = "TRIP-DEMO-1",
            ambulanceId = "AMB001",
            driverId = "driver_001",
            severity = "Critical",
            destinationHospitalId = "HOSP001",
            status = "emergency_active",
            startedAt = 1735689600000L
        ),
        TripRecord(
            tripId = "TRIP-DEMO-0",
            ambulanceId = "AMB002",
            driverId = "driver_002",
            severity = "Serious",
            destinationHospitalId = "HOSP003",
            status = "completed",
            startedAt = 1735603200000L,
            endedAt = 1735606800000L
        )
    )

    val policeAlerts = listOf(
        AlertRecord(
            key = "-demo1",
            tripId = "TRIP-DEMO-1",
            ambulanceId = "AMB001",
            severity = "Critical",
            message = "Ambulance approaching junction",
            preemptionMode = "gps_lora",
            distanceMeters = 420.0,
            timestamp = 1735689600000L
        )
    )

    val hospitalAlerts = listOf(
        AlertRecord(
            key = "-demo1",
            tripId = "TRIP-DEMO-1",
            ambulanceId = "AMB001",
            severity = "Critical",
            eta = "8 min",
            status = "incoming",
            timestamp = 1735689600000L
        )
    )

    val loRaTelemetry = LoRaTelemetry(
        lat = 12.9716,
        lng = 77.5946,
        speedKmph = 42.0,
        headingDeg = 185.0,
        distanceMeters = 420.0,
        bearingToJunctionDeg = 182.0,
        rssi = -72,
        gpsFix = true,
        approaching = true,
        preemptionEligible = true
    )

    val adminSummary = AdminSummary(
        users = demoUsers.map { "${it.name} (${it.userId})" },
        ambulances = ambulances.map { "${it.ambulanceId}" },
        hospitals = hospitals.map { "${it.name} (${it.hospitalId})" },
        rfidTags = rfidTags.map { "${it.rfidTagId} → ${it.ambulanceId}" },
        junctions = junctions.map { "${it.name ?: it.junctionId} (${it.junctionId})" }
    )
}
