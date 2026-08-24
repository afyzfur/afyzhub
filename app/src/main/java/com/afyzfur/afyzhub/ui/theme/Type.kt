package com.afyzfur.afyzhub.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 排版系统，覆盖 Material 3 全部 15 档样式。
 *
 * 字体决策：使用系统 sans-serif，不打包外部字体。
 *
 * 曾评估打包 Inter，实测其字符集仅 2849 个码位，不含任何 CJK 字形，
 * 连全角标点（，。）都缺失。本应用界面以中文为主，结果会是拉丁字符走 Inter、
 * 汉字回落系统字体，两者字面高度与基线不一致，中英混排出现高低错落。
 * 收益小于代价，故不引入。
 *
 * 中文界面的现代感主要来自行高、字重与字距的节奏，而非字形本身，
 * 因此下面三项是实际起作用的部分：
 *
 * 1. 行高普遍放宽到字号的 1.4–1.5 倍。中文字符是全方框，
 *    按拉丁文的 1.2 倍行高排会显得拥挤。
 * 2. 中文不使用 letterSpacing 正值。M3 默认值针对拉丁文设计，
 *    加在汉字上会让字与字散开。标题一律为 0 或负值。
 * 3. 关闭 includeFontPadding。这是 Android 遗留行为，
 *    会在文本上下额外加不可控留白，导致精确的垂直居中无法实现。
 */

private val AppFontFamily = FontFamily.SansSerif

/** 统一关闭 includeFontPadding，使行高值与实际占位一致 */
@Suppress("DEPRECATION")
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

/** 行高按字体中心对齐分配，中英混排时基线更稳 */
private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun appTextStyle(
    fontSize: Int,
    lineHeight: Int,
    fontWeight: FontWeight,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = AppFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    platformStyle = NoFontPadding,
    lineHeightStyle = CenteredLineHeight
)

val AppTypography = Typography(
    // Display —— 仅用于空会话首屏的问候语等大字场景
    displayLarge = appTextStyle(52, 64, FontWeight.Normal, -0.5),
    displayMedium = appTextStyle(42, 52, FontWeight.Normal, -0.25),
    displaySmall = appTextStyle(34, 44, FontWeight.Normal),

    // Headline —— 页面级标题。设置页「设置」标题用 headlineLarge
    headlineLarge = appTextStyle(30, 40, FontWeight.SemiBold, -0.25),
    headlineMedium = appTextStyle(26, 36, FontWeight.SemiBold),
    headlineSmall = appTextStyle(22, 30, FontWeight.Medium),

    // Title —— 顶栏会话名、列表项标题、对话框标题
    titleLarge = appTextStyle(20, 28, FontWeight.SemiBold),
    titleMedium = appTextStyle(16, 24, FontWeight.Medium),
    titleSmall = appTextStyle(14, 20, FontWeight.Medium),

    // Body —— 消息正文与设置项副标题。行高偏宽，长段中文更易读
    bodyLarge = appTextStyle(16, 26, FontWeight.Normal),
    bodyMedium = appTextStyle(14, 22, FontWeight.Normal),
    bodySmall = appTextStyle(13, 20, FontWeight.Normal),

    // Label —— 按钮、chip、元信息。字距保留极小正值以提升小字号可读性
    labelLarge = appTextStyle(14, 20, FontWeight.Medium, 0.1),
    labelMedium = appTextStyle(12, 16, FontWeight.Medium, 0.2),
    labelSmall = appTextStyle(11, 16, FontWeight.Medium, 0.2)
)
