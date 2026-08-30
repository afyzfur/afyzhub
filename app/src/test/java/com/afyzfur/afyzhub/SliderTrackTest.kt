package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.settings.THUMB_RADIUS
import com.afyzfur.afyzhub.ui.settings.positionToFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑块轨道的坐标换算。
 *
 * 端点是这里唯一容易出错的地方：绘制时两端各留出 thumb 半径，换算若
 * 不扣掉同样的量，手指移到最左也到不了 0%、移到最右也到不了 100%。
 * 那正是"0% 难拖"的一种成因。
 */
class SliderTrackTest {

    private val width = 400f

    @Test
    fun `轨道左端换算为零`() {
        assertEquals(0f, positionToFraction(THUMB_RADIUS, width), 1e-5f)
    }

    @Test
    fun `轨道右端换算为一`() {
        assertEquals(1f, positionToFraction(width - THUMB_RADIUS, width), 1e-5f)
    }

    @Test
    fun `中点换算为一半`() {
        assertEquals(0.5f, positionToFraction(width / 2f, width), 1e-5f)
    }

    @Test
    fun `超出左端仍夹到零而非负数`() {
        // 手指划到组件外面是常见操作，不能得到负数比例
        assertEquals(0f, positionToFraction(0f, width), 1e-5f)
        assertEquals(0f, positionToFraction(-50f, width), 1e-5f)
    }

    @Test
    fun `超出右端仍夹到一`() {
        assertEquals(1f, positionToFraction(width, width), 1e-5f)
        assertEquals(1f, positionToFraction(width + 80f, width), 1e-5f)
    }

    @Test
    fun `零宽度不会除零`() {
        // 首帧测量结果可能是 0，此时必须返回有效值而不是 NaN
        val r = positionToFraction(10f, 0f)
        assertEquals(0f, r, 1e-5f)
        assertTrue("不得为 NaN", !r.isNaN())
    }

    @Test
    fun `宽度小于两倍半径不会得到反向结果`() {
        // 极窄容器下 span 为负，同样要退回 0 而非产生诡异的比例
        val r = positionToFraction(5f, THUMB_RADIUS)
        assertEquals(0f, r, 1e-5f)
        assertTrue(r in 0f..1f)
    }

    @Test
    fun `换算随横坐标单调递增`() {
        // 拖动过程中比例必须随手指单向变化，否则会出现回跳
        var prev = -1f
        var x = THUMB_RADIUS
        while (x <= width - THUMB_RADIUS) {
            val f = positionToFraction(x, width)
            assertTrue("比例不得回退：x=$x", f >= prev)
            prev = f
            x += 10f
        }
    }
}
