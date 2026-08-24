package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.components.MarkdownText
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用内更新日志。
 *
 * 内容来自打包进 assets 的 CHANGELOG.md（由 build.gradle.kts 的
 * syncChangelog 任务从仓库根目录同步）。此前这里是跳转 GitHub 的链接，
 * 但看更新内容不该要求联网、更不该离开应用。
 *
 * 用 MarkdownText 渲染而非纯文本：CHANGELOG 的层级与强调靠 Markdown
 * 表达，纯文本显示会把 `##` 和 `**` 直接暴露出来。
 */
@Composable
fun ChangelogScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    // assets 读取是 IO，放在 produceState 里避免阻塞首帧
    val state by produceState<ChangelogState>(initialValue = ChangelogState.Loading) {
        value = withContext(Dispatchers.IO) {
            try {
                val text = context.assets
                    .open(CHANGELOG_ASSET)
                    .bufferedReader()
                    .use { it.readText() }
                if (text.isBlank()) {
                    ChangelogState.Failed("更新日志内容为空")
                } else {
                    ChangelogState.Loaded(stripFrontMatter(text))
                }
            } catch (e: Exception) {
                // 读不到通常意味着构建时 syncChangelog 没执行，属于构建配置问题。
                // 把原因显示出来而非停在加载态——后者会表现为永久转圈
                ChangelogState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
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
            SettingsPageHeader(title = "更新日志", onNavigateBack = onNavigateBack)

            when (val current = state) {
                is ChangelogState.Loading -> LoadingIndicator()

                is ChangelogState.Failed -> ErrorBanner(reason = current.reason)

                is ChangelogState.Loaded -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    MarkdownText(
                        text = current.markdown,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * 读取状态。
 *
 * 失败态单独区分并带上原因：此前加载与失败共用一个占位，
 * 真读不到时用户只会看到永久转圈，无从判断发生了什么。
 */
private sealed interface ChangelogState {
    data object Loading : ChangelogState
    data class Loaded(val markdown: String) : ChangelogState
    data class Failed(val reason: String) : ChangelogState
}

/**
 * 失败提示，固定在页面上方。
 *
 * 与聊天外观页的图片错误条一致——弹窗会打断浏览，
 * 而这里的失败不影响其余操作。
 */
@Composable
private fun ErrorBanner(reason: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = AppShapeTokens.SettingsGroup,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "无法读取更新日志",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        Text(
            text = "可以在项目仓库的 CHANGELOG.md 中查看完整记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "正在读取更新日志",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 去掉文件开头的标题与说明段落。
 *
 * CHANGELOG.md 开头有「# 更新日志」标题和版本号规则说明，
 * 这些内容在页面标题已有体现，重复显示会占掉首屏。
 * 从第一个版本条目（`## `）开始截取。
 */
private fun stripFrontMatter(markdown: String): String {
    val index = markdown.indexOf("\n## ")
    return if (index >= 0) markdown.substring(index + 1) else markdown
}

private const val CHANGELOG_ASSET = "CHANGELOG.md"
