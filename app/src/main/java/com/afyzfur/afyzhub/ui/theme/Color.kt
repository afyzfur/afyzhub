package com.afyzfur.afyzhub.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 静态色板，用于 Android 12 以下设备（无 Material You 动态取色）。
 *
 * 种子色为品牌橙 #FCA43C（取自应用图标）。色板按 Material 3 的色调调色板规则展开，
 * primary 系为橙色主色调，secondary 系降低彩度作为次要容器，tertiary 系取互补的青蓝
 * 用于强调点缀。
 *
 * 关键点：surface 系列必须提供完整的 surfaceContainer 五档色阶，界面层次全靠它建立，
 * 不使用阴影。浅色档位彼此差值刻意做小（约 2–4 点亮度），差值过大会显脏。
 */

// ---- 品牌种子色 ----

/** 品牌色，取自应用图标本体 */
val BrandOrange = Color(0xFFFCA43C)

// ---- 浅色主题 ----

val LightPrimary = Color(0xFF8B5000)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFDCBE)
val LightOnPrimaryContainer = Color(0xFF2D1600)

val LightSecondary = Color(0xFF725A42)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFFDDBB)
val LightOnSecondaryContainer = Color(0xFF281806)

val LightTertiary = Color(0xFF13656F)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFB0EBF6)
val LightOnTertiaryContainer = Color(0xFF001F24)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

/** 页面最底层背景。非纯白，带极轻的暖色相倾向 */
val LightBackground = Color(0xFFFFF8F4)
val LightOnBackground = Color(0xFF211A14)
val LightSurface = Color(0xFFFFF8F4)
val LightOnSurface = Color(0xFF211A14)
val LightSurfaceVariant = Color(0xFFF2DFD1)
val LightOnSurfaceVariant = Color(0xFF51443A)

// surface 容器色阶，由低到高。层次感的来源
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFF2E7)
val LightSurfaceContainer = Color(0xFFFCECE1)
val LightSurfaceContainerHigh = Color(0xFFF6E6DB)
val LightSurfaceContainerHighest = Color(0xFFF0E0D6)

val LightOutline = Color(0xFF837469)
val LightOutlineVariant = Color(0xFFD5C3B6)
val LightInverseSurface = Color(0xFF372F28)
val LightInverseOnSurface = Color(0xFFFDEEE4)
val LightInversePrimary = Color(0xFFFFB870)
val LightScrim = Color(0xFF000000)

// ---- 深色主题 ----
// 手动调校，不由 lightColorScheme 反推。背景不使用纯黑，
// 纯黑在 OLED 上与内容对比过硬，且会失去层次表达空间。

val DarkPrimary = Color(0xFFFFB870)
val DarkOnPrimary = Color(0xFF4A2800)
val DarkPrimaryContainer = Color(0xFF693C00)
val DarkOnPrimaryContainer = Color(0xFFFFDCBE)

val DarkSecondary = Color(0xFFE1C1A4)
val DarkOnSecondary = Color(0xFF402C18)
val DarkSecondaryContainer = Color(0xFF58422C)
val DarkOnSecondaryContainer = Color(0xFFFFDDBB)

val DarkTertiary = Color(0xFF94CFDA)
val DarkOnTertiary = Color(0xFF00363C)
val DarkTertiaryContainer = Color(0xFF004E56)
val DarkOnTertiaryContainer = Color(0xFFB0EBF6)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

/** 深灰而非纯黑，带轻微暖色相以呼应品牌色 */
val DarkBackground = Color(0xFF19120C)
val DarkOnBackground = Color(0xFFEDE0D4)
val DarkSurface = Color(0xFF19120C)
val DarkOnSurface = Color(0xFFEDE0D4)
val DarkSurfaceVariant = Color(0xFF51443A)
val DarkOnSurfaceVariant = Color(0xFFD5C3B6)

// 深色下的容器色阶。逐档提亮，与浅色主题方向相反
val DarkSurfaceContainerLowest = Color(0xFF130C07)
val DarkSurfaceContainerLow = Color(0xFF211A14)
val DarkSurfaceContainer = Color(0xFF261E17)
val DarkSurfaceContainerHigh = Color(0xFF312921)
val DarkSurfaceContainerHighest = Color(0xFF3D332B)

val DarkOutline = Color(0xFF9E8E81)
val DarkOutlineVariant = Color(0xFF51443A)
val DarkInverseSurface = Color(0xFFEDE0D4)
val DarkInverseOnSurface = Color(0xFF372F28)
val DarkInversePrimary = Color(0xFF8B5000)
val DarkScrim = Color(0xFF000000)
