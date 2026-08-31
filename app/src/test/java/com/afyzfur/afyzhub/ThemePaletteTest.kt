package com.afyzfur.afyzhub

import androidx.compose.ui.graphics.Color
import com.afyzfur.afyzhub.ui.theme.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 配色派生的测试。
 *
 * 重点验证明度守恒：色板的层次感全靠 surfaceContainer 五档之间的明度差，
 * 若旋转色相时明度发生漂移，层次会失效或反转。
 */
class ThemePaletteTest {

    /** 感知明度，用于比较旋转前后是否守恒 */
    private fun lightness(c: Color): Float = (maxOf(c.red, c.green, c.blue) +
        minOf(c.red, c.green, c.blue)) / 2f

    @Test
    fun `基准配色返回原色板`() {
        // ORANGE 的偏移为 0，应直接返回基准色板而非走一遍浮点运算
        val orange = ThemePalette.ORANGE.light()
        assertEquals(
            com.afyzfur.afyzhub.ui.theme.LightColors.primary,
            orange.primary
        )
    }

    @Test
    fun `旋转色相后明度保持不变`() {
        val base = ThemePalette.ORANGE.light()
        val blue = ThemePalette.BLUE.light()

        // 逐项比较关键的层次色，容差 0.01 覆盖浮点误差
        listOf(
            base.surfaceContainerLowest to blue.surfaceContainerLowest,
            base.surfaceContainerLow to blue.surfaceContainerLow,
            base.surfaceContainer to blue.surfaceContainer,
            base.surfaceContainerHigh to blue.surfaceContainerHigh,
            base.surfaceContainerHighest to blue.surfaceContainerHighest,
            base.primary to blue.primary,
            base.onSurface to blue.onSurface
        ).forEach { (a, b) ->
            assertTrue(
                "明度漂移过大：${lightness(a)} vs ${lightness(b)}",
                abs(lightness(a) - lightness(b)) < 0.01f
            )
        }
    }

    @Test
    fun `surface 五档色阶的明度顺序不被破坏`() {
        // 若旋转导致顺序反转，界面层次会失效
        ThemePalette.entries.forEach { palette ->
            val s = palette.light()
            val steps = listOf(
                lightness(s.surfaceContainerLowest),
                lightness(s.surfaceContainerLow),
                lightness(s.surfaceContainer),
                lightness(s.surfaceContainerHigh),
                lightness(s.surfaceContainerHighest)
            )
            // 浅色主题下由亮到暗递减
            steps.zipWithNext().forEach { (higher, lower) ->
                assertTrue(
                    "${palette.id} 的 surface 色阶顺序异常：$steps",
                    higher >= lower - 0.001f
                )
            }
        }
    }

    @Test
    fun `错误色不参与色相旋转`() {
        // 错误状态必须始终是红色，跨应用的通用约定
        val base = ThemePalette.ORANGE.light()
        ThemePalette.entries.forEach { palette ->
            assertEquals(
                "${palette.id} 的 error 色被改变了",
                base.error,
                palette.light().error
            )
        }
    }

    @Test
    fun `不同配色的主色确实不同`() {
        val primaries = ThemePalette.entries.map { it.light().primary }
        // 六套配色应产出六个不同的主色，否则枚举里有重复定义
        assertEquals(primaries.size, primaries.toSet().size)
    }

    @Test
    fun `石墨配色降低了彩度`() {
        fun saturation(c: Color): Float {
            val max = maxOf(c.red, c.green, c.blue)
            val min = minOf(c.red, c.green, c.blue)
            if (max == min) return 0f
            val l = (max + min) / 2f
            return if (l > 0.5f) (max - min) / (2f - max - min) else (max - min) / (max + min)
        }

        val orange = saturation(ThemePalette.ORANGE.light().primary)
        val graphite = saturation(ThemePalette.GRAPHITE.light().primary)
        assertTrue("石墨的彩度应明显低于品牌橙", graphite < orange * 0.5f)
    }

    @Test
    fun `深色配色同样完成派生`() {
        val orangeDark = ThemePalette.ORANGE.dark()
        val blueDark = ThemePalette.BLUE.dark()
        assertNotEquals(orangeDark.primary, blueDark.primary)
        assertTrue(
            abs(lightness(orangeDark.primary) - lightness(blueDark.primary)) < 0.01f
        )
    }

    @Test
    fun `id 可往返解析`() {
        ThemePalette.entries.forEach { palette ->
            assertEquals(palette, ThemePalette.fromId(palette.id))
        }
        // 未知 id 回落默认值而非抛异常，避免旧配置导致崩溃
        assertEquals(ThemePalette.DEFAULT, ThemePalette.fromId("nonexistent"))
        // 默认是石墨而非橙：橙色作为品牌色留在图标上，铺满界面过于抢眼
        assertEquals(ThemePalette.GRAPHITE, ThemePalette.DEFAULT)
        assertEquals(ThemePalette.DEFAULT, ThemePalette.fromId(null))
    }
}
