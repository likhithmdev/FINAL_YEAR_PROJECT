package com.smartambulance.driver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping layer is the boundary between "whatever is in the database" and
 * "what the screens believe". Each case here corresponds to a shape the
 * firmware, the admin console or the driver app really can produce, including
 * the awkward ones: absent nodes, integers arriving as Long, and numbers that
 * firmware wrote as strings.
 */
class MappingTest {

    // -- ambulance ---------------------------------------------------------

    @Test
    fun `ambulance reads phone location and emergency state`() {
        val state = Mapping.ambulance(
            "AMB001",
            mapOf(
                "driverId" to "driver_001",
                "rfidTagId" to "RFID_TAG_001",
                "status" to "emergency_active",
                "emergencyActive" to true,
                "severity" to "Critical",
                "destinationHospitalId" to "HOSP001",
                "lastLocation" to mapOf("lat" to 12.9716, "lng" to 77.5946, "source" to "android_gps")
            )
        )

        assertEquals("AMB001", state.ambulanceId)
        assertEquals("driver_001", state.driverId)
        assertTrue(state.emergencyActive)
        assertEquals("Critical", state.severity)
        assertEquals(12.9716, state.lat!!, 1e-9)
        assertEquals(77.5946, state.lng!!, 1e-9)
        assertTrue(state.hasLocation)
        assertEquals("android_gps", state.locationSource)
    }

    @Test
    fun `ambulance without a location reports no fix rather than zeroes`() {
        val state = Mapping.ambulance("AMB009", mapOf("status" to "available"))

        assertNull(state.lat)
        assertNull(state.lng)
        assertFalse(state.hasLocation)
        assertFalse(state.emergencyActive)
    }

    @Test
    fun `ambulance tolerates a completely missing record`() {
        val state = Mapping.ambulance("AMB404", null)

        assertEquals("AMB404", state.ambulanceId)
        assertNull(state.status)
        assertNull(state.severity)
        assertFalse(state.hasLocation)
    }

    @Test
    fun `ambulance accepts integers written as strings by firmware`() {
        val state = Mapping.ambulance(
            "AMB002",
            mapOf("lastLocation" to mapOf("lat" to "12.9352", "lng" to "77.6245"))
        )

        assertEquals(12.9352, state.lat!!, 1e-9)
        assertEquals(77.6245, state.lng!!, 1e-9)
    }

    @Test
    fun `ambulance reads emergency flag written as a string`() {
        val state = Mapping.ambulance("AMB003", mapOf("emergencyActive" to "true"))

        assertTrue(state.emergencyActive)
        assertTrue(state.isOnEmergency)
    }

    // -- LoRa telemetry ----------------------------------------------------

    @Test
    fun `loRa telemetry prefers the receiver node`() {
        val telemetry = Mapping.loRaTelemetry(
            receiverNode = mapOf(
                "lat" to 12.9716,
                "lng" to 77.5946,
                "speedKmph" to 42L,
                "rssi" to -72L,
                "distanceMeters" to 420L,
                "gpsFix" to true
            ),
            ambulanceFallback = mapOf("lat" to 1.0, "lng" to 2.0, "speedKmph" to 99L)
        )

        assertEquals(12.9716, telemetry.lat!!, 1e-9)
        assertEquals(42.0, telemetry.speedKmph!!, 1e-9)
        assertEquals(-72, telemetry.rssi)
        assertEquals(420.0, telemetry.distanceMeters!!, 1e-9)
        assertTrue(telemetry.hasFix)
    }

    @Test
    fun `loRa telemetry falls back to the ambulance record when the receiver has not reported`() {
        val telemetry = Mapping.loRaTelemetry(
            receiverNode = null,
            ambulanceFallback = mapOf("lat" to 12.9, "lng" to 77.6, "gpsFix" to true)
        )

        assertEquals(12.9, telemetry.lat!!, 1e-9)
        assertTrue(telemetry.hasFix)
    }

