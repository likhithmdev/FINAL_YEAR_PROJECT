package com.smartambulance.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.ui.components.design.InfoBanner
import com.smartambulance.driver.ui.theme.SecondaryAmber
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.smartambulance.driver.data.AdminSummary
import com.smartambulance.driver.data.AmbulanceState
import com.smartambulance.driver.data.AlertRecord
import com.smartambulance.driver.data.AppUser
import com.smartambulance.driver.data.Geo
import com.smartambulance.driver.data.Hospital
import com.smartambulance.driver.data.HospitalOption
import com.smartambulance.driver.data.HospitalRecord
import com.smartambulance.driver.data.JunctionEvent
import com.smartambulance.driver.data.JunctionState
import com.smartambulance.driver.data.LoRaTelemetry
import com.smartambulance.driver.data.Registration
import com.smartambulance.driver.data.RfidTag
import com.smartambulance.driver.data.SaptcsRepository
import com.smartambulance.driver.data.TripRecord
import com.smartambulance.driver.mqtt.MqttManager
import com.smartambulance.driver.mqtt.MqttTopics
import com.smartambulance.driver.services.HospitalDiscoveryService
import com.smartambulance.driver.services.NavigationService
import com.smartambulance.driver.ui.screens.AdminActions
import com.smartambulance.driver.ui.screens.AdminDashboard
import com.smartambulance.driver.ui.screens.DriverDashboard
import com.smartambulance.driver.ui.screens.HospitalDashboard
import com.smartambulance.driver.ui.screens.HospitalSearchScreen
import com.smartambulance.driver.ui.screens.LoginScreen
import com.smartambulance.driver.ui.screens.PoliceDashboard
import com.smartambulance.driver.ui.theme.SmartAmbulanceTheme

/**
 * Single-activity host.
 *
 * This class owns the data plumbing rather than the screens: it subscribes to
 * the nodes each role needs, cancels those subscriptions on sign-out, and hands
 * screens typed models. Screens never talk to Firebase and never fill a gap in
 * the data themselves.
 */
class MainActivity : ComponentActivity() {

    private var repository = SaptcsRepository()
    private val subscriptions = mutableListOf<Registration>()

    private val locationHandler = Handler(Looper.getMainLooper())
    private lateinit var mqttManager: MqttManager
    private lateinit var hospitalDiscoveryService: HospitalDiscoveryService
    private lateinit var navigationService: NavigationService

    private var showHospitalSearch by mutableStateOf(false)

    // -- session ----------------------------------------------------------
    private var userId by mutableStateOf("")
    private var pin by mutableStateOf("")
    private var offlineRequested by mutableStateOf(false)
    private var message by mutableStateOf("Sign in with your operator credentials.")
    private var loading by mutableStateOf(false)
    private var user by mutableStateOf<AppUser?>(null)

    /**
     * Problems that surface *after* sign-in — a missing assignment, a failed
     * write. They cannot use [message] because that is only rendered by the
     * sign-in screen, which is no longer on screen once a role dashboard opens.
     */
    private var notice by mutableStateOf<String?>(null)

    // -- live data --------------------------------------------------------
    private var ambulances by mutableStateOf<List<AmbulanceState>>(emptyList())
    private var junctions by mutableStateOf<List<JunctionState>>(emptyList())
    private var hospitals by mutableStateOf<List<HospitalRecord>>(emptyList())
    private var rfidTags by mutableStateOf<List<RfidTag>>(emptyList())
    private var junctionEvents by mutableStateOf<List<JunctionEvent>>(emptyList())
    private var trips by mutableStateOf<List<TripRecord>>(emptyList())
    private var policeAlerts by mutableStateOf<List<AlertRecord>>(emptyList())
    private var hospitalAlerts by mutableStateOf<List<AlertRecord>>(emptyList())
    private var ownAmbulance by mutableStateOf<AmbulanceState?>(null)
    private var lora by mutableStateOf(LoRaTelemetry.EMPTY)

    // -- driver mission ---------------------------------------------------
    private var selectedSeverity by mutableStateOf("Serious")
    private var selectedHospital by mutableStateOf<HospitalOption?>(null)
    private var activeTripId by mutableStateOf<String?>(null)
    private var gpsNote by mutableStateOf("Location publishing starts with the emergency.")
    private var lastKnownLocation by mutableStateOf<Pair<Double?, Double?>?>(null)

