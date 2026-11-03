package com.afyzfur.afyzhub.data.log

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private const val REDACTED_REQUEST_VALUE = "***"
private val BODY_SENSITIVE_KEYS = setOf("api_key", "apikey", "api-key", "key", "token", "access_token", "authorization")

/** Redacts credential fields in structured JSON request bodies such as Tavily requests. */
fun redactRequestBody(body: String?): String? {
    if (body.isNullOrBlank()) return body
    return runCatching {
        val parsed = Json.parseToJsonElement(body)
        if (parsed is JsonObject || parsed is JsonArray) redactJson(parsed).toString()
        else "[request body redacted]"
    }.getOrElse {
        // If a body is not JSON, avoid logging it rather than guessing which text is secret.
        "[request body redacted]"
    }
}

private fun redactJson(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> buildJsonObject {
        element.forEach { (key, value) ->
            put(key, if (key.lowercase() in BODY_SENSITIVE_KEYS) JsonPrimitive(REDACTED_REQUEST_VALUE) else redactJson(value))
        }
    }
    is JsonArray -> buildJsonArray { element.forEach { add(redactJson(it)) } }
    else -> element
}
