package com.afyzfur.afyzhub.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 思考程度的图标：一个侧面的大脑轮廓。
 *
 * 从灯泡换过来的。灯泡的意思是"想到了"——一个瞬间的点子，
 * 而这个按钮控制的是"想多深"，是过程的深浅而非结果的有无。
 * 大脑更接近后者。
 *
 * 自绘而非用 material-icons-extended 的 Psychology：那个包有
 * 2MB，为一个图标引入不值得。
 *
 * 用描边而非填充：填充的大脑在小尺寸下会糊成一团黑块，
 * 描边能保留脑沟的走向，看得出画的是什么。
 */
val ThinkingBrain: ImageVector by lazy {
    ImageVector.Builder(
        name = "ThinkingBrain",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // 外轮廓：从脑干起笔绕一圈回到脑干
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13.5f, 21f)
            curveTo(13.5f, 19f, 15f, 18.5f, 16.5f, 17.5f)
            curveTo(18.5f, 16.2f, 19.5f, 14.5f, 19.5f, 12.5f)
            curveTo(19.5f, 8f, 16.5f, 4.5f, 12f, 4.5f)
            curveTo(7.5f, 4.5f, 4.5f, 8f, 4.5f, 12f)
            curveTo(4.5f, 14.5f, 5.5f, 16.5f, 7.5f, 17.5f)
            curveTo(9f, 18.3f, 10.5f, 19f, 10.5f, 21f)
        }
        // 中缝：把左右脑分开，是大脑最好认的特征
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 4.5f)
            lineTo(12f, 21f)
        }
        // 左右各一道脑沟暗示褶皱。只画两道：画满在小尺寸下会糊成实心
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(9f, 8f)
            curveTo(7.5f, 9f, 7.5f, 11f, 9f, 12f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(15f, 8f)
            curveTo(16.5f, 9f, 16.5f, 11f, 15f, 12f)
        }
    }.build()
}
