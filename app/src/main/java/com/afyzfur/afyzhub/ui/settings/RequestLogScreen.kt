package com.afyzfur.afyzhub.ui.settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Delete
import com.afyzfur.afyzhub.data.log.LogRetention
import com.afyzfur.afyzhub.ui.components.IconHistory
import com.afyzfur.afyzhub.ui.components.IconDocument
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.log.RequestLogEntry
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 请求日志页。
 *
 * 用于排查"消息发不出去"这类问题——错误提示往往只有一句"请求失败"，
 * 而真正的原因在服务端响应体里。
 */
@Composable
fun RequestLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: RequestLogViewModel = koinViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val allEntries by viewModel.allEntries.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val models by viewModel.availableModels.collectAsState()
    val providers by viewModel.availableProviders.collectAsState()
    val logEnabled by viewModel.logEnabled.collectAsState()
    val retention by viewModel.retention.collectAsState()

    // 删除是不可撤销的，先确认。区分"删筛选结果"与"全部清空"：
    // 前者在筛选后是主要用法，后者才是真的清干净
    val selected by viewModel.selected.collectAsState()
    val inSelection = selected.isNotEmpty()
    val selectedEntries = allEntries.filter { it.id in selected }

    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmDeleteFiltered by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    // 多选态下拦截返回：先退出多选而不是直接离开页面。
    // 否则辛苦选了十几条，误触返回就全没了
    BackHandler(enabled = inSelection, onBack = viewModel::clearSelection)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        if (inSelection) {
            // 多选态下顶栏整体换掉，而不是在原有基础上加按钮：
            // 此时"返回上一页"和"筛选"都不该是主要动作
            SelectionHeader(
                count = selected.size,
                onExit = viewModel::clearSelection,
                onSelectAll = viewModel::selectAllVisible,
                onCopy = {
                    val text = selectedEntries.joinToString("\n\n") { e ->
                        buildString {
                            appendLine("Host: " + e.host + "  Provider: " + (e.provider ?: "-"))
                            appendLine("Model: " + (e.model ?: "-") + "  Status: " + (e.statusCode?.toString() ?: "-"))
                            e.requestBody?.takeIf { it.isNotBlank() }?.let { appendLine("Request:\n" + it) }
                            e.responseBody?.takeIf { it.isNotBlank() }?.let { appendLine("Response:\n" + it) }
                            e.error?.let { appendLine("Error: " + it) }
                        }
                    }
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                    android.widget.Toast.makeText(context, "已复制 ${selectedEntries.size} 条日志", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDelete = { confirmDeleteSelected = true }
            )
        } else {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_d9d4d4e7ddce), onNavigateBack = onNavigateBack)
        }

        // 多选时收起设置与筛选：此刻的任务是挑记录，别的都是干扰
        if (!inSelection) {
        // 开关与保留策略放在这一页：它们只作用于本页的数据，
        // 放到设置首页要两处来回跳才能理解彼此关系
        SettingsGroup {
            SettingsSwitchItem(
                icon = IconDocument,
                title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_e12e4a159572),
                subtitle = if (logEnabled) {
                    com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a5d875e627fa)
                } else {
                    com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_0edf8eee41a5)
                },
                checked = logEnabled,
                onCheckedChange = viewModel::setLogEnabled
            )
            SettingsItemDivider()
            SettingsDropdownItem(
                icon = IconHistory,
                title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_8c714efbb0b7),
                // 说明随当前选项变化：固定写"默认一直保留"在用户已经
                // 选了 7 天时是错的，而这一项的关键差别正是"会不会
                // 自动消失"
                subtitle = when (retention) {
                    LogRetention.FOREVER -> com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_427f8cb991a4)
                    LogRetention.DAY -> com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_b588c7cf23a9)
                    LogRetention.WEEK -> com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_97f80bd6a855)
                    LogRetention.MONTH -> com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_76bf3e5f1707)
                },
                options = LogRetention.entries,
                selected = retention,
                label = { it.label },
                onSelect = viewModel::setRetention
            )
        }

        Spacer(Modifier.height(12.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = when {
                    allEntries.isEmpty() -> com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4f9ba23510ec)
                    // 筛过之后要同时说明筛出多少、总共多少，
                    // 否则看到条数变少会以为记录丢了
                    !filter.isEmpty -> "筛出 ${entries.size} 条，共 ${allEntries.size} 条"
                    else -> "共 ${entries.size} 条。一直保留，只在本机，可手动删除"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (allEntries.isNotEmpty()) {
                // 复制全部日志: 手机端无法连 adb 时, 用这个把日志导出
                // 用文字按钮而非图标: 核心图标集里没有"复制"图标, 借用的列表图标含义不清
                TextButton(
                    onClick = {
                        val sourceEntries = if (inSelection) selectedEntries else allEntries
                        val text = sourceEntries.joinToString("\n\n") { e ->
                            buildString {
                                appendLine("Host: " + e.host + "  Provider: " + (e.provider ?: "-"))
                                appendLine("Model: " + (e.model ?: "-") + "  Status: " + (e.statusCode?.toString() ?: "-"))
                                e.requestBody?.takeIf { it.isNotBlank() }?.let { appendLine("Request:\n" + it) }
                                e.responseBody?.takeIf { it.isNotBlank() }?.let { appendLine("Response:\n" + it) }
                                e.error?.let { appendLine("Error: " + it) }
                            }
                        }
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                        android.widget.Toast.makeText(context, "已复制 ${sourceEntries.size} 条日志", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(if (inSelection) com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4edd1d00875d) else com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_55f59bc2f0c3))
                }
                IconButton(
                    onClick = {
                        if (filter.isEmpty) confirmClearAll = true
                        else confirmDeleteFiltered = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = if (filter.isEmpty) com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_1ef9339dbae3) else com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_0707ba9f4deb),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 没有记录时不显示筛选栏：无从筛选，只会占地方
        if (allEntries.isNotEmpty()) {
            LogFilterBar(
                filter = filter,
                models = models,
                providers = providers,
                onAgeGroup = viewModel::setAgeGroup,
                onModel = viewModel::setModel,
                onProvider = viewModel::setProvider,
                onFailedOnly = viewModel::setFailedOnly,
                onReset = viewModel::resetFilter
            )
            Spacer(Modifier.height(8.dp))
        }

        if (entries.isEmpty() && !filter.isEmpty) {
            Text(
                text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_1911830559dd),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                LogCard(
                    entry = entry,
                    onDelete = { viewModel.delete(entry.id) },
                    selected = entry.id in selected,
                    inSelection = inSelection,
                    onLongPress = { viewModel.startSelection(entry.id) },
                    onToggleSelect = { viewModel.toggleSelection(entry.id) }
                )
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_74c6b428b737)) },
            text = { Text("将删除 ${allEntries.size} 条记录，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.clear()
                }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_84fcd70d4280)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4d0b4688c787)) }
            }
        )
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4373baf54548)) },
            text = { Text("将删除选中的 ${selected.size} 条记录，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelected = false
                    viewModel.deleteSelected()
                }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3755f56f2f83)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4d0b4688c787)) }
            }
        )
    }

    if (confirmDeleteFiltered) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFiltered = false },
            title = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_0707ba9f4deb)) },
            text = { Text("将删除当前筛出的 ${entries.size} 条记录，其余保留。无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteFiltered = false
                    viewModel.deleteFiltered()
                }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3755f56f2f83)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFiltered = false }) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4d0b4688c787)) }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LogCard(
    entry: RequestLogEntry,
    onDelete: () -> Unit,
    selected: Boolean,
    inSelection: Boolean,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit
) {
    // 每条独立记住展开状态，列表滚动后不丢失
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val cardInteractionSource = remember { MutableInteractionSource() }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AppShapeTokens.SettingsGroup,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapeTokens.SettingsGroup)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 交互层只包住摘要区域。展开详情不改变 ripple 的绘制尺寸，
            // 因此点击展开时波纹不会随着卡片变高重新计算成大圆。
            Column(
                modifier = Modifier
                    .clip(AppShapeTokens.SettingsGroup)
                    .combinedClickable(
                    interactionSource = cardInteractionSource,
                    indication = androidx.compose.material3.ripple(bounded = true),
                    onClick = {
                        if (inSelection) onToggleSelect() else expanded = !expanded
                    },
                    onLongClick = onLongPress
                ).padding(12.dp)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (inSelection) {
                    Checkbox(
                        checked = selected,
                        // 勾选框本身不接手势：整张卡片都可点，
                        // 单独给它加回调会让点框和点卡片行为不一致
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                StatusBadge(entry)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.host,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatDuration(entry.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                // 标注来源：不注明的话重启后的旧记录看起来像刚发生的
                text = buildString {
                    append(formatTime(entry.startedAt))
                    append("  ")
                    append(entry.method)
                    if (entry.restored) append(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_e3060a504ec0))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 失败原因不折叠：这是打开这个页面最常见的目的
            entry.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                DetailSection(label = "URL", content = entry.url)

                if (entry.headers.isNotEmpty()) {
                    DetailSection(
                        label = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_7cabf2e2bdc6),
                        content = entry.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    )
                }

                entry.requestBody?.let { DetailSection(label = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a526db39fb8c), content = it) }
                entry.responseBody?.let { DetailSection(label = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6e83ee0273ad), content = it) }

                // 删除入口只在展开后出现：折叠那一行已有状态、主机、
                // 耗时三样，再塞图标会挤掉主机名。展开也意味着用户
                // 已经看过内容，此时删除的误触风险更低
                if (!inSelection) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDelete) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_bcf10102a080)) }
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_cdd65356222b),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(entry: RequestLogEntry) {
    val (text, color) = when {
        entry.statusCode == null -> "ERR" to MaterialTheme.colorScheme.error
        entry.isSuccess -> entry.statusCode.toString() to MaterialTheme.colorScheme.primary
        else -> entry.statusCode.toString() to MaterialTheme.colorScheme.error
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 详情段落。
 *
 * 正文用等宽字体并允许横向滚动——JSON 折行后可读性反而更差。
 */
@Composable
private fun DetailSection(label: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp)
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

private fun formatDuration(millis: Long): String = when {
    millis < 1000 -> "${millis}ms"
    else -> "${"%.1f".format(millis / 1000.0)}s"
}

/**
 * 多选态下的顶栏。
 *
 * 替换掉原本的页头而非叠加按钮：多选时"返回上一页"和"筛选"都不是
 * 主要动作，把它们留在原位反而容易误触。左侧的关闭对应"退出多选"，
 * 与系统返回键的行为一致。
 */
@Composable
private fun SelectionHeader(
    count: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_c194fdd677d2)
            )
        }
        Text(
            text = "已选 $count 项",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        TextButton(onClick = onSelectAll) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3e44b2a93338)) }
        TextButton(onClick = onCopy) { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_4edd1d00875d)) }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_5d071a7a421e),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
