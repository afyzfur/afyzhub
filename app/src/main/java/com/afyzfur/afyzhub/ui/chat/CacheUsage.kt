package com.afyzfur.afyzhub.ui.chat

/** null means the provider did not report cache use; zero means it explicitly reported a miss. */
internal fun cacheUsageText(cached: Int?, input: Int?): String {
    if (cached == null) return "cache: 未获取统计"
    val percentage = input?.takeIf { it > 0 }?.let { (cached.toLong() * 100 + it / 2) / it }
    return if (percentage != null) "cache: $cached (hit: $percentage%)" else "cache: $cached"
}
