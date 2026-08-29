package com.afyzfur.afyzhub.data.remote.provider

/**
 * 把分离到独立通道的流式思考内容折回内嵌标签形式。
 *
 * Claude 与 Gemini 都不在正文里内嵌思考标签，而是用独立的
 * 事件类型（thinking_delta）或字段标记（thought: true）把思考
 * 与正文分开推送。上层只认内嵌标签一种形式，所以在这层折回来。
 *
 * 不能逐块各自包一层标签：那样会得到一串首尾相接的小标签，
 * 而上层的解析只认成对出现的一整段。正确做法是在第一块思考
 * 到来时开标签、在思考结束转正文时闭标签，中间的内容原样透传。
 *
 * 本类持有状态，每次请求都要新建一个。
 */
class ThinkingStreamWrapper {

    private var inThinking = false
    private var closed = false

    /**
     * 处理一块思考内容，返回应当发给上层的文本。
     *
     * 第一次调用会带上开标签。
     */
    fun onThinking(delta: String): String {
        if (delta.isEmpty() || closed) return ""
        return if (inThinking) {
            delta
        } else {
            inThinking = true
            "$OPEN$delta"
        }
    }

    /**
     * 处理一块正文，返回应当发给上层的文本。
     *
     * 若此前有过思考内容，会先补上闭标签。闭标签只补一次：
     * 模型可能在正文中途再次思考，但那属于同一段推理的延续，
     * 反复开闭会产生多段互相嵌套的标签让解析失效。
     */
    fun onText(delta: String): String {
        if (delta.isEmpty()) return ""
        return if (inThinking && !closed) {
            closed = true
            "$CLOSE$delta"
        } else {
            delta
        }
    }

    /**
     * 流结束时补齐未闭合的标签。
     *
     * 全程都在思考、一个字正文都没有的情况确实存在——被
     * token 上限截断时就是这样。此时留下的半个标签会原样
     * 显示在气泡里，所以要在这里补上。
     */
    fun finish(): String = if (inThinking && !closed) {
        closed = true
        CLOSE
    } else {
        ""
    }

    private companion object {
        const val OPEN = "<think>"
        const val CLOSE = "</think>"
    }
}
