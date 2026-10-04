package com.smartambulance.driver.mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the MQTT topic contract shared with the ESP32 firmware and the web
 * dashboard. The dashboard previously routed messages by their `/events`
 * suffix alone, which made junction events and trip events collide; these
 * tests keep the two namespaces distinguishable.
 */
class MqttTopicsTest {

    private val prefix = "smart-ambulance"

    @Test
    fun `topics match the documented contract`() {
        assertEquals("smart-ambulance/ambulances/AMB001/lora-gps", MqttTopics.ambulanceLoRaGps("AMB001"))
        assertEquals("smart-ambulance/ambulances/AMB001/status", MqttTopics.ambulanceStatus("AMB001"))
        assertEquals("smart-ambulance/junctions/JNC001/approach", MqttTopics.junctionApproach("JNC001"))
        assertEquals("smart-ambulance/junctions/JNC001/events", MqttTopics.junctionEvents("JNC001"))
        assertEquals("smart-ambulance/junctions/JNC001/signal", MqttTopics.junctionSignal("JNC001"))
        assertEquals("smart-ambulance/trips/TRIP001/events", MqttTopics.tripEvents("TRIP001"))
    }

    @Test
    fun `every topic is namespaced under the shared prefix`() {
        val topics = listOf(
            MqttTopics.ambulanceLoRaGps("AMB001"),
            MqttTopics.ambulanceStatus("AMB001"),
            MqttTopics.junctionApproach("JNC001"),
            MqttTopics.junctionEvents("JNC001"),
            MqttTopics.junctionSignal("JNC001"),
            MqttTopics.tripEvents("TRIP001"),
        )
        topics.forEach { topic ->
            assertTrue("'$topic' must start with the prefix", topic.startsWith("$prefix/"))
        }
    }

    @Test
    fun `every topic has the prefix and three further segments`() {
        val topics = listOf(
            MqttTopics.ambulanceLoRaGps("AMB001"),
            MqttTopics.ambulanceStatus("AMB001"),
            MqttTopics.junctionApproach("JNC001"),
            MqttTopics.junctionEvents("JNC001"),
            MqttTopics.junctionSignal("JNC001"),
            MqttTopics.tripEvents("TRIP001"),
        )
        topics.forEach { topic ->
            assertEquals("'$topic' must have 4 segments", 4, topic.split("/").size)
        }
    }

    @Test
    fun `junction events and trip events live in different namespaces`() {
        // Even with identical ids these must not be the same topic, otherwise a
        // suffix-based subscriber cannot tell them apart.
        val junction = MqttTopics.junctionEvents("001")
        val trip = MqttTopics.tripEvents("001")
        assertNotEquals(junction, trip)
        assertTrue(junction.startsWith("$prefix/junctions/"))
        assertTrue(trip.startsWith("$prefix/trips/"))
        assertTrue(junction.endsWith("/events"))
        assertTrue(trip.endsWith("/events"))
    }

    @Test
    fun `ids are interpolated rather than hardcoded`() {
        assertNotEquals(MqttTopics.junctionEvents("JNC001"), MqttTopics.junctionEvents("JNC002"))
        assertNotEquals(MqttTopics.junctionSignal("JNC001"), MqttTopics.junctionSignal("JNC002"))
        assertNotEquals(MqttTopics.ambulanceLoRaGps("AMB001"), MqttTopics.ambulanceLoRaGps("AMB002"))
        assertNotEquals(MqttTopics.ambulanceStatus("AMB001"), MqttTopics.ambulanceLoRaGps("AMB001"))
        assertNotEquals(MqttTopics.tripEvents("TRIP001"), MqttTopics.tripEvents("TRIP002"))
    }

    @Test
    fun `the three per-junction topics are distinct`() {
        val junctionId = "JNC001"
        val topics = setOf(
            MqttTopics.junctionEvents(junctionId),
            MqttTopics.junctionSignal(junctionId),
            MqttTopics.junctionApproach(junctionId),
        )
        assertEquals(3, topics.size)
    }
}
