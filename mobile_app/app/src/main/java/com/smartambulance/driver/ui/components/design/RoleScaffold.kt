package com.smartambulance.driver.ui.components.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.ui.theme.Border
import com.smartambulance.driver.ui.theme.CanvasGradient
import com.smartambulance.driver.ui.theme.DataText
import com.smartambulance.driver.ui.theme.SecondaryAmber
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary
import com.smartambulance.driver.ui.theme.headerGradient

/**
 * Identity header shared by every role dashboard.
 *
 * The accent wash is painted behind the system bars while the content below is
 * inset-padded, so the colour bleeds under the status bar but no text or control
 * is ever overlapped by it.
 */
@Composable
fun AppHeaderBar(
    accent: Color,
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusLive: Boolean = false,
    onLogout: (() -> Unit)? = null,
    navigation: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigation != null) {
            navigation()
            Spacer(Modifier.size(Spacing.sm))
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.size(Spacing.md))

        Column(Modifier.weight(1f)) {
            Eyebrow(eyebrow, accent)
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = DataText,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (status != null) {
            Spacer(Modifier.size(Spacing.sm))
            StatusPill(text = status, color = accent, live = statusLive)
        }

        if (trailing != null) {
            Spacer(Modifier.size(Spacing.xs))
            trailing()
        }

        if (onLogout != null) {
            Spacer(Modifier.size(Spacing.xs))
            IconButton(
                onClick = onLogout,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(TextPrimary.copy(alpha = 0.05f))
                    .border(1.dp, Border, RoundedCornerShape(11.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign out",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Square icon button matching the header's visual weight. */
@Composable
fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(18.dp))
    }
}

/**
 * Shell for the driver, police and hospital dashboards.
 *
 * Owns the canvas, the header, the tab control and the tab host so all three
 * roles are pixel-identical in chrome and none of them can drift. Tab bodies keep
 * their own scrolling; the host hands them a bounded, inset-padded box.
 */
@Composable
fun RoleScaffold(
    accent: Color,
    eyebrow: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusLive: Boolean = false,
    onLogout: (() -> Unit)? = null,
    demoMode: Boolean = false,
    headerTrailing: (@Composable () -> Unit)? = null,
    tabContent: @Composable (Int) -> Unit
) {
    Box(
        modifier
            .fillMaxSize()
            .background(CanvasGradient)
    ) {
        // Accent wash, drawn full-bleed so it continues under the status bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(headerGradient(accent))
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            AppHeaderBar(
                accent = accent,
                eyebrow = eyebrow,
                title = title,
                subtitle = subtitle,
                icon = icon,
                status = status,
                statusLive = statusLive,
                onLogout = onLogout,
                trailing = headerTrailing
            )

            if (demoMode) {
                // The one place the app admits its numbers are not measurements.
                Box(Modifier.padding(horizontal = Spacing.lg)) {
                    InfoBanner(
                        text = "Offline demo · these values are canned sample data, not readings " +
                            "from any device",
                        accent = SecondaryAmber,
                        emphasized = true,
                        icon = Icons.Filled.WarningAmber
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }

            if (tabs.isNotEmpty()) {
                SegmentedTabs(
                    tabs = tabs,
                    selectedIndex = selectedTab,
                    onSelect = onTabSelected,
                    accent = accent,
                    modifier = Modifier.padding(horizontal = Spacing.lg)
                )
            }

            Spacer(Modifier.height(Spacing.md))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "tabContent"
                ) { index ->
                    Box(Modifier.fillMaxSize()) {
                        tabContent(index)
                    }
                }
            }
        }
    }
}

/**
 * Standard tab body: vertically scrollable, full-size, and already inside the
 * scaffold's horizontal insets. Keeps every tab's scroll behaviour identical.
 */
@Composable
fun TabBody(
    modifier: Modifier = Modifier,
    spacing: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}
