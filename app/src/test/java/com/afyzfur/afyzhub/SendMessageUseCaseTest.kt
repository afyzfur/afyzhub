package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.domain.usecase.SendMessageUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageUseCaseTest {

    @Test
    fun `短消息直接作为标题`() {
        assertEquals("今天天气怎么样", SendMessageUseCase.generateTitle("今天天气怎么样"))
    }

    @Test
    fun `长消息被截断并加省略号`() {
        val long = "这是一段非常长的消息内容用于验证标题会被正确截断处理"
        val title = SendMessageUseCase.generateTitle(long)
        assertTrue(title.length <= 21)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `换行与多余空白被压缩`() {
        assertEquals("第一行 第二行", SendMessageUseCase.generateTitle("  第一行\n\n   第二行  "))
    }

    @Test
    fun `空消息回退到默认标题`() {
        assertEquals("新对话", SendMessageUseCase.generateTitle("   "))
    }
}
