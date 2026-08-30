package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.settings.CropRect
import com.afyzfur.afyzhub.ui.settings.Grab
import com.afyzfur.afyzhub.ui.settings.MIN_SIZE
import com.afyzfur.afyzhub.ui.settings.applyDrag
import com.afyzfur.afyzhub.ui.settings.grabOf
import com.afyzfur.afyzhub.ui.settings.initialCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 裁剪框的抓取判定与拖动计算。
 *
 * 这是裁剪交互里唯一能纯 JVM 验证的部分，也是出过最多问题的地方。
 * 手感本身测不了，但"拖了没反应""抓错边"这类都是这里的逻辑问题。
 */
class CropGrabTest {

    private val full = CropRect(0f, 0f, 1f, 1f)
    private val mid = CropRect(0.3f, 0.2f, 0.7f, 0.8f)

    // ---- 抓取判定 ----

    @Test
    fun `四个角优先于边`() {
        // 角落同时靠近两条边，必须判成角。先判边的话这四个断言全会失败
        assertEquals(Grab.TOP_LEFT, grabOf(0f, 0f, full))
        assertEquals(Grab.TOP_RIGHT, grabOf(1f, 0f, full))
        assertEquals(Grab.BOTTOM_LEFT, grabOf(0f, 1f, full))
        assertEquals(Grab.BOTTOM_RIGHT, grabOf(1f, 1f, full))
    }

    @Test
    fun `边的中点判成对应的边`() {
        assertEquals(Grab.LEFT, grabOf(0f, 0.5f, full))
        assertEquals(Grab.RIGHT, grabOf(1f, 0.5f, full))
        assertEquals(Grab.TOP, grabOf(0.5f, 0f, full))
        assertEquals(Grab.BOTTOM, grabOf(0.5f, 1f, full))
    }

    @Test
    fun `框中央判成整体平移`() {
        assertEquals(Grab.INSIDE, grabOf(0.5f, 0.5f, full))
    }

    @Test
    fun `收窄后的框抓的是它自己的边而非图片的边`() {
        assertEquals(Grab.LEFT, grabOf(0.3f, 0.5f, mid))
        assertEquals(Grab.RIGHT, grabOf(0.7f, 0.5f, mid))
        assertEquals(Grab.TOP, grabOf(0.5f, 0.2f, mid))
        assertEquals(Grab.BOTTOM, grabOf(0.5f, 0.8f, mid))
        // 图片的左边界此时离框很远，不应被当成边
        assertEquals(Grab.INSIDE, grabOf(0.5f, 0.5f, mid))
    }

    @Test
    fun `容差之内算靠近容差之外不算`() {
        assertEquals(Grab.LEFT, grabOf(0.05f, 0.5f, full))
        assertEquals(Grab.INSIDE, grabOf(0.4f, 0.5f, full))
    }

    @Test
    fun `触点落在图片之外仍能抓到贴边的把手`() {
        // 手势区比图片大一圈，贴边的把手有一半在图片外面，换算出的
        // 归一化坐标会是负数或大于 1。这些点必须照样判成对应的边，
        // 否则边界处依然难拖
        val edge = CropRect(0f, 0f, 1f, 1f)
        assertEquals(Grab.LEFT, grabOf(-0.04f, 0.5f, edge))
        assertEquals(Grab.RIGHT, grabOf(1.04f, 0.5f, edge))
        assertEquals(Grab.TOP, grabOf(0.5f, -0.04f, edge))
        assertEquals(Grab.BOTTOM, grabOf(0.5f, 1.04f, edge))
        // 图片外的角同样要能抓
        assertEquals(Grab.TOP_LEFT, grabOf(-0.03f, -0.03f, edge))
        assertEquals(Grab.BOTTOM_RIGHT, grabOf(1.03f, 1.03f, edge))
    }

    // ---- 初始范围 ----

    @Test
    fun `初始范围留有内缩，中间可平移且边不贴图片边界`() {
        val r = initialCrop()
        // 曾经的 bug：初始为整张图时，中间拖动的可动空间是 0，
        // 第一次拖动完全没反应，看起来像"要拖两次"
        assertTrue("必须留出平移空间", r.width < 1f && r.height < 1f)
        assertTrue("左边不应贴在图片边界上", r.left > 0f)
        assertTrue("上边不应贴在图片边界上", r.top > 0f)
        assertTrue("右边不应贴在图片边界上", r.right < 1f)
        assertTrue("下边不应贴在图片边界上", r.bottom < 1f)

        val moved = applyDrag(r, Grab.INSIDE, 0.02f, 0.02f)
        assertTrue("初始状态下框内拖动必须立即生效", moved.left > r.left)
        assertTrue(moved.top > r.top)
    }

