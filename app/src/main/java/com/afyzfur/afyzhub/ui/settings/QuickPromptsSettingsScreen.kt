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
            SettingsPageHeader(title = "首屏提示词", onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "空会话首屏会把这些显示为可点击的 chip，" +
                        "点击后填入输入框。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
                )

                SettingsCategoryTitle("当前提示词（${prefs.quickPrompts.size}）")

                if (prefs.quickPrompts.isEmpty()) {
                    Text(
                        text = "没有提示词，首屏只显示问候语。",
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

                SettingsCategoryTitle("添加")

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = newPrompt,
                        onValueChange = { newPrompt = it },
                        placeholder = { Text("输入一条提示词") },
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
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = { viewModel.setQuickPrompts(DefaultQuickPrompts) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text("恢复默认")
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
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
