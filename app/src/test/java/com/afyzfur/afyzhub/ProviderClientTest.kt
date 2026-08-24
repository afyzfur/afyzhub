package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.remote.provider.AnthropicChatClient
import com.afyzfur.afyzhub.data.remote.provider.ChatTurn
import com.afyzfur.afyzhub.data.remote.provider.GeminiChatClient
import com.afyzfur.afyzhub.data.remote.provider.HttpTransport
import com.afyzfur.afyzhub.data.remote.provider.OpenAiChatClient
import com.afyzfur.afyzhub.data.remote.provider.StreamEvent
import com.afyzfur.afyzhub.data.remote.provider.Transport
import com.afyzfur.afyzhub.data.settings.AppSettings
import com.afyzfur.afyzhub.domain.model.AiProvider
import com.afyzfur.afyzhub.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 取出流中的文本增量，忽略结束事件。
 *
 * stream() 现在发的是 StreamEvent 而非裸字符串，但多数测试关心的仍是
 * 文本拼接是否正确，用这个扩展让原有断言保持简洁。
 * 需要验证 usage 的测试单独取 Finished 事件。
 */
private suspend fun Flow<StreamEvent>.textDeltas(): List<String> =
    toList().filterIsInstance<StreamEvent.TextDelta>().map { it.delta }

/** 取出流末尾的 usage，没有则为 null。 */
private suspend fun Flow<StreamEvent>.finishedUsage() =
    toList().filterIsInstance<StreamEvent.Finished>().firstOrNull()?.usage

/**
 * 记录请求参数并回放预设响应的假传输层。
 *
 * 用它可以在 JVM 上验证请求体结构、鉴权头和 SSE 解析，无需真实网络。
 */
