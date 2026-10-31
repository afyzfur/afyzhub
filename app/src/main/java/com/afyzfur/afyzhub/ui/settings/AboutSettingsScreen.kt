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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.afyzhub.chat.BuildConfig

/**
 * 关于子页面：版本与项目链接。
 */
@Composable
fun AboutSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChangelog: () -> Unit
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
            SettingsPageHeader(title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_bed172efc953), onNavigateBack = onNavigateBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_989d1affa089))

                SettingsGroup {
                    SettingsValueItem(
                        icon = Icons.Default.Info,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_46e66f63217a),
                        subtitle = "versionCode ${BuildConfig.VERSION_CODE}",
                        value = BuildConfig.VERSION_NAME,
                        onClick = {}
                    )
                }

                SettingsCategoryTitle(com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_22336e6b892f))

                SettingsGroup {
                    SettingsNavItem(
                        icon = Icons.Default.Share,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_1bc5d1cb9ee7),
                        subtitle = REPO_URL.removePrefix("https://"),
                        onClick = { openUrl(REPO_URL) }
                    )
                    SettingsItemDivider()
                    SettingsNavItem(
                        icon = Icons.Default.List,
                        title = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_185368440e18),
                        subtitle = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_f61d73e36b81),
                        onClick = onNavigateToChangelog
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_badf44811a47) +
                        com.afyzfur.afyzhub.util.AppText.get(com.afyzhub.chat.R.string.ui_fca6f0972365),
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
