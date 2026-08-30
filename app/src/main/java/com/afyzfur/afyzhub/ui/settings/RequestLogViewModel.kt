package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.log.LogAgeGroup
import com.afyzfur.afyzhub.data.log.LogFilter
import com.afyzfur.afyzhub.data.log.LogRetention
import com.afyzfur.afyzhub.data.log.RequestLogEntry
import com.afyzfur.afyzhub.data.log.RequestLogStore
import com.afyzfur.afyzhub.data.log.filterLogs
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * 请求日志页的状态持有者。
 *
 * 日志本身存在单例 [RequestLogStore] 里，这里做筛选、删除与转发，
 * 因为日志需要跨页面生命周期存活。
 */
class RequestLogViewModel(
    private val store: RequestLogStore,
    private val settings: SettingsRepository
) : ViewModel() {

    /**
     * 记录开关与保留策略。
     *
     * 这两项放在日志页而非设置首页：它们只影响这一个页面里的数据，
     * 放远了要在两处之间来回跳才能理解彼此的关系。
     */
    val logEnabled: StateFlow<Boolean> = settings.logEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true
    )

    val retention: StateFlow<LogRetention> = settings.logRetention.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), LogRetention.DEFAULT
    )

    fun setLogEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLogEnabled(enabled) }
    }

    /**
     * 改保留策略并立刻按新策略清一次。
     *
     * 不等下次启动：用户刚把"一直保留"改成"保留 1 天"，期望马上看到
     * 旧记录消失，否则会以为设置没生效。
     */
    fun setRetention(value: LogRetention) {
        viewModelScope.launch {
            settings.setLogRetention(value)
            store.purgeExpired(value)
        }
    }

    private val _filter = MutableStateFlow(LogFilter())
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    /** 未筛选的全部记录，用于统计可选的模型与提供商 */
    val allEntries: StateFlow<List<RequestLogEntry>> = store.entries

    /**
     * 筛选后的记录。
     *
     * 时间基准在组合时取一次而非每次重组取：否则同一批数据在相邻两帧
     * 可能落进不同的时间分组，列表会自己跳动。
     */
    val entries: StateFlow<List<RequestLogEntry>> =
        combine(store.entries, _filter) { list, f ->
            filterLogs(list, f, System.currentTimeMillis())
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /**
     * 当前数据里出现过的模型与提供商。
     *
     * 从实际记录里取而不是列出全部已配置项：没有请求记录的模型放在
     * 筛选栏里只会占位置，选中后必然是空列表。
     */
    val availableModels: StateFlow<List<String>> =
        store.entries.map { list ->
            list.mapNotNull { it.model }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableProviders: StateFlow<List<String>> =
        store.entries.map { list ->
            list.mapNotNull { it.provider }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAgeGroup(group: LogAgeGroup?) {
        _filter.value = _filter.value.copy(ageGroup = group)
    }

    fun setModel(model: String?) {
        _filter.value = _filter.value.copy(model = model)
    }

    fun setProvider(provider: String?) {
        _filter.value = _filter.value.copy(provider = provider)
    }

    fun setFailedOnly(enabled: Boolean) {
        _filter.value = _filter.value.copy(failedOnly = enabled)
    }

    fun resetFilter() {
        _filter.value = LogFilter()
    }

    /**
     * 删掉当前筛选结果里的全部记录。
     *
     * 比逐条删实用：筛出"某个模型的全部失败"再一次清掉，是实际的
     * 使用方式。快照当前 id 集合而非把条件交给数据层，避免删除瞬间
     * 有新记录落进筛选范围而被连带删掉。
     */
    fun deleteFiltered() {
        val ids = entries.value.map { it.id }.toSet()
        viewModelScope.launch { store.remove(ids) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { store.remove(setOf(id)) }
    }

    fun clear() {
        viewModelScope.launch { store.clear() }
    }
}
