package com.smartambulance.driver.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.data.AlertRecord
import com.smartambulance.driver.data.AmbulanceState
import com.smartambulance.driver.data.AppUser
import com.smartambulance.driver.data.JunctionEvent
import com.smartambulance.driver.data.JunctionState
import com.smartambulance.driver.data.LoRaTelemetry
import com.smartambulance.driver.data.RfidTag
import com.smartambulance.driver.ui.components.design.ActionButton
import com.smartambulance.driver.ui.components.design.ActionTone
import com.smartambulance.driver.ui.components.design.DetailRow
import com.smartambulance.driver.ui.components.design.EmptyState
import com.smartambulance.driver.ui.components.design.InfoBanner
import com.smartambulance.driver.ui.components.design.LiveDot
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
import com.smartambulance.driver.ui.theme.DataText
import com.smartambulance.driver.ui.theme.PoliceBlue
import com.smartambulance.driver.ui.theme.PrimaryRed
import com.smartambulance.driver.ui.theme.SecondaryAmber
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.SuccessGreen
import com.smartambulance.driver.ui.theme.TextDim
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary

private const val DASH = "—"

private fun signalAccent(state: String?): Color = when (state?.lowercase()) {
    "green", "preempted", "preemption_granted" -> SuccessGreen
    "preemption", "amber", "hold" -> SecondaryAmber
    else -> TextDim
}

// The database holds a wider severity vocabulary than the app itself writes:
// "Very Emergency" and "Critical" are already stored by older callers, so they
// are mapped explicitly rather than falling through to grey — a top-tier alert
// rendering as dim would be actively misleading on the desk.
private fun priorityAccent(severity: String?): Color = when (severity?.uppercase()) {
    "P1", "CRITICAL", "VERY EMERGENCY" -> PrimaryRed
    "P2", "SERIOUS", "EMERGENCY" -> SecondaryAmber
    "P3", "MODERATE" -> PoliceBlue
    else -> TextDim
}

/** Multi-line console text built strictly from reported figures. */
private fun telemetryLines(lora: LoRaTelemetry): String = listOf(
    "Position     " + if (lora.hasFix) "%.5f, %.5f".format(lora.lat, lora.lng) else "no fix",
    "Speed        " + (lora.speedKmph?.let { "%.0f km/h".format(it) } ?: DASH),
    "Heading      " + (lora.headingDeg?.let { "%.0f°".format(it) } ?: DASH),
    "To junction  " + (lora.distanceMeters?.let { "%.0f m".format(it) } ?: DASH),
    "Bearing      " + (lora.bearingToJunctionDeg?.let { "%.0f°".format(it) } ?: DASH),
    "RSSI         " + (lora.rssi?.let { "$it dBm" } ?: DASH)
).joinToString("\n")

@Composable
fun PoliceDashboard(
    user: AppUser,
    junctionId: String,
    alerts: List<AlertRecord>,
    ambulances: List<AmbulanceState>,
    junctions: List<JunctionState>,
    rfidTags: List<RfidTag>,
    junctionEvents: List<JunctionEvent>,
    lora: LoRaTelemetry,
    demoMode: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val lead = alerts.firstOrNull()

    RoleScaffold(
        accent = PoliceBlue,
        eyebrow = "Traffic police",
        title = user.name,
        subtitle = user.assignedJunctionId ?: "No junction assigned",
        icon = Icons.Filled.Security,
        status = when {
            demoMode -> "Demo"
            lead != null -> "Alert"
            else -> "Monitoring"
        },
        statusLive = lead != null && !demoMode,
        onLogout = onLogout,
        demoMode = demoMode,
        tabs = listOf("Live map", "Junctions", "Alerts"),
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        headerTrailing = {
            ActionIconButton(
                icon = Icons.Filled.Refresh,
                description = "Refresh alerts",
                accent = PoliceBlue,
                onClick = onRefresh
            )
        }
    ) { index ->
        when (index) {
            0 -> LiveMapTab(
                lead = lead,
                ambulances = ambulances,
                lora = lora,
                junctionId = junctionId
            )
            1 -> JunctionsTab(junctions = junctions, events = junctionEvents, onRefresh = onRefresh)
            else -> AlertsTab(
                alerts = alerts,
                rfidTags = rfidTags,
                events = junctionEvents,
                ambulances = ambulances
            )
        }
    }
}

