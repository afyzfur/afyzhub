package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.settings.isSettled
import com.afyzfur.afyzhub.ui.settings.shouldAdoptExternal
import com.afyzfur.afyzhub.ui.settings.sliderDisplayValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑块的取值来源。
 *
 * 上一版这里的断言写的是"未拖动时显示外部值"，把一个 bug 钉住了：
 * 抬手那一刻落盘还没完成，外部值仍是拖动之前的旧值，交回去就会让
 * thumb 先跳回原位再跳到新位置。判断依据应当是"外部值是否已追上
 * 本地值"，与手指在不在滑块上无关。
 */
class SliderValueSourceTest {

    @Test
    fun `有待落盘改动时显示本地值`() {
        assertEquals(0.7f, sliderDisplayValue(external = 0.3f, local = 0.7f, pending = true), 1e-6f)
    }

    @Test
    fun `没有待落盘改动时显示外部值`() {
        // 此时两者本应相等，取外部值是为了让别处的修改能同步进来
        assertEquals(0.3f, sliderDisplayValue(external = 0.3f, local = 0.3f, pending = false), 1e-6f)
    }

    @Test
    fun `抬手后落盘未完成期间不交回外部值`() {
        // 这是上一版的 bug：拖到 0.8 抬手，落盘还在路上，外部值仍是 0.2。
        // 若此刻显示外部值，thumb 会跳回 0.2 再跳到 0.8
        val afterDrag = 0.8f
        val notYetPersisted = 0.2f
        val pending = !isSettled(external = notYetPersisted, local = afterDrag)
        assertTrue("落盘未完成时必须仍算待处理", pending)
        assertEquals(
            "抬手后 thumb 必须停在手指松开的位置",
            afterDrag,
            sliderDisplayValue(notYetPersisted, afterDrag, pending),
            1e-6f
        )
    }

    @Test
    fun `落盘完成后交回外部值`() {
        val settled = 0.8f
        val pending = !isSettled(external = settled, local = settled)
        assertFalse(pending)
        assertEquals(settled, sliderDisplayValue(settled, settled, pending), 1e-6f)
    }

    @Test
    fun `待落盘期间不采纳外部值`() {
        assertFalse(shouldAdoptExternal(external = 0.3f, local = 0.7f, pending = true))
    }

    @Test
    fun `无待落盘且值不同时采纳外部值`() {
        // 别处改了设置，这里要跟着更新
        assertTrue(shouldAdoptExternal(external = 0.3f, local = 0.7f, pending = false))
    }

    @Test
    fun `值相同时不重复采纳`() {
        // 写入相同的值也会让状态标记为已变更，白白多一次重组
        assertFalse(shouldAdoptExternal(external = 0.5f, local = 0.5f, pending = false))
    }

    @Test
    fun `浮点往返误差不会让待落盘状态卡死`() {
        // DataStore 的浮点序列化往返可能差一个最小精度单位。
        // 若用严格相等判断，pending 会永远为真，滑块从此不再接受外部更新
        val written = 0.37f
        val readBack = 0.37f + 1e-7f
        assertTrue("微小误差应视为已落盘", isSettled(external = readBack, local = written))
        assertFalse(!isSettled(external = readBack, local = written))
    }

    @Test
    fun `真实差异不会被误判为已落盘`() {
        // 容差不能大到把一个百分点的差异也吃掉
        assertFalse(isSettled(external = 0.50f, local = 0.51f))
    }

    @Test
    fun `一次完整拖动的取值序列`() {
        // 起始 0.2，拖到 0.8，抬手，落盘回来
        var local = 0.2f
        val stored = 0.2f

        // 未改动时
        assertFalse(!isSettled(stored, local))
        assertEquals(0.2f, sliderDisplayValue(stored, local, pending = false), 1e-6f)

        // 拖动中的几帧：外部值一直是 0.2
        listOf(0.35f, 0.5f, 0.65f, 0.8f).forEach { frame ->
            local = frame
            val pending = !isSettled(stored, local)
            assertTrue("拖动中必然有待落盘改动", pending)
            assertEquals(frame, sliderDisplayValue(stored, local, pending), 1e-6f)
        }

        // 抬手，落盘仍未回来
        var pending = !isSettled(stored, local)
        assertEquals("抬手后不该跳回", 0.8f, sliderDisplayValue(stored, local, pending), 1e-6f)

        // 落盘回来了
        val persisted = 0.8f
        pending = !isSettled(persisted, local)
        assertFalse(pending)
        assertEquals(0.8f, sliderDisplayValue(persisted, local, pending), 1e-6f)
    }

    @Test
    fun `零值不被特殊对待`() {
        // 0% 曾是最难拖的位置，确认 0 没有被当成"空值"
        assertTrue(isSettled(external = 0f, local = 0f))
        assertEquals(0f, sliderDisplayValue(external = 0.5f, local = 0f, pending = true), 1e-6f)
    }
}
