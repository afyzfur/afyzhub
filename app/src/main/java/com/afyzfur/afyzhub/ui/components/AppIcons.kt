package com.afyzfur.afyzhub.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘的补充图标。
 *
 * material-icons-core 只带 56 个图标，文件夹、标签、调色板这类
 * 常用的都不在其中。而 material-icons-extended 有 2MB，为十来个
 * 图标引入一整个包不合适——这个应用的 APK 现在总共才 3.2MB。
 *
 * 一律用描边风格并统一 24x24 视口与 1.8f 线宽，与内置图标混排时
 * 视觉重量才接近；填充与描边混用会让某几个图标显得格外重。
 */

/** 统一的线宽，改这里就能整体调整 */
private const val STROKE = 1.8f

private fun icon(
    name: String,
    block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply(block).build()

/** 分组：一个带页签的文件夹。 */
val IconFolder: ImageVector by lazy {
    icon("Folder") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 7f)
            lineTo(3f, 18.5f)
            curveTo(3f, 19.3f, 3.7f, 20f, 4.5f, 20f)
            lineTo(19.5f, 20f)
            curveTo(20.3f, 20f, 21f, 19.3f, 21f, 18.5f)
            lineTo(21f, 9.5f)
            curveTo(21f, 8.7f, 20.3f, 8f, 19.5f, 8f)
            lineTo(11.5f, 8f)
            lineTo(9.8f, 5.6f)
            curveTo(9.5f, 5.2f, 9.1f, 5f, 8.6f, 5f)
            lineTo(4.5f, 5f)
            curveTo(3.7f, 5f, 3f, 5.7f, 3f, 6.5f)
            close()
        }
    }
}

/** 外观 / 主题：调色板。 */
val IconPalette: ImageVector by lazy {
    icon("Palette") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 缺一口的圆，是调色板最好认的形状
            moveTo(12f, 3f)
            curveTo(7f, 3f, 3f, 7f, 3f, 12f)
            curveTo(3f, 17f, 7f, 21f, 12f, 21f)
            curveTo(13.4f, 21f, 14.5f, 19.9f, 14.5f, 18.5f)
            curveTo(14.5f, 17.8f, 14.2f, 17.2f, 13.8f, 16.8f)
            curveTo(13.4f, 16.3f, 13.2f, 15.8f, 13.2f, 15.2f)
            curveTo(13.2f, 13.9f, 14.3f, 12.8f, 15.6f, 12.8f)
            lineTo(18f, 12.8f)
            curveTo(19.7f, 12.8f, 21f, 11.4f, 21f, 9.8f)
            curveTo(21f, 6.1f, 17f, 3f, 12f, 3f)
            close()
        }
        // 三个颜料点
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.5f, 10.5f)
            curveTo(8.3f, 10.5f, 9f, 9.8f, 9f, 9f)
            curveTo(9f, 8.2f, 8.3f, 7.5f, 7.5f, 7.5f)
            curveTo(6.7f, 7.5f, 6f, 8.2f, 6f, 9f)
            curveTo(6f, 9.8f, 6.7f, 10.5f, 7.5f, 10.5f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 8.5f)
            curveTo(12.8f, 8.5f, 13.5f, 7.8f, 13.5f, 7f)
            curveTo(13.5f, 6.2f, 12.8f, 5.5f, 12f, 5.5f)
            curveTo(11.2f, 5.5f, 10.5f, 6.2f, 10.5f, 7f)
            curveTo(10.5f, 7.8f, 11.2f, 8.5f, 12f, 8.5f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 15.5f)
            curveTo(7.8f, 15.5f, 8.5f, 14.8f, 8.5f, 14f)
            curveTo(8.5f, 13.2f, 7.8f, 12.5f, 7f, 12.5f)
            curveTo(6.2f, 12.5f, 5.5f, 13.2f, 5.5f, 14f)
            curveTo(5.5f, 14.8f, 6.2f, 15.5f, 7f, 15.5f)
            close()
        }
    }
}

