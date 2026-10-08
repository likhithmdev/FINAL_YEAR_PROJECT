package com.smartambulance.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.smartambulance.driver.data.Hospital
import com.smartambulance.driver.services.HospitalDiscoveryService
import com.smartambulance.driver.ui.components.design.ActionButton
import com.smartambulance.driver.ui.components.design.ActionTone
import com.smartambulance.driver.ui.components.design.AppHeaderBar
import com.smartambulance.driver.ui.components.design.EmptyState
import com.smartambulance.driver.ui.components.design.HeaderIconButton
import com.smartambulance.driver.ui.components.design.InfoBanner
import com.smartambulance.driver.ui.components.design.LiveDot
import com.smartambulance.driver.ui.components.design.SaptcsCard
import com.smartambulance.driver.ui.components.design.SectionHeader
import com.smartambulance.driver.ui.components.design.StatusPill
import com.smartambulance.driver.ui.theme.Border
import com.smartambulance.driver.ui.theme.CanvasGradient
import com.smartambulance.driver.ui.theme.HospitalGreen
import com.smartambulance.driver.ui.theme.PoliceBlue
import com.smartambulance.driver.ui.theme.PrimaryRed
import com.smartambulance.driver.ui.theme.SecondaryAmber
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.SuccessGreen
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary

private fun formatDistance(metres: Double): String = when {
    metres <= 0.0 -> "--"
    metres < 1000 -> "${metres.toInt()} m"
    else -> String.format("%.1f km", metres / 1000.0)
}

/**
 * Nearby-hospital discovery for the driver.
 *
 * Behaviour is unchanged (location on load, debounced search, optional tracking);
 * what changed is that results render through the shared card system, the header
 * no longer sits under the status bar, and each result states its distance once.
 */
@Composable
fun HospitalSearchScreen(
    hospitalDiscoveryService: HospitalDiscoveryService,
    onHospitalSelected: (Hospital) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var hospitals by remember { mutableStateOf<List<Hospital>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isTrackingLocation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        hospitalDiscoveryService.getCurrentLocation()
            .onSuccess { location ->
                currentLocation = location
                hospitalDiscoveryService.findNearbyHospitals(location)
                    .onSuccess { nearbyHospitals ->
                        hospitals = nearbyHospitals.sortedBy { it.distance }
                    }
                    .onFailure { exception ->
                        errorMessage = "Error finding hospitals: ${exception.message}"
                    }
            }
            .onFailure { exception ->
                errorMessage = "Error getting location: ${exception.message}"
                val defaultLocation = LatLng(12.9716, 77.5946) // Bangalore default
                currentLocation = defaultLocation
                hospitalDiscoveryService.findNearbyHospitals(defaultLocation)
                    .onSuccess { nearbyHospitals ->
                        hospitals = nearbyHospitals.sortedBy { it.distance }
                    }
            }
        isLoading = false
    }

    LaunchedEffect(isTrackingLocation) {
        if (isTrackingLocation && currentLocation != null) {
            kotlinx.coroutines.delay(3000)
            if (currentLocation != null) {
                hospitalDiscoveryService.findNearbyHospitals(currentLocation!!)
                    .onSuccess { updatedHospitals ->
                        hospitals = updatedHospitals.sortedBy { it.distance }
                    }
            }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            val location = currentLocation
            if (location != null) {
                kotlinx.coroutines.delay(500)
                hospitalDiscoveryService.searchHospitals(searchQuery, location)
                    .onSuccess { searchResults -> hospitals = searchResults }
            }
        } else if (searchQuery.isBlank()) {
            val location = currentLocation
            if (location != null) {
                hospitalDiscoveryService.findNearbyHospitals(location)
                    .onSuccess { nearbyHospitals ->
                        hospitals = nearbyHospitals.sortedBy { it.distance }
                    }
            }
        }
    }

    val location = currentLocation

    Box(
        Modifier
            .fillMaxSize()
            .background(CanvasGradient)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AppHeaderBar(
                accent = HospitalGreen,
                eyebrow = "Hospital discovery",
                title = "Nearby hospitals",
                subtitle = location?.let { "%.4f, %.4f".format(it.latitude, it.longitude) }
                    ?: "Locating device…",
                icon = Icons.Filled.LocalHospital,
                status = if (isTrackingLocation) "Tracking" else null,
                statusLive = isTrackingLocation,
                navigation = {
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        accent = HospitalGreen,
                        onClick = onBack
                    )
                }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Spacing.lg)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search hospitals by name", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HospitalGreen,
                        unfocusedBorderColor = Border,
                        focusedLabelColor = HospitalGreen,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = HospitalGreen,
                        focusedLeadingIconColor = HospitalGreen,
                        unfocusedLeadingIconColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(Modifier.height(Spacing.md))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = if (location != null) "Position locked" else "No position",
                        color = if (location != null) SuccessGreen else SecondaryAmber,
                        live = isTrackingLocation
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    LiveDot(
                        color = if (isTrackingLocation) SuccessGreen else TextMuted,
                        size = 6
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(
                        text = if (isTrackingLocation) "Live refresh every 3 s" else "Tap track to refresh",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(Spacing.md))

                ActionButton(
                    label = if (isTrackingLocation) "Stop tracking" else "Track my position",
                    onClick = { isTrackingLocation = !isTrackingLocation },
                    tone = if (isTrackingLocation) ActionTone.Tinted else ActionTone.Outline,
                    accent = if (isTrackingLocation) SuccessGreen else PoliceBlue,
                    leadingIcon = Icons.Filled.MyLocation,
                    enabled = location != null,
                    height = 46.dp
                )

                errorMessage?.let { error ->
                    Spacer(Modifier.height(Spacing.md))
                    InfoBanner(
                        text = error,
                        accent = PrimaryRed,
                        emphasized = true,
                        icon = Icons.Filled.SearchOff
                    )
                }

                SectionHeader(
                    title = "Results",
                    accent = HospitalGreen,
                    trailing = {
                        Text(
                            text = if (hospitals.isEmpty()) "None" else "${hospitals.size} found",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                )

                when {
                    isLoading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(Spacing.xxxl),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HospitalGreen, strokeWidth = 2.dp)
                    }

                    hospitals.isEmpty() -> EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "No hospitals found",
                        body = "Nothing matched this search. Try a shorter name or clear the field " +
                            "to list everything nearby.",
                        accent = SecondaryAmber
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(hospitals, key = { it.placeId.ifBlank { it.name } }) { hospital ->
                            HospitalListItem(
                                hospital = hospital,
                                onClick = { onHospitalSelected(hospital) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HospitalListItem(
    hospital: Hospital,
    onClick: () -> Unit
) {
    val hasDuration = hospital.duration.isNotBlank()

    SaptcsCard(
        accent = HospitalGreen,
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HospitalGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.LocalHospital,
                    contentDescription = null,
                    tint = HospitalGreen,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = hospital.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = hospital.address.ifBlank { "Address unavailable" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(text = formatDistance(hospital.distance), color = HospitalGreen)
            StatusPill(
                text = if (hasDuration) hospital.duration else "Calculating",
                color = if (hasDuration) SecondaryAmber else TextMuted
            )
            Spacer(Modifier.weight(1f))
            if (hospital.isOpen == true) {
                StatusPill(text = "Open", color = SuccessGreen)
            }
        }
    }
}
