package com.fanta.androidsport.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// State holder for the active application theme
object ThemeManager {
    val themeState = mutableStateOf("forest")
}

// Forest/Classique Color Schemes
private val ForestLightColorScheme = lightColorScheme(
    primary = ForestGreenDark,
    onPrimary = ForestWhite,
    primaryContainer = ForestGreenLight,
    onPrimaryContainer = ForestGreenDark,
    secondary = ForestGreenDark,
    onSecondary = ForestWhite,
    secondaryContainer = ForestGreenLight,
    onSecondaryContainer = ForestGreenDark,
    background = ForestWhite,
    onBackground = ForestDark,
    surface = ForestWhite,
    onSurface = ForestDark
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = ForestGreenDark,
    primaryContainer = ForestGreenDark,
    onPrimaryContainer = ForestGreenLight,
    secondary = ForestGreenLight,
    onSecondary = ForestGreenDark,
    secondaryContainer = ForestGreenDark,
    onSecondaryContainer = ForestGreenLight,
    background = Color(0xFF152219),
    onBackground = ForestWhite,
    surface = Color(0xFF1E2F24),
    onSurface = ForestWhite
)

// Orchid Color Schemes
private val OrchidLightColorScheme = lightColorScheme(
    primary = OrchidMedium,
    onPrimary = OrchidWhite,
    primaryContainer = OrchidLight,
    onPrimaryContainer = OrchidDark,
    secondary = OrchidMedium,
    onSecondary = OrchidWhite,
    secondaryContainer = OrchidLight,
    onSecondaryContainer = OrchidDark,
    background = OrchidWhite,
    onBackground = OrchidDark,
    surface = OrchidWhite,
    onSurface = OrchidDark
)

private val OrchidDarkColorScheme = darkColorScheme(
    primary = OrchidLight,
    onPrimary = OrchidDark,
    primaryContainer = OrchidDark,
    onPrimaryContainer = OrchidLight,
    secondary = OrchidLight,
    onSecondary = OrchidDark,
    secondaryContainer = OrchidDark,
    onSecondaryContainer = OrchidLight,
    background = Color(0xFF2D162C),
    onBackground = OrchidWhite,
    surface = Color(0xFF3F203D),
    onSurface = OrchidWhite
)

// Blue Sky Color Schemes
private val BlueSkyLightColorScheme = lightColorScheme(
    primary = BlueSkyMediumDark,
    onPrimary = BlueSkyWhite,
    primaryContainer = BlueSkyLight,
    onPrimaryContainer = BlueSkyDark,
    secondary = BlueSkyMediumDark,
    onSecondary = BlueSkyWhite,
    secondaryContainer = BlueSkyLight,
    onSecondaryContainer = BlueSkyDark,
    background = BlueSkyWhite,
    onBackground = BlueSkyDark,
    surface = BlueSkyWhite,
    onSurface = BlueSkyDark
)

private val BlueSkyDarkColorScheme = darkColorScheme(
    primary = BlueSkyLight,
    onPrimary = BlueSkyDark,
    primaryContainer = BlueSkyDark,
    onPrimaryContainer = BlueSkyLight,
    secondary = BlueSkyLight,
    onSecondary = BlueSkyDark,
    secondaryContainer = BlueSkyDark,
    onSecondaryContainer = BlueSkyLight,
    background = Color(0xFF0F1E24),
    onBackground = BlueSkyWhite,
    surface = Color(0xFF192F38),
    onSurface = BlueSkyWhite
)

// Fallback / original color schemes
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight
)

@Composable
fun SportAndroidTheme(
    theme: String = ThemeManager.themeState.value,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (only used as fallback)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        "forest" -> if (darkTheme) ForestDarkColorScheme else ForestLightColorScheme
        "orchid" -> if (darkTheme) OrchidDarkColorScheme else OrchidLightColorScheme
        "blue_sky" -> if (darkTheme) BlueSkyDarkColorScheme else BlueSkyLightColorScheme
        else -> {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val context = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val BrandGreen: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary
