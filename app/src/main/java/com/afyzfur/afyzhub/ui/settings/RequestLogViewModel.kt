package com.afyzfur.afyzhub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.log.RequestLogEntry
import com.afyzfur.afyzhub.data.log.RequestLogStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 请求日志页的状态持有者。
 *
 * 日志本身存在单例 [RequestLogStore] 里，这里只做转发与清空，
 * 因为日志需要跨页面生命周期存活。
 */
class RequestLogViewModel(
    private val store: RequestLogStore
) : ViewModel() {

    val entries: StateFlow<List<RequestLogEntry>> = store.entries

    fun clear() {
        viewModelScope.launch { store.clear() }
    }
}
