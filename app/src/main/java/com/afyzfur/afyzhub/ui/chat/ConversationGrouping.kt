package com.afyzfur.afyzhub.ui.chat

import com.afyzfur.afyzhub.domain.model.ConversationItem
import java.util.Calendar

/**
 * 抽屉会话列表的时间分组。
 *
 * 分档参考常见做法：今天 / 昨天 / 本周 / 本月 / 更早。
 * 不做"3 天前"这类相对天数，档位过细反而增加扫视成本。
 */
enum class ConversationGroup(val label: String) {
    TODAY("今天"),
    YESTERDAY("昨天"),
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    EARLIER("更早")
}

/**
 * 按更新时间把会话分组，保持各组内原有的倒序。
 *
 * 返回 LinkedHashMap 以固定组的顺序（今天在最上），空组不出现。
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

    // 按枚举声明顺序输出，跳过空组
    return ConversationGroup.entries
        .mapNotNull { group -> grouped[group]?.let { group to it } }
        .toMap(LinkedHashMap())
}

/**
 * 各时间档的起始时刻，只在分组开始时计算一次。
 *
 * 逐条会话现算 Calendar 会产生大量临时对象，列表滚动时尤其明显。
 */
private class DayBounds(now: Long) {

    private val todayStart: Long
    private val yesterdayStart: Long
    private val weekStart: Long
    private val monthStart: Long

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

        // 回到今天再回退到本周第一天，避免受上面 -1 天的影响
        cal.timeInMillis = todayStart
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        weekStart = cal.timeInMillis

        cal.timeInMillis = todayStart
        cal.set(Calendar.DAY_OF_MONTH, 1)
        monthStart = cal.timeInMillis
    }

    fun groupOf(timestamp: Long): ConversationGroup = when {
        timestamp >= todayStart -> ConversationGroup.TODAY
        timestamp >= yesterdayStart -> ConversationGroup.YESTERDAY
        timestamp >= weekStart -> ConversationGroup.THIS_WEEK
        timestamp >= monthStart -> ConversationGroup.THIS_MONTH
        else -> ConversationGroup.EARLIER
    }
}