    @Test
    fun `loRa telemetry with no source at all is empty and has no fix`() {
        val telemetry = Mapping.loRaTelemetry(null, null)

        assertEquals(LoRaTelemetry.EMPTY, telemetry)
        assertFalse(telemetry.hasFix)
        assertNull(telemetry.speedKmph)
    }

    @Test
    fun `hasFix requires the gps fix flag as well as coordinates`() {
        val telemetry = Mapping.loRaTelemetry(
            mapOf("lat" to 12.9, "lng" to 77.6, "gpsFix" to false),
            null
        )

        assertFalse("coordinates alone must not claim a fix", telemetry.hasFix)
    }

    // -- junctions, tags, events -------------------------------------------

    @Test
    fun `junction reads signal state`() {
        val junction = Mapping.junction(
            "JNC001",
            mapOf("name" to "Main Road Junction", "signalState" to "green", "activeLane" to "north")
        )

        assertEquals("Main Road Junction", junction.name)
        assertEquals("green", junction.signalState)
        assertEquals("north", junction.activeLane)
    }

    @Test
    fun `rfid tag keeps authorization and link`() {
        val tag = Mapping.rfidTag(
            "RFID_TAG_001",
            mapOf("ambulanceId" to "AMB001", "authorized" to true, "active" to true)
        )

        assertEquals("AMB001", tag.ambulanceId)
        assertTrue(tag.authorized)
        assertTrue(tag.active)
    }

    @Test
    fun `junction event reads the stop-line record`() {
        val event = Mapping.junctionEvent(
            "-Nabc123",
            mapOf(
                "junctionId" to "JNC001",
                "ambulanceId" to "AMB001",
                "eventType" to "stop_line_cleared",
                "preemptionMode" to "rfid",
                "timestamp" to 1735689600000L
            )
        )

        assertEquals("stop_line_cleared", event.eventType)
        assertEquals("rfid", event.preemptionMode)
        assertEquals(1735689600000L, event.timestamp)
    }

    // -- alerts ------------------------------------------------------------

    @Test
    fun `police alert reads approach distance`() {
        val alert = Mapping.alert(
            "-Nkey",
            mapOf(
                "ambulanceId" to "AMB001",
                "severity" to "P1",
                "message" to "Ambulance approaching junction",
                "preemptionMode" to "gps_lora",
                "distanceMeters" to 420L,
                "timestamp" to 1000L
            )
        )

        assertEquals("AMB001", alert.ambulanceId)
        assertEquals("P1", alert.severity)
        assertEquals(420.0, alert.distanceMeters!!, 1e-9)
        assertEquals("gps_lora", alert.preemptionMode)
    }

    @Test
    fun `hospital alert reads eta and status`() {
        val alert = Mapping.alert(
            "-Nkey",
            mapOf("ambulanceId" to "AMB001", "eta" to "8 min", "status" to "incoming", "severity" to "Critical")
        )

        assertEquals("8 min", alert.eta)
        assertEquals("incoming", alert.status)
    }

    // -- trips -------------------------------------------------------------

    @Test
    fun `trip accepts either timestamp naming convention`() {
        val explicit = Mapping.trip("TRIP001", mapOf("startedAt" to 500L, "endedAt" to 900L))
        val legacy = Mapping.trip("TRIP002", mapOf("startTime" to 500L, "endTime" to 900L))

        assertEquals(500L, explicit.startedAt)
        assertEquals(900L, explicit.endedAt)
        assertEquals(500L, legacy.startedAt)
        assertEquals(900L, legacy.endedAt)
    }

    @Test
    fun `trip without an end time is active`() {
        val active = Mapping.trip("TRIP003", mapOf("startedAt" to 100L))
        val finished = Mapping.trip("TRIP004", mapOf("startedAt" to 100L, "endedAt" to 200L))

        assertTrue(active.isActive)
        assertFalse(finished.isActive)
    }

    // -- hospitals ---------------------------------------------------------

