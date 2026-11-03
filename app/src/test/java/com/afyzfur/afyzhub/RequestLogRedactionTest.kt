package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.data.log.redactRequestBody
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestLogRedactionTest {
    @Test fun jsonCredentialFieldsAreRedactedWithoutChangingOtherFields() {
        val body = """{"api_key":"secret","query":"天气","nested":{"token":"nested-secret","value":"keep"}}"""
        val redacted = redactRequestBody(body).orEmpty()
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("nested-secret"))
        assertEquals("天气", kotlinx.serialization.json.Json.parseToJsonElement(redacted).jsonObject["query"]?.jsonPrimitive?.content)
        assertEquals("keep", kotlinx.serialization.json.Json.parseToJsonElement(redacted).jsonObject["nested"]?.jsonObject?.get("value")?.jsonPrimitive?.content)
    }

    @Test fun invalidRequestBodyIsNotLoggedVerbatim() {
        val redacted = redactRequestBody("api_key=secret&query=weather")
        assertEquals("[request body redacted]", redacted)
    }
}
