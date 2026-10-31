package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 单组配置的模型管理页。从编辑页拆出来的二级页。
 *
 * 编辑页曾经把"名称/分组/提供商/Key/地址/测试/模型列表"全部纵向
 * 堆在一屏里，滚动下来信息量过载。模型相关的操作（改名、拉列表、
 * 挑选已选集、设当前）彼此相关，整页拆走后编辑页只剩 5 组,
 * 每页各司其职。
 *
 * 交互与数据完全不变, 只是换了位置：ModelSelectionSection 与
 * ProfileModelsViewModel 原样复用。
 */
@Composable
fun ApiProfileModelsScreen(
    profileId: String,
    onNavigateBack: () -> Unit,
    viewModel: ApiProfilesViewModel = koinViewModel(),
    modelsViewModel: ProfileModelsViewModel = koinViewModel()
) {
    val store by viewModel.store.collectAsState()
    val profile = store.profiles.firstOrNull { it.id == profileId }

    if (profile == null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_98fd0cbd9c31), onNavigateBack = onNavigateBack)
                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_9f61d7d38215),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(28.dp)
                )
            }
        }
        return
    }

    val loading by modelsViewModel.loading.collectAsState()
    val error by modelsViewModel.error.collectAsState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_98fd0cbd9c31), onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_a0af8f7df552))
                SettingsGroup {
                    SettingsTextFieldItem(
                        identityKey = profileId,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_38719c9968ae),
                        value = profile.model,
                        onValueChange = { viewModel.updateProfile(profile.copy(model = it)) },
                        placeholder = "留空则使用 ${profile.provider.fallbackModel}"
                    )
                    SettingsItemDivider()
                    ModelFetchRow(
                        loading = loading,
                        hasModels = profile.cachedModels.isNotEmpty(),
                        onRefresh = {
                            modelsViewModel.fetchModels(profile) { models ->
                                viewModel.updateProfile(
                                    profile.copy(cachedModels = models)
                                )
                            }
                        }
                    )
                    error?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }

                if (profile.cachedModels.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SettingsGroup {
                        ModelSelectionSection(
                            profile = profile,
                            onChange = viewModel::updateProfile
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 拉取模型列表的操作行（从编辑页原样搬来） */
@Composable
private fun ModelFetchRow(
    loading: Boolean,
    hasModels: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onRefresh)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = if (hasModels) com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_fa199b22e631) else com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_e4f6b70a8317),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
