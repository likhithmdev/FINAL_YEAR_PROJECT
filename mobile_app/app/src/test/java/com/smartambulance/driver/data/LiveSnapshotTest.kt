package com.smartambulance.driver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the whole read path — every mapping function the app uses — over a verbatim
 * snapshot of the live database, rather than over input invented for the test.
 *
 * This is the closest thing to an end-to-end check that runs without a device or a
 * signed-in account: it proves the app can actually read the shapes the firmware and
 * dashboard wrote. Every assertion below is a fact observed in `LiveSnapshot`, so if a
 * writer changes shape in a way the app cannot follow, this fails instead of the app
 * quietly rendering blanks on a real ambulance.
 */
class LiveSnapshotTest {

    private val nodes = LiveSnapshot.nodes

    @Suppress("UNCHECKED_CAST")
    private fun node(name: String): Map<String, Any?> = nodes[name] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.child(name: String): Map<String, Any?> = this[name] as Map<String, Any?>

    // ------------------------------------------------------------------
    // The snapshot itself must not carry live credentials.
    // ------------------------------------------------------------------

    @Test
    fun `the fixture contains no real credentials`() {
        val leaked = buildList {
            fun walk(value: Any?, path: String) {
                when (value) {
                    is Map<*, *> -> value.forEach { (k, v) ->
                        if ((k == "pin" || k == "password") && v != "[scrubbed]") add("$path.$k")
                        walk(v, "$path.$k")
                    }
                    is List<*> -> value.forEachIndexed { i, v -> walk(v, "$path[$i]") }
                }
            }
            walk(nodes, "")
        }

        assertTrue("credential fields survived into the committed fixture: $leaked", leaked.isEmpty())
    }

    // ------------------------------------------------------------------
    // Registry nodes
    // ------------------------------------------------------------------

    @Test
    fun `live ambulances map with their registry fields`() {
        val ambulances = Mapping.ambulances(node("ambulances"))

        assertEquals(listOf("AMB001", "AMB002", "AMB003"), ambulances.map { it.ambulanceId })

        val amb001 = ambulances.first { it.ambulanceId == "AMB001" }
        assertEquals("driver_001", amb001.driverId)
        assertEquals("RFID_TAG_001", amb001.rfidTagId)
        assertEquals("available", amb001.status)
        assertFalse("AMB001 is not on an emergency right now", amb001.emergencyActive)
    }

    @Test
    fun `live junctions map and an unreported field stays null`() {
        val junctions = Mapping.junctions(node("junctions"))

        assertEquals(listOf("JNC001", "JNC002", "JNC003"), junctions.map { it.junctionId })
        assertEquals("Main Road Junction", junctions.first().name)
        assertEquals("normal", junctions.first().signalState)
        assertEquals("northbound", junctions.first().activeLane)

        // JNC002/JNC003 have never reported a signal state. The mapper must not invent one.
        assertNull(junctions.first { it.junctionId == "JNC002" }.signalState)
        assertNull(junctions.first { it.junctionId == "JNC002" }.activeLane)
    }

    @Test
    fun `live hospitals keep the distance and eta the registry stored`() {
        val hospitals = Mapping.hospitals(node("hospitals"))

        assertEquals(listOf("HOSP001", "HOSP002", "HOSP003"), hospitals.map { it.hospitalId })

        // Regression: these stored values used to be discarded, so the driver's
        // destination picker showed "distance unknown" beside a hospital that has one.
        hospitals.forEach { hospital ->
            assertNotNull("${hospital.hospitalId} lost its stored distance", hospital.distance)
            assertNotNull("${hospital.hospitalId} lost its stored ETA", hospital.eta)
            assertNotNull("${hospital.hospitalId} lost its bed count", hospital.bedsAvailable)
        }
        assertEquals("2.4 km", hospitals.first().distance)
        assertEquals("6 min", hospitals.first().eta)
    }

    @Test
    fun `live tags and trips map`() {
        val tags = Mapping.rfidTags(node("rfidTags"))
        assertEquals(listOf("RFID_TAG_001"), tags.map { it.rfidTagId })
        assertTrue(tags.first().authorized)
        assertEquals("AMB001", tags.first().ambulanceId)

        val trips = Mapping.trips(node("emergencyTrips"))
        assertEquals(listOf("TRIP001"), trips.map { it.tripId })
        val trip = trips.first()
        assertEquals("AMB001", trip.ambulanceId)
        assertEquals("HOSP001", trip.destinationHospitalId)
        assertEquals("Serious", trip.severity)
        assertEquals("active", trip.status)
    }

