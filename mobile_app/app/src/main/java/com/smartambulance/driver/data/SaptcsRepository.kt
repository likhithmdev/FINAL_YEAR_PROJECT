package com.smartambulance.driver.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/** Handle for a live database subscription, so screens can detach cleanly. */
fun interface Registration {
    fun cancel()
}

/**
 * The application's data layer.
 *
 * Two modes:
 *  - **live** (default): everything is read from and written to the Realtime
 *    Database, and the operator is a real Firebase Auth identity.
 *  - **offline**: for demonstrations without Firebase or hardware, every read is
 *    served from [DemoDataSource] and nothing is ever written. Callers are
 *    expected to badge the UI while this is on.
 *
 * The important invariant in both modes: **nothing here invents a value.** A
 * field that no device has reported stays null and the UI renders a dash. The
 * previous implementation published a fabricated `lastLoRaTelemetry` block
 * (hardcoded coordinates, 42 km/h, -72 dBm) whenever a driver started a trip,
 * which put numbers that no radio ever measured in front of the police and
 * hospital desks.
 */
class SaptcsRepository(
    private val offline: Boolean = false,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    firebase: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    /**
     * The database root as a reference. `child()` only exists on
     * `DatabaseReference`, so holding the `FirebaseDatabase` itself here would
     * leave every node path unresolvable.
     */
    private val database: DatabaseReference = firebase.getReference()

    /** True when reads are served from the canned dataset. */
    val isOffline: Boolean get() = offline

    companion object {
        /**
         * Firebase Auth needs an email, but operators sign in with a bare ID.
         * A synthetic address in this domain bridges the two, so `driver_001`
         * authenticates as `driver_001@saptcs.local`. Change this one constant
         * if your accounts live on a different domain.
         */
        const val OPERATOR_EMAIL_DOMAIN = "saptcs.local"

        /** Turns an operator ID (or a full email) into the Auth account address. */
        fun emailFor(input: String): String {
            val trimmed = input.trim()
            return if (trimmed.contains('@')) {
                trimmed.lowercase()
            } else {
                "${trimmed.lowercase()}@$OPERATOR_EMAIL_DOMAIN"
            }
        }

        /** The operator ID implied by whatever the user typed. */
        fun operatorIdFor(input: String): String = input.trim().substringBefore('@')

        /**
         * Firebase Auth rejects passwords shorter than six characters, so a bare
         * four-digit PIN cannot be one. Every operator PIN is therefore stored as
         * this prefix followed by the PIN, and the app puts the prefix back on
         * before signing in. Operators only ever type the four digits.
         */
        const val PIN_PREFIX = "saptcs"

        /**
         * Expands a typed PIN into the password that Auth actually holds.
         *
         * Anything that is not exactly four digits is passed through untouched,
         * so a full password (or an account created outside this convention)
         * still signs in.
         */
        fun passwordFor(secret: String): String {
            val trimmed = secret.trim()
            val isPin = trimmed.length == 4 && trimmed.all { it.isDigit() }
            return if (isPin) "$PIN_PREFIX$trimmed" else trimmed
        }

        /** Subscription handle for reads that have nothing to detach. */
        private val NO_OP = Registration { }
    }

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    /**
     * Signs an operator in. Accepts either a bare operator ID or a full email
     * address, so accounts created outside the synthetic-domain convention still
     * work.
     */
    fun signIn(input: String, password: String, onResult: (AppUser?, String?) -> Unit) {
        val operatorInput = input.trim()
        if (operatorInput.isEmpty() || password.isEmpty()) {
            onResult(null, "Enter your operator ID and PIN")
            return
        }

        if (offline) {
            val user = DemoDataSource.signIn(operatorInput, password)
            if (user == null) {
                onResult(null, "No offline demo account matches \"$operatorInput\"")
            } else {
                onResult(user, null)
            }
            return
        }

        auth.signInWithEmailAndPassword(emailFor(operatorInput), passwordFor(password))
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                    ?: return@addOnSuccessListener onResult(null, "Signed in without a user record")
                loadProfile(operatorIdFor(operatorInput), uid, onResult)
            }
            .addOnFailureListener { error -> onResult(null, describeAuthError(error, operatorInput)) }
    }

    fun signOut() {
        if (!offline) auth.signOut()
    }

    /**
     * Reads the authorisation profile for a signed-in operator, preferring
     * `users/$operatorId` and falling back to a profile keyed by uid so either
     * convention in the database works.
     */
    private fun loadProfile(operatorId: String, uid: String, onResult: (AppUser?, String?) -> Unit) {
        val byOperatorId = database.child(FirebasePaths.USERS).child(operatorId)
        byOperatorId.get().addOnCompleteListener { task ->
            val snapshot = task.result
            if (task.isSuccessful && snapshot != null && snapshot.exists()) {
                val user = profileFrom(snapshot, operatorId)
                if (user == null) {
                    onResult(null, "Signed in, but $operatorId has no role assigned")
                } else {
                    onResult(user, null)
                }
                return@addOnCompleteListener
            }

            database.child(FirebasePaths.USERS).child(uid).get()
                .addOnSuccessListener { byUid ->
                    val user = if (byUid.exists()) profileFrom(byUid, operatorId) else null
                    onResult(
                        user,
                        if (user == null) {
                            "Signed in, but no profile exists at users/$operatorId for this account"
                        } else {
                            null
                        }
                    )
                }
                .addOnFailureListener { error ->
                    onResult(null, "Could not read the operator profile: ${error.message}")
                }
        }
    }

    private fun profileFrom(snapshot: DataSnapshot, fallbackId: String): AppUser? {
        val active = snapshot.child("active").getValue(Boolean::class.java) ?: true
        if (!active) return null
        val role = snapshot.child("role").getValue(String::class.java).orEmpty()
        if (role.isBlank()) return null
        return AppUser(
            userId = snapshot.child("userId").getValue(String::class.java) ?: fallbackId,
            name = snapshot.child("name").getValue(String::class.java) ?: fallbackId,
            role = role,
            ambulanceId = snapshot.child("ambulanceId").getValue(String::class.java),
            assignedJunctionId = snapshot.child("assignedJunctionId").getValue(String::class.java),
            hospitalId = snapshot.child("hospitalId").getValue(String::class.java)
        )
    }

    /**
     * Turns Auth failures into something an operator can act on.
     *
     * The project-wide provider switch arrives as a credential-style failure, so
     * it is matched by message first. Reporting it as a bad PIN would send an
     * operator hunting for a credential problem that does not exist — the fix is
     * a console toggle, not a different password.
     */
    private fun describeAuthError(error: Exception, input: String): String {
        val raw = error.message.orEmpty()
        if (raw.contains("disabled", ignoreCase = true)) {
            return "Email/Password sign-in is turned off for this Firebase project. " +
                "Enable it under Authentication → Sign-in method, then sign in as " +
                "${emailFor(input)}."
        }
        return when (error) {
            is FirebaseAuthInvalidUserException ->
                "No account for ${emailFor(input)}. Create it in the Firebase console, " +
                    "or type the account's full email address."
            is FirebaseAuthInvalidCredentialsException ->
                "Incorrect PIN for ${operatorIdFor(input)}."
            else -> raw.ifBlank { "Sign-in failed" }
        }
    }

    // -----------------------------------------------------------------------
    // Live reads
    // -----------------------------------------------------------------------

    fun observeAmbulances(onChange: (List<AmbulanceState>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.ambulances, onChange)
        return observeNode(FirebasePaths.AMBULANCES) { onChange(Mapping.ambulances(it)) }
    }

    fun observeJunctions(onChange: (List<JunctionState>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.junctions, onChange)
        return observeNode(FirebasePaths.JUNCTIONS) { onChange(Mapping.junctions(it)) }
    }

    fun observeRfidTags(onChange: (List<RfidTag>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.rfidTags, onChange)
        return observeNode(FirebasePaths.RFID_TAGS) { onChange(Mapping.rfidTags(it)) }
    }

    fun observeTrips(onChange: (List<TripRecord>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.trips, onChange)
        return observeNode(FirebasePaths.EMERGENCY_TRIPS) { onChange(Mapping.trips(it)) }
    }

    fun observeHospitals(onChange: (List<HospitalRecord>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.hospitals, onChange)
        return observeNode(FirebasePaths.HOSPITALS) { onChange(Mapping.hospitals(it)) }
    }

    fun observePoliceAlerts(junctionId: String, onChange: (List<AlertRecord>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.policeAlerts, onChange)
        return observeNode("${FirebasePaths.POLICE_ALERTS}/$junctionId") { onChange(Mapping.alerts(it)) }
    }

    fun observeHospitalAlerts(hospitalId: String, onChange: (List<AlertRecord>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.hospitalAlerts, onChange)
        return observeNode("${FirebasePaths.HOSPITAL_ALERTS}/$hospitalId") { onChange(Mapping.alerts(it)) }
    }

    fun observeJunctionEvents(onChange: (List<JunctionEvent>) -> Unit): Registration {
        if (offline) return emitCanned(DemoDataSource.junctionEvents, onChange)
        return observeNode(FirebasePaths.JUNCTION_EVENTS) { onChange(Mapping.junctionEvents(it)) }
    }

    /** A single ambulance record, or null when the node does not exist. */
    fun observeAmbulance(ambulanceId: String, onChange: (AmbulanceState?) -> Unit): Registration {
        if (offline) {
            onChange(DemoDataSource.ambulances.firstOrNull { it.ambulanceId == ambulanceId })
            return NO_OP
        }
        val query = database.child(FirebasePaths.AMBULANCES).child(ambulanceId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onChange(if (snapshot.exists()) Mapping.ambulance(ambulanceId, snapshot.value.asMap()) else null)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        query.addValueEventListener(listener)
        return Registration { query.removeEventListener(listener) }
    }

    /**
     * LoRa telemetry for one ambulance at one junction, preferring the node the
     * roadside receiver writes and falling back to the ambulance's own record.
     */
    fun observeLoRaTelemetry(
        junctionId: String,
        ambulanceId: String,
        onChange: (LoRaTelemetry) -> Unit
    ): Registration {
        if (offline) {
            onChange(DemoDataSource.loRaTelemetry)
            return NO_OP
        }

        val receiverQuery = database
            .child(FirebasePaths.LORA_TELEMETRY).child(junctionId).child(ambulanceId)
        val fallbackQuery = database
            .child(FirebasePaths.AMBULANCES).child(ambulanceId).child("lastLoRaTelemetry")

        var receiverValue: Map<String, Any?>? = null
        var fallbackValue: Map<String, Any?>? = null
        val publish = { onChange(Mapping.loRaTelemetry(receiverValue, fallbackValue)) }

        val receiverListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                receiverValue = if (snapshot.exists()) snapshot.value.asMap() else null
                publish()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        val fallbackListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fallbackValue = if (snapshot.exists()) snapshot.value.asMap() else null
                publish()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }

        receiverQuery.addValueEventListener(receiverListener)
        fallbackQuery.addValueEventListener(fallbackListener)
        return Registration {
            receiverQuery.removeEventListener(receiverListener)
            fallbackQuery.removeEventListener(fallbackListener)
        }
    }

    /**
     * LoRa telemetry for a named ambulance without knowing which junction it is
     * approaching, found by scanning the telemetry tree for that ambulance. The
     * driver app has no way to know its junction ahead of time, so hardcoding
     * one here would silently read the wrong node.
     */
    fun observeAmbulanceLoRaTelemetry(
        ambulanceId: String,
        onChange: (LoRaTelemetry) -> Unit
    ): Registration {
        if (offline) {
            onChange(DemoDataSource.loRaTelemetry)
            return NO_OP
        }
        return observeNode(FirebasePaths.LORA_TELEMETRY) { raw ->
            val match = raw.orEmpty()
                .values
                .mapNotNull { junctionNode -> junctionNode.asMap()?.get(ambulanceId).asMap() }
                .firstOrNull()
            onChange(Mapping.loRaTelemetry(match, null))
        }
    }

    private fun observeNode(
        path: String,
        onChange: (Map<String, Any?>?) -> Unit
    ): Registration {
        val query: Query = database.child(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onChange(if (snapshot.exists()) snapshot.value.asMap() else null)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        query.addValueEventListener(listener)
        return Registration { query.removeEventListener(listener) }
    }

    private fun <T> emitCanned(values: List<T>, onChange: (List<T>) -> Unit): Registration {
        onChange(values)
        return NO_OP
    }

    // -----------------------------------------------------------------------
    // Trip lifecycle
    // -----------------------------------------------------------------------

    /**
     * Opens an emergency trip. Writes only what the app actually knows: the
     * destination, severity and start time. Position and LoRa figures are left
     * for the devices that measure them.
     */
    fun startEmergencyTrip(
        user: AppUser,
        severity: String,
        destinationHospitalId: String?,
        onResult: (tripId: String?, error: String?) -> Unit
    ) {
        val ambulanceId = user.ambulanceId
        if (ambulanceId.isNullOrBlank()) {
            onResult(null, "This operator has no ambulance assigned")
            return
        }
        if (offline) {
            onResult("TRIP-DEMO", null)
            return
        }

        val tripsRef = database.child(FirebasePaths.EMERGENCY_TRIPS)
        val tripId = tripsRef.push().key
        if (tripId == null) {
            onResult(null, "Could not allocate a trip reference")
            return
        }

        val trip = mapOf(
            "tripId" to tripId,
            "ambulanceId" to ambulanceId,
            "driverId" to user.userId,
            "severity" to severity,
            "destinationHospitalId" to destinationHospitalId,
            "status" to "emergency_active",
            "startedAt" to ServerValue.TIMESTAMP
        )

        tripsRef.child(tripId).setValue(trip)
            .addOnSuccessListener {
                database.child(FirebasePaths.AMBULANCES).child(ambulanceId).updateChildren(
                    mapOf(
                        "ambulanceId" to ambulanceId,
                        "driverId" to user.userId,
                        "emergencyActive" to true,
                        "status" to "emergency_active",
                        "severity" to severity,
                        "destinationHospitalId" to destinationHospitalId,
                        "tripId" to tripId,
                        "updatedAt" to ServerValue.TIMESTAMP
                    )
                ).addOnSuccessListener { onResult(tripId, null) }
                    .addOnFailureListener { error ->
                        onResult(tripId, "Trip opened but the ambulance record did not update: ${error.message}")
                    }
            }
            .addOnFailureListener { error ->
                onResult(null, "Could not open the trip: ${error.message}")
            }
    }

    fun endEmergencyTrip(user: AppUser, tripId: String?, onResult: (Boolean, String) -> Unit) {
        val ambulanceId = user.ambulanceId
        if (ambulanceId.isNullOrBlank()) {
            onResult(false, "This operator has no ambulance assigned")
            return
        }
        if (offline) {
            onResult(true, "Trip closed (offline demo, nothing written)")
            return
        }

        val closeAmbulance = database.child(FirebasePaths.AMBULANCES).child(ambulanceId).updateChildren(
            mapOf(
                "emergencyActive" to false,
                "status" to "available",
                "severity" to null,
                "destinationHospitalId" to null,
                "tripId" to null,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        )

        val closeTrip = tripId?.let { id ->
            database.child(FirebasePaths.EMERGENCY_TRIPS).child(id).updateChildren(
                mapOf(
                    "status" to "completed",
                    "endedAt" to ServerValue.TIMESTAMP
                )
            )
        }

        closeAmbulance.addOnCompleteListener { ambulanceTask ->
            if (!ambulanceTask.isSuccessful) {
                onResult(false, "Could not release the corridor: ${ambulanceTask.exception?.message}")
            } else if (closeTrip == null) {
                onResult(true, "Corridor released (no trip reference to close)")
            } else {
                closeTrip.addOnCompleteListener { tripTask ->
                    if (tripTask.isSuccessful) {
                        onResult(true, "Trip closed · corridor released")
                    } else {
                        onResult(
                            true,
                            "Corridor released, but the trip record did not close: " +
                                "${tripTask.exception?.message}"
                        )
                    }
                }
            }
        }
    }

    /** Phone GPS from the driver app. Written only when a fix exists. */
    fun updateLocation(ambulanceId: String, latitude: Double, longitude: Double) {
        if (offline) return
        database.child(FirebasePaths.AMBULANCES).child(ambulanceId).child("lastLocation").updateChildren(
            mapOf(
                "lat" to latitude,
                "lng" to longitude,
                "source" to "android_gps",
                "updatedAt" to ServerValue.TIMESTAMP
            )
        )
    }

    /** Bay-readiness acknowledgement, written against the trip it belongs to. */
    fun updateHospitalAlertStatus(hospitalId: String, tripId: String, status: String) {
        if (offline) return
        database.child(FirebasePaths.HOSPITAL_ALERTS).child(hospitalId).child(tripId).updateChildren(
            mapOf(
                "status" to status,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        )
    }

    // -----------------------------------------------------------------------
    // Registry writes (admin console)
    //
    // These write authorization profiles only. Creating the matching Firebase
    // Auth account is a console action, because the client SDK cannot create
    // credentials for another person without signing in as them.
    // -----------------------------------------------------------------------

    fun registerDriver(
        operatorId: String,
        name: String,
        ambulanceId: String,
        phone: String,
        onResult: (Boolean, String) -> Unit
    ) = writeProfile(
        operatorId, name, "ambulance_driver", onResult,
        extra = mapOf("ambulanceId" to ambulanceId.trim().uppercase(), "phone" to phone.trim())
    )

    fun registerPolice(
        operatorId: String,
        name: String,
        junctionId: String,
        onResult: (Boolean, String) -> Unit
    ) = writeProfile(
        operatorId, name, "police", onResult,
        extra = mapOf("assignedJunctionId" to junctionId.trim().uppercase())
    )

    fun registerHospitalUser(
        operatorId: String,
        name: String,
        hospitalId: String,
        onResult: (Boolean, String) -> Unit
    ) = writeProfile(
        operatorId, name, "hospital", onResult,
        extra = mapOf("hospitalId" to hospitalId.trim().uppercase())
    )

    private fun writeProfile(
        operatorId: String,
        name: String,
        role: String,
        onResult: (Boolean, String) -> Unit,
        extra: Map<String, Any?>
    ) {
        val cleanId = operatorId.trim().lowercase()
        if (cleanId.isBlank()) {
            onResult(false, "Operator ID is required")
            return
        }
        if (offline) {
            onResult(false, "Offline demo mode never writes to the database")
            return
        }

        val payload = HashMap<String, Any?>(extra)
        payload["userId"] = cleanId
        payload["operatorId"] = cleanId
        payload["email"] = "$cleanId@$OPERATOR_EMAIL_DOMAIN"
        payload["name"] = name.trim().ifBlank { cleanId }
        payload["role"] = role
        payload["active"] = true
        payload["updatedAt"] = ServerValue.TIMESTAMP

        database.child(FirebasePaths.USERS).child(cleanId).updateChildren(payload)
            .addOnSuccessListener {
                onResult(
                    true,
                    "Profile saved for $cleanId. Create its sign-in account in the " +
                        "Firebase console as ${emailFor(cleanId)}."
                )
            }
            .addOnFailureListener { error ->
                onResult(false, "Could not save $cleanId: ${error.message}")
            }
    }

    fun registerHospital(
        hospitalId: String,
        name: String,
        beds: Int,
        phone: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanId = hospitalId.trim().uppercase()
        if (cleanId.isBlank() || name.isBlank()) {
            onResult(false, "Hospital ID and name are required")
            return
        }
        if (offline) {
            onResult(false, "Offline demo mode never writes to the database")
            return
        }
        database.child(FirebasePaths.HOSPITALS).child(cleanId).updateChildren(
            mapOf(
                "hospitalId" to cleanId,
                "name" to name.trim(),
                "bedsAvailable" to beds,
                "phone" to phone.trim(),
                "emergencyAvailable" to true,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).addOnSuccessListener { onResult(true, "Hospital saved: $cleanId") }
            .addOnFailureListener { error -> onResult(false, "Could not save $cleanId: ${error.message}") }
    }

    fun registerAmbulance(
        ambulanceId: String,
        driverId: String,
        rfidTagId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanAmbulanceId = ambulanceId.trim().uppercase()
        val cleanTagId = rfidTagId.trim().uppercase()
        if (cleanAmbulanceId.isBlank()) {
            onResult(false, "Ambulance ID is required")
            return
        }
        if (offline) {
            onResult(false, "Offline demo mode never writes to the database")
            return
        }

        val ambulanceRef = database.child(FirebasePaths.AMBULANCES).child(cleanAmbulanceId)
        ambulanceRef.updateChildren(
            mapOf(
                "ambulanceId" to cleanAmbulanceId,
                "driverId" to driverId.trim().lowercase(),
                "rfidTagId" to cleanTagId,
                "loraNodeId" to "LORA_$cleanAmbulanceId",
                "status" to "available",
                "emergencyActive" to false,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).addOnSuccessListener {
            if (cleanTagId.isBlank()) {
                onResult(true, "Ambulance saved: $cleanAmbulanceId")
            } else {
                registerRfidTag(cleanTagId, cleanAmbulanceId) { ok, message ->
                    onResult(ok, if (ok) "Ambulance and RFID tag saved: $cleanAmbulanceId" else message)
                }
            }
        }.addOnFailureListener { error ->
            onResult(false, "Could not save $cleanAmbulanceId: ${error.message}")
        }
    }

    fun registerRfidTag(
        rfidTagId: String,
        ambulanceId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanTagId = rfidTagId.trim().uppercase()
        if (cleanTagId.isBlank()) {
            onResult(false, "RFID tag ID is required")
            return
        }
        if (offline) {
            onResult(false, "Offline demo mode never writes to the database")
            return
        }
        database.child(FirebasePaths.RFID_TAGS).child(cleanTagId).updateChildren(
            mapOf(
                "rfidTagId" to cleanTagId,
                "ambulanceId" to ambulanceId.trim().uppercase(),
                "authorized" to true,
                "active" to true,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).addOnSuccessListener { onResult(true, "RFID tag saved: $cleanTagId") }
            .addOnFailureListener { error -> onResult(false, "Could not save $cleanTagId: ${error.message}") }
    }

    fun registerJunction(
        junctionId: String,
        name: String,
        activeLane: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanId = junctionId.trim().uppercase()
        if (cleanId.isBlank() || name.isBlank()) {
            onResult(false, "Junction ID and name are required")
            return
        }
        if (offline) {
            onResult(false, "Offline demo mode never writes to the database")
            return
        }
        database.child(FirebasePaths.JUNCTIONS).child(cleanId).updateChildren(
            mapOf(
                "junctionId" to cleanId,
                "name" to name.trim(),
                "activeLane" to activeLane.trim().ifBlank { "normal" },
                "signalState" to "normal",
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).addOnSuccessListener { onResult(true, "Junction saved: $cleanId") }
            .addOnFailureListener { error -> onResult(false, "Could not save $cleanId: ${error.message}") }
    }

    fun deactivateUser(userId: String, onResult: (Boolean, String) -> Unit) {
        val cleanId = userId.trim().lowercase()
        if (cleanId.isBlank()) {
            onResult(false, "Operator ID is required")
            return
        }
        if (offline) {
            onResult(false, "Offline demo mode never writes to the database")
            return
        }
        database.child(FirebasePaths.USERS).child(cleanId).updateChildren(
            mapOf("active" to false, "updatedAt" to ServerValue.TIMESTAMP)
        ).addOnSuccessListener { onResult(true, "Deactivated: $cleanId") }
            .addOnFailureListener { error -> onResult(false, "Could not deactivate $cleanId: ${error.message}") }
    }

    /** Registry listing for the admin overview. */
    fun loadAdminSummary(onResult: (AdminSummary?, String?) -> Unit) {
        if (offline) {
            onResult(DemoDataSource.adminSummary, null)
            return
        }
        val root = database
        val nodes = listOf(
            FirebasePaths.USERS,
            FirebasePaths.AMBULANCES,
            FirebasePaths.HOSPITALS,
            FirebasePaths.RFID_TAGS,
            FirebasePaths.JUNCTIONS
        )
        val results = HashMap<String, List<String>>()
        var remaining = nodes.size
        var failed: String? = null

        nodes.forEach { node ->
            root.child(node).get().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    results[node] = task.result.children.map { child ->
                        val label = child.child("name").getValue(String::class.java)
                            ?: child.child("hospitalId").getValue(String::class.java)
                            ?: child.child("ambulanceId").getValue(String::class.java)
                            ?: child.key.orEmpty()
                        "$label (${child.key})"
                    }
                } else if (failed == null) {
                    failed = "${task.exception?.message}"
                }
                remaining--
                if (remaining == 0) {
                    onResult(
                        AdminSummary(
                            users = results[FirebasePaths.USERS].orEmpty(),
                            ambulances = results[FirebasePaths.AMBULANCES].orEmpty(),
                            hospitals = results[FirebasePaths.HOSPITALS].orEmpty(),
                            rfidTags = results[FirebasePaths.RFID_TAGS].orEmpty(),
                            junctions = results[FirebasePaths.JUNCTIONS].orEmpty()
                        ),
                        failed
                    )
                }
            }
        }
    }

}