    // -- hospital desk ----------------------------------------------------
    private var readiness by mutableStateOf("No readiness updates recorded yet.")

    // -- admin ------------------------------------------------------------
    private var adminMessage by mutableStateOf("Ready to register project data.")
    private var adminSummary by mutableStateOf<AdminSummary?>(null)

    /** Re-bound whenever the tracked ambulance for this junction changes. */
    private var policeTelemetrySub: Registration? = null

    private val locationPublisher = object : Runnable {
        override fun run() {
            publishCurrentLocation()
            if (ownAmbulance?.emergencyActive == true) locationHandler.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        requestLocationPermission()

        mqttManager = MqttManager(this)
        mqttManager.setOnConnectionStatusChanged { connected ->
            runOnUiThread {
                if (connected) {
                    val ambulanceId = user?.ambulanceId
                    if (ambulanceId != null) mqttManager.subscribeToAmbulanceTopics(ambulanceId)
                    user?.assignedJunctionId?.let { mqttManager.subscribeToJunctionTopics(it) }
                }
            }
        }
        mqttManager.connect()

        hospitalDiscoveryService = HospitalDiscoveryService(this)
        navigationService = NavigationService(this)

        setContent {
            SmartAmbulanceTheme {
                val current = user
                when (current?.role) {
                    "ambulance_driver" -> DriverDashboard(
                        user = current,
                        hospitals = destinationOptions(),
                        junctions = junctions,
                        emergencyActive = ownAmbulance?.emergencyActive == true,
                        selectedSeverity = selectedSeverity,
                        selectedHospital = selectedHospital,
                        status = driverStatus(),
                        ambulance = ownAmbulance,
                        lora = lora,
                        demoMode = repository.isOffline,
                        onSeverityChange = { selectedSeverity = it },
                        onHospitalChange = { selectedHospital = it },
                        onStartEmergency = { startEmergency(current) },
                        onEndEmergency = { completeEmergency(current) },
                        onSearchHospital = { showHospitalSearch = true },
                        onOpenNavigation = { hospital -> openDirections(hospital) },
                        onLogout = { logout() }
                    )

                    "police" -> PoliceDashboard(
                        user = current,
                        junctionId = current.assignedJunctionId ?: "—",
                        alerts = policeAlerts,
                        ambulances = ambulances,
                        junctions = junctions,
                        rfidTags = rfidTags,
                        junctionEvents = junctionEvents,
                        lora = lora,
                        demoMode = repository.isOffline,
                        onRefresh = { bindPolice(current) },
                        onLogout = { logout() }
                    )

                    "hospital" -> HospitalDashboard(
                        user = current,
                        hospitalId = current.hospitalId ?: "—",
                        alerts = hospitalAlerts,
                        trips = trips.filter { it.destinationHospitalId == current.hospitalId },
                        ambulances = ambulances,
                        readiness = readiness,
                        demoMode = repository.isOffline,
                        onReady = { key -> markReady(current, key) },
                        onLogout = { logout() }
                    )

                    "admin" -> AdminDashboard(
                        user = current,
                        message = adminMessage,
                        summary = adminSummary,
                        actions = adminActions(),
                        demoMode = repository.isOffline,
                        onLogout = { logout() }
                    )

                    else -> LoginScreen(
                        userId = userId,
                        pin = pin,
                        message = message,
                        loading = loading,
                        offline = offlineRequested,
                        onOfflineChange = { offlineRequested = it },
                        onUserId = { userId = it },
                        onPin = { pin = it },
                        onFill = { id, secret -> userId = id; if (offlineRequested) pin = secret },
                        onLogin = { login() }
                    )
                }

                // Anything that went wrong after sign-in is surfaced here, on top of
                // whichever dashboard opened, and clears on tap.
                notice?.let { text ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = 96.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            Modifier
                                .padding(horizontal = 16.dp)
                                .clickable { notice = null }
                        ) {
                            InfoBanner(
                                text = "$text\n\nTap to dismiss.",
                                accent = SecondaryAmber,
                                emphasized = true,
                                icon = Icons.Filled.WarningAmber
                            )
                        }
                    }
                }

                if (showHospitalSearch) {
                    HospitalSearchScreen(
                        hospitalDiscoveryService = hospitalDiscoveryService,
                        onHospitalSelected = { hospital ->
                            selectedHospital = hospital.toDestinationOption()
                            showHospitalSearch = false
                        },
                        onBack = { showHospitalSearch = false }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearSubscriptions()
        locationHandler.removeCallbacks(locationPublisher)
        mqttManager.disconnect()
    }

    // -----------------------------------------------------------------------
    // Session
    // -----------------------------------------------------------------------

    private fun login() {
        loading = true
        message = "Authenticating…"

        // Swap the data source if the operator changed the demo switch.
        if (repository.isOffline != offlineRequested) {
            clearSubscriptions()
            repository.signOut()
            repository = SaptcsRepository(offline = offlineRequested)
            user = null
        }

        repository.signIn(userId, pin) { found, error ->
            runOnUiThread {
                loading = false
                if (found == null) {
                    message = error ?: "Sign-in failed"
                    return@runOnUiThread
                }
                user = found
                pin = ""
                message = if (repository.isOffline) {
                    "Offline demo mode · showing canned data"
                } else {
                    "Signed in as ${found.name}"
                }
                bindForRole(found)
            }
        }
    }

    private fun logout() {
        locationHandler.removeCallbacks(locationPublisher)
        clearSubscriptions()
        policeTelemetrySub = null
        repository.signOut()
        mqttManager.disconnect()

        user = null
        ownAmbulance = null
        lora = LoRaTelemetry.EMPTY
        ambulances = emptyList()
        junctions = emptyList()
        hospitals = emptyList()
        rfidTags = emptyList()
        junctionEvents = emptyList()
        trips = emptyList()
        policeAlerts = emptyList()
        hospitalAlerts = emptyList()
        selectedHospital = null
        activeTripId = null
        adminSummary = null
        notice = null
        message = "Signed out."
    }

    private fun clearSubscriptions() {
        subscriptions.forEach { it.cancel() }
        subscriptions.clear()
    }

    private fun bindForRole(current: AppUser) {
        clearSubscriptions()
        when (current.role) {
            "ambulance_driver" -> bindDriver(current)
            "police" -> bindPolice(current)
            "hospital" -> bindHospital(current)
            "admin" -> refreshAdminSummary()
        }
    }

    // -----------------------------------------------------------------------
    // Role-scoped subscriptions
    // -----------------------------------------------------------------------

    private fun bindDriver(current: AppUser) {
        val ambulanceId = current.ambulanceId
        if (ambulanceId.isNullOrBlank()) {
            notice = "This account has no ambulance assigned, so no unit data can be shown. " +
                "Ask an administrator to link one."
            return
        }

        subscriptions += repository.observeAmbulance(ambulanceId) { ownAmbulance = it }
        subscriptions += repository.observeAmbulanceLoRaTelemetry(ambulanceId) { lora = it }
        subscriptions += repository.observeJunctions { junctions = it }
        subscriptions += repository.observeHospitals { hospitals = it }
    }

    private fun bindPolice(current: AppUser) {
        val junctionId = current.assignedJunctionId
        if (junctionId.isNullOrBlank()) {
            notice = "This account has no junction assigned, so no alerts can be shown. " +
                "Ask an administrator to link one."
            return
        }
        subscriptions += repository.observeAmbulances { ambulances = it }
        subscriptions += repository.observeJunctions { junctions = it }
        subscriptions += repository.observeRfidTags { rfidTags = it }
        subscriptions += repository.observeJunctionEvents { junctionEvents = it }

        // The junction's LoRa feed is keyed by ambulance, and which ambulance is
        // inbound only becomes known from the alert stream. Re-attach whenever it
        // changes so the officer is never looking at the previous vehicle.
        subscriptions += repository.observePoliceAlerts(junctionId) { alerts ->
            policeAlerts = alerts
            val ambulanceId = alerts.firstOrNull()?.ambulanceId
            policeTelemetrySub?.cancel()
            policeTelemetrySub = null
            lora = LoRaTelemetry.EMPTY
            if (ambulanceId != null) {
                policeTelemetrySub = repository.observeLoRaTelemetry(junctionId, ambulanceId) { lora = it }
            }
        }
    }

    private fun bindHospital(current: AppUser) {
        val hospitalId = current.hospitalId
        if (hospitalId.isNullOrBlank()) {
            notice = "This account has no hospital assigned, so no inbound cases can be " +
                "shown. Ask an administrator to link one."
            return
        }
        subscriptions += repository.observeHospitalAlerts(hospitalId) { hospitalAlerts = it }
        subscriptions += repository.observeAmbulances { ambulances = it }
        subscriptions += repository.observeTrips { trips = it }
    }

    private fun refreshAdminSummary() {
        adminSummary = null
        repository.loadAdminSummary { summary, error ->
            runOnUiThread {
                adminSummary = summary
                if (error != null) adminMessage = "Some registry nodes could not be read: $error"
            }
        }
    }

    // -----------------------------------------------------------------------
    // Derived, non-invented values
    // -----------------------------------------------------------------------

    /** The driver's destination picker: real hospitals, distance only when known. */
    private fun destinationOptions(): List<HospitalOption> {
        val from = lastKnownLocation ?: (ownAmbulance?.lat to ownAmbulance?.lng)
        return hospitals.map { hospital ->
            val km = Geo.haversineKm(from.first, from.second, hospital.lat, hospital.lng)
            HospitalOption(
                id = hospital.hospitalId,
                name = hospital.name,
                beds = hospital.bedsAvailable,
                // A registry value is a fact; a straight line is an estimate. Prefer the fact.
                distance = hospital.distance ?: Geo.formatKm(km),
                eta = hospital.eta
            )
        }
    }

    private fun driverStatus(): String {
        val active = ownAmbulance?.emergencyActive == true
        val destination = selectedHospital?.name
        return when {
            repository.isOffline -> "Offline demo · no live data"
            active && destination != null -> "Emergency live · $selectedSeverity · $destination"
            active -> "Emergency live · no destination selected"
            else -> "Standby · no active emergency"
        }
    }

    private fun startEmergency(current: AppUser) {
        val destination = selectedHospital
        repository.startEmergencyTrip(current, selectedSeverity, destination?.id) { tripId, error ->
            runOnUiThread {
                if (error != null) {
                    notice = error
                    return@runOnUiThread
                }
                notice = null
                activeTripId = tripId
                gpsNote = "Publishing phone GPS every 10 s."
                locationHandler.removeCallbacks(locationPublisher)
                locationHandler.post(locationPublisher)
                publishStatusViaMqtt(current, "emergency_active")
            }
        }
    }

    private fun completeEmergency(current: AppUser) {
        locationHandler.removeCallbacks(locationPublisher)
        repository.endEmergencyTrip(current, activeTripId) { _, result ->
            runOnUiThread {
                notice = result
                activeTripId = null
                gpsNote = "Location publishing stopped."
                publishStatusViaMqtt(current, "available")
            }
        }
    }

    private fun markReady(current: AppUser, key: String) {
        val hospitalId = current.hospitalId ?: return
        val tripId = hospitalAlerts.firstOrNull()?.tripId
        if (tripId == null) {
            readiness = "No inbound trip to acknowledge yet."
            return
        }
        repository.updateHospitalAlertStatus(hospitalId, tripId, key)
        readiness = when (key) {
            "team_alerted" -> "Emergency team alerted."
            "bed_ready" -> "Trauma bed ready."
            "doctor_ready" -> "Doctor standing by."
            "ambulance_received" -> "Patient received at the bay."
            else -> "Bay updated."
        }
    }

    private fun publishStatusViaMqtt(current: AppUser, status: String) {
        val ambulanceId = current.ambulanceId ?: return
        if (!mqttManager.isConnected()) return
        mqttManager.publish(
            MqttTopics.ambulanceStatus(ambulanceId),
            """{"ambulanceId":"$ambulanceId","status":"$status","severity":"$selectedSeverity","destinationHospitalId":"${selectedHospital?.id ?: ""}","timestamp":${System.currentTimeMillis()}}"""
        )
    }

    /**
     * Turns a Google/OSM discovery result into a destination. Bed count comes
     * from our own registry when the place matches one, and stays null
     * otherwise rather than being filled in with a guess.
     */
    private fun Hospital.toDestinationOption(): HospitalOption {
        val registered = hospitals.firstOrNull {
            it.hospitalId.equals(placeId, ignoreCase = true) ||
                it.name.equals(name, ignoreCase = true)
        }
        val km = if (distance > 0) distance / 1000.0 else null
        return HospitalOption(
            id = registered?.hospitalId ?: placeId,
            name = name,
            beds = registered?.bedsAvailable,
            distance = Geo.formatKm(km),
            eta = duration.ifBlank { null }
        )
    }

    // -----------------------------------------------------------------------
    // Location
    // -----------------------------------------------------------------------

    private fun publishCurrentLocation() {
        val ambulanceId = user?.ambulanceId ?: return
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            gpsNote = "Location permission is required to publish your position."
            requestLocationPermission()
            return
        }

        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    gpsNote = "Waiting for a location fix."
                    return@addOnSuccessListener
                }
                lastKnownLocation = location.latitude to location.longitude
                gpsNote = "GPS %.5f, %.5f".format(location.latitude, location.longitude)
                repository.updateLocation(ambulanceId, location.latitude, location.longitude)

                if (mqttManager.isConnected()) {
                    mqttManager.publish(
                        MqttTopics.ambulanceLoRaGps(ambulanceId),
                        """{"ambulanceId":"$ambulanceId","lat":${location.latitude},"lng":${location.longitude},"timestamp":${System.currentTimeMillis()}}"""
                    )
                }
            }
            .addOnFailureListener { error ->
                gpsNote = "Location update failed: ${error.message ?: "unknown error"}"
            }
    }

