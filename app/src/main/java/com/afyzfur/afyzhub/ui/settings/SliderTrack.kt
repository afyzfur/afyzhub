package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 百分比滑块的轨道。
 *
 * 自绘而不用 Material3 的 Slider，为的是触摸区。Slider 的可交互高度
 * 固定在 48dp，而且必须先压住 thumb 才能拖动——细轨道上要瞄准那个小圆
 * 点，很容易按空。
 *
 * 这里做两件事把它变好拖：
 *  - 整块区域高 [TOUCH_HEIGHT]，比 Slider 高出一截，纵向不用瞄
 *  - 按下即跳到指点位置，横向也不用瞄。这比单纯加大 thumb 有效得多，
 *    因为用户根本不需要先找到 thumb 在哪
 *
 * 手势用 detectDragGestures 而非 Slider 的内部状态，值全程由调用方的
 * 本地状态持有，不经过磁盘往返。
 */
@Composable
internal fun SliderTrack(
    fraction: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOUCH_HEIGHT)
            // 单击（不拖动）也要生效：轻点轨道某处应当直接跳过去，
            // 只装 detectDragGestures 的话点一下没有任何反应
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onDragStart()
                    onDrag(positionToFraction(pos.x, size.width.toFloat()))
                    onDragEnd()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        onDragStart()
                        // 按下立刻跳到指点位置，不必先压住 thumb
                        onDrag(positionToFraction(pos.x, size.width.toFloat()))
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, _ ->
                        // 用绝对位置而非累加位移：累加会因每帧取整
                        // 积累误差，长距离拖动后 thumb 与手指错开
                        onDrag(positionToFraction(change.position.x, size.width.toFloat()))
                    }
                )
            }
    ) {
        val w = size.width
        val cy = size.height / 2f
        val trackH = TRACK_THICKNESS
        val r = fraction.coerceIn(0f, 1f)
        // thumb 半径参与端点计算：轨道两端各留出半径，thumb 在 0% 和
        // 100% 时才不会有一半探到轨道外面
        val thumbR = THUMB_RADIUS
        val left = thumbR
        val right = w - thumbR
        val span = (right - left).coerceAtLeast(0f)
        val cx = left + span * r

        // 未选中段
        drawLine(
            color = inactive,
            start = Offset(left, cy),
            end = Offset(right, cy),
            strokeWidth = trackH,
            cap = StrokeCap.Round
        )
        // 已选中段
        if (cx > left) {
            drawLine(
                color = active,
                start = Offset(left, cy),
                end = Offset(cx, cy),
                strokeWidth = trackH,
                cap = StrokeCap.Round
            )
        }
        // thumb
        drawCircle(color = active, radius = thumbR, center = Offset(cx, cy))
    }
}

/**
 * 整块可点区域的高度。
 *
 * 比 Material 的 48dp 更高。滑块所在的设置行本身有纵向内边距，多出来
 * 的高度不会挤到相邻项。
 */
private val TOUCH_HEIGHT = 56.dp

/** 轨道线宽（像素） */
private const val TRACK_THICKNESS = 10f
