package com.afyzfur.afyzhub.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 灯泡图标，用于思考程度按钮。
 *
 * 自绘而非引入 material-icons-extended：那个包为了一个图标要多带
 * 约 2MB，而 core 包里没有灯泡（只有 49 个基础图标）。
 *
 * 路径取自 Material Symbols 的 lightbulb，24dp 视口。
 */
val ThinkingLightbulb: ImageVector by lazy {
    ImageVector.Builder(
        name = "ThinkingLightbulb",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.NonZero
        ) {
            // 灯泡外形：上半为圆弧，下接灯颈
            moveTo(12f, 2f)
            curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
            curveTo(5f, 11.38f, 6.19f, 13.47f, 8f, 14.74f)
            lineTo(8f, 17f)
            curveTo(8f, 17.55f, 8.45f, 18f, 9f, 18f)
            lineTo(15f, 18f)
            curveTo(15.55f, 18f, 16f, 17.55f, 16f, 17f)
            lineTo(16f, 14.74f)
            curveTo(17.81f, 13.47f, 19f, 11.38f, 19f, 9f)
            curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
            close()

            // 灯座的两道横条，表意"通电/在运转"
            moveTo(9f, 20f)
            lineTo(15f, 20f)
            curveTo(15.55f, 20f, 16f, 20.45f, 16f, 21f)
            curveTo(16f, 21.55f, 15.55f, 22f, 15f, 22f)
            lineTo(9f, 22f)
            curveTo(8.45f, 22f, 8f, 21.55f, 8f, 21f)
            curveTo(8f, 20.45f, 8.45f, 20f, 9f, 20f)
            close()
        }
    }.build()
}
