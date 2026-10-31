package com.afyzfur.afyzhub

import android.os.Build
import android.os.Bundle
// Display modes are read from the Activity display
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.afyzfur.afyzhub.data.settings.ColorMode
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.ui.navigation.NavGraph
import com.afyzfur.afyzhub.ui.theme.AfyzHubTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    /**
     * 直接注入而非用 ViewModel：主题需要在内容组装前就拿到偏好值，
     * 且 SettingsRepository 是单例、已用 Eagerly 预热，取值不涉及磁盘等待。
     */
    private val settingsRepository: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 按设备显示器支持的最高档位请求 60/90/120Hz。
        // 这是窗口偏好，不绕过省电模式、系统刷新率限制或温控策略。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display
            val supported = display?.supportedModes.orEmpty()
            val maxSupported = supported.maxOfOrNull { it.refreshRate } ?: 60f
            val targetRefreshRate = when {
                maxSupported >= 120f -> 120f
                maxSupported >= 90f -> 90f
                else -> 60f
            }
            val targetMode = supported
                .filter { it.refreshRate >= targetRefreshRate - 0.5f }
                .maxByOrNull { it.refreshRate }
            window.attributes = window.attributes.apply {
                preferredRefreshRate = targetRefreshRate
                if (targetMode != null) preferredDisplayModeId = targetMode.modeId
            }
        }

        setContent {
            val prefs by settingsRepository.uiPreferences.collectAsState()

            val darkTheme = when (prefs.colorMode) {
                ColorMode.SYSTEM -> isSystemInDarkTheme()
                ColorMode.LIGHT -> false
                ColorMode.DARK -> true
            }

            AfyzHubTheme(
                darkTheme = darkTheme,
                dynamicColor = prefs.dynamicColor,
                palette = prefs.palette
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    androidx.compose.foundation.layout.Column {
                        val unavailable by settingsRepository.credentialsUnavailable.collectAsState()
                        if (unavailable) {
                            androidx.compose.material3.Text(
                                androidx.compose.ui.res.stringResource(com.afyzhub.chat.R.string.credential_recovery_message),
                                modifier = Modifier.padding(16.dp).statusBarsPadding(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { NavGraph() }
                    }
                }
            }
        }
    }
}
