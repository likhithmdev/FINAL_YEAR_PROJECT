package com.smartambulance.driver.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Realtime Database node names.
 *
 * These strings are a contract shared with the ESP32 firmware, the Android app
 * and the web dashboard. The dashboard keeps its own copy of this list, so a
 * silent rename on either side would stop live data arriving without breaking
 * any build — this test is what makes such a rename fail loudly.
 */
class FirebasePathsTest {

    @Test
    fun `node names match the documented schema`() {
        assertEquals("users", FirebasePaths.USERS)
        assertEquals("ambulances", FirebasePaths.AMBULANCES)
        assertEquals("drivers", FirebasePaths.DRIVERS)
        assertEquals("emergencyTrips", FirebasePaths.EMERGENCY_TRIPS)
        assertEquals("junctions", FirebasePaths.JUNCTIONS)
        assertEquals("junctionEvents", FirebasePaths.JUNCTION_EVENTS)
        assertEquals("loraTelemetry", FirebasePaths.LORA_TELEMETRY)
        assertEquals("hospitals", FirebasePaths.HOSPITALS)
        assertEquals("hospitalAlerts", FirebasePaths.HOSPITAL_ALERTS)
        assertEquals("policeAlerts", FirebasePaths.POLICE_ALERTS)
        assertEquals("rfidTags", FirebasePaths.RFID_TAGS)
    }

    @Test
    fun `every node name is distinct`() {
        // A copy-paste slip would silently merge two nodes into one and the
        // collision would only show up as missing data in production.
        val nodes = listOf(
            FirebasePaths.USERS,
            FirebasePaths.AMBULANCES,
            FirebasePaths.DRIVERS,
            FirebasePaths.EMERGENCY_TRIPS,
            FirebasePaths.JUNCTIONS,
            FirebasePaths.JUNCTION_EVENTS,
            FirebasePaths.LORA_TELEMETRY,
            FirebasePaths.HOSPITALS,
            FirebasePaths.HOSPITAL_ALERTS,
            FirebasePaths.POLICE_ALERTS,
            FirebasePaths.RFID_TAGS,
        )
        assertEquals("duplicate Firebase node name", nodes.size, nodes.toSet().size)
    }

    @Test
    fun `node names are usable as database keys`() {
        val nodes = listOf(
            FirebasePaths.USERS,
            FirebasePaths.AMBULANCES,
            FirebasePaths.DRIVERS,
            FirebasePaths.EMERGENCY_TRIPS,
            FirebasePaths.JUNCTIONS,
            FirebasePaths.JUNCTION_EVENTS,
            FirebasePaths.LORA_TELEMETRY,
            FirebasePaths.HOSPITALS,
            FirebasePaths.HOSPITAL_ALERTS,
            FirebasePaths.POLICE_ALERTS,
            FirebasePaths.RFID_TAGS,
        )
        nodes.forEach { node ->
            assertTrue("'$node' must not be blank", node.isNotBlank())
            assertTrue("'$node' must not contain a slash", !node.contains("/"))
            assertTrue("'$node' must not contain whitespace", !node.contains(" "))
        }
    }

    @Test
    fun `the alert streams the operator relies on are present`() {
        // hospitalAlerts and policeAlerts were originally absent from the web
        // client entirely, so they are the two most likely to be dropped again.
        assertTrue(FirebasePaths.HOSPITAL_ALERTS.isNotEmpty())
        assertTrue(FirebasePaths.POLICE_ALERTS.isNotEmpty())
        assertEquals("hospitalAlerts", FirebasePaths.HOSPITAL_ALERTS)
        assertEquals("policeAlerts", FirebasePaths.POLICE_ALERTS)
    }
}
