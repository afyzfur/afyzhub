package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.components.matchModelIcon
import com.afyzfur.afyzhub.ui.components.referencedIconFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 模型图标匹配的测试。
 *
 * 这段逻辑的价值在于应对中转服务给出的非标准模型名，
 * 因此用例以真实见过的名称为主。
 */
class ModelIconTest {

    @Test
    fun `中转加的中文前缀不影响匹配`() {
        // 实际遇到的名称，此前会因首字符是 [ 而在头像位显示方括号
        assertEquals(
            "claude-color.svg",
            matchModelIcon("[限时福利]claude-opus-5-thinking")
        )
    }

    @Test
    fun `官方模型名可匹配`() {
        assertEquals("openai.svg", matchModelIcon("gpt-4o-mini"))
        assertEquals("claude-color.svg", matchModelIcon("claude-3-5-sonnet-20241022"))
        assertEquals("gemini-color.svg", matchModelIcon("gemini-2.0-flash"))
        assertEquals("deepseek-color.svg", matchModelIcon("deepseek-chat"))
    }

    @Test
    fun `gemma 不会被误配成 gemini`() {
        // 同属 Google 但图标不同，都含 gem 前缀
        assertEquals("gemma-color.svg", matchModelIcon("gemma-2-27b"))
        assertEquals("gemini-color.svg", matchModelIcon("gemini-1.5-pro"))
    }

    @Test
    fun `ollama 不会被误配成 meta`() {
        // llama 是 Meta 的模型，ollama 是本地推理工具，两者不同
        assertEquals("ollama.svg", matchModelIcon("ollama"))
        assertEquals("meta-color.svg", matchModelIcon("llama-3.3-70b"))
    }

    @Test
    fun `o 系列要求词界`() {
        // OpenAI 的 o 系列
        assertEquals("openai.svg", matchModelIcon("o3-mini"))
        // 不该因为名字里有 o 接数字就判成 OpenAI
        assertNull(matchModelIcon("turbo3x-plus"))
    }

    @Test
    fun `厂商别名指向同一图标`() {
        assertEquals(matchModelIcon("qwen-max"), matchModelIcon("qwq-32b"))
        assertEquals(matchModelIcon("glm-4-plus"), matchModelIcon("智谱清言"))
        assertEquals(matchModelIcon("mistral-large"), matchModelIcon("mixtral-8x7b"))
        assertEquals(matchModelIcon("kimi-k2"), matchModelIcon("moonshot-v1-8k"))
    }

    @Test
    fun `大小写与分隔符不影响匹配`() {
        listOf("CLAUDE-3-OPUS", "Claude_3_Opus", "claude.3.opus", "  claude  ")
            .forEach { assertEquals("「$it」未匹配", "claude-color.svg", matchModelIcon(it)) }
    }

    @Test
    fun `无法匹配时返回 null`() {
        assertNull(matchModelIcon("some-unknown-model-v2"))
        assertNull(matchModelIcon(""))
        assertNull(matchModelIcon("[]---"))
    }

    @Test
    fun `规则引用的图标文件全部存在`() {
        // 清单与文件分离，漏放文件时匹配成功但显示空白，不易归因
        val dir = listOf(File("app/src/main/assets/icons"), File("src/main/assets/icons"))
            .firstOrNull { it.exists() }
        assertTrue("找不到图标目录", dir != null)
        val present = dir!!.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val missing = referencedIconFiles - present
        assertTrue("规则引用但文件缺失：$missing", missing.isEmpty())
    }
}
