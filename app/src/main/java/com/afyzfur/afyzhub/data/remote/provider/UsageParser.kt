package com.afyzfur.afyzhub.data.remote.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Merge cumulative usage events without losing cache information supplied by an earlier event. */
internal fun mergeTokenUsage(a: TokenUsage?, b: TokenUsage?): TokenUsage? = when {
    a == null -> b
    b == null -> a
    else -> TokenUsage(
        maxOf(a.promptTokens, b.promptTokens),
        maxOf(a.completionTokens, b.completionTokens),
        listOfNotNull(a.cachedTokens, b.cachedTokens).maxOrNull()
    )
}

/** Read only known token fields. Cache creation/cost fields are never interpreted as cache hits. */
internal fun parseTokenUsage(payload: String, json: Json, anthropic: Boolean = false): TokenUsage? {
    val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
    val containers = listOfNotNull(
        root,
        root["message"] as? JsonObject,
        root["response"] as? JsonObject
    )
    var result: TokenUsage? = null
    for (container in containers) {
        for (key in listOf("usage", "usage_metadata", "usageMetadata")) {
            val usage = container[key] as? JsonObject ?: continue
            result = mergeTokenUsage(result, parseUsageObject(usage, anthropic))
        }
    }
    return result
}

private fun parseUsageObject(usage: JsonObject, anthropic: Boolean): TokenUsage? {
    fun number(value: JsonElement?): Int? = (value as? JsonPrimitive)?.contentOrNull
        ?.trim()?.toLongOrNull()?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    fun numbers(source: JsonObject, vararg names: String): List<Int> = names.mapNotNull { number(source[it]) }
    val details = listOf("prompt_tokens_details", "input_tokens_details", "details")
        .mapNotNull { usage[it] as? JsonObject }
    val cached = (
        numbers(usage, "prompt_cache_hit_tokens", "cache_read_input_tokens", "cached_tokens",
            "cache_hit_tokens", "prompt_cache_tokens", "cachedContentTokenCount", "cached_content_token_count") +
            details.flatMap { numbers(it, "cached_tokens", "cache_read_tokens", "cached_input_tokens", "cache_read_input_tokens") }
        ).maxOrNull()
    val prompt = numbers(usage, "prompt_tokens", "input_tokens", "promptTokenCount", "prompt_token_count").maxOrNull()
    val completion = numbers(usage, "completion_tokens", "output_tokens", "candidatesTokenCount", "candidates_token_count").maxOrNull()
    if (prompt == null && completion == null && cached == null) return null
    // Native Claude input_tokens excludes both cache reads and writes. OpenAI-compatible totals include them.
    val input = if (anthropic && usage["input_tokens"] != null && usage["prompt_tokens"] == null) {
        ((prompt ?: 0).toLong() + (cached ?: 0) + (number(usage["cache_creation_input_tokens"]) ?: 0))
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else prompt ?: 0
    return TokenUsage(input, completion ?: 0, cached)
}
