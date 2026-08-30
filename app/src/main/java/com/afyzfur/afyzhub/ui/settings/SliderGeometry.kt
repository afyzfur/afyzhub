package com.afyzfur.afyzhub.ui.settings

/**
 * 滑块轨道的坐标换算。
 *
 * 单独成文件而非留在 SliderTrack.kt 里：那个文件含 @Composable，
 * 本地测试环境在 Compose 代码上生成不出字节码，测试类会加载不到。
 * 端点换算是这里唯一容易错的地方，值得单独测。
 */

/**
 * 把触点横坐标换算成 0..1 的比例。
 *
 * 两端各扣掉 thumb 半径，与绘制时的端点保持一致；否则手指移到最左
 * 也到不了 0%，因为绘制的起点在 thumb 半径处。
 */
internal fun positionToFraction(x: Float, width: Float): Float {
    val left = THUMB_RADIUS
    val right = width - THUMB_RADIUS
    val span = right - left
    if (span <= 0f) return 0f
    return ((x - left) / span).coerceIn(0f, 1f)
}



/** thumb 半径（像素），比 Material 默认略大以便看清位置 */
internal const val THUMB_RADIUS = 16f
