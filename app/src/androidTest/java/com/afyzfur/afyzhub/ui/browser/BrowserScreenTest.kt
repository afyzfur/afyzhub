package com.afyzfur.afyzhub.ui.browser

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }
    @Test fun runsScriptsTracksHistoryAndPreservesAddressEdits() {
        compose.setContent {
            MaterialTheme { InAppBrowserScreen("https://example.invalid", {}, searchEngineOverride = "bing") }
        }
        lateinit var web: WebView
        compose.runOnIdle {
            web = checkNotNull(findWebView(compose.activity.window.decorView))
            assertTrue(web.settings.javaScriptEnabled)
            web.stopLoading()
            web.loadDataWithBaseURL("https://example.test/", "<html><script>document.title='Script ready';</script><body>Local test</body></html>", "text/html", "UTF-8", null)
        }
        var ready = false
        compose.waitUntil(10_000) {
            compose.runOnUiThread { ready = web.title == "Script ready" }
            ready
        }
        compose.onNodeWithTag("browser-address").performClick().performTextReplacement("draft.example")
        compose.runOnIdle { web.evaluateJavascript("history.pushState({}, '', '/next');document.title='Next';", null) }
        var navigated = false
        compose.waitUntil(10_000) {
            compose.runOnUiThread { navigated = web.url?.endsWith("/next") == true && web.canGoBack() }
            navigated
        }
        compose.onNodeWithTag("browser-address").assertTextContains("draft.example")
        compose.runOnIdle { web.goBack() }
        var returned = false
        compose.waitUntil(10_000) {
            compose.runOnUiThread { returned = web.url == "https://example.test/" }
            returned
        }
    }
}
