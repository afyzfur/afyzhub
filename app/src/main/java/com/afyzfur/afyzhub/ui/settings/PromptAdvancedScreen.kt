package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 高级提示词编辑页。当前角色设定复用该配置组的系统提示词字段。 */
@Composable
fun PromptAdvancedScreen(
    openingPrompt: String,
    chatSettingsPrompt: String,
    onOpeningPromptChange: (String) -> Unit,
    onChatSettingsPromptChange: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsPageHeader(title = "高级提示词", onNavigateBack = onNavigateBack)
            // 角色设定在上一级页面直接编辑；此页集中放其余高级选项。

            SettingsCategoryTitle("开场白")
            SettingsGroup {
                SettingsTextFieldItem(
                    title = "开场白",
                    value = openingPrompt,
                    onValueChange = onOpeningPromptChange,
                    placeholder = "设置新对话开始时的开场内容",
                    singleLine = false
                )
            }
            SettingsCategoryTitle("聊天设定")
            SettingsGroup {
                SettingsTextFieldItem(
                    title = "聊天设定",
                    value = chatSettingsPrompt,
                    onValueChange = onChatSettingsPromptChange,
                    placeholder = "设置回答格式、语言和其他聊天规则",
                    singleLine = false
                )
            }
            Text(
                text = "以上内容按配置组分别保存，切换配置组时会使用对应的提示词。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
