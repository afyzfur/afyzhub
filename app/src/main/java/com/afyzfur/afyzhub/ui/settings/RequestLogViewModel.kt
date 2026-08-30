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

    /**
     * 多选中的记录 id。
     *
     * 空集合表示不在多选模式。用"集合是否为空"而非另设一个布尔标记：
     * 两个状态各存一份就得保证它们始终一致，而"选了东西却不在多选态"
     * 或反过来都是不合法的中间态。
     */
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    /** 长按进入多选并选中第一条 */
    fun startSelection(id: Long) {
        _selected.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = _selected.value
        _selected.value = if (id in current) current - id else current + id
    }

    /**
     * 退出多选。
     *
     * 改筛选条件时也会调用：否则 selected 里会残留当前筛选看不到的 id，
     * 点删除就把屏幕外的记录也删了，用户不知道自己删掉了什么。相比在
     * 删除时再过滤一遍，直接清空更实在——选择的语义本来就是"我在这批里
     * 挑了这几条"，换了一批就不该沿用。
     */
    fun clearSelection() {
        _selected.value = emptySet()
    }

    /** 全选当前筛选出的记录，而非全部——多选是在筛选结果之上操作的 */
    fun selectAllVisible() {
        _selected.value = entries.value.map { it.id }.toSet()
    }

    fun deleteSelected() {
        val ids = _selected.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            store.remove(ids)
            // 删完退出多选：留在多选态里而选中项已不存在，
            // 顶栏会显示"已选 0 项"
            _selected.value = emptySet()
        }
    }

    fun setAgeGroup(group: LogAgeGroup?) {
        _filter.value = _filter.value.copy(ageGroup = group)
        clearSelection()
    }

    fun setModel(model: String?) {
        _filter.value = _filter.value.copy(model = model)
        clearSelection()
    }

    fun setProvider(provider: String?) {
        _filter.value = _filter.value.copy(provider = provider)
        clearSelection()
    }

    fun setFailedOnly(enabled: Boolean) {
        _filter.value = _filter.value.copy(failedOnly = enabled)
        clearSelection()
    }

    fun resetFilter() {
        _filter.value = LogFilter()
        clearSelection()
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
