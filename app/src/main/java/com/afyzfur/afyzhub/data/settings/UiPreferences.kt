package com.afyzfur.afyzhub.data.settings

/** 颜色模式。 */
enum class ColorMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        val DEFAULT = SYSTEM
        fun fromId(id: String?): ColorMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 消息元信息的显示开关。
 *
 * 分项而非单个总开关：参考对象把 token 用量、缓存命中、吐字速度、耗时、
 * 完整时间戳和操作行全部常驻，一条消息带三行元信息，默认状态偏拥挤。
 * 这里把暴露程度交给用户决定——默认只开时间戳与操作行，
 * 需要调试的用户可以全部打开。
 *
 * [showTokenUsage] / [showSpeed] / [showLatency] 依赖阶段 6 的 Room 字段，
 * 历史消息缺少数据时对应项不显示。
 */
data class MessageDisplayOptions(
    val showTimestamp: Boolean = true,
    val showActions: Boolean = true,
    val showModelName: Boolean = false,
    val showTokenUsage: Boolean = false,
    val showSpeed: Boolean = false,
    val showLatency: Boolean = false
) {
    /** 是否有任何依赖 usage 数据的项被打开，用于跳过整行渲染 */
    val anyUsageShown: Boolean
        get() = showTokenUsage || showSpeed || showLatency
}

/**
 * 界面偏好。与 [AppSettings] 分开：后者是"以什么配置发请求"，
 * 这里是"界面怎么显示"，两者的读取方与变更频率都不同。
 */
data class UiPreferences(
    val colorMode: ColorMode = ColorMode.DEFAULT,
    /** Android 12+ 生效，取系统壁纸配色 */
    val dynamicColor: Boolean = true,
    val messageDisplay: MessageDisplayOptions = MessageDisplayOptions(),
    /** 空会话首屏的提示词 chip */
    val quickPrompts: List<String> = DefaultQuickPrompts
)

/** 首次安装时的内置提示词。 */
val DefaultQuickPrompts = listOf(
    "帮我总结一段文字",
    "解释一个概念",
    "润色这段话",
    "写一段代码"
)
