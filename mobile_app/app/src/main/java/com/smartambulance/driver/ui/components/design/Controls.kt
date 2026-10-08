package com.smartambulance.driver.ui.components.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartambulance.driver.ui.theme.Background
import com.smartambulance.driver.ui.theme.Border
import com.smartambulance.driver.ui.theme.CardBackground
import com.smartambulance.driver.ui.theme.ElevatedCard
import com.smartambulance.driver.ui.theme.EmergencyGradient
import com.smartambulance.driver.ui.theme.EyebrowText
import com.smartambulance.driver.ui.theme.OnAccent
import com.smartambulance.driver.ui.theme.PrimaryRed
import com.smartambulance.driver.ui.theme.Spacing
import com.smartambulance.driver.ui.theme.SuccessGreen
import com.smartambulance.driver.ui.theme.TextMuted
import com.smartambulance.driver.ui.theme.TextPrimary

/** Visual weight of an action; maps to a fill/border pair rather than a size. */
enum class ActionTone { Solid, Tinted, Outline, Success }

/**
 * Full-width action button. One component covers the sign-in, refresh and
 * confirm actions so their heights, radii and pressed states never drift.
 */
@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Solid,
    accent: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 52.dp
) {
    val container: Color
    val content: Color
    val stroke: Color?
    when (tone) {
        ActionTone.Solid -> {
            container = accent
            content = OnAccent
            stroke = null
        }
        ActionTone.Tinted -> {
            container = accent.copy(alpha = 0.14f)
            content = accent
            stroke = accent.copy(alpha = 0.45f)
        }
        ActionTone.Outline -> {
            container = Color.Transparent
            content = TextPrimary
            stroke = Border
        }
        ActionTone.Success -> {
            container = SuccessGreen
            content = Background
            stroke = null
        }
    }

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(if (stroke != null) Modifier.border(1.dp, stroke, MaterialTheme.shapes.medium) else Modifier),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.6f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = content,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(Spacing.md))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
            Spacer(Modifier.width(Spacing.md))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The driver's mission control. Idle it is the loudest thing on screen (brand
 * gradient plus a slow halo); armed it recedes to an outlined stop affordance so
 * it can never be hit by accident.
 */
@Composable
fun EmergencyActionButton(
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    idleLabel: String = "Start emergency",
    activeLabel: String = "End emergency"
) {
    val shape = MaterialTheme.shapes.large
    val halo by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "haloAlpha"
    )

    val fill by animateColorAsState(
        targetValue = if (active) CardBackground else PrimaryRed,
        animationSpec = tween(240),
        label = "ctaFill"
    )

    Box(modifier.fillMaxWidth()) {
        if (!active && enabled) {
            // Expanding ring that draws the eye to the primary action.
            Box(
                Modifier
                    .matchParentSize()
                    .scale(1f + (1f - halo) * 0.03f)
                    .clip(shape)
                    .border(2.dp, PrimaryRed.copy(alpha = halo), shape)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(shape)
                .then(
                    if (active) Modifier
                        .background(fill)
                        .border(1.5.dp, PrimaryRed.copy(alpha = 0.55f), shape)
                    else Modifier.background(EmergencyGradient)
                )
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Cancel else Icons.Filled.Emergency,
                contentDescription = null,
                tint = if (active) PrimaryRed else OnAccent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = if (active) activeLabel else idleLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (active) PrimaryRed else OnAccent,
                maxLines = 1
            )
        }
    }
}

/**
 * Segmented tab control. A single rounded track with an animated accent pill
 * behind the active segment reads as one component instead of three buttons.
 */
@Composable
fun SegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            val container by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                animationSpec = tween(180),
                label = "segFill"
            )
            val labelColor by animateColorAsState(
                targetValue = if (selected) TextPrimary else TextMuted,
                animationSpec = tween(180),
                label = "segLabel"
            )
            val source = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(container)
                    .then(
                        if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                        else Modifier
                    )
                    .clickable(interactionSource = source, indication = null) { onSelect(index) }
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Selectable option card used for severity, hospital and role pickers. The
 * selected state changes container, border weight and a leading indicator so
 * the choice is unambiguous without relying on colour alone.
 */
@Composable
fun SelectableCard(
    title: String,
    subtitle: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    SaptcsCard(
        modifier = modifier,
        accent = accent,
        selected = selected,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.lg,
            vertical = Spacing.md
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (selected) 0.22f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(leadingIcon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(Spacing.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(Spacing.sm))
                trailing()
            } else if (selected) {
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                }
            }
        }
    }
}

/**
 * Login-style field with a consistent focused/hovered treatment. Errors are
 * surfaced through the supporting text slot instead of a separate banner.
 */
@Composable
fun LabelledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isError: Boolean = false,
    masked: Boolean = false,
    numeric: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = when {
                numeric -> KeyboardType.NumberPassword
                else -> KeyboardType.Text
            }
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Border,
            errorBorderColor = PrimaryRed,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = TextMuted,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = TextMuted,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = ElevatedCard.copy(alpha = 0.4f),
            unfocusedContainerColor = CardBackground.copy(alpha = 0.4f)
        )
    )
}

/** Uppercase eyebrow with generous tracking, for headers and brand lockups. */
@Composable
fun Eyebrow(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = EyebrowText,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Slow opacity pulse used on live badges, tuned to avoid strobing. */
@Composable
fun BreathingAlpha(min: Float = 0.4f, max: Float = 1f, periodMillis: Int = 1600): Float {
    val transition = rememberInfiniteTransition(label = "breathing")
    val alpha by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )
    return alpha
}

/**
 * Horizontally scrolling pill tabs for sections that do not fit one row.
 * Used where a segmented control would squeeze its labels below legibility.
 */
@Composable
fun ScrollableTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        contentPadding = PaddingValues(vertical = Spacing.xxs)
    ) {
        itemsIndexed(tabs) { index, title ->
            val selected = index == selectedIndex
            val container by animateColorAsState(
                targetValue = if (selected) accent.copy(alpha = 0.16f) else CardBackground,
                animationSpec = tween(180),
                label = "pillFill"
            )
            val labelColor by animateColorAsState(
                targetValue = if (selected) accent else TextMuted,
                animationSpec = tween(180),
                label = "pillLabel"
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(container)
                    .border(
                        1.dp,
                        if (selected) accent.copy(alpha = 0.5f) else Border,
                        CircleShape
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = labelColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