    // ---- 拖动计算 ----

    @Test
    fun `拖各边只改对应的那一条`() {
        val l = applyDrag(mid, Grab.LEFT, 0.05f, 0.05f)
        assertEquals(0.35f, l.left, 1e-5f)
        // dy 不应影响左边界之外的任何值
        assertEquals(mid.top, l.top, 1e-5f)
        assertEquals(mid.right, l.right, 1e-5f)
        assertEquals(mid.bottom, l.bottom, 1e-5f)

        val b = applyDrag(mid, Grab.BOTTOM, 0.05f, -0.1f)
        assertEquals(0.7f, b.bottom, 1e-5f)
        assertEquals(mid.left, b.left, 1e-5f)
        assertEquals(mid.right, b.right, 1e-5f)
    }

    @Test
    fun `拖角同时改两条边`() {
        val r = applyDrag(mid, Grab.BOTTOM_RIGHT, -0.05f, -0.05f)
        assertEquals(0.65f, r.right, 1e-5f)
        assertEquals(0.75f, r.bottom, 1e-5f)
        assertEquals(mid.left, r.left, 1e-5f)
        assertEquals(mid.top, r.top, 1e-5f)
    }

    @Test
    fun `收缩到最小边长后不再变窄`() {
        // 右边界往左推很多，应停在 left + MIN_SIZE
        val r = applyDrag(mid, Grab.RIGHT, -1f, 0f)
        assertEquals(mid.left + MIN_SIZE, r.right, 1e-5f)
        assertTrue("宽度不得小于最小边长", r.width >= MIN_SIZE - 1e-5f)

        // 左边界往右推很多，应停在 right - MIN_SIZE
        val l = applyDrag(mid, Grab.LEFT, 1f, 0f)
        assertEquals(mid.right - MIN_SIZE, l.left, 1e-5f)
    }

    @Test
    fun `边界不会越出图片范围`() {
        val r = applyDrag(mid, Grab.RIGHT, 5f, 0f)
        assertEquals(1f, r.right, 1e-5f)
        val l = applyDrag(mid, Grab.LEFT, -5f, 0f)
        assertEquals(0f, l.left, 1e-5f)
        val t = applyDrag(mid, Grab.TOP, 0f, -5f)
        assertEquals(0f, t.top, 1e-5f)
        val b = applyDrag(mid, Grab.BOTTOM, 0f, 5f)
        assertEquals(1f, b.bottom, 1e-5f)
    }

    @Test
    fun `平移保持尺寸不变`() {
        val r = applyDrag(mid, Grab.INSIDE, 0.1f, 0.1f)
        assertEquals(mid.width, r.width, 1e-5f)
        assertEquals(mid.height, r.height, 1e-5f)
        assertEquals(0.4f, r.left, 1e-5f)
        assertEquals(0.3f, r.top, 1e-5f)
    }

    @Test
    fun `平移撞到边界时停住且不被压扁`() {
        val r = applyDrag(mid, Grab.INSIDE, 5f, 5f)
        // 贴到右下角，尺寸必须原样保留——若平移也用逐边夹取，
        // 这里的宽高会被压缩
        assertEquals(mid.width, r.width, 1e-5f)
        assertEquals(mid.height, r.height, 1e-5f)
        assertEquals(1f, r.right, 1e-5f)
        assertEquals(1f, r.bottom, 1e-5f)

        val neg = applyDrag(mid, Grab.INSIDE, -5f, -5f)
        assertEquals(mid.width, neg.width, 1e-5f)
        assertEquals(0f, neg.left, 1e-5f)
        assertEquals(0f, neg.top, 1e-5f)
    }

    @Test
    fun `满框时平移不抛异常`() {
        // 可动空间为 0，coerceIn 的上界会算成负数。
        // 不加保护会因 min 大于 max 直接抛 IllegalArgumentException
        val r = applyDrag(full, Grab.INSIDE, 0.1f, 0.1f)
        assertEquals(full, r)
    }

    @Test
    fun `连续拖动逐步累积`() {
        // 手势期间每帧都是一次小位移，累积结果应与一次大位移一致
        var r = mid
        repeat(5) { r = applyDrag(r, Grab.RIGHT, -0.02f, 0f) }
        assertEquals(0.6f, r.right, 1e-5f)
    }
}
