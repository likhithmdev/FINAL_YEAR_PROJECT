package com.smartambulance.driver.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// Colour
// ---------------------------------------------------------------------------

// Layered dark surfaces. Each step is a deliberate elevation level so cards can
// stack without borders doing all the work.
val Background = Color(0xFF070B12)      // App canvas
val CardBackground = Color(0xFF0F1623)  // Level 1 surface (cards)
val ElevatedCard = Color(0xFF162030)    // Level 2 surface (selected / raised)
val SurfaceHigh = Color(0xFF1D293D)     // Level 3 surface (menus, chips)
val Muted = Color(0xFF1E2940)           // Inert fill
val Border = Color(0xFF1E2D42)          // Hairline border
val BorderStrong = Color(0xFF2A3D57)    // Emphasised border

// Text ramp
val TextPrimary = Color(0xFFF0F4FF)
val TextMuted = Color(0xFF8C9AB5)
val TextDim = Color(0xFF55658A)

// Semantic accents
val PrimaryRed = Color(0xFFEF233C)      // Emergency / critical
val SecondaryAmber = Color(0xFFF59E0B)  // Caution / secondary
val SuccessGreen = Color(0xFF10B981)    // Healthy / ready
val InfoBlue = Color(0xFF3B82F6)        // Informational
val OnAccent = Color(0xFFFFFFFF)

// Tinted containers for badges and banners, derived from the accents.
val DangerContainer = Color(0xFF33121B)
val WarningContainer = Color(0xFF33260D)
val SuccessContainer = Color(0xFF0C2D25)
val InfoContainer = Color(0xFF11213D)

// Role accents
val DriverRed = PrimaryRed
val PoliceBlue = InfoBlue
val HospitalGreen = SuccessGreen
val AdminAmber = SecondaryAmber

// Legacy aliases (kept so existing call sites keep compiling)
val Crimson = PrimaryRed
val Teal = SuccessGreen
val Amber = SecondaryAmber
val Blue = InfoBlue
val Ink = Background
val Panel = CardBackground
val PanelAlt = ElevatedCard
val Paper = TextPrimary
val Mute = TextMuted

private val Scheme = darkColorScheme(
    primary = PrimaryRed,
    onPrimary = OnAccent,
    primaryContainer = DangerContainer,
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = SecondaryAmber,
    onSecondary = Background,
    secondaryContainer = WarningContainer,
    onSecondaryContainer = Color(0xFFFFE6BE),
    tertiary = SuccessGreen,
    onTertiary = Background,
    tertiaryContainer = SuccessContainer,
    onTertiaryContainer = Color(0xFFB6F3E2),
    background = Background,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextMuted,
    surfaceContainer = CardBackground,
    surfaceContainerHigh = ElevatedCard,
    surfaceContainerHighest = SurfaceHigh,
    outline = Border,
    outlineVariant = BorderStrong,
    error = PrimaryRed,
    onError = OnAccent,
    errorContainer = DangerContainer,
    onErrorContainer = Color(0xFFFFD9DE),
    scrim = Color(0xCC060A11)
)

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

/** Tabular figures keep stacked telemetry columns aligned. */
private const val TABULAR = "tnum"

private val Type = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1).sp,
        fontFeatureSettings = TABULAR,
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = TABULAR,
        color = TextPrimary
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = TABULAR,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        color = TextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
        color = TextMuted
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.2.sp,
        color = TextDim
    )
)

/** Monospace style for identifiers, coordinates and raw telemetry readings. */
val DataText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.5.sp,
    lineHeight = 17.sp,
    fontFeatureSettings = TABULAR,
    color = TextPrimary
)

/** Small all-caps key used above values and in section eyebrows. */
val EyebrowText = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    letterSpacing = 1.4.sp,
    color = TextMuted
)

// ---------------------------------------------------------------------------
// Shape & spacing
// ---------------------------------------------------------------------------

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Single source of truth for padding and gaps, in device pixels. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val xxxl = 40.dp
}

/** Elevation levels, kept low: depth comes from surface tint, not shadow. */
object Elevation {
    val flat = 0.dp
    val raised = 2.dp
    val overlay = 6.dp
}

// ---------------------------------------------------------------------------
// Gradients
// ---------------------------------------------------------------------------

/** App canvas: a soft vertical wash so flat screens do not read as empty. */
val CanvasGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0A101C), Background, Color(0xFF060910))
)

/** Header wash used by role dashboards, tinted with the role accent. */
fun headerGradient(accent: Color) = Brush.linearGradient(
    colors = listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.06f), Color.Transparent)
)

/** Primary call-to-action fill. */
val EmergencyGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFF4354C), Color(0xFFC51127))
)

/** Resolves the accent for a role key coming from the Firebase user record. */
fun roleAccent(role: String?): Color = when (role) {
    "ambulance_driver" -> DriverRed
    "police" -> PoliceBlue
    "hospital" -> HospitalGreen
    "admin" -> AdminAmber
    else -> PrimaryRed
}

@Composable
fun SmartAmbulanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Type,
        shapes = Shapes,
        content = content
    )
}