    @Test
    fun `hospital falls back to its id when unnamed`() {
        val hospital = Mapping.hospital("HOSP007", mapOf("bedsAvailable" to 12L))

        assertEquals("HOSP007", hospital.name)
        assertEquals(12, hospital.bedsAvailable)
    }

    @Test
    fun `hospital reads legacy contact and coordinate keys`() {
        val hospital = Mapping.hospital(
            "HOSP004",
            mapOf(
                "name" to "Sathya Sai Hospital",
                "contact" to "+91 7022649111",
                "latitude" to 13.0285,
                "longitude" to 77.6585
            )
        )

        assertEquals("+91 7022649111", hospital.phone)
        assertEquals(13.0285, hospital.lat!!, 1e-9)
        assertEquals(77.6585, hospital.lng!!, 1e-9)
    }

    // -- whole nodes -------------------------------------------------------

    @Test
    fun `a missing node maps to an empty list rather than throwing`() {
        assertEquals(emptyList<AmbulanceState>(), Mapping.ambulances(null))
        assertEquals(emptyList<JunctionState>(), Mapping.junctions(null))
        assertEquals(emptyList<TripRecord>(), Mapping.trips(null))
        assertEquals(emptyList<HospitalRecord>(), Mapping.hospitals(null))
    }

    @Test
    fun `ambulances are ordered by id for a stable list`() {
        val list = Mapping.ambulances(
            mapOf(
                "AMB003" to mapOf("status" to "available"),
                "AMB001" to mapOf("status" to "available"),
                "AMB002" to mapOf("status" to "available")
            )
        )

        assertEquals(listOf("AMB001", "AMB002", "AMB003"), list.map { it.ambulanceId })
    }

    @Test
    fun `trips are ordered newest first`() {
        val list = Mapping.trips(
            mapOf(
                "TRIP001" to mapOf("startedAt" to 100L),
                "TRIP003" to mapOf("startedAt" to 300L),
                "TRIP002" to mapOf("startedAt" to 200L)
            )
        )

        assertEquals(listOf("TRIP003", "TRIP002", "TRIP001"), list.map { it.tripId })
    }

    @Test
    fun `alerts are ordered newest first`() {
        val list = Mapping.alerts(
            mapOf(
                "old" to mapOf("timestamp" to 100L),
                "new" to mapOf("timestamp" to 900L)
            )
        )

        assertEquals(listOf("new", "old"), list.map { it.key })
    }

    @Test
    fun `police alert node with children maps every child`() {
        val list = Mapping.alerts(
            mapOf(
                "-N1" to mapOf("ambulanceId" to "AMB001", "distanceMeters" to 420L),
                "-N2" to mapOf("ambulanceId" to "AMB002", "distanceMeters" to 210L)
            )
        )

        assertEquals(2, list.size)
        assertEquals(setOf("AMB001", "AMB002"), list.mapNotNull { it.ambulanceId }.toSet())
    }

    @Test
    fun `a malformed child does not abort the whole node`() {
        val list = Mapping.junctions(
            mapOf(
                "JNC001" to mapOf("name" to "Main Road Junction"),
                "JNC002" to "not-a-map",
                "JNC003" to null
            )
        )

        assertEquals(listOf("JNC001", "JNC002", "JNC003"), list.map { it.junctionId })
        assertNull(list.first { it.junctionId == "JNC002" }.name)
    }

    // ------------------------------------------------------------------
    // Fixtures below are verbatim payloads read back from the live
    // `smart-ambulance-36f9d` database, so the mapping stays pinned to the
    // shapes the firmware and dashboard actually write.
    // ------------------------------------------------------------------

