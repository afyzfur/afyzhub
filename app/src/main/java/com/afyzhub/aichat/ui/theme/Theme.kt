package com.afyzhub.aichat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * 由单个种子色生成一套协调的 Material 3 配色方案。
 * 通过对种子色做明度/色相偏移，推导各角色色，避免引入额外配色库。
 */
private fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    fun adjust(color: Color, factor: Float): Color {
        // factor > 1 变亮，< 1 变暗
        return Color(
            red = (color.red * factor).coerceIn(0f, 1f),
            green = (color.green * factor).coerceIn(0f, 1f),
            blue = (color.blue * factor).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    val onPrimary = if (seed.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White

    return if (dark) {
        val primary = adjust(seed, 1.15f)
        darkColorScheme(
            primary = primary,
            onPrimary = if (primary.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White,
            primaryContainer = adjust(seed, 0.55f),
            onPrimaryContainer = Color.White,
            secondary = adjust(seed, 0.9f),
            secondaryContainer = adjust(seed, 0.45f),
            onSecondaryContainer = Color.White,
            tertiary = adjust(seed, 1.05f),
            background = Color(0xFF121212),
            onBackground = Color(0xFFECECEC),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFECECEC),
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFDDDDDD)
        )
    } else {
        lightColorScheme(
            primary = seed,
            onPrimary = onPrimary,
            primaryContainer = adjust(seed, 1.35f),
            onPrimaryContainer = adjust(seed, 0.4f),
            secondary = adjust(seed, 0.9f),
            secondaryContainer = adjust(seed, 1.45f),
            onSecondaryContainer = adjust(seed, 0.35f),
            tertiary = adjust(seed, 0.8f),
            background = Color(0xFFFDFBFF),
            onBackground = Color(0xFF1A1A1A),
            surface = Color(0xFFFDFBFF),
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFF2ECEC),
            onSurfaceVariant = Color(0xFF4A4544)
        )
    }
}

@Composable
fun afyzhubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /** 自定义种子色（ARGB），非空时优先于动态取色。 */
    seedColor: Long? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        seedColor != null -> schemeFromSeed(Color(seedColor.toInt()), darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // edge-to-edge 下状态栏保持透明，让内容/背景延伸到状态栏后
            window.statusBarColor = Color.Transparent.toArgb()
            // 状态栏图标明暗跟随背景亮度（浅色背景用深色图标）
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.background.luminance() > 0.5f
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
