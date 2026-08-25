package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.settings.pageOfModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型列表分页的测试。
 *
 * 这段逻辑本身简单，值得测的是边界：中转服务给出的模型数量不定，
 * 且刷新后列表长度会变，页码可能残留成越界值。
 */
class ModelPagingTest {

    private fun models(n: Int) = (1..n).map { "model-$it" }

    @Test
    fun `十个以内只有一页且不显示翻页`() {
        val page = pageOfModels(models(10), pageIndex = 0)

        assertEquals(1, page.pageCount)
        assertEquals(10, page.models.size)
        assertFalse("十个刚好一页，不该出现翻页控件", page.showPager)
    }

    @Test
    fun `超过十个时分页并显示翻页`() {
        val page = pageOfModels(models(11), pageIndex = 0)

        assertEquals(2, page.pageCount)
        assertEquals(10, page.models.size)
        assertTrue(page.showPager)
        assertTrue(page.hasNext)
        assertFalse(page.hasPrevious)
    }

    @Test
    fun `末页允许不足整页`() {
        // 205 个模型：20 整页加 5 个
        val page = pageOfModels(models(205), pageIndex = 20)

        assertEquals(21, page.pageCount)
        assertEquals(5, page.models.size)
        assertEquals("model-201", page.models.first())
        assertFalse(page.hasNext)
        assertTrue(page.hasPrevious)
    }

    @Test
    fun `越界页码夹到末页`() {
        // 刷新后列表变短，界面上残留的页码会越界
        val page = pageOfModels(models(15), pageIndex = 99)

        assertEquals(1, page.pageIndex)
        assertEquals(5, page.models.size)
        assertFalse(page.hasNext)
    }

    @Test
    fun `负页码夹到首页`() {
        val page = pageOfModels(models(15), pageIndex = -3)

        assertEquals(0, page.pageIndex)
        assertEquals("model-1", page.models.first())
    }

    @Test
    fun `空列表不产生页`() {
        val page = pageOfModels(emptyList(), pageIndex = 0)

        assertEquals(0, page.pageCount)
        assertTrue(page.models.isEmpty())
        assertFalse(page.showPager)
    }

    @Test
    fun `翻页覆盖全部模型且不重不漏`() {
        val all = models(205)
        val collected = mutableListOf<String>()
        var index = 0
        while (true) {
            val page = pageOfModels(all, index)
            collected += page.models
            if (!page.hasNext) break
            index++
        }

        assertEquals(all, collected)
    }
}
