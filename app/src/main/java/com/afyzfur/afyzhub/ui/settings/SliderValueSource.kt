package com.afyzfur.afyzhub.ui.settings

/**
 * 滑块在拖动期间该显示哪个值。
 *
 * 单独抽成纯函数是因为这里的取舍容易搞反，而搞反的表现又很隐蔽：
 * 拖动中若采纳外部值，落盘往返回来的旧值会把 thumb 拽回去（表现为
 * 不跟手）；非拖动时若不采纳外部值，别处改了设置这个滑块不会更新
 * （表现为界面不同步）。两个方向都错得不明显，用测试钉住。
 *
 * 与聊天输入栏是同一个模式：打字期间本地状态是唯一来源，外部值的
 * 每次变化都是自己刚写出去的回声。
 */
internal fun sliderDisplayValue(
    external: Float,
    local: Float,
    dragging: Boolean
): Float = if (dragging) local else external

/**
 * 是否应当把外部值同步到本地状态。
 *
 * 仅在没有拖动且两者确实不同时同步。加"确实不同"这层判断是为了避免
 * 每次重组都写一遍本地状态——写入相同的值也会让状态标记为已变更。
 */
internal fun shouldAdoptExternal(
    external: Float,
    local: Float,
    dragging: Boolean
): Boolean = !dragging && local != external
