package com.afyzfur.afyzhub.ui.components

/**
 * 需要跟随主题着色的单色图标。
 *
 * 这些 SVG 内部用 `fill="currentColor"`。SVG 作为独立文件加载时
 * 没有可继承的上下文颜色，Coil 会解析成默认黑色——在深色主题下
 * 与背景几乎同色，看不见。
 *
 * 彩色图标（Claude 的橙、DeepSeek 的蓝等）不在此列：它们有确定的
 * 品牌色，着色会破坏识别度，且本身在两种主题下都有足够对比。
 *
 * 判定方式是查 SVG 里有无 `currentColor`。新增图标时若属单色，
 * 必须同时加进这里，否则深色主题下不可见。由测试核对。
 */
internal val MONOCHROME_ICONS: Set<String> = setOf(
    "anthropic.svg",
    "groq.svg",
    "moonshot.svg",
    "ollama.svg",
    "openai.svg",
    "openrouter.svg",
    "xai.svg",
    // 橙色图形加单色文字。整体着色会覆盖橙色，
    // 但文字是主体，不着色则整段文字在深色下消失
    "cerebras-color.svg",
    // 主体 K 原本硬编码 #fff（原图为深色背景设计），浅色主题下
    // 整个字形消失。已把该 fill 改成 currentColor 纳入着色，
    // 蓝色圆点保留品牌色
    "kimi-color.svg"
)

/** 该图标是否需要按主题着色 */
internal fun needsThemeTint(iconAsset: String): Boolean =
    iconAsset in MONOCHROME_ICONS
