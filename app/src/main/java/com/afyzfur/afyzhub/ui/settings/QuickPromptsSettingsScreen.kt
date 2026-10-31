package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.DefaultQuickPrompts
import com.afyzfur.afyzhub.data.settings.QUICK_PROMPT_DISPLAY_COUNT
import org.koin.androidx.compose.koinViewModel

/**
 * 首屏提示词编辑子页面。
 *
 * 这些 chip 显示在空会话首屏。内容由用户自定义而非 AI 生成——
 * 生成建议需要额外请求，新会话又缺乏上下文，产出质量低（见 4.3）。
 */
@Composable
fun QuickPromptsSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UiPreferencesViewModel = koinViewModel()
) {
    val prefs by viewModel.preferences.collectAsState()
    var newPrompt by remember { mutableStateOf("") }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_5d4357a73cd4), onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f0f79689a0fb) +
                        "点击后填入输入框。首屏一次显示 $QUICK_PROMPT_DISPLAY_COUNT 条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_75589922ea22))
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.Refresh,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_c58ae677073b),
                        subtitle = if (prefs.shufflePrompts) {
                            com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_6d17a4cf95b4)
                        } else {
                            "按下方列表顺序显示前 $QUICK_PROMPT_DISPLAY_COUNT 条"
                        },
                        checked = prefs.shufflePrompts,
                        onCheckedChange = viewModel::setShufflePrompts
                    )
                }

                SettingsCategoryTitle("提示词库（${prefs.quickPrompts.size}）")

                if (prefs.quickPrompts.isEmpty()) {
                    Text(
                        text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_32c97fa51c66),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                    )
                } else {
                    SettingsGroup {
                        prefs.quickPrompts.forEachIndexed { index, prompt ->
                            PromptRow(
                                text = prompt,
                                onRemove = {
                                    viewModel.setQuickPrompts(
                                        prefs.quickPrompts.filterIndexed { i, _ -> i != index }
                                    )
                                }
                            )
                        }
                    }
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_94191ce210d3))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = newPrompt,
                        onValueChange = { newPrompt = it },
                        placeholder = { Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_461387172760)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val text = newPrompt.trim()
                            if (text.isNotEmpty()) {
                                viewModel.setQuickPrompts(prefs.quickPrompts + text)
                                newPrompt = ""
                            }
                        },
                        enabled = newPrompt.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_94191ce210d3))
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = { viewModel.setQuickPrompts(DefaultQuickPrompts) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_a191935bc678))
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 提示词条目，右侧为删除按钮 */
@Composable
private fun PromptRow(
    text: String,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_3755f56f2f83),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
