package com.afyzfur.afyzhub.ui.settings

import kotlin.math.abs

/**
 * 裁剪框的抓取判定与拖动计算。
 *
 * 单独成文件而非留在 ImageCropDialog.kt 里：那个文件含 @Composable，
 * 本地测试环境在 Compose 代码上生成不出字节码，测试类因此加载不到。
 * 这里全是纯函数，分出来就能测——手势的正确性只有这部分测得了，
 * 剩下的（渲染、触摸区实际大小）得在真机上看。
 */

/** 本次手势抓住的部位。internal 以便单元测试访问判定逻辑 */
internal enum class Grab {
    LEFT, RIGHT, TOP, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    INSIDE
}

/** 裁剪范围，四个值都是相对图片的比例 */
internal data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * 判定归一化触点抓住了哪个部位。
 *
 * 先判角再判边：角落同时靠近两条边，若先判边就永远抓不到角。
 * 都不靠近则视作整体平移。
 */
internal fun grabOf(x: Float, y: Float, rect: CropRect): Grab {
    val nearLeft = abs(x - rect.left) < TOUCH_SLOP
    val nearRight = abs(x - rect.right) < TOUCH_SLOP
    val nearTop = abs(y - rect.top) < TOUCH_SLOP
    val nearBottom = abs(y - rect.bottom) < TOUCH_SLOP

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

/**
 * 施加一次拖动位移，返回新的裁剪范围。
 *
 * [dx]、[dy] 已归一化。各边收缩时留出 [MIN_SIZE] 的最小边长，
 * 平移时整体在 0..1 内滑动、碰到边界停住而不改变尺寸——若平移也用
 * 逐边夹取，贴边时框会被压扁。
 */
internal fun applyDrag(rect: CropRect, grab: Grab, dx: Float, dy: Float): CropRect =
    when (grab) {
        Grab.LEFT -> rect.copy(
            left = (rect.left + dx).coerceIn(0f, rect.right - MIN_SIZE)
        )
        Grab.RIGHT -> rect.copy(
            right = (rect.right + dx).coerceIn(rect.left + MIN_SIZE, 1f)
        )
        Grab.TOP -> rect.copy(
            top = (rect.top + dy).coerceIn(0f, rect.bottom - MIN_SIZE)
        )
        Grab.BOTTOM -> rect.copy(
            bottom = (rect.bottom + dy).coerceIn(rect.top + MIN_SIZE, 1f)
        )
        Grab.TOP_LEFT -> rect.copy(
            left = (rect.left + dx).coerceIn(0f, rect.right - MIN_SIZE),
            top = (rect.top + dy).coerceIn(0f, rect.bottom - MIN_SIZE)
        )
        Grab.TOP_RIGHT -> rect.copy(
            right = (rect.right + dx).coerceIn(rect.left + MIN_SIZE, 1f),
            top = (rect.top + dy).coerceIn(0f, rect.bottom - MIN_SIZE)
        )
        Grab.BOTTOM_LEFT -> rect.copy(
            left = (rect.left + dx).coerceIn(0f, rect.right - MIN_SIZE),
            bottom = (rect.bottom + dy).coerceIn(rect.top + MIN_SIZE, 1f)
        )
        Grab.BOTTOM_RIGHT -> rect.copy(
            right = (rect.right + dx).coerceIn(rect.left + MIN_SIZE, 1f),
            bottom = (rect.bottom + dy).coerceIn(rect.top + MIN_SIZE, 1f)
        )
        Grab.INSIDE -> {
            val w = rect.width
            val h = rect.height
            // 上界可能小于 0（框已满宽/满高），coerceIn 会因 min > max
            // 抛异常，所以先把上界抬到不小于 0
            val nl = (rect.left + dx).coerceIn(0f, (1f - w).coerceAtLeast(0f))
            val nt = (rect.top + dy).coerceIn(0f, (1f - h).coerceAtLeast(0f))
            CropRect(nl, nt, nl + w, nt + h)
        }
    }

/**
 * 打开裁剪时的初始范围。
 *
 * 不用整张图。满框时四条边正好压在预览框的边界上，触摸区一半落在
 * 组件之外；而框内平移的可动空间是 0，落在中间的第一次拖动完全没有
 * 效果，用起来就像"要拖第二次才有反应"。留出内缩后这两个问题都没了，
 * 也顺带提示了"这个框是可以调的"。
 */
internal fun initialCrop(): CropRect =
    CropRect(INITIAL_INSET, INITIAL_INSET, 1f - INITIAL_INSET, 1f - INITIAL_INSET)

/** 抓边的归一化容差 */
internal const val TOUCH_SLOP = 0.08f

/** 裁剪框的最小边长（归一化），防止收成一条线 */
internal const val MIN_SIZE = 0.1f

/** 初始范围四周的内缩量 */
internal const val INITIAL_INSET = 0.06f
