package com.smartambulance.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalHotel
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.data.AlertRecord
import com.smartambulance.driver.data.AmbulanceState
import com.smartambulance.driver.data.AppUser
import com.smartambulance.driver.data.TripRecord
import com.smartambulance.driver.ui.components.design.DetailRow
import com.smartambulance.driver.ui.components.design.EmptyState
import com.smartambulance.driver.ui.components.design.InfoBanner
import com.smartambulance.driver.ui.components.design.MetricTile
import com.smartambulance.driver.ui.components.design.RoleScaffold
import com.smartambulance.driver.ui.components.design.RowDivider
import com.smartambulance.driver.ui.components.design.SaptcsCard
import com.smartambulance.driver.ui.components.design.SectionHeader
import com.smartambulance.driver.ui.components.design.StatusPill
import com.smartambulance.driver.ui.components.design.TabBody
import com.smartambulance.driver.ui.components.design.TileRow
import com.smartambulance.driver.ui.theme.Border
import com.smartambulance.driver.ui.theme.CardBackground
import com.smartambulance.driver.ui.theme.HospitalGreen
import com.smartambulance.driver.ui.theme.PoliceBlue
import com.smartambulance.driver.ui.theme.PrimaryRed
import com.smartambulance.driver.ui.theme.SecondaryAmber
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.SuccessGreen
import com.smartambulance.driver.ui.theme.TextDim
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary

private const val NOT_REPORTED = "—"

/** The bay checklist. Keys match what the drivers write back to `hospitalAlerts`. */
private data class ReadinessStep(
    val key: String,
    val label: String,
    val hint: String,
    val icon: ImageVector
)

private val readinessSteps = listOf(
    ReadinessStep("team_alerted", "Response team", "Trauma team paged and assembling", Icons.Filled.Groups),
    ReadinessStep("bed_ready", "Bed + bay", "Resus bay cleared and bed assigned", Icons.Filled.LocalHotel),
    ReadinessStep("doctor_ready", "Doctor on duty", "Emergency physician standing by", Icons.Filled.MedicalServices),
    ReadinessStep("ambulance_received", "Patient received", "Handover complete at the bay", Icons.Filled.CheckCircle)
)

@Composable
fun HospitalDashboard(
    user: AppUser,
    hospitalId: String,
    alerts: List<AlertRecord>,
    trips: List<TripRecord>,
    ambulances: List<AmbulanceState>,
    readiness: String,
    demoMode: Boolean,
    onReady: (String) -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val inbound = alerts.firstOrNull()

    RoleScaffold(
        accent = HospitalGreen,
        eyebrow = "Receiving bay",
        title = user.name,
        subtitle = user.hospitalId ?: "No hospital assigned",
        icon = Icons.Filled.LocalHospital,
        status = when {
            demoMode -> "Demo"
            inbound != null -> "Inbound"
            else -> "Idle"
        },
        statusLive = inbound != null && !demoMode,
        onLogout = onLogout,
        demoMode = demoMode,
        tabs = listOf("Incoming", "Bay ready", "History"),
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it }
    ) { index ->
        when (index) {
            0 -> IncomingTab(inbound = inbound, ambulances = ambulances)
            1 -> BayReadyTab(inbound = inbound, readiness = readiness, onReady = onReady)
            else -> HistoryTab(trips = trips)
        }
    }
}

@Composable
private fun IncomingTab(
    inbound: AlertRecord?,
    ambulances: List<AmbulanceState>
) {
    val unit = inbound?.ambulanceId?.let { id -> ambulances.firstOrNull { it.ambulanceId == id } }

    TabBody {
        if (inbound == null) {
            EmptyState(
                icon = Icons.Filled.NotificationImportant,
                title = "No inbound ambulance",
                body = "This desk is idle. Incoming cases appear here the moment a driver opens " +
                    "a trip to this hospital.",
                accent = HospitalGreen
            )
        } else {
            InfoBanner(
                text = buildString {
                    append("AMBULANCE INBOUND")
                    append("\nUnit ")
                    append(inbound.ambulanceId ?: NOT_REPORTED)
                    append("  ·  severity ")
                    append(inbound.severity ?: NOT_REPORTED)
                    append("  ·  status ")
                    append(inbound.status ?: "unknown")
                },
                accent = PrimaryRed,
                emphasized = true,
                icon = Icons.Filled.NotificationImportant
            )

            SectionHeader(title = "Case", accent = HospitalGreen)

            SaptcsCard(accent = HospitalGreen, contentPadding = PaddingValues(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = inbound.ambulanceId ?: "Unknown unit",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatusPill(text = "Live", color = PrimaryRed, live = true)
                }

                RowDivider()

                DetailRow("Severity", inbound.severity ?: NOT_REPORTED)
                DetailRow("Status", inbound.status ?: NOT_REPORTED)
                DetailRow("Handover ETA", inbound.eta ?: "Not reported", valueColor = SecondaryAmber)
                DetailRow("Driver", unit?.driverId ?: NOT_REPORTED, mono = true)
            }

            TileRow {
                MetricTile(
                    label = "ETA",
                    value = inbound.eta ?: NOT_REPORTED,
                    accent = SecondaryAmber,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Distance",
                    value = inbound.distanceMeters?.let { "${it.toInt()} m" } ?: NOT_REPORTED,
                    accent = PoliceBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Priority",
                    value = inbound.severity ?: NOT_REPORTED,
                    accent = PrimaryRed,
                    modifier = Modifier.weight(1f)
                )
            }

            SectionHeader(title = "Unit position", accent = PoliceBlue)

            SaptcsCard(accent = PoliceBlue, contentPadding = PaddingValues(Spacing.lg)) {
                DetailRow(
                    "Phone GPS",
                    if (unit?.hasLocation == true) "%.5f, %.5f".format(unit.lat, unit.lng) else "No fix",
                    mono = true,
                    valueColor = if (unit?.hasLocation == true) SuccessGreen else TextMuted
                )
                DetailRow(
                    "Destination",
                    unit?.destinationHospitalId ?: inbound.tripId?.let { "trip $it" } ?: NOT_REPORTED,
                    mono = true
                )
                DetailRow(
                    "LoRa approach",
                    inbound.distanceMeters?.let { "${it.toInt()} m" } ?: "Not reported",
                    valueColor = SecondaryAmber
                )
            }
        }
    }
}

