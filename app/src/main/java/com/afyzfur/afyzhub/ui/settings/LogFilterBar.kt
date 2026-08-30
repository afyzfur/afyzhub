package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.afyzfur.afyzhub.data.log.LogAgeGroup
import com.afyzfur.afyzhub.data.log.LogFilter

/**
 * 日志筛选栏。
 *
 * 四个维度横向排列、可左右滑动。用 chip 而非下拉列表承载"只看失败"和
 * 时间分组：它们的选项少且固定，一眼能看全，点一下就切换；模型与提供商
 * 的取值来自实际数据、数量不定，用下拉更合适。
 *
 * 横向滑动而非换行：换行会让筛选栏的高度随选项数量变化，列表的起始
 * 位置跟着上下跳。
 */
@Composable
internal fun LogFilterBar(
    filter: LogFilter,
    models: List<String>,
    providers: List<String>,
    onAgeGroup: (LogAgeGroup?) -> Unit,
    onModel: (String?) -> Unit,
    onProvider: (String?) -> Unit,
    onFailedOnly: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // 「只看失败」放最前：打开这个页面最常见的目的就是找失败原因
        FilterChip(
            selected = filter.failedOnly,
            onClick = { onFailedOnly(!filter.failedOnly) },
            label = { Text("只看失败") }
        )

        // 时间分组用下拉而非四个并列的 chip：四个全排开会把后面的
        // 维度挤出屏幕，而时间是单选，下拉更贴合"选一个"的语义
        DropdownFilterChip(
            label = filter.ageGroup?.label ?: "时间",
            selected = filter.ageGroup != null,
            options = LogAgeGroup.entries.map { it to it.label },
            onSelect = { onAgeGroup(it) }
        )

        // 数据里没出现过的模型不列出来：选中后必然是空列表
        if (models.isNotEmpty()) {
            DropdownFilterChip(
                label = filter.model ?: "模型",
                selected = filter.model != null,
                options = models.map { it to it },
                onSelect = { onModel(it) }
            )
        }

        if (providers.isNotEmpty()) {
            DropdownFilterChip(
                label = filter.provider ?: "提供商",
                selected = filter.provider != null,
                options = providers.map { it to it },
                onSelect = { onProvider(it) }
            )
        }

        // 只在确实筛了东西时出现：没筛的时候它没有作用，
        // 常驻只会占位置
        if (!filter.isEmpty) {
            FilterChip(
                selected = false,
                onClick = onReset,
                label = { Text("清除筛选") }
            )
        }
    }
}

/**
 * 带下拉菜单的筛选 chip。
 *
 * 菜单里额外给一个"全部"用于取消该维度——把取消做成"再点一次已选项"
 * 不好发现，而在 chip 上加个小叉又和点开菜单的手势打架。
 */
@Composable
private fun <T> DropdownFilterChip(
    label: String,
    selected: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    FilterChip(
        selected = selected,
        onClick = { expanded = true },
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        // 不裁剪到屏幕边缘之外，长模型名的菜单才不会被切掉
        properties = PopupProperties(focusable = true)
    ) {
        DropdownMenuItem(
            text = { Text("全部") },
            onClick = {
                expanded = false
                onSelect(null)
            }
        )
        options.forEach { (value, text) ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = {
                    expanded = false
                    onSelect(value)
                }
            )
        }
    }
}
