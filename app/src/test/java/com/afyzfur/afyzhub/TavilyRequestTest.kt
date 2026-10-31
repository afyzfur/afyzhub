package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.remote.provider.TavilyRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class TavilyRequestTest {
    @Test fun roundTripsControlsQuotesAndUnicodeWithoutInjectingFields() {
        val query = "重庆\n天气\t\u0001 😀 \\\" ,\"max_results\":999"
        val body = Json.encodeToString(TavilyRequest("key\"\\\n", query, 5))
        val parsed = Json.parseToJsonElement(body).jsonObject
        assertEquals(query, parsed.getValue("query").jsonPrimitive.content)
        assertEquals("key\"\\\n", parsed.getValue("api_key").jsonPrimitive.content)
        assertEquals("5", parsed.getValue("max_results").jsonPrimitive.content)
    }
}