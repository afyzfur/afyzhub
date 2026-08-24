package com.afyzfur.afyzhub.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.afyzfur.afyzhub.BuildConfig

/**
 * 关于子页面：版本与项目链接。
 */
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SettingsPageHeader(title = "关于", onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle("版本")

                SettingsGroup {
                    SettingsValueItem(
                        icon = Icons.Default.Info,
                        title = "当前版本",
                        subtitle = "versionCode ${BuildConfig.VERSION_CODE}",
                        value = BuildConfig.VERSION_NAME,
                        onClick = {}
                    )
                }

                SettingsCategoryTitle("项目")

                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Code,
                        title = "源代码",
                        subtitle = REPO_URL.removePrefix("https://"),
                        onClick = { openUrl(REPO_URL) }
                    )
                    SettingsNavItem(
                        icon = Icons.Default.Description,
                        title = "更新日志",
                        subtitle = "查看各版本的变更内容",
                        onClick = { openUrl(CHANGELOG_URL) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "AfyzHub 是一个轻量的 Android AI 聊天客户端，" +
                        "所有凭证与对话仅保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private const val REPO_URL = "https://github.com/afyzfur/afyzhub"
private const val CHANGELOG_URL =
    "https://github.com/afyzfur/afyzhub/blob/main/CHANGELOG.md"