/** 聊天外观：对话气泡。 */
val IconChatBubble: ImageVector by lazy {
    icon("ChatBubble") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(21f, 12f)
            curveTo(21f, 16.4f, 17f, 20f, 12f, 20f)
            curveTo(10.9f, 20f, 9.8f, 19.8f, 8.9f, 19.5f)
            lineTo(4f, 21f)
            lineTo(5.5f, 16.8f)
            curveTo(4f, 15.5f, 3f, 13.9f, 3f, 12f)
            curveTo(3f, 7.6f, 7f, 4f, 12f, 4f)
            curveTo(17f, 4f, 21f, 7.6f, 21f, 12f)
            close()
        }
    }
}

/** 消息显示：一行行的文本。 */
val IconTextLines: ImageVector by lazy {
    icon("TextLines") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 7f); lineTo(20f, 7f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 12f); lineTo(20f, 12f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 17f); lineTo(14f, 17f)
        }
    }
}

/** 快捷提示词：一道闪电，表示"一下就发出去"。 */
val IconBolt: ImageVector by lazy {
    icon("Bolt") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13f, 3f)
            lineTo(5f, 13.5f)
            lineTo(11f, 13.5f)
            lineTo(10f, 21f)
            lineTo(18f, 10.5f)
            lineTo(12f, 10.5f)
            close()
        }
    }
}

/** 请求日志：带折角的文档。 */
val IconDocument: ImageVector by lazy {
    icon("Document") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 3f)
            lineTo(6.5f, 3f)
            curveTo(5.7f, 3f, 5f, 3.7f, 5f, 4.5f)
            lineTo(5f, 19.5f)
            curveTo(5f, 20.3f, 5.7f, 21f, 6.5f, 21f)
            lineTo(17.5f, 21f)
            curveTo(18.3f, 21f, 19f, 20.3f, 19f, 19.5f)
            lineTo(19f, 8f)
            close()
        }
        // 折角
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14f, 3f)
            lineTo(14f, 8f)
            lineTo(19f, 8f)
        }
    }
}

/** 更新日志：带指针的文档，强调"按时间记录"。 */
val IconHistory: ImageVector by lazy {
    icon("History") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 逆时针箭头暗示"回看过去"
            moveTo(3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(9.2f, 20.5f, 6.7f, 19.1f, 5.2f, 17f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3f, 13.5f)
            lineTo(3.5f, 11.5f)
            lineTo(5.5f, 12.5f)
        }
        // 指针
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 8f)
            lineTo(12f, 12.5f)
            lineTo(15.5f, 14.5f)
        }
    }
}

/** API 配置：钥匙。 */
val IconKey: ImageVector by lazy {
    icon("Key") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 环
            moveTo(8f, 5f)
            curveTo(10.5f, 5f, 12.5f, 7f, 12.5f, 9.5f)
            curveTo(12.5f, 12f, 10.5f, 14f, 8f, 14f)
            curveTo(5.5f, 14f, 3.5f, 12f, 3.5f, 9.5f)
            curveTo(3.5f, 7f, 5.5f, 5f, 8f, 5f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // 杆与齿
            moveTo(11.5f, 12f)
            lineTo(19f, 19.5f)
            moveTo(16.5f, 17f)
            lineTo(14.5f, 19f)
            moveTo(19f, 19.5f)
            lineTo(17f, 21.5f)
        }
    }
}
/** 时间戳：一个钟面。 */
val IconClock: ImageVector by lazy {
    icon("Clock") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 7.5f)
            lineTo(12f, 12.5f)
            lineTo(16f, 14.5f)
        }
    }
}