@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
    ) {
        Icon(icon, contentDescription = description, tint = accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PositionRadar(accent: Color, hasFix: Boolean, modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "radar").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(188.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(CardBackground)
            .border(1.dp, accent.copy(alpha = 0.22f), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(150.dp)) {
            val origin = center
            val radius = size.minDimension / 2f
            val stroke = 1.dp.toPx()

            listOf(0.35f, 0.68f, 1f).forEach { fraction ->
                drawCircle(
                    color = accent.copy(alpha = 0.16f),
                    radius = radius * fraction,
                    center = origin,
                    style = Stroke(stroke)
                )
            }
            drawLine(accent.copy(alpha = 0.12f), Offset(origin.x - radius, origin.y), Offset(origin.x + radius, origin.y), stroke)
            drawLine(accent.copy(alpha = 0.12f), Offset(origin.x, origin.y - radius), Offset(origin.x, origin.y + radius), stroke)

            if (hasFix) {
                drawCircle(
                    color = accent.copy(alpha = 0.35f * (1f - sweep)),
                    radius = radius * sweep,
                    center = origin,
                    style = Stroke(2.dp.toPx())
                )
            }
        }

        LiveDot(color = if (hasFix) accent else TextDim, size = 10)

        Text(
            text = if (hasFix) "CONTACT" else "NO FIX",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasFix) accent else TextDim,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(Spacing.md)
        )
    }
}

@Composable
private fun TelemetryConsole(text: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(CardBackground)
            .border(1.dp, Border, MaterialTheme.shapes.medium)
            .padding(Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(color = accent, size = 6)
            Spacer(Modifier.size(Spacing.sm))
            Text("REPORTED BY THE INBOUND UNIT", style = MaterialTheme.typography.labelSmall, color = accent)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(text, style = DataText, color = TextMuted)
    }
}

@Composable
private fun UnitCard(unit: AmbulanceState, relation: String) {
    val accent = if (unit.emergencyActive) priorityAccent(unit.severity) else TextDim

    SaptcsCard(accent = accent, contentPadding = PaddingValues(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                text = unit.severity?.uppercase() ?: if (unit.emergencyActive) "Active" else "Idle",
                color = accent
            )
            Spacer(Modifier.size(Spacing.md))
            Text(
                text = unit.ambulanceId,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = relation,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1
            )
        }

        RowDivider()

        DetailRow("Driver", unit.driverId ?: DASH)
        DetailRow("Status", unit.status ?: "Not reported")
        DetailRow("Destination", unit.destinationHospitalId ?: DASH, mono = true)
        DetailRow("RFID tag", unit.rfidTagId ?: DASH, mono = true)
        DetailRow(
            "Position",
            if (unit.hasLocation) "%.4f, %.4f".format(unit.lat, unit.lng) else "No fix",
            mono = true,
            valueColor = if (unit.hasLocation) PoliceBlue else TextMuted
        )
    }
}

