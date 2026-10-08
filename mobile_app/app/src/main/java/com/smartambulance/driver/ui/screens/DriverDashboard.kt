package com.smartambulance.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.data.AmbulanceState
import com.smartambulance.driver.data.AppUser
import com.smartambulance.driver.data.Geo
import com.smartambulance.driver.data.HospitalOption
import com.smartambulance.driver.data.JunctionState
import com.smartambulance.driver.data.LoRaTelemetry
import com.smartambulance.driver.ui.components.design.ActionButton
import com.smartambulance.driver.ui.components.design.ActionTone
import com.smartambulance.driver.ui.components.design.DetailRow
import com.smartambulance.driver.ui.components.design.EmergencyActionButton
import com.smartambulance.driver.ui.components.design.EmptyState
import com.smartambulance.driver.ui.components.design.InfoBanner
import com.smartambulance.driver.ui.components.design.MetricTile
import com.smartambulance.driver.ui.components.design.RoleScaffold
import com.smartambulance.driver.ui.components.design.RowDivider
import com.smartambulance.driver.ui.components.design.SaptcsCard
import com.smartambulance.driver.ui.components.design.SectionHeader
import com.smartambulance.driver.ui.components.design.SelectableCard
import com.smartambulance.driver.ui.components.design.StatusPill
import com.smartambulance.driver.ui.components.design.TabBody
import com.smartambulance.driver.ui.components.design.TileRow
import com.smartambulance.driver.ui.theme.Border
import com.smartambulance.driver.ui.theme.Crimson
import com.smartambulance.driver.ui.theme.DriverRed
import com.smartambulance.driver.ui.theme.PoliceBlue
import com.smartambulance.driver.ui.theme.SecondaryAmber
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.SuccessGreen
import com.smartambulance.driver.ui.theme.TextDim
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary

/** Sentinel shown wherever the database has no value for a field. */
private const val NO_VALUE = "—"

/**
 * Severity is a clinical choice the operator makes, not a value read from the
 * database, so this list is UI configuration rather than data.
 */
private data class SeverityOption(
    val value: String,
    val label: String,
    val code: String,
    val description: String,
    val accent: Color
)

private val severityOptions = listOf(
    SeverityOption("Critical", "P1 · Critical", "P1", "Life-threatening · preempt every junction", Crimson),
    SeverityOption("Serious", "P2 · Severe", "P2", "Serious but stable · preempt on approach", SecondaryAmber),
    SeverityOption("Moderate", "P3 · Moderate", "P3", "Moderate urgency · standard priority", PoliceBlue)
)

private fun severityAccent(value: String): Color =
    severityOptions.firstOrNull { it.value == value }?.accent ?: TextPrimary

/** Colour for a junction's reported signal state; dim when it has not reported. */
private fun signalAccent(state: String?): Color = when (state?.lowercase()) {
    "green", "preempted", "preemption_granted" -> SuccessGreen
    "preemption", "amber", "hold" -> SecondaryAmber
    else -> TextDim
}

