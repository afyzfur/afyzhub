package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.ui.draw.clip
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
                onDelete = { confirmDeleteSelected = true }
            )
        } else {
            SettingsPageHeader(title = "请求日志", onNavigateBack = onNavigateBack)
        }

        // 多选时收起设置与筛选：此刻的任务是挑记录，别的都是干扰
        if (!inSelection) {
        // 开关与保留策略放在这一页：它们只作用于本页的数据，
        // 放到设置首页要两处来回跳才能理解彼此关系
        SettingsGroup {
            SettingsSwitchItem(
                icon = IconDocument,
                title = "记录请求日志",
                subtitle = if (logEnabled) {
                    "每次请求都会留下一条，仅存在本机"
                } else {
                    "已关闭，不再记录新的请求。已有记录仍保留"
                },
                checked = logEnabled,
                onCheckedChange = viewModel::setLogEnabled
            )
            SettingsItemDivider()
            SettingsDropdownItem(
                icon = IconHistory,
                title = "保留时长",
                // 说明随当前选项变化：固定写"默认一直保留"在用户已经
                // 选了 7 天时是错的，而这一项的关键差别正是"会不会
                // 自动消失"
                subtitle = when (retention) {
                    LogRetention.FOREVER -> "记录不会自动消失，只在手动删除时清除"
                    LogRetention.DAY -> "超过 1 天的记录会在下次启动时清掉"
                    LogRetention.WEEK -> "超过 7 天的记录会在下次启动时清掉"
                    LogRetention.MONTH -> "超过 30 天的记录会在下次启动时清掉"
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
                    allEntries.isEmpty() -> "暂无记录"
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
                IconButton(
                    onClick = {
                        // 有筛选时删的是筛选结果，没筛选时删全部。
                        // 两种情况用同一个按钮但确认文案不同——单独放两个
                        // 图标按钮，未筛选时其中一个没有意义
                        if (filter.isEmpty) confirmClearAll = true
                        else confirmDeleteFiltered = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = if (filter.isEmpty) "清空日志" else "删除筛选结果",
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
                text = "没有符合条件的记录",
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
            title = { Text("清空全部日志") },
            text = { Text("将删除 ${allEntries.size} 条记录，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    viewModel.clear()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            }
        )
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("删除所选记录") },
            text = { Text("将删除选中的 ${selected.size} 条记录，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelected = false
                    viewModel.deleteSelected()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text("取消") }
            }
        )
    }

    if (confirmDeleteFiltered) {
        AlertDialog(
            onDismissRequest = { confirmDeleteFiltered = false },
            title = { Text("删除筛选结果") },
            text = { Text("将删除当前筛出的 ${entries.size} 条记录，其余保留。无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteFiltered = false
                    viewModel.deleteFiltered()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFiltered = false }) { Text("取消") }
            }
        )
    }
}

// combinedClickable 仍标记为实验性，但它是 Compose 里做长按的标准做法，
// 且 API 形状多年未变。替代方案是自己用 pointerInput 判定长按时长，
// 那等于重实现一遍且更容易出错
@OptIn(ExperimentalFoundationApi::class)
@Composable
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

    Surface(
        // 选中的卡片换底色而非只加个勾：一眼能看出选了哪几条，
        // 不必逐行找勾选框
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = AppShapeTokens.SettingsGroup,
        modifier = Modifier
            .fillMaxWidth()
            // 用 combinedClickable 而非 Surface 的 onClick：需要长按。
            // 涟漪跟随圆角靠外层的 clip
            .clip(AppShapeTokens.SettingsGroup)
            .combinedClickable(
                onClick = {
                    // 多选态下点击是改选择，不是展开——展开需要看详情，
                    // 而此刻用户在挑要删的条目
                    if (inSelection) onToggleSelect() else expanded = !expanded
                },
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    if (entry.restored) append("  · 上次运行")
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

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                DetailSection(label = "URL", content = entry.url)

                if (entry.headers.isNotEmpty()) {
                    DetailSection(
                        label = "请求头",
                        content = entry.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
                    )
                }

                entry.requestBody?.let { DetailSection(label = "请求体", content = it) }
                entry.responseBody?.let { DetailSection(label = "响应体", content = it) }

                // 删除入口只在展开后出现：折叠那一行已有状态、主机、
                // 耗时三样，再塞图标会挤掉主机名。展开也意味着用户
                // 已经看过内容，此时删除的误触风险更低
                if (!inSelection) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDelete) { Text("删除这条") }
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "点击查看详情",
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
                contentDescription = "退出多选"
            )
        }
        Text(
            text = "已选 $count 项",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        TextButton(onClick = onSelectAll) { Text("全选") }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除所选",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