/** 操作按钮：三个竖排的点，与消息旁那排按钮的形态呼应。 */
val IconActions: ImageVector by lazy {
    icon("Actions") {
        // 一个圆角方框里放三个点：表示"这里有一组可点的东西"
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 4.5f)
            lineTo(19f, 4.5f)
            curveTo(19.8f, 4.5f, 20.5f, 5.2f, 20.5f, 6f)
            lineTo(20.5f, 18f)
            curveTo(20.5f, 18.8f, 19.8f, 19.5f, 19f, 19.5f)
            lineTo(5f, 19.5f)
            curveTo(4.2f, 19.5f, 3.5f, 18.8f, 3.5f, 18f)
            lineTo(3.5f, 6f)
            curveTo(3.5f, 5.2f, 4.2f, 4.5f, 5f, 4.5f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 13.2f)
            curveTo(8.7f, 13.2f, 9.2f, 12.7f, 9.2f, 12f)
            curveTo(9.2f, 11.3f, 8.7f, 10.8f, 8f, 10.8f)
            curveTo(7.3f, 10.8f, 6.8f, 11.3f, 6.8f, 12f)
            curveTo(6.8f, 12.7f, 7.3f, 13.2f, 8f, 13.2f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 13.2f)
            curveTo(12.7f, 13.2f, 13.2f, 12.7f, 13.2f, 12f)
            curveTo(13.2f, 11.3f, 12.7f, 10.8f, 12f, 10.8f)
            curveTo(11.3f, 10.8f, 10.8f, 11.3f, 10.8f, 12f)
            curveTo(10.8f, 12.7f, 11.3f, 13.2f, 12f, 13.2f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 13.2f)
            curveTo(16.7f, 13.2f, 17.2f, 12.7f, 17.2f, 12f)
            curveTo(17.2f, 11.3f, 16.7f, 10.8f, 16f, 10.8f)
            curveTo(15.3f, 10.8f, 14.8f, 11.3f, 14.8f, 12f)
            curveTo(14.8f, 12.7f, 15.3f, 13.2f, 16f, 13.2f)
            close()
        }
    }
}

/** 模型名称：一枚标签。 */
val IconTag: ImageVector by lazy {
    icon("Tag") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11.5f, 3.5f)
            lineTo(20f, 12f)
            curveTo(20.6f, 12.6f, 20.6f, 13.5f, 20f, 14.1f)
            lineTo(14.1f, 20f)
            curveTo(13.5f, 20.6f, 12.6f, 20.6f, 12f, 20f)
            lineTo(3.5f, 11.5f)
            lineTo(3.5f, 5f)
            curveTo(3.5f, 4.2f, 4.2f, 3.5f, 5f, 3.5f)
            close()
        }
        // 打孔
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.8f, 9f)
            curveTo(8.5f, 9f, 9f, 8.5f, 9f, 7.8f)
            curveTo(9f, 7.1f, 8.5f, 6.6f, 7.8f, 6.6f)
            curveTo(7.1f, 6.6f, 6.6f, 7.1f, 6.6f, 7.8f)
            curveTo(6.6f, 8.5f, 7.1f, 9f, 7.8f, 9f)
            close()
        }
    }
}

/** Token 用量：三根高低不同的柱子。 */
val IconBarChart: ImageVector by lazy {
    icon("BarChart") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(6f, 20f); lineTo(6f, 13f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 20f); lineTo(12f, 6f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(18f, 20f); lineTo(18f, 10f)
        }
    }
}

/** 生成速度：速度表。 */
val IconSpeed: ImageVector by lazy {
    icon("Speed") {
        // 半圆表盘
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(3.5f, 17f)
            curveTo(3.5f, 12.3f, 7.3f, 8.5f, 12f, 8.5f)
            curveTo(16.7f, 8.5f, 20.5f, 12.3f, 20.5f, 17f)
        }
        // 指针指向右上，表示"快"
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 17f)
            lineTo(16.5f, 12.5f)
        }
    }
}

/** 耗时：秒表。 */
val IconStopwatch: ImageVector by lazy {
    icon("Stopwatch") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 6.5f)
            curveTo(16.1f, 6.5f, 19.5f, 9.9f, 19.5f, 14f)
            curveTo(19.5f, 18.1f, 16.1f, 21.5f, 12f, 21.5f)
            curveTo(7.9f, 21.5f, 4.5f, 18.1f, 4.5f, 14f)
            curveTo(4.5f, 9.9f, 7.9f, 6.5f, 12f, 6.5f)
            close()
        }
        // 顶部的按钮与提梁，是秒表区别于时钟的标志
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(9.5f, 3f)
            lineTo(14.5f, 3f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 3f)
            lineTo(12f, 6.5f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(12f, 10.5f)
            lineTo(12f, 14f)
        }
    }
}
/**
 * 思考程度：气泡里的三点。
 *
 * 换掉了先前的大脑轮廓。大脑在 18dp 下线条太密，脑沟与中缝挤在
 * 一起认不出形状；而"气泡 + 省略号"是等待与斟酌的通用表意，
 * 笔画少、小尺寸下依然清楚，也和聊天场景本身贴合。
 */
