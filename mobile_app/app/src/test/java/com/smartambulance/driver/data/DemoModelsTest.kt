package com.smartambulance.driver.data

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the defaults on the shared data models. These values are read directly
 * by the dashboards (search radius shown to the driver, the emergency filter
 * defaults) so changing one silently changes what the user sees.
 */
class DemoModelsTest {

    @Test
    fun `a driver account carries an ambulance id and nothing else`() {
        val driver = AppUser(
            userId = "driver_001",
            name = "Driver One",
            pin = "1111",
            role = "ambulance_driver",
            ambulanceId = "AMB001"
        )
        assertEquals("AMB001", driver.ambulanceId)
        assertNull(driver.assignedJunctionId)
        assertNull(driver.hospitalId)
    }

    @Test
    fun `a police account carries a junction id`() {
        val police = AppUser(
            userId = "police_001",
            name = "Traffic Police",
            pin = "2222",
            role = "police",
            assignedJunctionId = "JNC001"
        )
        assertEquals("JNC001", police.assignedJunctionId)
        assertNull(police.ambulanceId)
        assertNull(police.hospitalId)
    }

    @Test
    fun `a hospital account carries a hospital id`() {
        val hospital = AppUser(
            userId = "hospital_001",
            name = "City Care Desk",
            pin = "3333",
            role = "hospital",
            hospitalId = "HOSP001"
        )
        assertEquals("HOSP001", hospital.hospitalId)
        assertNull(hospital.ambulanceId)
        assertNull(hospital.assignedJunctionId)
    }

    @Test
    fun `an admin account leaves every role field unset`() {
        val admin = AppUser(userId = "admin_001", name = "System Admin", pin = "0000", role = "admin")
        assertNull(admin.ambulanceId)
        assertNull(admin.assignedJunctionId)
        assertNull(admin.hospitalId)
    }

    @Test
    fun `hospital search defaults to a ten kilometre radius`() {
        val result = HospitalSearchResult(
            hospitals = emptyList(),
            searchLocation = LatLng(12.9716, 77.5946)
        )
        assertEquals(10000, result.searchRadius)
        assertTrue("timestamp should default to now", result.timestamp > 0L)
    }

    @Test
    fun `hospital filter defaults to the widest, unrestricted search`() {
        val filter = HospitalFilter()
        assertFalse(filter.emergencyOnly)
        assertEquals(10000, filter.maxDistance)
        assertEquals(0f, filter.minRating, 0.0f)
        assertFalse(filter.isOpenNow)
        assertTrue(filter.specialties.isEmpty())
    }

    @Test
    fun `hospital emergencyServices is opt-in`() {
        val plain = Hospital(
            placeId = "PLACE1",
            name = "Some Clinic",
            address = "Somewhere",
            location = LatLng(12.9, 77.5),
            phone = "",
            rating = 4.0f,
            distance = 1200.0,
            duration = "3 min",
            isOpen = true,
            types = emptyList()
        )
        assertFalse(plain.emergencyServices)
        assertTrue(plain.copy(emergencyServices = true).emergencyServices)
    }

    @Test
    fun `toFirebaseId keys hospitals by their place id`() {
        val hospital = Hospital(
            placeId = "ChIJplaceId",
            name = "City Care Hospital",
            address = "Somewhere",
            location = LatLng(12.9698, 77.6015),
            phone = "+91 80 4000 1000",
            rating = 4.5f,
            distance = 900.0,
            duration = "2 min",
            isOpen = true,
            types = listOf("hospital")
        )
        assertEquals("ChIJplaceId", hospital.toFirebaseId())
        assertEquals(hospital.placeId, hospital.toFirebaseId())
    }

    @Test
    fun `a favourite starts unused`() {
        val favourite = HospitalFavorite(
            hospitalId = "HOSP001",
            hospitalName = "City Care Hospital",
            address = "Somewhere"
        )
        assertEquals(0, favourite.useCount)
        assertTrue(favourite.addedAt > 0L)
        assertTrue(favourite.lastUsed > 0L)
    }

    @Test
    fun `a hospital route without alternatives reports an empty list`() {
        val route = HospitalRoute(
            hospital = Hospital(
                placeId = "PLACE1",
                name = "City Care Hospital",
                address = "Somewhere",
                location = LatLng(12.9698, 77.6015),
                phone = "",
                rating = 4.0f,
                distance = 900.0,
                duration = "2 min",
                isOpen = true,
                types = emptyList()
            ),
            distance = 900.0,
            duration = "2 min",
            durationSeconds = 120,
            trafficCondition = "light"
        )
        assertEquals(0, route.alternativeRoutes.size)
        assertEquals("light", route.trafficCondition)
    }

    @Test
    fun `HospitalDetails reports unknown emergency capacity as null`() {
        val details = HospitalDetails(
            hospital = Hospital(
                placeId = "PLACE1",
                name = "City Care Hospital",
                address = "Somewhere",
                location = LatLng(12.9698, 77.6015),
                phone = "",
                rating = 4.0f,
                distance = 900.0,
                duration = "2 min",
                isOpen = true,
                types = emptyList()
            ),
            openingHours = emptyList(),
            reviews = emptyList(),
            photos = emptyList(),
            website = "",
            emergencyPhoneNumber = null,
            emergencyRoomCapacity = null,
            currentWaitTime = null
        )
        assertNull(details.emergencyPhoneNumber)
        assertNull(details.emergencyRoomCapacity)
        assertNull(details.currentWaitTime)
        assertFalse(details.hasEmergencyRoom)
        assertFalse(details.hasTraumaCenter)
        assertFalse(details.hasICU)
        assertTrue(details.specialties.isEmpty())
    }
}