@Composable
private fun ReadinessRow(
    step: ReadinessStep,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val accent = if (checked) SuccessGreen else TextDim

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(CardBackground)
            .border(1.dp, if (checked) SuccessGreen.copy(alpha = 0.7f) else Border, MaterialTheme.shapes.large)
            .clickable { onToggle(!checked) }
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(step.icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = step.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = step.hint,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(Spacing.sm))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SuccessGreen,
                checkedTrackColor = SuccessGreen.copy(alpha = 0.3f),
                checkedBorderColor = SuccessGreen.copy(alpha = 0.6f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardBackground,
                uncheckedBorderColor = Border
            )
        )
    }
}

@Composable
private fun BayReadyTab(
    inbound: AlertRecord?,
    readiness: String,
    onReady: (String) -> Unit
) {
    var state by remember { mutableStateOf(readinessSteps.associate { it.key to false }) }
    val completed = state.count { it.value }
    val total = readinessSteps.size
    val fullyReady = completed == total

    TabBody {
        SaptcsCard(
            accent = if (fullyReady) SuccessGreen else PrimaryRed,
            selected = fullyReady,
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (fullyReady) "Bay ready" else "Preparing bay",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = "$completed / $total",
                    color = if (fullyReady) SuccessGreen else SecondaryAmber
                )
            }

            Spacer(Modifier.height(Spacing.md))

            LinearProgressIndicator(
                progress = { completed.toFloat() / total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (fullyReady) SuccessGreen else SecondaryAmber,
                trackColor = Border
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = if (inbound == null) {
                    "No inbound case. Confirm the checklist once a trip is opened to this hospital."
                } else {
                    "${inbound.ambulanceId ?: "A unit"} inbound" +
                        (inbound.eta?.let { " · ETA $it" } ?: " · ETA not reported")
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        SectionHeader(title = "Bay readiness", accent = HospitalGreen)

        readinessSteps.forEach { step ->
            ReadinessRow(
                step = step,
                checked = state[step.key] == true,
                onToggle = { checked ->
                    state = state + (step.key to checked)
                    if (checked) onReady(step.key)
                }
            )
        }

        SectionHeader(title = "Desk log", accent = PoliceBlue)

        SaptcsCard(accent = PoliceBlue, contentPadding = PaddingValues(Spacing.lg)) {
            Text(
                text = readiness,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun HistoryTab(trips: List<TripRecord>) {
    TabBody {
        SectionHeader(
            title = "Handovers to this hospital",
            accent = HospitalGreen,
            trailing = {
                Text(
                    text = if (trips.isEmpty()) "None" else "${trips.size} trips",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        )

        if (trips.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "No recorded handovers",
                body = "Completed and in-progress trips that name this hospital as their " +
                    "destination are listed here.",
                accent = HospitalGreen
            )
        } else {
            SaptcsCard(accent = HospitalGreen, contentPadding = PaddingValues(Spacing.lg)) {
                trips.forEachIndexed { position, trip ->
                    if (position > 0) RowDivider()
                    val completed = !trip.isActive
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (completed) SuccessGreen.copy(alpha = 0.14f)
                                    else SecondaryAmber.copy(alpha = 0.14f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Filled.History,
                                contentDescription = null,
                                tint = if (completed) SuccessGreen else SecondaryAmber,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.size(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = trip.tripId,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${trip.ambulanceId ?: NOT_REPORTED} · ${trip.severity ?: NOT_REPORTED}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        StatusPill(
                            text = trip.status ?: if (completed) "completed" else "in progress",
                            color = if (completed) SuccessGreen else SecondaryAmber,
                            live = !completed
                        )
                    }
                }
            }
        }

        Text(
            text = "Trip history is read from the emergencyTrips record written when a driver " +
                "opens an emergency.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
