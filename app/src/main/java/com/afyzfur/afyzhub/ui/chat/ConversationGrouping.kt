package com.afyzfur.afyzhub.ui.chat

import com.afyzfur.afyzhub.domain.model.ConversationItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 抽屉会话列表的时间分组。
 *
 * 分档从近到远逐步变粗：今天、昨天、前天各自成组，本周内其余按星期几，
 * 更早的按日期。近期的会话用相对说法更好理解——"昨天"比具体日期
 * 更快对应到记忆；而超过一周后相对天数失去意义，日期反而清楚。
 *
 * 标签不放进枚举：星期几与日期取决于具体时间戳，只有分组时才能确定。
 */
data class ConversationGroup(
    /** 排序用的档位序号，越小越近 */
    val order: Int,
    val label: String
)

/**
 * 按更新时间把会话分组，保持各组内原有的倒序。
 *
 * 返回 LinkedHashMap 以固定组的顺序（最近的在最上），空组不出现。
 *
 * @param now 当前时间，可注入以便测试
 */
fun groupConversations(
    items: List<ConversationItem>,
    now: Long = System.currentTimeMillis()
): Map<ConversationGroup, List<ConversationItem>> {
    if (items.isEmpty()) return emptyMap()

    val bounds = DayBounds(now)

    // groupBy 保持原列表顺序，而列表已按 updatedAt 倒序，故各组内也是倒序
    val grouped = items.groupBy { bounds.groupOf(it.updatedAt) }

    // 按档位序号排序后输出。同一档位（如同周的不同星期几）
    // 靠序号里编入的天数偏移保持先后
    return grouped.entries
        .sortedBy { it.key.order }
        .associateTo(LinkedHashMap()) { it.key to it.value }
}

/**
 * 各时间档的起始时刻，只在分组开始时计算一次。
 *
 * 逐条会话现算 Calendar 会产生大量临时对象，列表滚动时尤其明显。
 */
private class DayBounds(now: Long) {

    private val todayStart: Long
    private val yesterdayStart: Long
    private val dayBeforeStart: Long
    private val weekStart: Long

    /** 复用同一个 Calendar 实例做标签格式化，避免逐条新建 */
    private val calendar: Calendar = Calendar.getInstance()

    init {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        todayStart = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        yesterdayStart = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        dayBeforeStart = cal.timeInMillis

        // 回到今天再取本周第一天，避免受上面回退的影响
        cal.timeInMillis = todayStart
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        weekStart = cal.timeInMillis
    }

    fun groupOf(timestamp: Long): ConversationGroup = when {
        timestamp >= todayStart -> ConversationGroup(0, "今天")
        timestamp >= yesterdayStart -> ConversationGroup(1, "昨天")
        timestamp >= dayBeforeStart -> ConversationGroup(2, "前天")

        // 本周内但早于前天，用星期几。序号加上距今天数，
        // 使同周的多个星期几之间也能正确排序
        timestamp >= weekStart -> ConversationGroup(
            order = 3 + daysAgo(timestamp),
            label = weekdayOf(timestamp)
        )

        // 跨周则用日期。序号统一取一个大于本周档的值，
        // 组内顺序由列表本身的倒序保证
        else -> ConversationGroup(
            order = 100 + daysAgo(timestamp),
            label = dateOf(timestamp)
        )
    }

    /** 距今天数，用于同档位内排序 */
    private fun daysAgo(timestamp: Long): Int =
        ((todayStart - timestamp) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)

    private fun weekdayOf(timestamp: Long): String {
        calendar.timeInMillis = timestamp
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "星期一"
            Calendar.TUESDAY -> "星期二"
            Calendar.WEDNESDAY -> "星期三"
            Calendar.THURSDAY -> "星期四"
            Calendar.FRIDAY -> "星期五"
            Calendar.SATURDAY -> "星期六"
            else -> "星期日"
        }
    }

    /**
     * 同年只显示月日，跨年补上年份。
     *
     * 同年时年份是冗余信息，省掉能让标签更短。
     */
    private fun dateOf(timestamp: Long): String {
        calendar.timeInMillis = timestamp
        val year = calendar.get(Calendar.YEAR)
        calendar.timeInMillis = todayStart
        val pattern = if (year == calendar.get(Calendar.YEAR)) "M月d日" else "yyyy年M月d日"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
