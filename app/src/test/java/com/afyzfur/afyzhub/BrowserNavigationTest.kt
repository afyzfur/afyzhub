package com.afyzfur.afyzhub

import com.afyzfur.afyzhub.ui.browser.browserTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationTest {
    @Test fun normalizesInitialAndEditedAddressesTheSameWay() {
        assertEquals("https://example.com/a?b=1#part", browserTarget(" example.com/a?b=1#part ", "bing"))
        assertEquals("https://example.com", browserTarget("//example.com", "bing"))
        assertEquals("https://localhost:8080/test", browserTarget("localhost:8080/test", "bing"))
        assertEquals("https://example.com:8080/test", browserTarget("example.com:8080/test", "bing"))
        assertEquals("https://example.com", browserTarget("https://example.com", "bing"))
    }
    @Test fun searchUsesSelectedEngineAndEncodesQueries() {
        assertTrue(checkNotNull(browserTarget("Android Compose", "google")).startsWith("https://www.google.com/search?q=Android+Compose"))
        assertTrue(checkNotNull(browserTarget("天气", "baidu")).startsWith("https://www.baidu.com/s?wd=%"))
        assertTrue(checkNotNull(browserTarget("天气", "tavily")).startsWith("https://www.bing.com/search?q="))
        assertTrue(checkNotNull(browserTarget("version 1.2", "bing")).contains("version+1.2"))
    }
    @Test fun rejectsInvalidAndExecutableAddresses() {
        for (raw in listOf("", "https://", "https://bad host", "javascript:alert(1)", "data:text/html,hi", "file:///sdcard/a", "intent://example")) {
            assertNull(raw, browserTarget(raw, "bing"))
        }
    }
}
