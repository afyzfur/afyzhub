package com.afyzfur.afyzhub.data.log

import java.util.concurrent.TimeUnit

/**
 * 请求日志的保留时长。
 *
 * 默认永久保留，只在用户手动删除时才清掉。自动过期的问题是它会在
 * 用户不知情时移除记录——而日志的用途恰恰是"隔了一阵回头查"，等到
 * 想查时发现被清了，这个功能就等于没有。存储增长由条数上限兜住，
 * 不需要再用时间兜一层。
 *
 * 按时长过期仍作为可选项保留：确实有人不想让日志一直堆着。
 */
enum class LogRetention(
    val id: String,
    val label: String,
    /** 保留的毫秒数，null 表示永久 */
    val durationMs: Long?
) {
    FOREVER("forever", "一直保留", null),
    DAY("1d", "保留 1 天", TimeUnit.DAYS.toMillis(1)),
    WEEK("7d", "保留 7 天", TimeUnit.DAYS.toMillis(7)),
    MONTH("30d", "保留 30 天", TimeUnit.DAYS.toMillis(30));

    companion object {
        /**
         * 默认一直保留。
         *
         * 排在第一位而非最后：它是默认项，也是多数情况下想要的行为。
         * 自动过期会在用户不知情时删掉记录，而日志的价值就在于事后
         * 还能查到。
         */
        val DEFAULT = FOREVER

        fun fromId(id: String?): LogRetention =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 日志的时间分组。
 *
 * 按"多久以前"而非具体日期分组：排查时的问法是"今天出的"或
 * "前几天那次"，很少精确到某月某日。
 */
enum class LogAgeGroup(val label: String) {
    TODAY("今天"),
    YESTERDAY("昨天"),
    THIS_WEEK("本周"),
    EARLIER("更早");

    companion object {
        /**
         * 按时间戳归组。
         *
         * [now] 由调用方传入而非内部取当前时间，使这个函数可测——
         * 依赖真实时钟的分组逻辑没法写断言。
         */
        fun of(timestamp: Long, now: Long): LogAgeGroup {
            val dayMs = TimeUnit.DAYS.toMillis(1)
            // 用"距今多久"而非日历日：跨零点时按日历日会把两分钟前的
            // 记录归到"昨天"，而用户感知上那还是刚刚
            val age = now - timestamp
            return when {
                age < dayMs -> TODAY
                age < dayMs * 2 -> YESTERDAY
                age < dayMs * 7 -> THIS_WEEK
                else -> EARLIER
            }
        }
    }
}

/**
 * 日志筛选条件。
 *
 * 三个维度可叠加。全为 null 表示不筛选。
 */
data class LogFilter(
    val ageGroup: LogAgeGroup? = null,
    val model: String? = null,
    val provider: String? = null,
    /** 只看失败的 */
    val failedOnly: Boolean = false
) {
    val isEmpty: Boolean
        get() = ageGroup == null && model == null && provider == null && !failedOnly
}

/**
 * 按条件筛选日志。
 *
 * 抽成纯函数便于测试，也让界面层不必关心匹配规则。
 */
fun filterLogs(
    entries: List<RequestLogEntry>,
    filter: LogFilter,
    now: Long
): List<RequestLogEntry> {
    if (filter.isEmpty) return entries
    return entries.filter { entry ->
        if (filter.failedOnly && entry.isSuccess) return@filter false
        if (filter.ageGroup != null &&
            LogAgeGroup.of(entry.startedAt, now) != filter.ageGroup
        ) {
            return@filter false
        }
        if (filter.model != null && entry.model != filter.model) return@filter false
        if (filter.provider != null && entry.provider != filter.provider) return@filter false
        true
    }
}

/**
 * 找出早于保留期限的记录。
 *
 * 返回要删除的那些，而不是直接返回保留的——调用方需要知道删了多少
 * 条才能给出反馈。
 */
fun expiredLogs(
    entries: List<RequestLogEntry>,
    retention: LogRetention,
    now: Long
): List<RequestLogEntry> {
    val duration = retention.durationMs ?: return emptyList()
    return entries.filter { now - it.startedAt > duration }
}
