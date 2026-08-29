package com.afyzfur.afyzhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * 模糊的降采样尺寸计算。
 *
 * BlurTransformation 本身依赖 android.graphics.Bitmap，在纯 JVM 下
 * 跑不了。但它出错的地方不在 Bitmap 调用，而在尺寸算术——算出 0 或
 * 负数会让 createScaledBitmap 直接抛异常。这里把那段算术单独验证。
 *
 * 公式与实现保持一致，改一处要同时改另一处。
 */
class BlurRadiusTest {

    /** 与 BlurTransformation.transform 里的计算相同 */
    private fun downscaled(width: Int, height: Int, radius: Float): Pair<Int, Int> {
        // 与实现同为平方曲线：低段平缓，避免 20% 就糊到看不出画面
        val r = radius.coerceIn(0f, 1f)
        val factor = 1f + r * r * 11f
        val w = (width / factor).roundToInt().coerceAtLeast(1)
        val h = (height / factor).roundToInt().coerceAtLeast(1)
        return w to h
    }

    @Test
    fun `任何半径下尺寸都至少为一像素`() {
        // 小图配最大半径是最容易算出 0 的组合
        for (r in listOf(0f, 0.1f, 0.5f, 1f)) {
            val (w, h) = downscaled(8, 8, r)
            assertTrue("radius=$r 得到 ${w}x$h", w >= 1 && h >= 1)
        }
    }

    @Test
    fun `极小图不会退化为零`() {
        val (w, h) = downscaled(1, 1, 1f)
        assertEquals(1, w)
        assertEquals(1, h)
    }

    @Test
    fun `半径越大缩得越小`() {
        val (wLow, _) = downscaled(1000, 1000, 0.2f)
        val (wHigh, _) = downscaled(1000, 1000, 0.9f)
        assertTrue("高半径应缩得更小：$wHigh vs $wLow", wHigh < wLow)
    }

    @Test
    fun `半径为零时尺寸不变`() {
        // 实现里会在 r <= 0.01 时直接返回原图，这里验证公式本身
        // 在 r=0 时也是恒等的，两处逻辑不会互相矛盾
        val (w, h) = downscaled(640, 480, 0f)
        assertEquals(640, w)
        assertEquals(480, h)
    }

    @Test
    fun `超出范围的半径被夹住而非放大图片`() {
        // 传进越界值时不应算出比原图更大的尺寸——那会白占内存
        val (w, h) = downscaled(400, 300, 5f)
        assertTrue(w <= 400 && h <= 300)
        assertTrue(w >= 1 && h >= 1)
    }

    @Test
    fun `低强度只做轻微柔化`() {
        // 20% 时若缩得太狠，滑块后段就没有可用区间了。
        // 这条断言钉住"低段平缓"这个意图，改回线性会让它失败
        val (w, _) = downscaled(1000, 1000, 0.2f)
        assertTrue("20% 缩到 $w，过于激进", w > 600)
    }

    @Test
    fun `满强度仍有足够的模糊力度`() {
        val (w, _) = downscaled(1200, 1200, 1f)
        assertTrue("100% 只缩到 $w，力度不足", w <= 120)
    }

    @Test
    fun `非正方形图片的两边独立计算`() {
        val (w, h) = downscaled(1600, 400, 0.5f)
        // 长宽比应大致保留：两边用同一个倍率
        assertTrue("w=$w h=$h", w > h)
    }
}