@Composable
fun DriverDashboard(
    user: AppUser,
    hospitals: List<HospitalOption>,
    junctions: List<JunctionState>,
    emergencyActive: Boolean,
    selectedSeverity: String,
    selectedHospital: HospitalOption?,
    status: String,
    ambulance: AmbulanceState?,
    lora: LoRaTelemetry,
    demoMode: Boolean,
    onSeverityChange: (String) -> Unit,
    onHospitalChange: (HospitalOption) -> Unit,
    onStartEmergency: () -> Unit,
    onEndEmergency: () -> Unit,
    onSearchHospital: () -> Unit,
    onOpenNavigation: (HospitalOption) -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    RoleScaffold(
        accent = DriverRed,
        eyebrow = "Ambulance unit",
        title = user.name,
        subtitle = user.ambulanceId ?: "No unit assigned",
        icon = Icons.Filled.DirectionsCar,
        status = when {
            demoMode -> "Demo"
            emergencyActive -> "Live"
            else -> "Standby"
        },
        statusLive = emergencyActive && !demoMode,
        onLogout = onLogout,
        demoMode = demoMode,
        tabs = listOf("Mission", "Navigate", "Status"),
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        headerTrailing = {
            IconButton(
                onClick = onSearchHospital,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(TextPrimary.copy(alpha = 0.05f))
                    .border(1.dp, Border, RoundedCornerShape(11.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search hospitals",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    ) { index ->
        when (index) {
            0 -> MissionTab(
                emergencyActive = emergencyActive,
                selectedSeverity = selectedSeverity,
                selectedHospital = selectedHospital,
                hospitals = hospitals,
                junctions = junctions,
                status = status,
                ambulance = ambulance,
                onSeverityChange = onSeverityChange,
                onHospitalChange = onHospitalChange,
                onStartEmergency = onStartEmergency,
                onEndEmergency = onEndEmergency,
                onSearchHospital = onSearchHospital
            )
            1 -> NavigateTab(
                selectedHospital = selectedHospital,
                junctions = junctions,
                onSearchHospital = onSearchHospital,
                onOpenNavigation = onOpenNavigation
            )
            else -> StatusTab(
                user = user,
                ambulance = ambulance,
                lora = lora,
                selectedHospital = selectedHospital,
                severity = selectedSeverity
            )
        }
    }
}

@Composable
private fun MissionTab(
    emergencyActive: Boolean,
    selectedSeverity: String,
    selectedHospital: HospitalOption?,
    hospitals: List<HospitalOption>,
    junctions: List<JunctionState>,
    status: String,
    ambulance: AmbulanceState?,
    onSeverityChange: (String) -> Unit,
    onHospitalChange: (HospitalOption) -> Unit,
    onStartEmergency: () -> Unit,
    onEndEmergency: () -> Unit,
    onSearchHospital: () -> Unit
) {
    val preempted = junctions.count { signalAccent(it.signalState) == SuccessGreen }

    TabBody {
        InfoBanner(
            text = status,
            accent = if (emergencyActive) DriverRed else TextMuted,
            emphasized = emergencyActive,
            icon = if (emergencyActive) Icons.Filled.Emergency else Icons.Filled.Traffic
        )

        SectionHeader(title = "Patient severity", accent = DriverRed)
        severityOptions.forEach { option ->
            SelectableCard(
                title = option.label,
                subtitle = option.description,
                accent = option.accent,
                selected = selectedSeverity == option.value,
                onClick = { onSeverityChange(option.value) },
                leadingIcon = Icons.Filled.Warning,
                trailing = {
                    Text(
                        text = option.code,
                        style = MaterialTheme.typography.labelLarge,
                        color = option.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(option.accent.copy(alpha = 0.14f))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
            )
        }

        SectionHeader(
            title = "Destination hospital",
            accent = DriverRed,
            trailing = {
                Text(
                    text = if (hospitals.isEmpty()) "None registered" else "${hospitals.size} registered",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        )

        if (hospitals.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.LocalHospital,
                title = "No hospitals registered",
                body = "An administrator needs to add hospitals to the registry before a " +
                    "destination can be selected.",
                accent = SecondaryAmber
            )
        } else {
            hospitals.forEach { hospital ->
                val beds = hospital.beds
                val open = beds == null || beds > 0
                SelectableCard(
                    title = hospital.name,
                    subtitle = buildString {
                        append(hospital.distance ?: "distance unknown")
                        append("  ·  ")
                        append(if (beds == null) "beds unknown" else "$beds beds")
                        hospital.eta?.let { append("  ·  $it") }
                    },
                    accent = if (open) DriverRed else TextDim,
                    selected = selectedHospital?.id == hospital.id,
                    onClick = { onHospitalChange(hospital) },
                    leadingIcon = Icons.Filled.LocalHospital,
                    trailing = {
                        StatusPill(
                            text = when {
                                beds == null -> "Unknown"
                                beds > 0 -> "Open"
                                else -> "Full"
                            },
                            color = when {
                                beds == null -> TextMuted
                                beds > 0 -> SuccessGreen
                                else -> DriverRed
                            }
                        )
                    }
                )
            }
        }

        ActionButton(
            label = "Search nearby hospitals",
            onClick = onSearchHospital,
            tone = ActionTone.Tinted,
            accent = PoliceBlue,
            leadingIcon = Icons.Filled.MyLocation
        )

        SectionHeader(title = "Trip control", accent = DriverRed)

        SaptcsCard(
            accent = if (emergencyActive) DriverRed else null,
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            DetailRow(
                "Destination",
                selectedHospital?.name ?: "None selected",
                valueColor = if (selectedHospital == null) SecondaryAmber else TextPrimary
            )
            DetailRow("Priority", selectedSeverity, valueColor = severityAccent(selectedSeverity))
            DetailRow("Unit", ambulance?.ambulanceId ?: NO_VALUE, mono = true)
            DetailRow(
                "Corridor",
                when {
                    !emergencyActive -> "Released"
                    preempted == 0 -> "Awaiting junction telemetry"
                    else -> "$preempted of ${junctions.size} junctions reporting green"
                },
                valueColor = if (emergencyActive) DriverRed else TextMuted
            )
        }

        EmergencyActionButton(
            active = emergencyActive,
            onToggle = if (emergencyActive) onEndEmergency else onStartEmergency,
            enabled = selectedHospital != null
        )

        if (selectedHospital == null) {
            Text(
                text = "Select a destination before starting a trip.",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryAmber
            )
        } else {
            Text(
                text = if (emergencyActive) {
                    "The vehicle LoRa beacon handles junction preemption; phone GPS publishes every 10 s."
                } else {
                    "Starting a trip requests a green corridor toward ${selectedHospital.name}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun NavigateTab(
    selectedHospital: HospitalOption?,
    junctions: List<JunctionState>,
    onSearchHospital: () -> Unit,
    onOpenNavigation: (HospitalOption) -> Unit
) {
    TabBody {
        if (selectedHospital == null) {
            EmptyState(
                icon = Icons.Filled.Navigation,
                title = "No destination yet",
                body = "Pick a hospital on the mission tab, or search for one nearby, and its " +
                    "route details will appear here.",
                accent = PoliceBlue
            )
        } else {
            SaptcsCard(accent = DriverRed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DriverRed.copy(alpha = 0.14f))
                            .border(1.dp, DriverRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.LocalHospital,
                            contentDescription = null,
                            tint = DriverRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("Destination", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                        Text(
                            text = selectedHospital.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                RowDivider()

                TileRow {
                    MetricTile(
                        label = "Distance",
                        value = selectedHospital.distance ?: NO_VALUE,
                        accent = PoliceBlue,
                        icon = Icons.Filled.Route,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "ETA",
                        value = selectedHospital.eta ?: NO_VALUE,
                        accent = SecondaryAmber,
                        icon = Icons.Filled.AccessTime,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Beds",
                        value = selectedHospital.beds?.toString() ?: NO_VALUE,
                        accent = when {
                            selectedHospital.beds == null -> TextMuted
                            selectedHospital.beds > 0 -> SuccessGreen
                            else -> DriverRed
                        },
                        icon = Icons.Filled.LocalHospital,
                        modifier = Modifier.weight(1f)
                    )
                }

                RowDivider()

                ActionButton(
                    label = "Open in Maps",
                    onClick = { onOpenNavigation(selectedHospital) },
                    tone = ActionTone.Solid,
                    accent = PoliceBlue,
                    leadingIcon = Icons.Filled.Navigation,
                    height = 46.dp
                )
            }

            ActionButton(
                label = "Find hospitals near me",
                onClick = onSearchHospital,
                tone = ActionTone.Outline,
                accent = SuccessGreen,
                leadingIcon = Icons.Filled.MyLocation
            )
        }

        SectionHeader(
            title = "Junction corridor",
            accent = DriverRed,
            trailing = {
                Text(
                    text = if (junctions.isEmpty()) "No junctions registered" else "${junctions.size} registered",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        )

        if (junctions.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Traffic,
                title = "No junctions registered",
                body = "Corridor state appears once junctions exist in the registry and their " +
                    "controllers start reporting.",
                accent = TextMuted
            )
        } else {
            SaptcsCard(accent = PoliceBlue, contentPadding = PaddingValues(Spacing.lg)) {
                junctions.forEachIndexed { position, junction ->
                    if (position > 0) RowDivider()
                    val accent = signalAccent(junction.signalState)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Traffic,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = junction.junctionId,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = junction.name ?: "Unnamed junction",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill(
                            text = junction.signalState ?: "No report",
                            color = accent,
                            live = accent == SuccessGreen
                        )
                    }
                }
            }
        }

        Text(
            text = "Junction state is published by the roadside controllers as they report in.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun StatusTab(
    user: AppUser,
    ambulance: AmbulanceState?,
    lora: LoRaTelemetry,
    selectedHospital: HospitalOption?,
    severity: String
) {
    val phoneFix = ambulance?.hasLocation == true
    val loraFix = lora.hasFix

    TabBody {
        SectionHeader(title = "Trip", accent = DriverRed)

        SaptcsCard(
            accent = if (ambulance?.emergencyActive == true) DriverRed else null,
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            DetailRow("Unit", ambulance?.ambulanceId ?: user.ambulanceId ?: NO_VALUE, mono = true)
            DetailRow(
                "State",
                when (ambulance?.status) {
                    "emergency_active" -> "Emergency active"
                    "available" -> "Available"
                    else -> ambulance?.status ?: "Not reported"
                },
                valueColor = if (ambulance?.emergencyActive == true) DriverRed else TextMuted
            )
            DetailRow("Severity", severity, valueColor = severityAccent(severity))
            DetailRow("Destination", selectedHospital?.name ?: "None selected", maxLines = 3)
            DetailRow("RFID tag", ambulance?.rfidTagId ?: NO_VALUE, mono = true)
        }

        SectionHeader(title = "Position", accent = PoliceBlue)

        SaptcsCard(accent = PoliceBlue, contentPadding = PaddingValues(Spacing.lg)) {
            DetailRow(
                "Phone GPS",
                if (phoneFix) "%.5f, %.5f".format(ambulance?.lat, ambulance?.lng) else "No fix",
                mono = true,
                valueColor = if (phoneFix) SuccessGreen else TextMuted
            )
            DetailRow(
                "Source",
                ambulance?.locationSource ?: NO_VALUE,
                mono = true,
                valueColor = TextMuted
            )
            RowDivider()
            DetailRow(
                "LoRa position",
                if (loraFix) "%.5f, %.5f".format(lora.lat, lora.lng) else "No fix",
                mono = true,
                valueColor = if (loraFix) SuccessGreen else TextMuted
            )
            DetailRow(
                "Approach",
                lora.distanceMeters?.let { "${it.toInt()} m to junction" } ?: NO_VALUE,
                valueColor = SecondaryAmber
            )
        }

        SectionHeader(title = "LoRa link", accent = SuccessGreen)

        SaptcsCard(accent = SuccessGreen, contentPadding = PaddingValues(Spacing.lg)) {
            DetailRow(
                "RSSI",
                lora.rssi?.let { "$it dBm" } ?: "Not reported",
                mono = true,
                valueColor = if (lora.rssi == null) TextMuted else SuccessGreen
            )
            DetailRow(
                "Speed",
                lora.speedKmph?.let { "%.0f km/h".format(it) } ?: "Not reported",
                mono = true,
                valueColor = if (lora.speedKmph == null) TextMuted else TextPrimary
            )
            DetailRow(
                "Heading",
                lora.headingDeg?.let { "%.0f°".format(it) } ?: "Not reported",
                mono = true,
                valueColor = TextMuted
            )
            RowDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SignalCellularAlt,
                    contentDescription = null,
                    tint = if (lora.rssi == null) TextDim else SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(Spacing.md))
                Text(
                    "Beacon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = if (lora.rssi == null) "Silent" else "Receiving",
                    color = if (lora.rssi == null) TextDim else SuccessGreen,
                    live = lora.rssi != null
                )
            }
        }

        Text(
            text = "Speeds and signal strength above are reported by the vehicle and roadside " +
                "radios. Values show as “$NO_VALUE” until a unit reports them.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