@Composable
private fun LiveMapTab(
    lead: AlertRecord?,
    ambulances: List<AmbulanceState>,
    lora: LoRaTelemetry,
    junctionId: String
) {
    val leadUnit = lead?.ambulanceId?.let { id -> ambulances.firstOrNull { it.ambulanceId == id } }
    val hasFix = lora.hasFix || leadUnit?.hasLocation == true
    val active = lead != null

    TabBody {
        if (lead == null) {
            InfoBanner(
                text = "No active alert for $junctionId.",
                accent = PoliceBlue,
                icon = Icons.Filled.NotificationImportant
            )
        } else {
            InfoBanner(
                text = buildString {
                    append(lead.message ?: "Ambulance approaching $junctionId")
                    append("\nUnit ")
                    append(lead.ambulanceId ?: DASH)
                    append("  ·  severity ")
                    append(lead.severity ?: DASH)
                    lead.distanceMeters?.let { append("  ·  ${it.toInt()} m out") }
                    lead.preemptionMode?.let { append("  ·  mode $it") }
                },
                accent = PrimaryRed,
                emphasized = true,
                icon = Icons.Filled.NotificationImportant
            )
        }

        SectionHeader(
            title = "Approach overlay",
            accent = PoliceBlue,
            trailing = {
                Text("Not to scale", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        )

        PositionRadar(accent = PoliceBlue, hasFix = hasFix)
        TelemetryConsole(text = telemetryLines(lora), accent = if (hasFix) PoliceBlue else TextDim)

        SectionHeader(
            title = "Registered units",
            accent = PoliceBlue,
            trailing = {
                Text(
                    text = if (ambulances.isEmpty()) "None" else "${ambulances.size} on the register",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        )

        if (ambulances.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Security,
                title = "No ambulances registered",
                body = "Units appear here once an administrator adds them to the registry.",
                accent = TextMuted
            )
        } else {
            ambulances.forEach { unit ->
                UnitCard(
                    unit = unit,
                    relation = when {
                        lead?.ambulanceId == unit.ambulanceId -> "Inbound"
                        unit.emergencyActive -> "On emergency"
                        else -> "Available"
                    }
                )
            }
        }

        if (!active) {
            Text(
                text = "Nothing is approaching this junction right now.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun JunctionsTab(
    junctions: List<JunctionState>,
    events: List<JunctionEvent>,
    onRefresh: () -> Unit
) {
    val green = junctions.count { signalAccent(it.signalState) == SuccessGreen }
    val holding = junctions.count { signalAccent(it.signalState) == SecondaryAmber }
    val silent = junctions.count { it.signalState == null }

    TabBody {
        TileRow {
            MetricTile(
                label = "Green",
                value = green.toString(),
                accent = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Holding",
                value = holding.toString(),
                accent = SecondaryAmber,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Silent",
                value = silent.toString(),
                accent = TextDim,
                modifier = Modifier.weight(1f)
            )
        }

        SectionHeader(
            title = "Junction status",
            accent = PoliceBlue,
            trailing = {
                Text(
                    text = if (junctions.isEmpty()) "None registered" else "${junctions.size} registered",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        )

        if (junctions.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Traffic,
                title = "No junctions registered",
                body = "Add junctions from the admin console to watch their signal state here.",
                accent = TextMuted
            )
        } else {
            junctions.forEach { junction ->
                val accent = signalAccent(junction.signalState)
                val recent = events.filter { it.junctionId == junction.junctionId }

                SaptcsCard(accent = accent, contentPadding = PaddingValues(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(accent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Traffic,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.size(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = junction.junctionId,
                                style = MaterialTheme.typography.titleMedium,
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

                    RowDivider()

                    DetailRow("Active lane", junction.activeLane ?: DASH, mono = true)
                    DetailRow(
                        "Recorded events",
                        if (recent.isEmpty()) "None" else "${recent.size}",
                        valueColor = if (recent.isEmpty()) TextMuted else TextPrimary
                    )
                    recent.firstOrNull()?.let { latest ->
                        DetailRow(
                            "Latest",
                            "${latest.eventType ?: "event"} · ${latest.ambulanceId ?: DASH}",
                            maxLines = 2
                        )
                    }
                }
            }
        }

        ActionButton(
            label = "Refresh live alerts",
            onClick = onRefresh,
            tone = ActionTone.Tinted,
            accent = PoliceBlue,
            leadingIcon = Icons.Filled.Refresh
        )
    }
}

@Composable
private fun AlertsTab(
    alerts: List<AlertRecord>,
    rfidTags: List<RfidTag>,
    events: List<JunctionEvent>,
    ambulances: List<AmbulanceState>
) {
    // The corridor log the desk cares about: grants, stop-line clears and hand-backs.
    // Plain `entry`/`exit` presence events are deliberately excluded.
    val preemptions = events
        .filter { it.openedCorridor || it.clearedAtStopLine || it.restored || it.preemptionMode != null }
        .sortedByDescending { it.timestamp ?: 0L }

    TabBody {
        if (alerts.isEmpty()) {
            InfoBanner(
                text = "No priority alerts recorded for this junction.",
                accent = PoliceBlue,
                icon = Icons.Filled.NotificationImportant
            )
        } else {
            SaptcsCard(
                accent = PrimaryRed,
                selected = true,
                contentPadding = PaddingValues(Spacing.lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryRed.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.NotificationImportant,
                            contentDescription = null,
                            tint = PrimaryRed,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${alerts.size} priority alert${if (alerts.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = alerts.first().message ?: "Most recent alert",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                RowDivider()

                alerts.take(5).forEachIndexed { position, alert ->
                    if (position > 0) RowDivider()
                    DetailRow(
                        label = alert.ambulanceId ?: alert.key,
                        value = buildString {
                            append(alert.severity ?: DASH)
                            alert.distanceMeters?.let { append(" · ${it.toInt()} m") }
                            append(" · ")
                            append(alert.status ?: alert.preemptionMode ?: "logged")
                        },
                        valueColor = priorityAccent(alert.severity)
                    )
                }
            }
        }

        SectionHeader(
            title = "RFID clearance log",
            accent = SuccessGreen,
            trailing = {
                Text(
                    text = if (rfidTags.isEmpty()) "No tags" else "${rfidTags.size} tags",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        )

        if (rfidTags.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Sensors,
                title = "No RFID tags registered",
                body = "Tag records appear once ambulances and their stop-line tags are registered.",
                accent = SuccessGreen
            )
        } else {
            SaptcsCard(accent = SuccessGreen, contentPadding = PaddingValues(Spacing.lg)) {
                rfidTags.forEachIndexed { position, tag ->
                    if (position > 0) RowDivider()
                    val registered = ambulances.any { it.rfidTagId == tag.rfidTagId }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Sensors,
                            contentDescription = null,
                            tint = if (tag.authorized && tag.active) SuccessGreen else TextDim,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = tag.rfidTagId,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = tag.ambulanceId ?: "Unassigned",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        StatusPill(
                            text = when {
                                !tag.authorized -> "Unauthorized"
                                !tag.active -> "Inactive"
                                registered -> "Linked"
                                else -> "Unlinked"
                            },
                            color = when {
                                !tag.authorized || !tag.active -> TextDim
                                registered -> SuccessGreen
                                else -> SecondaryAmber
                            }
                        )
                    }
                }
            }
        }

        SectionHeader(
            title = "Preemption events",
            accent = SecondaryAmber,
            trailing = {
                Text(
                    text = if (events.isEmpty()) "None recorded" else "${events.size} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        )

        if (events.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.WifiTethering,
                title = "No junction events yet",
                body = "Granted preemptions and stop-line clears are logged by the roadside " +
                    "controller as they happen.",
                accent = TextMuted
            )
        } else {
            SaptcsCard(accent = SecondaryAmber, contentPadding = PaddingValues(Spacing.lg)) {
                if (preemptions.isEmpty()) {
                    Text(
                        text = "No preemption events among the ${events.size} recorded events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                } else {
                    preemptions.take(6).forEachIndexed { position, event ->
                        if (position > 0) RowDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.WifiTethering,
                                contentDescription = null,
                                tint = SecondaryAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = event.junctionId ?: event.eventId,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${event.ambulanceId ?: DASH} · ${event.eventType ?: "event"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            StatusPill(
                                text = event.preemptionMode ?: when {
                                    event.openedCorridor -> "granted"
                                    event.clearedAtStopLine -> "cleared"
                                    else -> "logged"
                                },
                                color = when {
                                    event.clearedAtStopLine -> SuccessGreen
                                    event.restored -> TextDim
                                    else -> SecondaryAmber
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
