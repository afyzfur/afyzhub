package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.ui.chat.ChatInputBar
import com.afyzfur.afyzhub.ui.chat.MessageBlock
import com.afyzfur.afyzhub.ui.components.ChatBackgroundLayer
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import com.afyzfur.afyzhub.util.Constants
import com.afyzhub.chat.R

@Composable
internal fun AppearanceLivePreview(section: AppearanceSection, appearance: ChatAppearance, modifier: Modifier = Modifier) {
    val fallback = Brush.linearGradient(listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    ))
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = AppShapeTokens.SettingsGroup, modifier = modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.appearance_preview), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            when (section) {
                AppearanceSection.AVATARS -> AvatarPreview(appearance, fallback)
                AppearanceSection.INPUT -> InputPreview(appearance, fallback)
                else -> Unit
            }
            Text(stringResource(R.string.appearance_preview_live_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun PreviewCanvas(appearance: ChatAppearance, fallback: Brush, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(230.dp).padding(horizontal = 12.dp).clip(AppShapeTokens.SettingsGroup)) {
        Box(Modifier.fillMaxSize().background(fallback))
        ChatBackgroundLayer(appearance, Modifier.fillMaxSize())
        content()
    }
}

@Composable
internal fun AvatarPreview(appearance: ChatAppearance, brush: Brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))) {
    val options = androidx.compose.runtime.remember {
        MessageDisplayOptions(showTimestamp = false, showActions = false, showModelName = false,
            showTokenUsage = false, showSpeed = false, showLatency = false)
    }
    val assistantText = stringResource(R.string.appearance_preview_answer)
    val userText = stringResource(R.string.appearance_preview_user_message)
    val assistant = androidx.compose.runtime.remember(assistantText) {
        Message(1, 1, assistantText, Constants.ROLE_ASSISTANT, createdAt = 0L)
    }
    val user = androidx.compose.runtime.remember(userText) {
        Message(2, 1, userText, Constants.ROLE_USER, createdAt = 0L)
    }
    PreviewCanvas(appearance, brush) {
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MessageBlock(assistant, options, appearance, providerLabel = "AfyzHub", onLongPress = {})
            MessageBlock(user, options, appearance, providerLabel = "AfyzHub", onLongPress = {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputPreview(appearance: ChatAppearance, brush: Brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))) {
    PreviewCanvas(appearance, brush) {
        Column(Modifier.fillMaxSize()) {
            // Match the real chat TopAppBar (no system-bar inset inside a preview card).
            TopAppBar(
                modifier = Modifier.testTag("preview-top-bar"),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (appearance.transparentTopBar) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
                ),
                title = {
                    Column {
                        Text(stringResource(R.string.appearance_preview_title), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.appearance_preview_subtitle), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                }
            )
            Spacer(Modifier.weight(1f))
            ChatInputBar(
                value = stringResource(R.string.appearance_preview_input), onValueChange = {}, onSend = {}, onStop = {},
                providerLabel = "", modelName = "gpt-4o", isLoading = false, thinkingEnabled = true,
                floating = appearance.inputBarFloating && appearance.inputBarDeepSeeThrough,
                transparent = appearance.transparentInputBar, seeThrough = appearance.inputBarSeeThrough,
                deepSeeThrough = appearance.inputBarDeepSeeThrough, previewOnly = true,
                applyWindowInsets = false,
                modifier = Modifier.fillMaxWidth().testTag("preview-input-bar")
            )
        }
    }
}