    private fun openDirections(hospital: HospitalOption) {
        val uri = Uri.parse("google.navigation:q=${Uri.encode(hospital.name)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(hospital.name)}")
                )
            )
        }
    }

    private fun requestLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
        }
    }

    // -----------------------------------------------------------------------
    // Admin
    // -----------------------------------------------------------------------

    private fun adminActions() = AdminActions(
        refresh = { refreshAdminSummary() },
        deactivate = { id ->
            repository.deactivateUser(id) { ok, result ->
                runOnUiThread {
                    adminMessage = result
                    if (ok) refreshAdminSummary()
                }
            }
        },
        saveDriver = { operatorId, name, ambulanceId, phone ->
            adminMessage = "Saving driver…"
            repository.registerDriver(operatorId, name, ambulanceId, phone) { _, result ->
                runOnUiThread { adminMessage = result; refreshAdminSummary() }
            }
        },
        savePolice = { operatorId, name, junctionId ->
            adminMessage = "Saving junction officer…"
            repository.registerPolice(operatorId, name, junctionId) { _, result ->
                runOnUiThread { adminMessage = result; refreshAdminSummary() }
            }
        },
        saveHospitalDesk = { operatorId, name, hospitalId ->
            adminMessage = "Saving hospital desk…"
            repository.registerHospitalUser(operatorId, name, hospitalId) { _, result ->
                runOnUiThread { adminMessage = result; refreshAdminSummary() }
            }
        },
        saveHospital = { hospitalId, name, beds, phone ->
            adminMessage = "Saving hospital…"
            repository.registerHospital(hospitalId, name, beds, phone) { _, result ->
                runOnUiThread { adminMessage = result; refreshAdminSummary() }
            }
        },
        saveAmbulance = { ambulanceId, driverId, rfidTagId ->
            adminMessage = "Saving ambulance and RFID tag…"
            repository.registerAmbulance(ambulanceId, driverId, rfidTagId) { _, result ->
                runOnUiThread { adminMessage = result; refreshAdminSummary() }
            }
        },
        saveJunction = { junctionId, name, activeLane ->
            adminMessage = "Saving junction…"
            repository.registerJunction(junctionId, name, activeLane) { _, result ->
                runOnUiThread { adminMessage = result; refreshAdminSummary() }
            }
        }
    )
}
