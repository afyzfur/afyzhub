package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.settings.shouldAdoptExternal
import com.afyzfur.afyzhub.ui.settings.sliderDisplayValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑块拖动期间的取值来源。
 *
 * 这两个方向搞反的表现都很隐蔽：拖动中采纳外部值 -> 落盘回来的旧值
 * 把 thumb 拽回去，看起来是"不跟手"；非拖动时不采纳 -> 别处改了设置
 * 这个滑块不更新，看起来是"界面不同步"。都不会报错，只能靠断言。
 */
class SliderValueSourceTest {

    @Test
    fun `拖动期间显示本地值而非外部值`() {
        // 外部值是磁盘往返回来的旧值，拖动中必须忽略
        assertEquals(0.7f, sliderDisplayValue(external = 0.3f, local = 0.7f, dragging = true), 1e-6f)
    }

    @Test
    fun `未拖动时显示外部值`() {
        // 抬手之后以落盘结果为准，别处改了设置也能同步过来
        assertEquals(0.3f, sliderDisplayValue(external = 0.3f, local = 0.7f, dragging = false), 1e-6f)
    }

    @Test
    fun `拖动期间不采纳外部值`() {
        assertFalse(shouldAdoptExternal(external = 0.3f, local = 0.7f, dragging = true))
        // 即使两者相同也不采纳——拖动中根本不该碰本地状态
        assertFalse(shouldAdoptExternal(external = 0.5f, local = 0.5f, dragging = true))
    }

    @Test
    fun `未拖动且值不同时采纳外部值`() {
        assertTrue(shouldAdoptExternal(external = 0.3f, local = 0.7f, dragging = false))
    }

    @Test
    fun `值相同时不重复采纳`() {
        // 写入相同的值也会让状态标记为已变更，白白多一次重组
        assertFalse(shouldAdoptExternal(external = 0.5f, local = 0.5f, dragging = false))
    }

    @Test
    fun `一次完整拖动的取值序列`() {
        // 模拟：起始 0.2，手指拖到 0.8，抬手落盘。
        // 拖动中外部值一直停在 0.2（还没写），thumb 必须跟着本地走
        var local = 0.2f
        val external = 0.2f

        assertEquals(0.2f, sliderDisplayValue(external, local, dragging = false), 1e-6f)

        // 拖动中的几帧
        listOf(0.35f, 0.5f, 0.65f, 0.8f).forEach { frame ->
            local = frame
            assertEquals(
                "拖动中 thumb 必须停在手指位置",
                frame,
                sliderDisplayValue(external, local, dragging = true),
                1e-6f
            )
            assertFalse(shouldAdoptExternal(external, local, dragging = true))
        }

        // 抬手后外部值追上来
        val settled = 0.8f
        assertEquals(0.8f, sliderDisplayValue(settled, local, dragging = false), 1e-6f)
        assertFalse("落盘值与本地一致时无需再同步", shouldAdoptExternal(settled, local, dragging = false))
    }

    @Test
    fun `零值不被特殊对待`() {
        // 0% 曾经是最难拖的位置。那与取值逻辑无关，但要确认 0 没有
        // 被当成"空值"而回退到外部值
        assertEquals(0f, sliderDisplayValue(external = 0.5f, local = 0f, dragging = true), 1e-6f)
        assertTrue(shouldAdoptExternal(external = 0f, local = 0.5f, dragging = false))
    }
}
