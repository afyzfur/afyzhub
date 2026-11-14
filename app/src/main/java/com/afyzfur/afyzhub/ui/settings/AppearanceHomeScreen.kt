package com.afyzfur.afyzhub.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.afyzhub.chat.R

/** Stable route identifiers are independent of translated labels. */
enum class AppearanceSection(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector
) {
    COLORS("colors", R.string.appearance_colors, R.string.appearance_colors_hint, Icons.Default.Palette),
    MESSAGES("messages", R.string.appearance_messages, R.string.appearance_messages_hint, Icons.Default.ChatBubbleOutline),
    AVATARS("avatars", R.string.appearance_avatars, R.string.appearance_avatars_hint, Icons.Default.AccountCircle),
    BACKGROUND("background", R.string.appearance_background, R.string.appearance_background_hint, Icons.Default.Image),
    INPUT("input", R.string.appearance_input, R.string.appearance_input_hint, Icons.Default.Tune),
    OPACITY("opacity", R.string.opacity_category, R.string.appearance_opacity_hint, Icons.Default.Opacity);

    companion object {
        fun fromId(id: String?): AppearanceSection = entries.firstOrNull { it.id == id } ?: COLORS
    }
}

@Composable
fun AppearanceHomeScreen(onNavigateBack: () -> Unit, onOpenSection: (AppearanceSection) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SettingsPageHeader(stringResource(R.string.ui_cd4994e50397), onNavigateBack)
            Column(Modifier.weight(1f).navigationBarsPadding().verticalScroll(rememberScrollState())) {
                SettingsCategoryTitle(stringResource(R.string.appearance_customize))
                SettingsGroup {
                    AppearanceSection.entries.forEachIndexed { index, section ->
                        if (index > 0) SettingsItemDivider()
                        SettingsNavItem(
                            icon = section.icon,
                            title = stringResource(section.titleRes),
                            subtitle = stringResource(section.subtitleRes),
                            onClick = { onOpenSection(section) }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
