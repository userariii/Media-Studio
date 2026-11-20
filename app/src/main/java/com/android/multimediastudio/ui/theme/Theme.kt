package com.android.multimediastudio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.android.multimediastudio.data.model.AppThemeColor

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = OnPurple80,
    primaryContainer = PurpleContainer80,
    onPrimaryContainer = OnPurpleContainer80,
    secondary = PurpleGrey80,
    onSecondary = OnPurpleGrey80,
    secondaryContainer = PurpleGreyContainer80,
    onSecondaryContainer = OnPurpleGreyContainer80,
    tertiary = Pink80,
    onTertiary = OnPink80,
    tertiaryContainer = PinkContainer80,
    onTertiaryContainer = OnPinkContainer80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = OnPurple40,
    primaryContainer = PurpleContainer40,
    onPrimaryContainer = OnPurpleContainer40,
    secondary = PurpleGrey40,
    onSecondary = OnPurpleGrey40,
    secondaryContainer = PurpleGreyContainer40,
    onSecondaryContainer = OnPurpleGreyContainer40,
    tertiary = Pink40,
    onTertiary = OnPink40,
    tertiaryContainer = PinkContainer40,
    onTertiaryContainer = OnPinkContainer40,
)

private val OceanDarkColorScheme = darkColorScheme(
    primary = OceanBlue80,
    onPrimary = OnOceanBlue80,
    primaryContainer = OceanBlueContainer80,
    onPrimaryContainer = OnOceanBlueContainer80,
    secondary = OceanBlueGrey80,
    onSecondary = OnOceanBlueGrey80,
    secondaryContainer = OceanBlueGreyContainer80,
    onSecondaryContainer = OnOceanBlueGreyContainer80,
    tertiary = OceanPink80,
    onTertiary = OnOceanPink80,
    tertiaryContainer = OceanPinkContainer80,
    onTertiaryContainer = OnOceanPinkContainer80,
)

private val OceanLightColorScheme = lightColorScheme(
    primary = OceanBlue40,
    onPrimary = OnOceanBlue40,
    primaryContainer = OceanBlueContainer40,
    onPrimaryContainer = OnOceanBlueContainer40,
    secondary = OceanBlueGrey40,
    onSecondary = OnOceanBlueGrey40,
    secondaryContainer = OceanBlueGreyContainer40,
    onSecondaryContainer = OnOceanBlueGreyContainer40,
    tertiary = OceanPink40,
    onTertiary = OnOceanPink40,
    tertiaryContainer = OceanPinkContainer40,
    onTertiaryContainer = OnOceanPinkContainer40,
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = ForestGreen80,
    onPrimary = OnForestGreen80,
    primaryContainer = ForestGreenContainer80,
    onPrimaryContainer = OnForestGreenContainer80,
    secondary = ForestGreenGrey80,
    onSecondary = OnForestGreenGrey80,
    secondaryContainer = ForestGreenGreyContainer80,
    onSecondaryContainer = OnForestGreenGreyContainer80,
    tertiary = ForestPink80,
    onTertiary = OnForestPink80,
    tertiaryContainer = ForestPinkContainer80,
    onTertiaryContainer = OnForestPinkContainer80,
)

private val ForestLightColorScheme = lightColorScheme(
    primary = ForestGreen40,
    onPrimary = OnForestGreen40,
    primaryContainer = ForestGreenContainer40,
    onPrimaryContainer = OnForestGreenContainer40,
    secondary = ForestGreenGrey40,
    onSecondary = OnForestGreenGrey40,
    secondaryContainer = ForestGreenGreyContainer40,
    onSecondaryContainer = OnForestGreenGreyContainer40,
    tertiary = ForestPink40,
    onTertiary = OnForestPink40,
    tertiaryContainer = ForestPinkContainer40,
    onTertiaryContainer = OnForestPinkContainer40,
)

@Composable
fun MultimediaStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appThemeColor: AppThemeColor = AppThemeColor.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (appThemeColor) {
        AppThemeColor.OCEAN -> if (darkTheme) OceanDarkColorScheme else OceanLightColorScheme
        AppThemeColor.FOREST -> if (darkTheme) ForestDarkColorScheme else ForestLightColorScheme
        AppThemeColor.DEFAULT -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
