package com.smartambulance.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.data.AdminSummary
import com.smartambulance.driver.data.AppUser
import com.smartambulance.driver.ui.components.design.ActionButton
import com.smartambulance.driver.ui.components.design.ActionTone
import com.smartambulance.driver.ui.components.design.AppHeaderBar
import com.smartambulance.driver.ui.components.design.EmptyState
import com.smartambulance.driver.ui.components.design.InfoBanner
import com.smartambulance.driver.ui.components.design.LabelledField
import com.smartambulance.driver.ui.components.design.MetricTile
import com.smartambulance.driver.ui.components.design.SaptcsCard
import com.smartambulance.driver.ui.components.design.ScrollableTabs
import com.smartambulance.driver.ui.components.design.SectionHeader
import com.smartambulance.driver.ui.components.design.TabBody
import com.smartambulance.driver.ui.components.design.TileRow
import com.smartambulance.driver.ui.theme.AdminAmber
import com.smartambulance.driver.ui.theme.CanvasGradient
import com.smartambulance.driver.ui.theme.HospitalGreen
import com.smartambulance.driver.ui.theme.PoliceBlue
import com.smartambulance.driver.ui.theme.PrimaryRed
import com.smartambulance.driver.ui.theme.SecondaryAmber
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.SuccessGreen
import com.smartambulance.driver.ui.theme.TextMuted

/**
 * Registry writes the console performs. Split by record type so each form maps
 * to exactly one repository call.
 */
data class AdminActions(
    val refresh: () -> Unit,
    val deactivate: (operatorId: String) -> Unit,
    val saveDriver: (operatorId: String, name: String, ambulanceId: String, phone: String) -> Unit,
    val savePolice: (operatorId: String, name: String, junctionId: String) -> Unit,
    val saveHospitalDesk: (operatorId: String, name: String, hospitalId: String) -> Unit,
    val saveHospital: (hospitalId: String, name: String, beds: Int, phone: String) -> Unit,
    val saveAmbulance: (ambulanceId: String, driverId: String, rfidTagId: String) -> Unit,
    val saveJunction: (junctionId: String, name: String, activeLane: String) -> Unit
)

private data class FormField(
    val key: String,
    val label: String,
    val numeric: Boolean = false
)

/**
 * One registry form. Keeps every write path in the console identical in shape:
 * labelled fields, a submit button that is disabled until the required fields
 * are filled, and no optimistic success message.
 */
@Composable
private fun RegistryForm(
    title: String,
    hint: String,
    accent: Color,
    fields: List<FormField>,
    submitLabel: String,
    onSubmitted: (Map<String, String>) -> Unit
) {
    val values = remember {
        mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, "") } }
    }
    val complete = fields.all { values[it.key]?.isNotBlank() == true }

    SectionHeader(title = title, accent = accent)

    SaptcsCard(accent = accent, contentPadding = PaddingValues(Spacing.lg)) {
        Text(hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.height(Spacing.md))

        fields.forEachIndexed { position, field ->
            if (position > 0) Spacer(Modifier.height(Spacing.sm))
            LabelledField(
                value = values[field.key].orEmpty(),
                onValueChange = { values[field.key] = it },
                label = field.label,
                numeric = field.numeric
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        ActionButton(
            label = submitLabel,
            onClick = { onSubmitted(values.toMap()) },
            tone = ActionTone.Solid,
            accent = accent,
            enabled = complete,
            height = 48.dp
        )
    }
}

@Composable
fun AdminDashboard(
    user: AppUser,
    message: String,
    summary: AdminSummary?,
    actions: AdminActions,
    demoMode: Boolean,
    onLogout: () -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = listOf("Overview", "Driver", "Police", "Hospital", "Fleet", "Junction")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasGradient)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        AppHeaderBar(
            accent = AdminAmber,
            eyebrow = "System admin",
            title = user.name,
            subtitle = user.userId,
            icon = Icons.Filled.AdminPanelSettings,
            onLogout = onLogout
        )

        if (demoMode) {
            Box(Modifier.padding(horizontal = Spacing.lg)) {
                InfoBanner(
                    text = "Offline demo · the registry below is canned sample data and nothing " +
                        "you submit is written anywhere",
                    accent = SecondaryAmber,
                    emphasized = true,
                    icon = Icons.Filled.Shield
                )
            }
            Spacer(Modifier.height(Spacing.md))
        }

        ScrollableTabs(
            tabs = sections,
            selectedIndex = selectedSection,
            onSelect = { selectedSection = it },
            accent = AdminAmber,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )

        Spacer(Modifier.height(Spacing.md))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
        ) {
            when (selectedSection) {
                0 -> OverviewSection(message = message, summary = summary, actions = actions)
                1 -> DriverSection(actions = actions)
                2 -> PoliceSection(actions = actions)
                3 -> HospitalSection(actions = actions)
                4 -> FleetSection(actions = actions)
                else -> JunctionSection(actions = actions)
            }
        }
    }
}

@Composable
private fun OverviewSection(
    message: String,
    summary: AdminSummary?,
    actions: AdminActions
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        InfoBanner(
            text = message,
            accent = AdminAmber,
            emphasized = message.contains("could not", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true),
            icon = Icons.Filled.AdminPanelSettings
        )

        Spacer(Modifier.height(Spacing.md))

        TileRow {
            MetricTile(
                label = "Operators",
                value = summary?.users?.size?.toString() ?: "—",
                accent = PoliceBlue,
                icon = Icons.Filled.People,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Ambulances",
                value = summary?.ambulances?.size?.toString() ?: "—",
                accent = PrimaryRed,
                icon = Icons.Filled.LocalShipping,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Junctions",
                value = summary?.junctions?.size?.toString() ?: "—",
                accent = SecondaryAmber,
                icon = Icons.Filled.Traffic,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        TileRow {
            MetricTile(
                label = "Hospitals",
                value = summary?.hospitals?.size?.toString() ?: "—",
                accent = HospitalGreen,
                icon = Icons.Filled.LocalHospital,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "RFID tags",
                value = summary?.rfidTags?.size?.toString() ?: "—",
                accent = SuccessGreen,
                icon = Icons.Filled.Badge,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.md))

        ActionButton(
            label = "Refresh registry",
            onClick = actions.refresh,
            tone = ActionTone.Tinted,
            accent = AdminAmber,
            leadingIcon = Icons.Filled.Refresh
        )

        if (summary == null) {
            Spacer(Modifier.height(Spacing.md))
            EmptyState(
                icon = Icons.Filled.AdminPanelSettings,
                title = "Loading registry",
                body = "Reading the users, ambulances, hospitals, tags and junctions nodes.",
                accent = TextMuted
            )
        } else {
            RegistryList("Operators", summary.users, PoliceBlue) { operatorId ->
                actions.deactivate(operatorId)
            }
            RegistryList("Ambulances", summary.ambulances, PrimaryRed)
            RegistryList("Hospitals", summary.hospitals, HospitalGreen)
            RegistryList("RFID tags", summary.rfidTags, SuccessGreen)
            RegistryList("Junctions", summary.junctions, SecondaryAmber)
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

/**
 * A registry listing. Operator rows carry a deactivate action; the identifier is
 * read back out of the "label (id)" format the repository produces.
 */
@Composable
private fun RegistryList(
    title: String,
    entries: List<String>,
    accent: Color,
    onDeactivate: ((String) -> Unit)? = null
) {
    SectionHeader(
        title = title,
        accent = accent,
        trailing = {
            Text(
                text = if (entries.isEmpty()) "Empty" else "${entries.size}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    )

    SaptcsCard(accent = accent, contentPadding = PaddingValues(Spacing.lg)) {
        if (entries.isEmpty()) {
            Text(
                text = "Nothing registered yet.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        } else {
            entries.forEachIndexed { position, entry ->
                if (position > 0) Spacer(Modifier.height(Spacing.sm))
                Column {
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (onDeactivate != null) {
                        val id = entry.substringAfter('(', "").substringBefore(')')
                        if (id.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.xs))
                            ActionButton(
                                label = "Deactivate $id",
                                onClick = { onDeactivate(id) },
                                tone = ActionTone.Outline,
                                accent = PrimaryRed,
                                height = 38.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverSection(actions: AdminActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        InfoBanner(
            text = "This saves the operator's authorisation profile. The matching sign-in " +
                "account is created in the Firebase console as <operatorId>@saptcs.local — the " +
                "app cannot create credentials for another person.",
            accent = PoliceBlue,
            icon = Icons.Filled.People
        )
        Spacer(Modifier.height(Spacing.md))
        RegistryForm(
            title = "Register driver",
            hint = "Links an operator to an ambulance unit.",
            accent = PrimaryRed,
            fields = listOf(
                FormField("operatorId", "Operator ID (e.g. driver_002)"),
                FormField("name", "Full name"),
                FormField("ambulanceId", "Ambulance ID (e.g. AMB002)"),
                FormField("phone", "Phone")
            ),
            submitLabel = "Save driver"
        ) { values ->
            actions.saveDriver(
                values["operatorId"].orEmpty(),
                values["name"].orEmpty(),
                values["ambulanceId"].orEmpty(),
                values["phone"].orEmpty()
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun PoliceSection(actions: AdminActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        InfoBanner(
            text = "Junction officers see only their own junction's alerts, so the junction ID " +
                "here decides what this account can monitor.",
            accent = PoliceBlue,
            icon = Icons.Filled.Shield
        )
        Spacer(Modifier.height(Spacing.md))
        RegistryForm(
            title = "Register junction officer",
            hint = "Scopes an operator to one junction.",
            accent = PoliceBlue,
            fields = listOf(
                FormField("operatorId", "Operator ID (e.g. police_002)"),
                FormField("name", "Full name"),
                FormField("junctionId", "Junction ID (e.g. JNC002)")
            ),
            submitLabel = "Save officer"
        ) { values ->
            actions.savePolice(
                values["operatorId"].orEmpty(),
                values["name"].orEmpty(),
                values["junctionId"].orEmpty()
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun HospitalSection(actions: AdminActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        InfoBanner(
            text = "A hospital record and a desk operator are separate entries: the record " +
                "holds beds and contact details, the operator holds the sign-in identity.",
            accent = HospitalGreen,
            icon = Icons.Filled.LocalHospital
        )
        Spacer(Modifier.height(Spacing.md))
        RegistryForm(
            title = "Register hospital",
            hint = "Beds and contact details shown to drivers and desk staff.",
            accent = HospitalGreen,
            fields = listOf(
                FormField("hospitalId", "Hospital ID (e.g. HOSP005)"),
                FormField("name", "Hospital name"),
                FormField("beds", "Beds available", numeric = true),
                FormField("phone", "Contact number")
            ),
            submitLabel = "Save hospital"
        ) { values ->
            actions.saveHospital(
                values["hospitalId"].orEmpty(),
                values["name"].orEmpty(),
                values["beds"].orEmpty().toIntOrNull() ?: 0,
                values["phone"].orEmpty()
            )
        }
        Spacer(Modifier.height(Spacing.md))
        RegistryForm(
            title = "Register receiving desk",
            hint = "Scopes an operator to one hospital's inbound alerts.",
            accent = HospitalGreen,
            fields = listOf(
                FormField("operatorId", "Operator ID (e.g. hospital_002)"),
                FormField("name", "Desk name"),
                FormField("hospitalId", "Hospital ID")
            ),
            submitLabel = "Save desk"
        ) { values ->
            actions.saveHospitalDesk(
                values["operatorId"].orEmpty(),
                values["name"].orEmpty(),
                values["hospitalId"].orEmpty()
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun FleetSection(actions: AdminActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        InfoBanner(
            text = "Registering an ambulance also authorises its stop-line RFID tag, which is " +
                "what lets the roadside reader release the corridor.",
            accent = SuccessGreen,
            icon = Icons.Filled.LocalShipping
        )
        Spacer(Modifier.height(Spacing.md))
        RegistryForm(
            title = "Register ambulance",
            hint = "Creates the unit record and its RFID tag.",
            accent = SuccessGreen,
            fields = listOf(
                FormField("ambulanceId", "Ambulance ID (e.g. AMB003)"),
                FormField("driverId", "Assigned operator ID"),
                FormField("rfidTagId", "RFID tag ID (e.g. RFID_TAG_003)")
            ),
            submitLabel = "Save ambulance"
        ) { values ->
            actions.saveAmbulance(
                values["ambulanceId"].orEmpty(),
                values["driverId"].orEmpty(),
                values["rfidTagId"].orEmpty()
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun JunctionSection(actions: AdminActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        InfoBanner(
            text = "Junctions appear on the driver and officer dashboards as soon as they are " +
                "registered and their controller starts reporting state.",
            accent = SecondaryAmber,
            icon = Icons.Filled.Traffic
        )
        Spacer(Modifier.height(Spacing.md))
        RegistryForm(
            title = "Register junction",
            hint = "Adds a junction the corridor can preempt.",
            accent = SecondaryAmber,
            fields = listOf(
                FormField("junctionId", "Junction ID (e.g. JNC005)"),
                FormField("name", "Junction name"),
                FormField("activeLane", "Active lane (e.g. north)")
            ),
            submitLabel = "Save junction"
        ) { values ->
            actions.saveJunction(
                values["junctionId"].orEmpty(),
                values["name"].orEmpty(),
                values["activeLane"].orEmpty()
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
