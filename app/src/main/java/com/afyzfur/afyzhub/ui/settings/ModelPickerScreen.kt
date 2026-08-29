package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.domain.model.ApiProfile
import com.afyzfur.afyzhub.ui.components.ModelIcon
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import org.koin.androidx.compose.koinViewModel

/**
 * 纯模型切换页。
 *
 * 与 API 配置页的分工：那里管"这组配置怎么连上"（Key、地址、拉列表），
 * 这里只管"现在用哪个模型"。日常最频繁的操作是换模型，却要穿过
 * 配置组列表、进入某组的编辑页、在一堆输入框里找到模型区——把这条
 * 路径单独拉出来。
 *
 * 按配置组分区列出各组缓存的模型，点选时同时切换激活组与模型：
 * 换模型往往连带换服务商（不同组的 Key 与地址不同），分两步做会
 * 让人先切组、再发现模型没跟着变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    onNavigateBack: () -> Unit,
    /** 去 API 配置页，供新增组或改 Key */
    onNavigateToProfiles: () -> Unit,
    /** 进入某组的编辑页，用于拉取模型列表 */
    onNavigateToProfileEdit: (String) -> Unit,
    viewModel: ApiProfilesViewModel = koinViewModel(),
    modelsViewModel: ProfileModelsViewModel = koinViewModel()
) {
    val store by viewModel.store.collectAsState()
    val loading by modelsViewModel.loading.collectAsState()
    val activeId = store.active?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择模型") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 通往 API 配置：新增组、改 Key、改地址都在那边
                    IconButton(onClick = onNavigateToProfiles) {
                        Icon(Icons.Default.Settings, contentDescription = "API 配置")
                    }
                }
            )
        }
    ) { padding ->
        if (store.profiles.isEmpty()) {
            EmptyProfilesHint(
                onNavigateToProfiles = onNavigateToProfiles,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
        ) {
            store.profiles.forEach { profile ->
                item(key = "header-${profile.id}") {
                    ProfileHeader(
                        profile = profile,
                        loading = loading,
                        onRefresh = {
                            // 拉取结果写回该组的缓存，下次进来直接可选
                            modelsViewModel.fetchModels(profile) { models ->
                                viewModel.updateProfile(profile.copy(cachedModels = models))
                            }
                        },
                        onEdit = { onNavigateToProfileEdit(profile.id) }
                    )
                }

                if (profile.cachedModels.isEmpty()) {
                    item(key = "empty-${profile.id}") {
                        // 没拉过列表时给出唯一可做的动作，而不是留一片空白
                        Text(
                            text = "还没有模型列表，点上方的刷新获取",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 32.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 12.dp
                            )
                        )
                    }
                } else {
                    items(
                        items = profile.cachedModels,
                        key = { "${profile.id}-$it" }
                    ) { model ->
                        ModelRow(
                            model = model,
                            // 只有激活组里的当前模型才算选中：同名模型可能
                            // 出现在多组里，不比对组 id 会同时勾选多行
                            selected = profile.id == activeId && model == profile.effectiveModel,
                            onClick = {
                                // 一次点击同时定下用哪组配置与哪个模型
                                viewModel.updateProfile(profile.copy(model = model))
                                viewModel.selectProfile(profile.id)
                                onNavigateBack()
                            }
                        )
                    }
                }
                item(key = "gap-${profile.id}") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/** 配置组的标题行，带刷新与进入编辑。 */
@Composable
private fun ProfileHeader(
    profile: ApiProfile,
    loading: Boolean,
    onRefresh: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // 用组的自定义名称而非提供商名：多组同一提供商时，
                // 全都显示「OpenAI」根本分不出是哪一组
                text = profile.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (profile.group.isNotEmpty()) {
                Text(
                    text = profile.group,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "刷新模型列表",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        TextButton(onClick = onEdit) { Text("配置") }
    }
}

/** 单个模型的可点行。 */
@Composable
private fun ModelRow(
    model: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        shape = AppShapeTokens.SettingsGroup,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // 裁剪在 clickable 之前：涟漪要跟随圆角轮廓，
                // 否则按下时会在四角露出方块
                .clip(AppShapeTokens.SettingsGroup)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            ModelIcon(modelName = model, size = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = model,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "当前使用",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 一组配置都没有时的提示。 */
@Composable
private fun EmptyProfilesHint(
    onNavigateToProfiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "还没有 API 配置",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "先添加一组配置并填入 Key，之后就能在这里切换模型",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onNavigateToProfiles) { Text("前往 API 配置") }
    }
}
