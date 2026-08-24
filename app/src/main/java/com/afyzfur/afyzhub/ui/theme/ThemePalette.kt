package com.afyzfur.afyzhub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * 可选的主题配色。
 *
 * 每套配色只需一个色相偏移量，色板由基准橙色色板旋转色相派生。
 *
 * 为什么不为每套手写完整色板：一套 Material 3 色板有 40 余个颜色值，
 * 手写六套近 250 个十六进制数，既易错也无法保证各套之间的明度与彩度一致。
 * 色相旋转保留了基准色板已调校好的明度层次与容器色阶差值，
 * 只改变色相，因此各套配色在深浅、对比度上表现一致。
 *
 * 为什么不用 material-color-utilities：那是 Material You 的官方生成库，
 * 但需要额外依赖，而它解决的问题（从任意种子色生成符合规范的色板）
 * 在这里被简化为「保持结构、只转色相」，旋转足够。
 */
enum class ThemePalette(
    val id: String,
    val label: String,
    /** 相对基准橙色的色相偏移（度）。0 即基准色板本身 */
    private val hueShift: Float,
    /** 该配色在设置页的代表色，用于色块预览 */
    val swatch: Color
) {
    ORANGE("orange", "品牌橙", 0f, Color(0xFFFCA43C)),
    BLUE("blue", "静蓝", 185f, Color(0xFF3C9CFC)),
    GREEN("green", "松绿", 105f, Color(0xFF4CAF50)),
    PURPLE("purple", "藤紫", 240f, Color(0xFF9C7CF4)),
    ROSE("rose", "玫红", 310f, Color(0xFFF45C8C)),
    GRAPHITE("graphite", "石墨", 0f, Color(0xFF7A7A7A)) {
        // 石墨是唯一的特例：它需要去饱和而非转色相，
        // 因此覆盖生成逻辑而不是给一个无意义的色相值
        override fun light(): ColorScheme = LightColors.desaturate()
        override fun dark(): ColorScheme = DarkColors.desaturate()
    };

    open fun light(): ColorScheme = LightColors.shiftHue(hueShift)
    open fun dark(): ColorScheme = DarkColors.shiftHue(hueShift)

    companion object {
        val DEFAULT = ORANGE

        fun fromId(id: String?): ThemePalette =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 旋转整套色板的色相。
 *
 * error 系不参与旋转——错误状态必须始终是红色，这是跨应用的通用约定，
 * 把它转成绿色会造成误解。
 */
private fun ColorScheme.shiftHue(degrees: Float): ColorScheme {
    if (degrees == 0f) return this
    fun Color.s() = shiftColorHue(this, degrees)
    return copy(
        primary = primary.s(),
        onPrimary = onPrimary.s(),
        primaryContainer = primaryContainer.s(),
        onPrimaryContainer = onPrimaryContainer.s(),
        inversePrimary = inversePrimary.s(),
        secondary = secondary.s(),
        onSecondary = onSecondary.s(),
        secondaryContainer = secondaryContainer.s(),
        onSecondaryContainer = onSecondaryContainer.s(),
        tertiary = tertiary.s(),
        onTertiary = onTertiary.s(),
        tertiaryContainer = tertiaryContainer.s(),
        onTertiaryContainer = onTertiaryContainer.s(),
        background = background.s(),
        onBackground = onBackground.s(),
        surface = surface.s(),
        onSurface = onSurface.s(),
        surfaceVariant = surfaceVariant.s(),
        onSurfaceVariant = onSurfaceVariant.s(),
        surfaceContainerLowest = surfaceContainerLowest.s(),
        surfaceContainerLow = surfaceContainerLow.s(),
        surfaceContainer = surfaceContainer.s(),
        surfaceContainerHigh = surfaceContainerHigh.s(),
        surfaceContainerHighest = surfaceContainerHighest.s(),
        outline = outline.s(),
        outlineVariant = outlineVariant.s(),
        inverseSurface = inverseSurface.s(),
        inverseOnSurface = inverseOnSurface.s()
    )
}

/** 去饱和，用于石墨配色。error 系同样保留原色。 */
private fun ColorScheme.desaturate(): ColorScheme {
    fun Color.d() = desaturateColor(this)
    return copy(
        primary = primary.d(),
        onPrimary = onPrimary.d(),
        primaryContainer = primaryContainer.d(),
        onPrimaryContainer = onPrimaryContainer.d(),
        inversePrimary = inversePrimary.d(),
        secondary = secondary.d(),
        onSecondary = onSecondary.d(),
        secondaryContainer = secondaryContainer.d(),
        onSecondaryContainer = onSecondaryContainer.d(),
        tertiary = tertiary.d(),
        onTertiary = onTertiary.d(),
        tertiaryContainer = tertiaryContainer.d(),
        onTertiaryContainer = onTertiaryContainer.d(),
        background = background.d(),
        onBackground = onBackground.d(),
        surface = surface.d(),
        onSurface = onSurface.d(),
        surfaceVariant = surfaceVariant.d(),
        onSurfaceVariant = onSurfaceVariant.d(),
        surfaceContainerLowest = surfaceContainerLowest.d(),
        surfaceContainerLow = surfaceContainerLow.d(),
        surfaceContainer = surfaceContainer.d(),
        surfaceContainerHigh = surfaceContainerHigh.d(),
        surfaceContainerHighest = surfaceContainerHighest.d(),
        outline = outline.d(),
        outlineVariant = outlineVariant.d(),
        inverseSurface = inverseSurface.d(),
        inverseOnSurface = inverseOnSurface.d()
    )
}

/**
 * 在 HSL 空间旋转单个颜色的色相，保持明度与饱和度。
 *
 * 近乎无彩的颜色（饱和度极低）跳过处理：纯白、纯黑及接近它们的
 * 中性色旋转色相不会有视觉变化，但浮点运算会引入微小偏差。
 */
private fun shiftColorHue(color: Color, degrees: Float): Color {
    val hsl = rgbToHsl(color)
    if (hsl[1] < 0.02f) return color
    val newHue = (hsl[0] + degrees).mod(360f)
    return hslToRgb(newHue, hsl[1], hsl[2], color.alpha)
}

/** 保留少量彩度而非完全归零：纯灰界面显得死板 */
private fun desaturateColor(color: Color): Color {
    val hsl = rgbToHsl(color)
    if (hsl[1] < 0.02f) return color
    return hslToRgb(hsl[0], hsl[1] * 0.12f, hsl[2], color.alpha)
}

/** 返回 [hue(0..360), saturation(0..1), lightness(0..1)] */
private fun rgbToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f

    if (delta == 0f) return floatArrayOf(0f, 0f, l)

    val s = if (l > 0.5f) delta / (2f - max - min) else delta / (max + min)
    val h = when (max) {
        r -> ((g - b) / delta + if (g < b) 6f else 0f)
        g -> ((b - r) / delta + 2f)
        else -> ((r - g) / delta + 4f)
    } * 60f

    return floatArrayOf(h, s, l)
}

private fun hslToRgb(h: Float, s: Float, l: Float, alpha: Float): Color {
    if (s == 0f) return Color(l, l, l, alpha)

    val c = (1f - abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - abs(hp.mod(2f) - 1f))
    val m = l - c / 2f

    val (r1, g1, b1) = when (hp.toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}
