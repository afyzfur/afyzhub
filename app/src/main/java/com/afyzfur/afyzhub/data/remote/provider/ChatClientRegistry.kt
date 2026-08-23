package com.afyzfur.afyzhub.data.remote.provider

import com.afyzfur.afyzhub.domain.model.AiProvider

/**
 * 按提供商分发对话客户端。
 *
 * 上层只需给出当前提供商，无需感知具体实现类。
 */
class ChatClientRegistry(
    private val clients: Map<AiProvider, ChatClient>
) {
    fun clientFor(provider: AiProvider): ChatClient =
        clients[provider]
            ?: throw IllegalStateException("暂不支持的提供商：${provider.displayName}")
}
