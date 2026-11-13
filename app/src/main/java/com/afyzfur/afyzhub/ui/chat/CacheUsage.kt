package com.afyzfur.afyzhub.ui.chat

/** null is unreported; zero is an explicit cache miss. */
internal fun cacheUsageText(cached: Int?, input: Int?): String {
    if (cached == null) return "cache: n/a"
    val percentage = input?.takeIf { it > 0 }?.let { (cached.toLong() * 100 + it / 2) / it }
    return if (percentage != null) "cache: $cached (hit: $percentage%)" else "cache: $cached"
}
