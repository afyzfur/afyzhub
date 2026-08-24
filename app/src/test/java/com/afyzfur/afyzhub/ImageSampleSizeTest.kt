package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.image.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 降采样率计算的测试。
 *
 * BitmapFactory 本身在 JVM 下不可用，但采样率是纯算术，
 * 而它决定了大图能否被解码——算错会直接 OOM。
 */
class ImageSampleSizeTest {

    @Test
    fun `小于目标尺寸的图不降采样`() {
        assertEquals(1, sampleSizeFor(400, 300, 512))
        assertEquals(1, sampleSizeFor(512, 512, 512))
    }

    @Test
    fun `采样率始终是 2 的幂`() {
        // BitmapFactory 会把非 2 的幂向下取整到最近的 2 的幂，
        // 直接给出 2 的幂可避免实际采样率与预期不符
        listOf(
            1000 to 800,
            4000 to 3000,
            8000 to 6000,
            12000 to 9000
        ).forEach { (w, h) ->
            val sample = sampleSizeFor(w, h, 512)
            assertTrue(
                "$w×$h 得到的采样率 $sample 不是 2 的幂",
                sample > 0 && (sample and (sample - 1)) == 0
            )
        }
    }

    @Test
    fun `采样后尺寸落在目标的两倍以内`() {
        // 留两倍余量：后续还要做一次精确缩放，
        // 直接采样到目标尺寸会因整数取整损失清晰度
        val maxSize = 512
        listOf(
            4000 to 3000,
            6000 to 8000,
            1500 to 1200
        ).forEach { (w, h) ->
            val sample = sampleSizeFor(w, h, maxSize)
            assertTrue(
                "$w×$h 采样 $sample 倍后仍超过 ${maxSize * 2}",
                w / sample <= maxSize * 2 && h / sample <= maxSize * 2
            )
        }
    }

    @Test
    fun `按长边决定采样率`() {
        // 极端长宽比时短边不应导致采样不足
        val sample = sampleSizeFor(width = 8000, height = 200, maxSize = 512)
        assertTrue("宽边 8000 应被采样", sample >= 4)
        assertTrue("采样后宽边应落入范围", 8000 / sample <= 1024)
    }

    @Test
    fun `尺寸异常时返回 1 而非死循环`() {
        // 解码失败时 outWidth/outHeight 会是 -1，
        // 若不做保护，while 条件永远成立
        assertEquals(1, sampleSizeFor(-1, -1, 512))
        assertEquals(1, sampleSizeFor(0, 0, 512))
        assertEquals(1, sampleSizeFor(1000, 1000, 0))
    }

    @Test
    fun `背景图的上限更大因此采样更少`() {
        val avatar = sampleSizeFor(4000, 3000, 512)
        val background = sampleSizeFor(4000, 3000, 1920)
        assertTrue("背景保留的像素应多于头像", background < avatar)
    }
}
