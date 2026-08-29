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

    // 外观。全局项，不按提供商区分
    const val KEY_COLOR_MODE = "color_mode"
    const val KEY_DYNAMIC_COLOR = "dynamic_color"

    // 消息元信息显示开关。分项控制而非一个总开关，
    // 使轻度用户保持界面安静、重度用户可开出完整调试视图
    const val KEY_SHOW_TIMESTAMP = "show_timestamp"
    const val KEY_SHOW_TOKEN_USAGE = "show_token_usage"
    const val KEY_SHOW_SPEED = "show_speed"
    const val KEY_SHOW_LATENCY = "show_latency"
    const val KEY_SHOW_ACTIONS = "show_actions"
    const val KEY_SHOW_MODEL_NAME = "show_model_name"

    /** 首屏提示词，换行分隔 */
    const val KEY_QUICK_PROMPTS = "quick_prompts"
    const val KEY_SHUFFLE_PROMPTS = "shuffle_prompts"

    /** 预设配色，动态取色关闭时生效 */
    const val KEY_PALETTE = "theme_palette"

    // 聊天外观
    const val KEY_USER_BUBBLE = "user_bubble_style"
    const val KEY_ASSISTANT_BUBBLE = "assistant_bubble_style"
    const val KEY_AVATAR_MODE = "avatar_mode"
    const val KEY_USER_AVATAR_PATH = "user_avatar_path"
    const val KEY_ASSISTANT_AVATAR_PATH = "assistant_avatar_path"
    const val KEY_BACKGROUND_MODE = "chat_background_mode"
    const val KEY_BACKGROUND_PATH = "chat_background_path"
    const val KEY_BACKGROUND_DIM = "chat_background_dim"
    const val KEY_IMAGE_VERSION = "chat_image_version"
    const val KEY_SHOW_USER_AVATAR = "show_user_avatar"
    const val KEY_SHOW_ASSISTANT_AVATAR = "show_assistant_avatar"
    const val KEY_TRANSPARENT_TOP_BAR = "transparent_top_bar"
    const val KEY_TRANSPARENT_INPUT_BAR = "transparent_input_bar"
    const val KEY_BACKGROUND_EFFECT = "chat_background_effect"
    const val KEY_BACKGROUND_BLUR = "chat_background_blur"
    const val KEY_AVATAR_BLUR = "chat_avatar_blur"

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

    /** 思考程度。全局项，不随配置组切换 */
    const val KEY_THINKING_EFFORT = "thinking_effort"

    /** 多组 API 配置，整体以 JSON 存一个键 */
    const val KEY_API_PROFILES = "api_profiles"

    /** 旧的单组配置是否已迁移成多组。只做一次，之后不再读旧键 */
    const val KEY_PROFILES_MIGRATED = "api_profiles_migrated"

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
