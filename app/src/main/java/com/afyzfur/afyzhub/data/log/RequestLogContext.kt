package com.afyzfur.afyzhub.data.log

/**
 * 一次请求的归属信息。
 *
 * 传输层只知道 URL，而"这条请求属于哪个提供商、用的哪个模型"要到
 * 更上层才有。日志的筛选恰恰需要这两项——主机名区分不出同一中转
 * 地址下的不同模型。
 *
 * 显式随每次调用传入，而不是让传输层持有可变字段：并发请求共用同一个
 * 传输层实例，可变字段会互相污染，日志张冠李戴比没有日志更糟。
 */
data class RequestLogContext(
    /** 提供商标识，取 AiProvider 的 id */
    val provider: String? = null,
    /** 模型名，用户配置的原文 */
    val model: String? = null
) {
    companion object {
        /**
         * 缺省值。
         *
         * 存在是为了让传输层的旧调用点与测试里的假实现不必逐个改。
         * 落到日志里显示为"未知"，而不是让整条记录缺失。
         */
        val EMPTY = RequestLogContext()
    }
}
