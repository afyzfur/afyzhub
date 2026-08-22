package com.afyzfur.afyzhub.util

object Constants {
    const val DATABASE_NAME = "afyzhub_database"

    /** 默认 API 地址，可在设置中覆盖为中转地址。必须以 "/" 结尾。 */
    const val DEFAULT_BASE_URL = "https://api.openai.com/"
    const val DEFAULT_MODEL = "gpt-3.5-turbo"

    /** 单次请求携带的历史消息条数上限，避免 token 超限。 */
    const val MAX_CONTEXT_MESSAGES = 20

    const val PREFS_NAME = "afyzhub_preferences"
    const val KEY_API_KEY = "openai_api_key"
    const val KEY_MODEL = "selected_model"
    const val KEY_BASE_URL = "api_base_url"
    const val KEY_STREAM_ENABLED = "stream_enabled"

    /** 消息发送状态 */
    const val STATUS_SENDING = "sending"
    const val STATUS_SUCCESS = "success"
    const val STATUS_FAILED = "failed"

    /** 消息角色 */
    const val ROLE_USER = "user"
    const val ROLE_ASSISTANT = "assistant"
}
