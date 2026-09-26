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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = "内置浏览器", onNavigateBack = onNavigateBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("通用")
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = IconPalette,
                        title = "应用内打开链接",
                        subtitle = "关闭后链接改用系统浏览器打开，且 AI 无法使用联网搜索",
                        checked = browserEnabled,
                        onCheckedChange = viewModel::setBrowserEnabled
                    )
                }
                SettingsCategoryTitle("联网搜索")
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.Search,
                        title = "AI 联网搜索",
                        subtitle = "非 Gemini 模型的应用内搜索（Gemini 在 API 配置中开启）",
                        checked = aiWebSearch,
                        onCheckedChange = viewModel::setAiWebSearch
                    )
                    SettingsItemDivider()
                    SettingsDropdownItem(
                        icon = Icons.Default.Search,
                        title = "搜索引擎",
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
                            subtitle = "在 tavily.com 注册获取; 免费额度足够日常使用"
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
