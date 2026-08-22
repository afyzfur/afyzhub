package com.afyzfur.afyzhub.data.settings

/**
 * 只读的设置来源。
 *
 * 抽出该接口是为了让数据层不直接依赖 DataStore，从而可以在 JVM 单元测试中替换。
 */
interface SettingsProvider {
    suspend fun current(): AppSettings
}
