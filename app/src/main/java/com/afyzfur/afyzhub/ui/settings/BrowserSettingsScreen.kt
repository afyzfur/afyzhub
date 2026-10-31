package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afyzfur.afyzhub.data.remote.provider.SearchEngine
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.ui.components.IconPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 浏览器设置页状态: 镜像 DataStore 里的两个开关 */
class BrowserSettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val credentialsUnavailable = settingsRepository.credentialsUnavailable
    private val _browserEnabled = MutableStateFlow(true)
    val browserEnabled: StateFlow<Boolean> = _browserEnabled.asStateFlow()
    private val _searchEngine = MutableStateFlow(SearchEngine.DEFAULT)
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()
    private val _aiWebSearch = MutableStateFlow(false)
    val aiWebSearch: StateFlow<Boolean> = _aiWebSearch.asStateFlow()
    private val _tavilyKey = MutableStateFlow("")
    val tavilyKey: StateFlow<String> = _tavilyKey.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            _browserEnabled.value = settings.inAppBrowserEnabled
            _searchEngine.value = SearchEngine.fromId(settings.searchEngine)
            _aiWebSearch.value = settings.webSearchEnabled
            _tavilyKey.value = settings.tavilyApiKey
        }
    }

    fun setBrowserEnabled(value: Boolean) {
        _browserEnabled.value = value
        viewModelScope.launch { settingsRepository.setInAppBrowserEnabled(value) }
    }

    fun setSearchEngine(value: SearchEngine) {
        _searchEngine.value = value
        viewModelScope.launch { settingsRepository.setSearchEngine(value.id) }
    }

    fun setAiWebSearch(value: Boolean) {
        _aiWebSearch.value = value
        viewModelScope.launch { settingsRepository.setWebSearchEnabled(value) }
    }
    fun setTavilyKey(value: String) {
        if (credentialsUnavailable.value) return
        _tavilyKey.value = value
        viewModelScope.launch { settingsRepository.setTavilyApiKey(value) }
    }
}

/**
 * 内置浏览器设置。
 *
 * 总开关决定消息链接在哪儿打开（内置 / 系统），搜索引擎决定
 * 联网搜索走哪家。两项相互独立：关掉内置浏览器不影响搜索。
 */
@Composable
fun BrowserSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BrowserSettingsViewModel = koinViewModel()
) {
    val browserEnabled by viewModel.browserEnabled.collectAsState()
    val engine by viewModel.searchEngine.collectAsState()
    val aiWebSearch by viewModel.aiWebSearch.collectAsState()
    val tavilyKey by viewModel.tavilyKey.collectAsState()
    val unavailable by viewModel.credentialsUnavailable.collectAsState()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_2e345cfee36f), onNavigateBack = onNavigateBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (unavailable) Text(
                    androidx.compose.ui.res.stringResource(com.afyzhub.chat.R.string.credential_recovery_message),
                    color = MaterialTheme.colorScheme.error
                )
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_1a0fdce8f88f))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = IconPalette,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b99c16ed8931),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b24ce2da8283),
                        checked = browserEnabled,
                        onCheckedChange = viewModel::setBrowserEnabled
                    )
                }
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_45e62e474f4a))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.Search,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_8afa289e32a2),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_c679c1387b44),
                        checked = aiWebSearch,
                        onCheckedChange = viewModel::setAiWebSearch
                    )
                    SettingsItemDivider()
                    SettingsDropdownItem(
                        icon = Icons.Default.Search,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_e58371cb291e),
                        subtitle = engine.label,
                        options = SearchEngine.entries,
                        selected = engine,
                        label = { it.label },
                        onSelect = viewModel::setSearchEngine
                    )
                    // Tavily 需要 API Key: 只在选中它时显示输入框,
                    // 避免其余引擎下多一个用不上的空框
                    if (engine.needsApiKey) {
                        SettingsItemDivider()
                        SettingsTextFieldItem(
                            title = "Tavily API Key",
                            value = tavilyKey,
                            onValueChange = viewModel::setTavilyKey,
                            placeholder = "tvly-xxxxxxxx",
                            subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_e12d30d10108)
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
