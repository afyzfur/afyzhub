package com.afyzfur.afyzhub.ui.settings

/**
 * 滑块该显示哪个值。
 *
 * 关键在于"抬手之后"这段空窗期：抬手时才发起落盘，而落盘要经过协程
 * 调度、写磁盘、Flow 回传，期间外部值还是拖动之前的旧值。若此刻就把
 * 显示权交回外部，thumb 会先跳回原位再跳到新位置——这就是松手时那下
 * 卡顿。
 *
 * 所以判断依据不是"手指还在不在滑块上"，而是"外部值是否已经追上本地
 * 值"。追上之前一律显示本地值，追上之后两者相等，显示哪个都一样。
 *
 * 这样也不需要 dragging 标记了：本地值与外部值一致就说明没有未落盘的
 * 改动，此时外部值的任何变化都是别处改的、该采纳。
 */
internal fun sliderDisplayValue(
    external: Float,
    local: Float,
    /** 本地值是否有尚未落盘的改动 */
    pending: Boolean
): Float = if (pending) local else external

/**
 * 是否应当把外部值同步到本地状态。
 *
 * 仅在没有待落盘改动、且两者确实不同时同步。
 *
 * "确实不同"这层判断避免每次重组都写一遍本地状态——写入相同的值也会
 * 让状态标记为已变更。
 */
internal fun shouldAdoptExternal(
    external: Float,
    local: Float,
    pending: Boolean
): Boolean = !pending && local != external

/**
 * 本地改动是否已经落盘完成。
 *
 * 用近似比较而非直接相等：滑块的值经过 DataStore 的浮点序列化往返，
 * 回来的值可能与写出去的差一个最小精度单位。直接用 != 判断会让
 * pending 永远为真，滑块从此再也不接受外部更新。
 */
internal fun isSettled(external: Float, local: Float): Boolean =
    kotlin.math.abs(external - local) < SETTLE_EPSILON

/** 判定"已落盘"的容差，取值远小于一个百分点 */
private const val SETTLE_EPSILON = 0.0005f
