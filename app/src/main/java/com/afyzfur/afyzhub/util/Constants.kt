package com.afyzfur.afyzhub.util

object Constants {
    const val DATABASE_NAME = "afyzhub_database"

    // 默认地址与模型由 AiProvider 按提供商给出，此处不再重复定义。

    /** 单次请求携带的历史消息条数上限，避免 token 超限。 */
    const val MAX_CONTEXT_MESSAGES = 20

    const val PREFS_NAME = "afyzhub_preferences"

    /** 当前选中的提供商 id，取值见 AiProvider。 */
    const val KEY_PROVIDER = "selected_provider"
    const val KEY_STREAM_ENABLED = "stream_enabled"

    /**
     * 以下三项按提供商分别存储，键名格式为 `前缀_提供商id`。
     * 切换提供商时不会互相覆盖凭证与地址。
     */
    const val KEY_PREFIX_API_KEY = "api_key"
    const val KEY_PREFIX_MODEL = "model"
    const val KEY_PREFIX_BASE_URL = "base_url"

    /**
     * 缓存的模型列表，按提供商分别存储，换行分隔。
     *
     * 缓存后重进设置页或重启应用列表仍在，只在用户主动刷新时重新拉取。
     */
    const val KEY_PREFIX_MODEL_LIST = "model_list"

    /** v0.1.3 及之前只存单份 OpenAI 配置，升级时迁移到新键名。 */
    const val LEGACY_KEY_API_KEY = "openai_api_key"
    const val LEGACY_KEY_MODEL = "selected_model"
    const val LEGACY_KEY_BASE_URL = "api_base_url"

    /** 消息发送状态 */
    const val STATUS_SENDING = "sending"
    const val STATUS_SUCCESS = "success"
    const val STATUS_FAILED = "failed"

    /** 消息角色 */
    const val ROLE_USER = "user"
    const val ROLE_ASSISTANT = "assistant"

    /**
     * system 角色。
     *
     * 目前尚无界面入口设置系统提示，但 Claude 与 Gemini 要求把它放在
     * 独立字段而非消息列表中，因此在协议层先统一识别该角色。
     */
    const val ROLE_SYSTEM = "system"

    /**
     * 单次回复的最大 token 数。
     *
     * Anthropic 的 max_tokens 是必填项，缺失会返回 400；
     * OpenAI 与 Gemini 不填则由服务端取默认值。
     */
    const val DEFAULT_MAX_TOKENS = 4096
}
