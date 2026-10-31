package com.afyzfur.afyzhub.data.remote.provider

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException

/** Rate-limited diagnostics: no response body, exception message, URL or credential is logged. */
internal object ParseDiagnostics {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val logger = Logger.getLogger("AfyzHub.Parse")

    fun failed(provider: String, error: Exception) {
        if (error is CancellationException) throw error
        val count = counts.getOrPut(provider) { AtomicInteger() }.incrementAndGet()
        if (count == 1 || count % 100 == 0) {
            logger.warning("$provider response parse failure: ${error.javaClass.simpleName}; count=$count")
        }
    }
}