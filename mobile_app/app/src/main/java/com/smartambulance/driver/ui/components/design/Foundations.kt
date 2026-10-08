package com.smartambulance.driver.ui.components.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.ui.theme.Border
import com.smartambulance.driver.ui.theme.BorderStrong
import com.smartambulance.driver.ui.theme.CardBackground
import com.smartambulance.driver.ui.theme.DataText
import com.smartambulance.driver.ui.theme.ElevatedCard
import com.smartambulance.driver.ui.theme.EyebrowText
import com.smartambulance.driver.ui.theme.PulseAnimation
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary

/**
 * The single card primitive used across every role dashboard.
 *
 * Depth comes from one step of surface elevation plus a hairline border; an
 * accent tints that border and the leading edge stripe. `selected` promotes the
 * surface one more step so the active choice reads instantly at arm's length.
 */
@Composable
fun SaptcsCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val container by animateColorAsState(
        targetValue = if (selected) ElevatedCard else CardBackground,
        animationSpec = tween(180),
        label = "cardContainer"
    )
    val stroke by animateColorAsState(
        targetValue = when {
            selected && accent != null -> accent.copy(alpha = 0.9f)
            accent != null -> accent.copy(alpha = 0.24f)
            else -> Border
        },
        animationSpec = tween(180),
        label = "cardStroke"
    )

    val surface = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(container)
        .border(if (selected) 1.5.dp else 1.dp, stroke, shape)

    Row(
        modifier = (if (onClick != null) surface.clickable(onClick = onClick) else surface),
        verticalAlignment = Alignment.Top
    ) {
        // Leading accent stripe: a two-pixel signature that survives density
        // changes and keeps the card edge crisp at any corner radius.
        if (accent != null && selected) {
            Box(
                Modifier
                    .padding(vertical = Spacing.md)
                    .width(3.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/** All-caps eyebrow with an accent tick, used to open every section. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(title.uppercase(), style = EyebrowText, color = TextMuted)
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * Label on the left, value on the right. Weights (rather than wrap-content)
 * keep long hospital names and coordinate pairs from pushing the row off screen.
 */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    mono: Boolean = false,
    maxLines: Int = 2
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = value,
            style = if (mono) DataText else MaterialTheme.typography.titleSmall,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.3f)
        )
    }
}

/** Thin rule used to separate rows inside a single card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .height(1.dp)
            .background(BorderStrong.copy(alpha = 0.5f))
    )
}

/**
 * Numeric readout tile: eyebrow, large tabular figure, optional caption and
 * icon. Sized so three fit across a phone without truncating the label.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(CardBackground)
            .border(1.dp, accent.copy(alpha = 0.22f), MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.md, vertical = Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = EyebrowText,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (caption != null) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Compact status chip. `live` adds a pulsing dot for streaming states. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    live: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live) {
            LiveDot(color = color)
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = text.uppercase(),
            style = EyebrowText,
            color = color,
            maxLines = 1
        )
    }
}

/** Small solid dot with an expanding halo; the app's "data is flowing" cue. */
@Composable
fun LiveDot(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Int = 7
) {
    Box(modifier.size((size * 2).dp), contentAlignment = Alignment.Center) {
        PulseAnimation(color = color, size = size * 2)
        Box(
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** Neutral placeholder for empty lists and unpopulated telemetry. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Color = TextMuted
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(CardBackground)
            .border(1.dp, Border, MaterialTheme.shapes.large)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** Inline banner for status copy that arrives as a single string. */
@Composable
fun InfoBanner(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    icon: ImageVector? = null
) {
    val alpha by animateFloatAsState(
        targetValue = if (emphasized) 0.18f else 0.08f,
        animationSpec = tween(220),
        label = "bannerAlpha"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(accent.copy(alpha = alpha))
            .border(1.dp, accent.copy(alpha = if (emphasized) 0.6f else 0.3f), MaterialTheme.shapes.medium)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.Top
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.md))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasized) TextPrimary else TextMuted
        )
    }
}

/** Vertical rhythm helper so stacked sections keep a constant gap. */
@Composable
fun StackSpacer(height: androidx.compose.ui.unit.Dp = Spacing.md) {
    Spacer(Modifier.height(height))
}

/** Equal-width row of tiles; every child shares the width evenly. */
@Composable
fun TileRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = Spacing.sm,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Top,
        content = content
    )
}
