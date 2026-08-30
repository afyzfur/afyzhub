package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.settings.Grab
import com.afyzfur.afyzhub.ui.settings.grabOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 裁剪框的抓取判定。
 *
 * 这是新方案里唯一能纯 JVM 验证的部分，也是最容易写错的地方——
 * 判定顺序一旦反了，角落就永远抓不到（先判边会把角判成边）。
 *
 * 以整张图（0,0,1,1）为基准，触点用归一化坐标。
 */
class CropGrabTest {

    private fun grab(x: Float, y: Float, l: Float = 0f, t: Float = 0f, r: Float = 1f, b: Float = 1f) =
        grabOf(x = x, y = y, left = l, top = t, right = r, bottom = b)

    @Test
    fun `四个角优先于边`() {
        // 角落同时靠近两条边，必须判成角。先判边的话这四个断言全会失败
        assertEquals(Grab.TOP_LEFT, grab(0f, 0f))
        assertEquals(Grab.TOP_RIGHT, grab(1f, 0f))
        assertEquals(Grab.BOTTOM_LEFT, grab(0f, 1f))
        assertEquals(Grab.BOTTOM_RIGHT, grab(1f, 1f))
    }

    @Test
    fun `边的中点判成对应的边`() {
        assertEquals(Grab.LEFT, grab(0f, 0.5f))
        assertEquals(Grab.RIGHT, grab(1f, 0.5f))
        assertEquals(Grab.TOP, grab(0.5f, 0f))
        assertEquals(Grab.BOTTOM, grab(0.5f, 1f))
    }

    @Test
    fun `框中央判成整体平移`() {
        assertEquals(Grab.INSIDE, grab(0.5f, 0.5f))
    }

    @Test
    fun `收窄后的框仍能抓到它自己的边`() {
        // 框收到中间一块时，抓的应是框的边而不是图片的边
        assertEquals(Grab.LEFT, grab(0.3f, 0.5f, l = 0.3f, t = 0.2f, r = 0.7f, b = 0.8f))
        assertEquals(Grab.RIGHT, grab(0.7f, 0.5f, l = 0.3f, t = 0.2f, r = 0.7f, b = 0.8f))
        assertEquals(Grab.TOP, grab(0.5f, 0.2f, l = 0.3f, t = 0.2f, r = 0.7f, b = 0.8f))
        assertEquals(Grab.BOTTOM, grab(0.5f, 0.8f, l = 0.3f, t = 0.2f, r = 0.7f, b = 0.8f))
    }

    @Test
    fun `框外远处判成整体平移而非最近的边`() {
        // 图片左上角、而框在右下：这一点离框的任何边都不近
        val g = grab(0.05f, 0.05f, l = 0.6f, t = 0.6f, r = 1f, b = 1f)
        assertEquals(Grab.INSIDE, g)
    }

    @Test
    fun `容差之内算靠近容差之外不算`() {
        // 略微偏离左边界仍应抓到左边
        assertEquals(Grab.LEFT, grab(0.05f, 0.5f))
        // 明显偏离则不再是边
        assertEquals(Grab.INSIDE, grab(0.4f, 0.5f))
    }

    @Test
    fun `窄框的相邻两边不会互相干扰`() {
        // 框宽刚好等于最小边长时，左右两边相距 0.1，仍在容差范围内。
        // 此时靠左的点应判成左边——顺序保证了这一点
        assertEquals(Grab.LEFT, grab(0.45f, 0.5f, l = 0.45f, t = 0f, r = 0.55f, b = 1f))
    }
}
