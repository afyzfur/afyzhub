package com.afyzfur.afyzhub.data.log

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.parseToJsonElement

private val BODY_SENSITIVE_KEYS = setOf("api_key", "apikey", "api-key", "key", "token", "access_token", "authorization")

/** Redacts credential fields in structured JSON request bodies such as Tavily requests. */
fun redactRequestBody(body: String?): String? {
    if (body.isNullOrBlank()) return body
    return runCatching {
        redactJson(parseToJsonElement(body), parentKey = null).toString()
    }.getOrElse {
        // If a body is not JSON, avoid logging it rather than guessing which text is secret.
        "[request body redacted]"
    }
}

private fun redactJson(element: JsonElement, parentKey: String?): JsonElement = when (element) {
    is JsonObject -> buildJsonObject {
        element.forEach { (key, value) ->
            put(key, if (key.lowercase() in BODY_SENSITIVE_KEYS) JsonPrimitive(REDACTED) else redactJson(value, key))
        }
    }
    is JsonArray -> buildJsonArray { element.forEach { add(redactJson(it, parentKey)) } }
    else -> element
}