val IconThinking: ImageVector by lazy {
    icon("Thinking") {
        // 圆角气泡，尾巴在左下
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6.5f, 4f)
            lineTo(17.5f, 4f)
            curveTo(18.9f, 4f, 20f, 5.1f, 20f, 6.5f)
            lineTo(20f, 13.5f)
            curveTo(20f, 14.9f, 18.9f, 16f, 17.5f, 16f)
            lineTo(9.5f, 16f)
            lineTo(5.5f, 20f)
            lineTo(5.5f, 16f)
            curveTo(4.7f, 15.7f, 4f, 14.7f, 4f, 13.5f)
            lineTo(4f, 6.5f)
            curveTo(4f, 5.1f, 5.1f, 4f, 6.5f, 4f)
            close()
        }
        // 三个点表示正在斟酌
        path(fill = SolidColor(Color.Black)) {
            moveTo(8.4f, 11.2f)
            curveTo(9.1f, 11.2f, 9.6f, 10.7f, 9.6f, 10f)
            curveTo(9.6f, 9.3f, 9.1f, 8.8f, 8.4f, 8.8f)
            curveTo(7.7f, 8.8f, 7.2f, 9.3f, 7.2f, 10f)
            curveTo(7.2f, 10.7f, 7.7f, 11.2f, 8.4f, 11.2f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 11.2f)
            curveTo(12.7f, 11.2f, 13.2f, 10.7f, 13.2f, 10f)
            curveTo(13.2f, 9.3f, 12.7f, 8.8f, 12f, 8.8f)
            curveTo(11.3f, 8.8f, 10.8f, 9.3f, 10.8f, 10f)
            curveTo(10.8f, 10.7f, 11.3f, 11.2f, 12f, 11.2f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.6f, 11.2f)
            curveTo(16.3f, 11.2f, 16.8f, 10.7f, 16.8f, 10f)
            curveTo(16.8f, 9.3f, 16.3f, 8.8f, 15.6f, 8.8f)
            curveTo(14.9f, 8.8f, 14.4f, 9.3f, 14.4f, 10f)
            curveTo(14.4f, 10.7f, 14.9f, 11.2f, 15.6f, 11.2f)
            close()
        }
    }
}
/**
 * 颜色模式：半明半暗的圆。
 *
 * 左半实心右半空心，一眼看出是"明暗"这件事。比月亮或太阳更中性——
 * 那两个各自偏向某一种模式，而这一项是三态循环（跟随系统在内）。
 */
val IconContrast: ImageVector by lazy {
    icon("Contrast") {
        // 外圈
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            close()
        }
        // 左半填充
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4.8f)
            lineTo(12f, 19.2f)
            curveTo(8f, 19.2f, 4.8f, 16f, 4.8f, 12f)
            curveTo(4.8f, 8f, 8f, 4.8f, 12f, 4.8f)
            close()
        }
    }
}

/** 动态取色：两个交叠的圆，表示颜色互相影响。 */
val IconColorSwatch: ImageVector by lazy {
    icon("ColorSwatch") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9.5f, 5f)
            curveTo(12f, 5f, 14f, 7f, 14f, 9.5f)
            curveTo(14f, 12f, 12f, 14f, 9.5f, 14f)
            curveTo(7f, 14f, 5f, 12f, 5f, 9.5f)
            curveTo(5f, 7f, 7f, 5f, 9.5f, 5f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.5f, 10f)
            curveTo(17f, 10f, 19f, 12f, 19f, 14.5f)
            curveTo(19f, 17f, 17f, 19f, 14.5f, 19f)
            curveTo(12f, 19f, 10f, 17f, 10f, 14.5f)
            curveTo(10f, 12f, 12f, 10f, 14.5f, 10f)
            close()
        }
    }
}