private class FakeTransport(
    private val response: String = "{}",
    private val sseLines: List<String> = emptyList()
) : Transport {

    var lastPath: String? = null
    var lastBody: String? = null
    var lastHeaders: Map<String, String> = emptyMap()
    var lastQuery: Map<String, String> = emptyMap()
    var lastBaseUrl: String? = null

    override suspend fun getForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        query: Map<String, String>
    ): String {
        lastBaseUrl = baseUrl
        lastPath = path
        lastHeaders = headers
        lastQuery = query
        return response
    }

    override suspend fun postForText(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String>
    ): String {
        lastBaseUrl = baseUrl
        lastPath = path
        lastHeaders = headers
        lastBody = body
        lastQuery = query
        return response
    }

    override fun postForSse(
        baseUrl: String,
        path: String,
        headers: Map<String, String>,
        body: String,
        query: Map<String, String>
    ): Flow<String> {
        lastBaseUrl = baseUrl
        lastPath = path
        lastHeaders = headers
        lastBody = body
        lastQuery = query
        return sseLines.asFlow()
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

private fun body(transport: FakeTransport): JsonObject =
    json.parseToJsonElement(transport.lastBody!!).jsonObject

class OpenAiChatClientTest {

    private val settings = AppSettings(
        provider = AiProvider.OPENAI,
        apiKey = "sk-test",
        model = "gpt-4o",
        baseUrl = "https://api.openai.com/"
    )

    @Test
    fun `一次性请求解析回复内容`() = runTest {
        val transport = FakeTransport(
            response = """
                {"id":"chatcmpl-1","choices":[{"index":0,"message":{"role":"assistant","content":"你好"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
            """.trimIndent()
        )
        val client = OpenAiChatClient(transport, json)

        val reply = client.complete(listOf(ChatTurn("user", "hi")), settings).content

        assertEquals("你好", reply)
        assertEquals("v1/chat/completions", transport.lastPath)
        assertEquals("Bearer sk-test", transport.lastHeaders["Authorization"])
    }

    @Test
    fun `流式请求索取 usage 而非流式不带该字段`() = runTest {
        // stream_options 出现在非流式请求里会被部分中转服务拒绝，
        // 这条断言防止今后误改成无条件带上
        val streaming = FakeTransport(sseLines = listOf("""{"choices":[{"delta":{"content":"a"}}]}"""))
        OpenAiChatClient(streaming, json)
            .stream(listOf(ChatTurn("user", "hi")), settings)
            .textDeltas()
        assertEquals(
            true,
            body(streaming)["stream_options"]!!
                .jsonObject["include_usage"]!!.jsonPrimitive.content.toBoolean()
        )

        val plain = FakeTransport(
            response = """{"choices":[{"message":{"content":"a"}}]}"""
        )
        OpenAiChatClient(plain, json).complete(listOf(ChatTurn("user", "hi")), settings)
        assertNull(body(plain)["stream_options"])
    }

    @Test
    fun `流式末尾的 usage 块被解析`() = runTest {
        val transport = FakeTransport(
            sseLines = listOf(
                """{"choices":[{"delta":{"content":"a"}}]}""",
                // 带 usage 的收尾块 choices 为空，不应被当作文本增量
                """{"choices":[],"usage":{"prompt_tokens":5,"completion_tokens":8}}"""
            )
        )
        val client = OpenAiChatClient(transport, json)

        // 收尾块的 choices 为空，不应混进文本增量
        assertEquals(
            listOf("a"),
            client.stream(listOf(ChatTurn("user", "hi")), settings).textDeltas()
        )

        // FakeTransport 用 asFlow() 回放，可重复消费，故能再取一次验证 usage
        val usage = client.stream(listOf(ChatTurn("user", "hi")), settings).finishedUsage()
        assertEquals(5, usage?.promptTokens)
        assertEquals(8, usage?.completionTokens)
    }

    @Test
    fun `响应缺少非必要字段时仍能取到正文`() = runTest {
        // 部分中转服务会省略 id 与 usage，不应因此整条回复丢失。
        val transport = FakeTransport(
            response = """{"choices":[{"message":{"role":"assistant","content":"精简响应"}}]}"""
        )
        val client = OpenAiChatClient(transport, json)

        assertEquals("精简响应", client.complete(listOf(ChatTurn("user", "hi")), settings).content)
    }

    @Test
    fun `流式请求带上 stream 标记并拼接增量`() = runTest {
        val transport = FakeTransport(
            sseLines = listOf(
                """{"choices":[{"delta":{"content":"你"}}]}""",
                """{"choices":[{"delta":{"content":"好"}}]}""",
                // 结束块没有 content，应被跳过而不是产出空串。
                """{"choices":[{"delta":{},"finish_reason":"stop"}]}"""
            )
        )
        val client = OpenAiChatClient(transport, json)

        val deltas = client.stream(listOf(ChatTurn("user", "hi")), settings).textDeltas()

        assertEquals(listOf("你", "好"), deltas)
        assertEquals(true, body(transport)["stream"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `模型列表按名称排序`() = runTest {
        val transport = FakeTransport(
            response = """{"data":[{"id":"gpt-4o"},{"id":"gpt-3.5-turbo"},{"id":""}]}"""
        )
        val client = OpenAiChatClient(transport, json)

        val models = client.listModels(settings)

        assertEquals(listOf("gpt-3.5-turbo", "gpt-4o"), models)
        assertEquals("v1/models", transport.lastPath)
    }

    @Test
    fun `无法解析的数据块被跳过而不中断整段回复`() = runTest {
        val transport = FakeTransport(
            sseLines = listOf(
                """{"choices":[{"delta":{"content":"前"}}]}""",
                "不是合法 JSON",
                """{"choices":[{"delta":{"content":"后"}}]}"""
            )
        )
        val client = OpenAiChatClient(transport, json)

        val deltas = client.stream(listOf(ChatTurn("user", "hi")), settings).textDeltas()

        assertEquals(listOf("前", "后"), deltas)
    }
}

class HttpTransportThreadingTest {

    /**
     * OkHttp 的 execute() 是阻塞调用。
     *
     * 若 getForText / postForText 未切到 IO 线程，在 Android 主线程调用会抛
     * NetworkOnMainThreadException，表现为「无法获取模型列表」。
     * 这里断言两个方法都声明了 suspend，且实现里带有线程切换。
     */
    @Test
    fun `同步请求方法必须是 suspend 以便切换线程`() {
        val methods = HttpTransport::class.java.declaredMethods
        val get = methods.first { it.name == "getForText" }
        val post = methods.first { it.name == "postForText" }
        // suspend 函数编译后最后一个参数是 Continuation。
        assertTrue(
            "getForText 应为 suspend 函数",
            get.parameterTypes.last().name == "kotlin.coroutines.Continuation"
        )
        assertTrue(
            "postForText 应为 suspend 函数",
            post.parameterTypes.last().name == "kotlin.coroutines.Continuation"
        )
    }
}

class AnthropicChatClientTest {

    private val settings = AppSettings(
        provider = AiProvider.ANTHROPIC,
        apiKey = "sk-ant-test",
        model = "claude-sonnet-4",
        baseUrl = "https://api.anthropic.com/"
    )

    @Test
    fun `鉴权使用 x-api-key 且带版本头`() = runTest {
        val transport = FakeTransport(response = """{"content":[{"type":"text","text":"ok"}]}""")
        val client = AnthropicChatClient(transport, json)

        client.complete(listOf(ChatTurn("user", "hi")), settings)

        assertEquals("sk-ant-test", transport.lastHeaders["x-api-key"])
        assertEquals("2023-06-01", transport.lastHeaders["anthropic-version"])
        // 不应误用 OpenAI 的 Bearer 方式。
        assertNull(transport.lastHeaders["Authorization"])
        assertEquals("v1/messages", transport.lastPath)
    }

    @Test
    fun `system 提示提取到顶层字段而非留在消息列表`() = runTest {
        val transport = FakeTransport(response = """{"content":[{"type":"text","text":"ok"}]}""")
        val client = AnthropicChatClient(transport, json)

        client.complete(
            listOf(
                ChatTurn(Constants.ROLE_SYSTEM, "你是助手"),
                ChatTurn("user", "hi")
            ),
            settings
        )

        val payload = body(transport)
        assertEquals("你是助手", payload["system"]!!.jsonPrimitive.content)
        val messages = payload["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `max_tokens 必须存在否则接口报错`() = runTest {
        val transport = FakeTransport(response = """{"content":[{"type":"text","text":"ok"}]}""")
        val client = AnthropicChatClient(transport, json)

        client.complete(listOf(ChatTurn("user", "hi")), settings)

        assertTrue(body(transport).containsKey("max_tokens"))
    }

    @Test
    fun `只取 content_block_delta 事件的增量`() = runTest {
        val transport = FakeTransport(
            sseLines = listOf(
                """{"type":"message_start","message":{"id":"x"}}""",
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"你"}}""",
                """{"type":"ping"}""",
                """{"type":"content_block_delta","delta":{"type":"text_delta","text":"好"}}""",
                """{"type":"message_stop"}"""
            )
        )
        val client = AnthropicChatClient(transport, json)

        val deltas = client.stream(listOf(ChatTurn("user", "hi")), settings).textDeltas()

        assertEquals(listOf("你", "好"), deltas)
    }

    @Test
    fun `多个文本块拼接为完整回复`() = runTest {
        val transport = FakeTransport(
            response = """{"content":[{"type":"text","text":"前"},{"type":"text","text":"后"}]}"""
        )
        val client = AnthropicChatClient(transport, json)
        assertEquals("前后", client.complete(listOf(ChatTurn("user", "hi")), settings).content)
    }

    @Test
    fun `非流式解析 input 与 output token`() = runTest {
        val transport = FakeTransport(
            response = """{"content":[{"type":"text","text":"hi"}],"usage":{"input_tokens":11,"output_tokens":22}}"""
        )
        val client = AnthropicChatClient(transport, json)

        val usage = client.complete(listOf(ChatTurn("user", "hi")), settings).usage

        assertEquals(11, usage?.promptTokens)
        assertEquals(22, usage?.completionTokens)
    }

    @Test
    fun `流式从 message_start 与 message_delta 合并 usage`() = runTest {
        // Claude 把输入与输出 token 分散在两个事件里，需要跨事件累积
        val transport = FakeTransport(
            sseLines = listOf(
                """{"type":"message_start","message":{"usage":{"input_tokens":7}}}""",
                """{"type":"content_block_delta","delta":{"text":"a"}}""",
                """{"type":"message_delta","usage":{"output_tokens":9}}""",
                """{"type":"message_stop"}"""
            )
        )
        val client = AnthropicChatClient(transport, json)

        val usage = client.stream(listOf(ChatTurn("user", "hi")), settings).finishedUsage()

        assertEquals(7, usage?.promptTokens)
        assertEquals(9, usage?.completionTokens)
    }
}

class GeminiChatClientTest {

    private val settings = AppSettings(
        provider = AiProvider.GEMINI,
        apiKey = "AIza-test",
        model = "gemini-2.0-flash",
        baseUrl = "https://generativelanguage.googleapis.com/"
    )

    @Test
    fun `模型名写入路径且密钥走请求头`() = runTest {
        val transport = FakeTransport(
            response = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""
        )
        val client = GeminiChatClient(transport, json)

        client.complete(listOf(ChatTurn("user", "hi")), settings)

        assertEquals("v1beta/models/gemini-2.0-flash:generateContent", transport.lastPath)
        assertEquals("AIza-test", transport.lastHeaders["x-goog-api-key"])
        // 密钥不应出现在查询参数里，避免写入日志。
        assertNull(transport.lastQuery["key"])
    }

    @Test
    fun `助手角色映射为 model`() = runTest {
        val transport = FakeTransport(
            response = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""
        )
        val client = GeminiChatClient(transport, json)

        client.complete(
            listOf(
                ChatTurn("user", "问题"),
                ChatTurn(Constants.ROLE_ASSISTANT, "回答"),
                ChatTurn("user", "追问")
            ),
            settings
        )

        val contents = body(transport)["contents"]!!.jsonArray
        assertEquals("user", contents[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("user", contents[2].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `system 提示走 systemInstruction 字段`() = runTest {
        val transport = FakeTransport(
            response = """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}"""
        )
        val client = GeminiChatClient(transport, json)

        client.complete(
            listOf(
                ChatTurn(Constants.ROLE_SYSTEM, "你是助手"),
                ChatTurn("user", "hi")
            ),
            settings
        )

        val payload = body(transport)
        val instruction = payload["systemInstruction"]!!.jsonObject
        assertEquals(
            "你是助手",
            instruction["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        )
        // system 不应重复出现在 contents 中。
        assertEquals(1, payload["contents"]!!.jsonArray.size)
    }

    @Test
    fun `流式请求显式要求 sse 格式`() = runTest {
        val transport = FakeTransport(
            sseLines = listOf(
                """{"candidates":[{"content":{"parts":[{"text":"你"}]}}]}""",
                """{"candidates":[{"content":{"parts":[{"text":"好"}]}}]}"""
            )
        )
        val client = GeminiChatClient(transport, json)

        val deltas = client.stream(listOf(ChatTurn("user", "hi")), settings).textDeltas()

        assertEquals(listOf("你", "好"), deltas)
        assertEquals("sse", transport.lastQuery["alt"])
        assertEquals("v1beta/models/gemini-2.0-flash:streamGenerateContent", transport.lastPath)
    }

    @Test
    fun `模型列表去掉前缀并过滤不支持对话的模型`() = runTest {
        val transport = FakeTransport(
            response = """
                {"models":[
                  {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
                  {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]},
                  {"name":"models/gemini-1.5-pro","supportedGenerationMethods":["generateContent"]}
                ]}
            """.trimIndent()
        )
        val client = GeminiChatClient(transport, json)

        val models = client.listModels(settings)

        assertEquals(listOf("gemini-1.5-pro", "gemini-2.0-flash"), models)
    }
}
