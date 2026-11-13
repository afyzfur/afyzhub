package com.afyzfur.afyzhub.ui.browser

import java.net.URI
import java.net.URLEncoder

/** Same normalization for the initial link and address-bar submissions. Null means unsupported input. */
internal fun browserTarget(input: String, searchEngine: String?): String? {
    val raw = input.trim()
    if (raw.isEmpty()) return null
    val explicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(raw)
    if (explicitScheme || raw.startsWith("http:", true) || raw.startsWith("https:", true)) {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        return raw.takeIf { uri.scheme.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() }
    }
    if (raw.startsWith("//")) return browserTarget("https:$raw", searchEngine)
    // A host:port is an address; other URI schemes must never be treated as a search or loaded as a page.
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(raw) &&
        !Regex("^[^/\\s:]+:\\d+(?:/.*)?$").matches(raw)) return null
    if (raw.none { it.isWhitespace() }) {
        val uri = runCatching { URI("https://$raw") }.getOrNull()
        val host = uri?.host
        if (host != null && (host.contains('.') || host == "localhost" || host.contains(':'))) return "https://$raw"
    }
    val query = URLEncoder.encode(raw, "UTF-8")
    return when (searchEngine) {
        "baidu" -> "https://www.baidu.com/s?wd=$query"
        "google" -> "https://www.google.com/search?q=$query"
        // Tavily is an API, so browser searches use Bing rather than treating it as a webpage.
        else -> "https://www.bing.com/search?q=$query"
    }
}
