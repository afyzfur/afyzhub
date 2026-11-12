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

    private val memoryRepository: com.afyzfur.afyzhub.data.memory.MemoryRepository by inject()

    override fun onStart() {
        super.onStart()
        // Only new messages are sent, so frequent foreground checks cost nothing without new chats.
        memoryRepository.onTrigger(com.afyzfur.afyzhub.domain.model.MemoryTrigger.APP_OPEN)
    }

    override fun onResume() {
        super.onResume()
        requestHighestRefreshRate()
    }

    /**
     * 请求当前分辨率下屏幕支持的最高刷新率。省电模式、温控等系统限制仍会生效，
     * 动画按实际帧时间推进，在被限制为 60Hz 时时长不变。
     */
    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay) ?: return
        val toSpec = { mode: android.view.Display.Mode ->
            com.afyzfur.afyzhub.util.DisplayModeSpec(mode.modeId, mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
        }
        val best = com.afyzfur.afyzhub.util.highestRefreshMode(toSpec(display.mode), display.supportedModes.map(toSpec)) ?: return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId == best.id) return
        window.attributes = attributes.apply {
            preferredDisplayModeId = best.id
            preferredRefreshRate = best.refreshRate
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
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
