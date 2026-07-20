package com.afyzhub.aichat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("afyzhub_local_data", Context.MODE_PRIVATE)

    fun loadConfig(): ApiConfig = ApiConfig(
        baseUrl = preferences.getString("base_url", null)
            ?: "https://api.openai.com/v1",
        model = preferences.getString("model", null) ?: "gpt-4o-mini",
        systemPrompt = preferences.getString("system_prompt", null) ?: "",
        temperature = preferences.getFloat("temperature", 0.7f),
        maxTokens = preferences.getInt("max_tokens", 2048),
        useStream = preferences.getBoolean("use_stream", true),
        themeSeedColor = preferences.getLong("theme_seed_color", -1L)
            .takeIf { it != -1L },
        contextMode = runCatching {
            ContextMode.valueOf(
                preferences.getString("context_mode", null) ?: ContextMode.LIMITED.name
            )
        }.getOrDefault(ContextMode.LIMITED),
        contextLimit = preferences.getInt("context_limit", 32_768)
            .let { if (it < 1_000) 32_768 else it } // 迁移：旧版存的是条数，纠正为长度
    )

    fun saveConfig(config: ApiConfig) {
        preferences.edit()
            .putString("base_url", config.baseUrl)
            .putString("model", config.model)
            .putString("system_prompt", config.systemPrompt)
            .putFloat("temperature", config.temperature)
            .putInt("max_tokens", config.maxTokens)
            .putBoolean("use_stream", config.useStream)
            .putLong("theme_seed_color", config.themeSeedColor ?: -1L)
            .putString("context_mode", config.contextMode.name)
            .putInt("context_limit", config.contextLimit)
            .apply()
    }

    fun loadConversations(): List<Conversation> = try {
        val array = JSONArray(preferences.getString("conversations", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val messagesJson = item.optJSONArray("messages") ?: JSONArray()
            val messages = List(messagesJson.length()) { messageIndex ->
                val message = messagesJson.getJSONObject(messageIndex)
                ChatMessage(
                    id = message.getString("id"),
                    role = message.getString("role"),
                    content = message.getString("content")
                )
            }
            Conversation(
                id = item.getString("id"),
                title = item.optString("title", "新对话"),
                messages = messages
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveConversations(conversations: List<Conversation>) {
        val array = JSONArray()
        conversations.forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { message ->
                messages.put(
                    JSONObject()
                        .put("id", message.id)
                        .put("role", message.role)
                        .put("content", message.content)
                )
            }
            array.put(
                JSONObject()
                    .put("id", conversation.id)
                    .put("title", conversation.title)
                    .put("messages", messages)
            )
        }
        preferences.edit().putString("conversations", array.toString()).apply()
    }

    fun clearConversations() {
        preferences.edit().remove("conversations").apply()
    }
}