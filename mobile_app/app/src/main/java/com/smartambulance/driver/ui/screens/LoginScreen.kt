package com.smartambulance.driver.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.data.SaptcsRepository
import com.smartambulance.driver.ui.components.design.ActionButton
import com.smartambulance.driver.ui.components.design.Eyebrow
import com.smartambulance.driver.ui.components.design.InfoBanner
import com.smartambulance.driver.ui.components.design.LabelledField
import com.smartambulance.driver.ui.components.design.RowDivider
import com.smartambulance.driver.ui.components.design.SaptcsCard
import com.smartambulance.driver.ui.components.design.SectionHeader
import com.smartambulance.driver.ui.theme.AdminAmber
import com.smartambulance.driver.ui.theme.Border
import com.smartambulance.driver.ui.theme.CanvasGradient
import com.smartambulance.driver.ui.theme.CardBackground
import com.smartambulance.driver.ui.theme.DriverRed
import com.smartambulance.driver.ui.theme.HospitalGreen
import com.smartambulance.driver.ui.theme.PoliceBlue
import com.smartambulance.driver.ui.theme.PrimaryRed
import com.smartambulance.driver.ui.theme.SecondaryAmber
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.SuccessGreen
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary
import com.smartambulance.driver.ui.theme.headerGradient