    // ------------------------------------------------------------------
    // The roadside log — the node with the most shape variety
    // ------------------------------------------------------------------

    private fun liveEvents(): List<JunctionEvent> = Mapping.junctionEvents(node("junctionEvents"))

    @Test
    fun `every live junction event exposes the fields the console reads`() {
        val events = liveEvents()

        assertEquals(39, events.size)
        events.forEach { event ->
            assertNotNull("event ${event.eventId} lost its timestamp", event.timestamp)
            assertNotNull("event ${event.eventId} lost its lane", event.lane)
            assertNotNull("event ${event.eventId} lost its junction name", event.junctionName)
            assertNotNull("event ${event.eventId} lost its tag id", event.rfidTagId)
        }
    }

    @Test
    fun `the corridor log classifies all seven event types the controller writes`() {
        val events = liveEvents()

        fun type(t: String) = events.filter { it.eventType == t }

        assertEquals(19, type("entry").size)
        assertEquals(8, type("exit").size)
        assertEquals(4, type("manual_reset").size)
        assertEquals(2, type("timeout_restore").size)
        assertEquals(2, type("gps_preempt_started").size)
        assertEquals(2, type("rssi_preempt_started").size)
        assertEquals(2, type("rfid_clearance").size)

        assertTrue(type("gps_preempt_started").all { it.openedCorridor })
        assertTrue(type("rssi_preempt_started").all { it.openedCorridor })
        assertTrue(type("rfid_clearance").all { it.clearedAtStopLine })
        assertTrue(type("timeout_restore").all { it.restored })
        assertTrue(type("manual_reset").all { it.restored })

        // Presence detection must never be promoted into a preemption claim.
        assertTrue((type("entry") + type("exit")).none { it.openedCorridor || it.clearedAtStopLine })
    }

    @Test
    fun `the preemption list the police desk shows is neither empty nor polluted`() {
        val events = liveEvents()

        // The same filter PoliceDashboard's corridor log applies.
        val shown = events.filter {
            it.openedCorridor || it.clearedAtStopLine || it.restored || it.preemptionMode != null
        }

        // 4 corridor opens + 2 stop-line clears + 6 hand-backs (4 manual_reset, 2 timeout_restore).
        assertEquals(12, shown.size)
        assertTrue("presence detections leaked into the corridor log", shown.none { it.eventType == "entry" })
        assertTrue("presence detections leaked into the corridor log", shown.none { it.eventType == "exit" })

        // Newest first, so the desk always reads the latest corridor action at the top.
        assertEquals(shown.map { it.timestamp!! }.sortedDescending(), shown.map { it.timestamp!! })
    }

    @Test
    fun `dwell times arrive as durations, not milliseconds`() {
        val events = liveEvents()

        // The controller writes "7s"/"13s" strings. Reading them as numbers yielded null.
        val exitDwells = events.filter { it.eventType == "exit" }.mapNotNull { it.dwellTimeMs }.sorted()
        assertEquals(listOf(7_000L, 7_000L, 8_000L, 12_000L, 12_000L, 14_000L), exitDwells)

        val clearDwells = events.filter { it.eventType == "rfid_clearance" }.mapNotNull { it.dwellTimeMs }.sorted()
        assertEquals(listOf(6_000L, 13_000L), clearDwells)
    }

    @Test
    fun `rssi and distance survive on the events that carry them`() {
        val opens = liveEvents().filter { it.eventType == "gps_preempt_started" }
        assertEquals(2, opens.size)

        // Matched by distance rather than position: the list is newest-first, which is
        // not the order the database returns keys in.
        val near = opens.first { it.distanceMeters == 378.0 }
        val far = opens.first { it.distanceMeters == 454.0 }
        assertEquals(-75, near.rssi)
        assertEquals(-79, far.rssi)
        assertEquals("gps_lora", near.preemptionMode)
        assertEquals("JNC001", near.junctionId)
    }

    // ------------------------------------------------------------------
    // Alerts, which are scoped one level deeper than the root node
    // ------------------------------------------------------------------

