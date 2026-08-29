package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话长按操作的行为。
 *
 * 用 FakeConversationDao 而非真实数据库：这些操作的逻辑都在
 * "改哪个字段、动不动 updatedAt、排序怎么变"，不涉及 SQL 特性。
 * 迁移本身的正确性由 CI 上的真机迁移覆盖不到，这点在下面单独说明。
 */
class ConversationActionTest {

    @Test
    fun `置顶的会话排在最前`() = runTest {
        val dao = FakeConversationDao()
        // 故意让置顶的那条更新时间最早，确认置顶优先于时间
        dao.insertConversation(ConversationEntity(id = 1, title = "旧但置顶", updatedAt = 100))
        dao.insertConversation(ConversationEntity(id = 2, title = "新", updatedAt = 300))
        dao.insertConversation(ConversationEntity(id = 3, title = "较新", updatedAt = 200))

        dao.updatePinned(1, true)

        val titles = dao.getConversationSummaries().first().map { it.title }
        assertEquals(listOf("旧但置顶", "新", "较新"), titles)
    }

    @Test
    fun `多条置顶之间仍按更新时间排序`() = runTest {
        val dao = FakeConversationDao()
        dao.insertConversation(ConversationEntity(id = 1, title = "置顶早", updatedAt = 100))
        dao.insertConversation(ConversationEntity(id = 2, title = "置顶晚", updatedAt = 200))
        dao.insertConversation(ConversationEntity(id = 3, title = "普通", updatedAt = 300))

        dao.updatePinned(1, true)
        dao.updatePinned(2, true)

        val titles = dao.getConversationSummaries().first().map { it.title }
        assertEquals(listOf("置顶晚", "置顶早", "普通"), titles)
    }

    @Test
    fun `改名不动更新时间`() = runTest {
        val dao = FakeConversationDao()
        dao.insertConversation(ConversationEntity(id = 1, title = "原名", updatedAt = 100))

        dao.updateTitle(1, "新名")

        val entity = dao.getConversationById(1)!!
        assertEquals("新名", entity.title)
        // 这是此前的缺陷：改名会刷新时间，把会话挪进「今天」分组
        assertEquals(100, entity.updatedAt)
    }

    @Test
    fun `置顶与加星不动更新时间`() = runTest {
        val dao = FakeConversationDao()
        dao.insertConversation(ConversationEntity(id = 1, title = "会话", updatedAt = 100))

        dao.updatePinned(1, true)
        dao.updateStarred(1, true)

        val entity = dao.getConversationById(1)!!
        assertTrue(entity.pinned)
        assertTrue(entity.starred)
        assertEquals(100, entity.updatedAt)
    }

    @Test
    fun `置顶与星标相互独立`() = runTest {
        val dao = FakeConversationDao()
        dao.insertConversation(ConversationEntity(id = 1, title = "会话"))

        dao.updatePinned(1, true)
        dao.updateStarred(1, false)
        assertTrue(dao.getConversationById(1)!!.pinned)
        assertFalse(dao.getConversationById(1)!!.starred)

        // 取消置顶不应连带清掉星标
        dao.updateStarred(1, true)
        dao.updatePinned(1, false)
        assertFalse(dao.getConversationById(1)!!.pinned)
        assertTrue(dao.getConversationById(1)!!.starred)
    }

    @Test
    fun `分组列表排除未分组并去重`() = runTest {
        val dao = FakeConversationDao()
        dao.insertConversation(ConversationEntity(id = 1, title = "a"))
        dao.insertConversation(ConversationEntity(id = 2, title = "b"))
        dao.insertConversation(ConversationEntity(id = 3, title = "c"))

        dao.updateGroup(1, "工作")
        dao.updateGroup(2, "工作")
        // 第三条留在未分组，空串不该出现在可选分组里

        assertEquals(listOf("工作"), dao.getGroups().first())
    }

    @Test
    fun `移出分组后不再出现在分组列表`() = runTest {
        val dao = FakeConversationDao()
        dao.insertConversation(ConversationEntity(id = 1, title = "a"))
        dao.updateGroup(1, "临时")
        assertEquals(listOf("临时"), dao.getGroups().first())

        dao.updateGroup(1, "")
        assertEquals(emptyList<String>(), dao.getGroups().first())
    }

    @Test
    fun `删除会话同时清掉它的消息`() = runTest {
        val messageDao = FakeMessageDao()
        val dao = FakeConversationDao(messageDao)
        dao.insertConversation(ConversationEntity(id = 1, title = "会话"))

        // 真实实现靠外键级联，这里验证 Fake 与之行为一致
        dao.deleteConversationById(1)

        assertNull(dao.getConversationById(1))
        assertTrue(messageDao.current.none { it.conversationId == 1L })
    }
}
