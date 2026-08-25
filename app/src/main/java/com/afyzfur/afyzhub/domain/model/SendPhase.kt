package com.afyzfur.afyzhub.domain.model

/**
 * 发送过程的阶段。
 *
 * 界面据此显示具体在做什么，而非笼统的"发送中"。区分这几个阶段
 * 是因为它们的耗时特征不同：连接受网络影响、等待首字取决于模型
 * 排队与推理速度、接收阶段则已有内容在陆续显现。卡在哪一步
 * 对判断问题出在哪很有用。
 */
enum class SendPhase {
    /** 空闲，没有进行中的请求 */
    IDLE,

    /** 正在建立连接、发出请求 */
    CONNECTING,

    /** 请求已发出，等待模型返回首个内容片段 */
    WAITING,

    /** 正在接收流式内容 */
    RECEIVING;

    /** 是否有进行中的请求。用于控制发送按钮与暂停按钮的切换 */
    val isActive: Boolean get() = this != IDLE

    /** 界面展示用的文字 */
    val label: String
        get() = when (this) {
            IDLE -> ""
            CONNECTING -> "正在连接服务器"
            WAITING -> "正在等待模型响应"
            RECEIVING -> "正在接收回复"
        }
}
