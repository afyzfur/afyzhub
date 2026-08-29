package com.afyzfur.afyzhub.ui.components

import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.roundToInt

/**
 * 通过降采样再放大实现的模糊。
 *
 * 为什么不用 Compose 的 `Modifier.blur`：那个要求 API 31+，而本应用
 * minSdk 是 26，低版本上会静默不生效——用户改了滑块却看不到变化。
 *
 * 为什么不用高斯卷积：真正的高斯模糊在大图上很慢，而这里的用途是
 * 背景与头像，只需要"看不清细节"这个效果，不要求数学上正确。把图
 * 缩到很小再拉回原尺寸，硬件双线性插值会自然抹平高频细节，成本几乎
 * 只有一次缩放。代价是极大半径下会看出块状边界，所以下采样倍率
 * 有上限。
 *
 * 模糊在解码阶段一次完成并进缓存，不像 Modifier.blur 那样每帧重算。
 *
 * [radius] 取 0..1，0 表示不处理。
 */
class BlurTransformation(private val radius: Float) : Transformation {

    /**
     * 缓存键必须带上半径。
     *
     * 少了它，改半径后 Coil 会命中上一次的缓存，滑块拖动没有反应——
     * 而这恰恰是最容易被当成"模糊功能坏了"的表现。
     */
    override val cacheKey: String = "${javaClass.name}#$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val r = radius.coerceIn(0f, 1f)
        if (r <= 0.01f) return input

        // 下采样倍率：1 倍即原图，越大越模糊。上限 24 是块状感开始
        // 明显的地方，再高只是变糊而不再变"柔"
        val factor = 1f + r * 23f
        val w = (input.width / factor).roundToInt().coerceAtLeast(1)
        val h = (input.height / factor).roundToInt().coerceAtLeast(1)

        // filter = true 让缩小与放大都做插值，是模糊感的来源；
        // 关掉它会得到马赛克而非模糊
        val small = input.scale(w, h, filter = true)
        val blurred = small.scale(input.width, input.height, filter = true)

        // 只回收确实由本函数新建的中间产物。createScaledBitmap 在目标
        // 尺寸与源一致时会原样返回入参，此时 small 就是 input——而
        // input 归 Coil 所有并可能仍在缓存中被引用，回收它会让后续
        // 绘制拿到已释放的 bitmap 而崩溃
        if (small !== input && small !== blurred) small.recycle()
        return blurred
    }

    override fun equals(other: Any?): Boolean =
        other is BlurTransformation && other.radius == radius

    override fun hashCode(): Int = radius.hashCode()
}