private data class RoleHint(
    val role: String,
    val operatorId: String,
    val pin: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * Role shortcuts. Tapping one fills the operator ID, so the only thing an
 * operator has to type is their four-digit PIN. The PIN shown on the tile is
 * revealed only in offline mode, where it unlocks the canned dataset.
 */
private val roleHints = listOf(
    RoleHint("Driver", "driver_001", "1111", Icons.Filled.DirectionsCar, DriverRed),
    RoleHint("Police", "police_001", "2222", Icons.Filled.Security, PoliceBlue),
    RoleHint("Hospital", "hospital_001", "3333", Icons.Filled.LocalHospital, HospitalGreen),
    RoleHint("Admin", "admin_001", "0000", Icons.Filled.Shield, AdminAmber)
)

@Composable
fun LoginScreen(
    userId: String,
    pin: String,
    message: String,
    loading: Boolean,
    offline: Boolean,
    onOfflineChange: (Boolean) -> Unit,
    onUserId: (String) -> Unit,
    onPin: (String) -> Unit,
    onFill: (String, String) -> Unit,
    onLogin: () -> Unit
) {
    val isHint = message.isBlank() || message.startsWith("Sign in with your operator credentials")
    val showMessage = !isHint
    val messageIsSuccess = message.contains("Signed in", ignoreCase = true) ||
        message.contains("demo mode", ignoreCase = true)

    Box(
        Modifier
            .fillMaxSize()
            .background(CanvasGradient)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(headerGradient(PrimaryRed))
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
        ) {
            Spacer(Modifier.height(Spacing.xxl))

            BrandLockup()

            Spacer(Modifier.height(Spacing.xl))

            OfflineToggle(offline = offline, onChange = onOfflineChange)

            Spacer(Modifier.height(Spacing.md))

            SaptcsCard(
                accent = PrimaryRed,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.xl)
            ) {
                Text(
                    text = "Operator sign in",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = if (offline) {
                        "Offline demo credentials: any role below, with the PIN shown."
                    } else {
                        "Tap a role below, then enter its four-digit PIN. Accounts are " +
                            "authenticated by Firebase, not by the app."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )

                Spacer(Modifier.height(Spacing.xl))

                LabelledField(
                    value = userId,
                    onValueChange = onUserId,
                    label = if (offline) "Operator ID" else "Operator ID or email",
                    leadingIcon = Icons.Filled.Person,
                    enabled = !loading
                )

                Spacer(Modifier.height(Spacing.md))

                LabelledField(
                    value = pin,
                    onValueChange = onPin,
                    label = "4-digit PIN",
                    leadingIcon = Icons.Filled.Lock,
                    masked = true,
                    numeric = true,
                    isError = showMessage && !messageIsSuccess,
                    enabled = !loading
                )

                if (!offline) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Bare IDs sign in as <id>@${SaptcsRepository.OPERATOR_EMAIL_DOMAIN}. " +
                            "Type a full address instead if your account uses a different domain.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                if (showMessage) {
                    Spacer(Modifier.height(Spacing.lg))
                    InfoBanner(
                        text = message,
                        accent = if (messageIsSuccess) SuccessGreen else PrimaryRed,
                        emphasized = true,
                        icon = if (messageIsSuccess) Icons.Filled.Shield else Icons.Filled.WarningAmber
                    )
                }

                Spacer(Modifier.height(Spacing.xl))

                ActionButton(
                    label = if (offline) "Open offline demo" else "Sign in",
                    onClick = onLogin,
                    loading = loading,
                    accent = if (offline) SecondaryAmber else PrimaryRed,
                    leadingIcon = Icons.AutoMirrored.Filled.Login,
                    height = 54.dp
                )

                RowDivider()

                Text(
                    text = "Your PIN is verified by Firebase Authentication and is never stored " +
                        "by this app. There is no self-registration.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(Modifier.height(Spacing.xxl))

            SectionHeader(title = if (offline) "Demo roles" else "Role shortcuts", accent = PrimaryRed)
            Text(
                text = if (offline) {
                    "Tap a role to fill its demo ID and PIN."
                } else {
                    "Tap a role to fill its operator ID, then enter your own PIN."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(Modifier.height(Spacing.md))

            roleHints.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    row.forEach { hint ->
                        RoleAccessTile(
                            hint = hint,
                            showPin = offline,
                            selected = userId == hint.operatorId,
                            onClick = { onFill(hint.operatorId, if (offline) hint.pin else "") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.md))
            }

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = "SAPTCS · priority corridor control · driver, police, hospital and admin roles",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun OfflineToggle(offline: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (offline) SecondaryAmber.copy(alpha = 0.12f) else CardBackground)
            .border(
                1.dp,
                if (offline) SecondaryAmber.copy(alpha = 0.6f) else Border,
                MaterialTheme.shapes.large
            )
            .clickable { onChange(!offline) }
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.WifiOff,
            contentDescription = null,
            tint = if (offline) SecondaryAmber else TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Offline demo mode",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = if (offline) {
                    "Canned sample data. Nothing is read from or written to Firebase, and every " +
                        "screen is badged."
                } else {
                    "For demonstrations without Firebase or hardware."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(
            checked = offline,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SecondaryAmber,
                checkedTrackColor = SecondaryAmber.copy(alpha = 0.3f),
                checkedBorderColor = SecondaryAmber.copy(alpha = 0.6f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardBackground,
                uncheckedBorderColor = Border
            )
        )
    }
}

@Composable
private fun BrandLockup() {
    Column(horizontalAlignment = Alignment.Start) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PrimaryRed.copy(alpha = 0.14f))
                .border(1.dp, PrimaryRed.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Emergency,
                contentDescription = null,
                tint = PrimaryRed,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(Spacing.lg))

        Eyebrow("Priority traffic control system", PrimaryRed)

        Spacer(Modifier.height(Spacing.xs))

        Text(
            text = "Smart Ambulance",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = "Green-corridor control for ambulance units, junction police, " +
                "receiving hospitals and system administrators.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

@Composable
private fun RoleAccessTile(
    hint: RoleHint,
    showPin: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) hint.accent.copy(alpha = 0.14f) else CardBackground)
            .border(
                1.dp,
                if (selected) hint.accent.copy(alpha = 0.85f) else Border,
                MaterialTheme.shapes.large
            )
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(hint.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(hint.icon, contentDescription = null, tint = hint.accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = hint.role,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = hint.operatorId,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showPin) {
            Text(
                text = "PIN ${hint.pin}",
                style = MaterialTheme.typography.labelSmall,
                color = hint.accent
            )
        }
    }
}