    @Test
    fun `a police alert is read from its junction scope`() {
        // policeAlerts/$junctionId/$tripId — the repository observes the junction, not the root.
        val alerts = Mapping.alerts(node("policeAlerts").child("JNC001"))

        assertEquals(listOf("TRIP001"), alerts.map { it.key })
        val alert = alerts.first()
        assertEquals("AMB001", alert.ambulanceId)
        assertEquals("HOSP001", alert.destinationHospitalId)
        assertEquals("Serious", alert.severity)
        assertEquals("ambulance_approaching", alert.status)
        assertEquals(420.0, alert.distanceMeters!!, 0.001)
        assertEquals("gps_lora", alert.preemptionMode)
        assertNotNull(alert.message)
        assertNotNull(alert.timestamp)
    }

    @Test
    fun `a hospital alert is read from its hospital scope`() {
        val alerts = Mapping.alerts(node("hospitalAlerts").child("HOSP001"))

        assertEquals(listOf("TRIP001"), alerts.map { it.key })
        val alert = alerts.first()
        assertEquals("AMB001", alert.ambulanceId)
        assertEquals("6 min", alert.eta)
        assertEquals("completed", alert.status)
        assertEquals("Serious", alert.severity)
    }

    @Test
    fun `scoped alerts work for numeric-looking place ids too`() {
        // A discovered Google place id is stored as an all-digit key.
        val alerts = Mapping.alerts(node("hospitalAlerts").child("1209132340"))

        assertEquals(listOf("TRIP001"), alerts.map { it.key })
        assertEquals("incoming", alerts.first().status)
        assertEquals("Calculating...", alerts.first().eta)
    }

    // ------------------------------------------------------------------
    // Telemetry — the node the receiver ESP32 owns
    // ------------------------------------------------------------------

    @Test
    fun `live receiver telemetry maps for the ambulances that have reported`() {
        val telemetry = node("loraTelemetry").child("JNC001")
        assertEquals(setOf("AMB001", "AMB002"), telemetry.keys.toSet())

        val amb001 = Mapping.loRaTelemetry(telemetry.child("AMB001"), null)
        assertTrue(amb001.hasFix)
        assertEquals(12.9716, amb001.lat!!, 1e-9)
        assertEquals(77.5946, amb001.lng!!, 1e-9)
        assertEquals(0.0, amb001.speedKmph!!, 1e-9)
        assertEquals(-72, amb001.rssi)
        assertFalse("AMB001 is not approaching", amb001.approaching!!)
        assertFalse(amb001.preemptionEligible!!)
        assertEquals(500.0, amb001.distanceMeters!!, 0.001)

        val amb002 = Mapping.loRaTelemetry(telemetry.child("AMB002"), null)
        assertEquals(42.0, amb002.speedKmph!!, 0.001)
        assertTrue(amb002.preemptionEligible!!)
    }

    @Test
    fun `telemetry falls back to the ambulance record when the receiver is silent`() {
        // AMB001 publishes lastLoRaTelemetry on its own record too. That record carries
        // range and signal but genuinely no coordinates, so it must not claim a fix —
        // the console shows "no fix" rather than plotting a stale position.
        val fallback = node("ambulances").child("AMB001").child("lastLoRaTelemetry")
        val telemetry = Mapping.loRaTelemetry(null, fallback)

        assertFalse("the ambulance record carries no lat/lng, so it cannot claim a fix", telemetry.hasFix)
        assertNull(telemetry.lat)
        assertEquals(-72, telemetry.rssi)
        assertEquals(500.0, telemetry.distanceMeters!!, 0.001)
        assertFalse(telemetry.approaching!!)
        assertFalse(telemetry.preemptionEligible!!)
    }

    @Test
    fun `receiver telemetry carries the position the ambulance record lacks`() {
        val receiver = Mapping.loRaTelemetry(node("loraTelemetry").child("JNC001").child("AMB001"), null)
        val ownRecord = Mapping.loRaTelemetry(null, node("ambulances").child("AMB001").child("lastLoRaTelemetry"))

        assertTrue(receiver.hasFix)
        assertEquals(12.9716, receiver.lat!!, 1e-9)
        assertFalse(ownRecord.hasFix)
        // Both agree on range, which is the reassurance that they describe one vehicle.
        assertEquals(receiver.distanceMeters, ownRecord.distanceMeters)
    }

    @Test
    fun `a silent receiver yields the empty telemetry rather than a fabricated fix`() {
        assertEquals(LoRaTelemetry.EMPTY, Mapping.loRaTelemetry(null, null))
        assertFalse(Mapping.loRaTelemetry(null, null).hasFix)
    }
}