    @Test
    fun `live police alert payload maps its destination and message`() {
        val alert = Mapping.alert(
            "TRIP001",
            mapOf(
                "ambulanceId" to "AMB001",
                "destinationHospitalId" to "HOSP001",
                "distanceMeters" to 420L,
                "message" to "Ambulance AMB001 approaching at 42 km/h",
                "preemptionMode" to "gps",
                "severity" to "Serious",
                "status" to "active",
                "tripId" to "TRIP001",
                "updatedAt" to 1_757_000_000_000L
            )
        )

        assertEquals("HOSP001", alert.destinationHospitalId)
        assertEquals("ACTIVE", alert.status?.uppercase())
        assertEquals(420.0, alert.distanceMeters!!, 0.001)
        assertEquals(1_757_000_000_000L, alert.timestamp)
    }

    @Test
    fun `live hospital alert payload carries eta and trip status`() {
        val alert = Mapping.alert(
            "TRIP001",
            mapOf(
                "ambulanceId" to "AMB001",
                "eta" to "6 min",
                "message" to "Incoming",
                "severity" to "Serious",
                "status" to "completed",
                "tripId" to "TRIP001",
                "updatedAt" to 1_757_000_100_000L
            )
        )

        assertEquals("6 min", alert.eta)
        assertEquals("completed", alert.status)
        assertNull(alert.destinationHospitalId)
    }

    @Test
    fun `live hospital record keeps the stored distance and eta`() {
        val hospital = Mapping.hospital(
            "HOSP001",
            mapOf(
                "bedsAvailable" to 8L,
                "distance" to "2.4 km",
                "emergencyAvailable" to true,
                "eta" to "6 min",
                "hospitalId" to "HOSP001",
                "name" to "City General Hospital"
            )
        )

        assertEquals("2.4 km", hospital.distance)
        assertEquals("6 min", hospital.eta)
        assertEquals(8, hospital.bedsAvailable)
        // This payload stores no coordinates, so no straight-line estimate is possible.
        assertNull(hospital.lat)
        assertNull(hospital.lng)
    }

    @Test
    fun `live preemption event is classified as opening a corridor`() {
        val event = Mapping.junctionEvent(
            "EVT_PREEMPT",
            mapOf(
                "ambulanceId" to "AMB001",
                "distanceMeters" to 500L,
                "eventType" to "gps_preempt_started",
                "junctionId" to "JNC001",
                "junctionName" to "Main Road Junction",
                "lane" to "north",
                "preemptionMode" to "gps",
                "rssi" to -72L,
                "timestamp" to 1_757_000_200_000L
            )
        )

        assertTrue(event.openedCorridor)
        assertFalse(event.clearedAtStopLine)
        assertEquals("gps", event.preemptionMode)
        assertEquals("north", event.lane)
        assertEquals(-72, event.rssi)
    }

    @Test
    fun `live rfid clearance is a clear event, not a preemption`() {
        val event = Mapping.junctionEvent(
            "EVT_CLEAR",
            mapOf(
                "ambulanceId" to "AMB001",
                "dwellTime" to 4_200L,
                "eventType" to "rfid_clearance",
                "junctionId" to "JNC001",
                "rfidTagId" to "RFID_TAG_001",
                "timestamp" to 1_757_000_300_000L
            )
        )

        assertTrue(event.clearedAtStopLine)
        assertFalse(event.openedCorridor)
        assertEquals("RFID_TAG_001", event.rfidTagId)
        assertEquals(4_200L, event.dwellTimeMs)
        // No preemption mode was ever written on this event.
        assertNull(event.preemptionMode)
    }

    @Test
    fun `plain presence events are neither opens nor clears`() {
        val entry = Mapping.junctionEvent("EVT_ENTRY", mapOf("eventType" to "entry"))
        val exit = Mapping.junctionEvent("EVT_EXIT", mapOf("eventType" to "exit"))

        assertFalse(entry.openedCorridor)
        assertFalse(entry.clearedAtStopLine)
        assertFalse(exit.openedCorridor)
        assertFalse(exit.restored)
    }

    @Test
    fun `timeout_restore is recognised as a hand-back`() {
        val event = Mapping.junctionEvent("EVT_RESTORE", mapOf("eventType" to "timeout_restore"))

        assertTrue(event.restored)
        assertFalse(event.openedCorridor)
    }
}
