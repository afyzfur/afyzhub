package com.afyzfur.afyzhub.ui.settings

import kotlin.math.abs

/**
 * 裁剪框的抓取判定。
 *
 * 单独成文件而非留在 ImageCropDialog.kt 里：那个文件含 @Composable，
 * 本地测试环境在 Compose 代码上生成不出字节码，测试类因此加载不到。
 * 判定逻辑本身是纯函数，分出来就能测——而它恰恰是最需要测的部分，
 * 判定顺序写反会让角落永远抓不到。
 */

/** 本次手势抓住的部位。internal 以便单元测试访问判定逻辑 */
internal enum class Grab {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    INSIDE
}

/**
 * 判定归一化触点抓住了哪个部位。
 *
 * 先判角再判边：角落同时靠近两条边，若先判边就永远抓不到角。
 * 落在框外或中间则视作整体平移。
 *
 * [TOUCH_SLOP] 是归一化的容差。用比例而非固定 dp：预览框的尺寸
 * 随图片比例变化，固定 dp 在窄框上会大到几乎覆盖整个框。
 */
internal fun grabOf(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float
): Grab {
    val nearLeft = abs(x - left) < TOUCH_SLOP
    val nearRight = abs(x - right) < TOUCH_SLOP
    val nearTop = abs(y - top) < TOUCH_SLOP
    val nearBottom = abs(y - bottom) < TOUCH_SLOP

    return when {
        nearLeft && nearTop -> Grab.TOP_LEFT
        nearRight && nearTop -> Grab.TOP_RIGHT
        nearLeft && nearBottom -> Grab.BOTTOM_LEFT
        nearRight && nearBottom -> Grab.BOTTOM_RIGHT
        nearLeft -> Grab.LEFT
        nearRight -> Grab.RIGHT
        nearTop -> Grab.TOP
        nearBottom -> Grab.BOTTOM
        else -> Grab.INSIDE
    }
}

/** 抓边的归一化容差 */
internal const val TOUCH_SLOP = 0.08f
