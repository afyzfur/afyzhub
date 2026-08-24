package com.afyzfur.afyzhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
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
                    NavGraph()
                }
            }
        }
    }
}
