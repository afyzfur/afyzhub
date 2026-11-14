package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.BubbleStyle
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.ui.chat.MessageBlock
import com.afyzfur.afyzhub.ui.chat.MessageMetaRow
import com.afyzfur.afyzhub.ui.chat.ReasoningBlock
import com.afyzfur.afyzhub.ui.chat.SearchBlock
import com.afyzfur.afyzhub.ui.components.ChatBackgroundLayer
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import com.afyzfur.afyzhub.util.Constants
import com.afyzhub.chat.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun ContentOpacitySettingsScreen(onNavigateBack: () -> Unit, viewModel: UiPreferencesViewModel = koinViewModel()) {
    val prefs by viewModel.preferences.collectAsState()
    ContentOpacitySettingsContent(
        stored = prefs.chatAppearance,
        onNavigateBack = onNavigateBack,
        onSearchChange = viewModel::setSearchOpacity,
        onThinkingChange = viewModel::setThinkingOpacity,
        onBodyChange = viewModel::setBodyOpacity,
        onInfoChange = viewModel::setInfoOpacity
    )
}

/** Keep persisted values separate from the drag overrides: the sliders wait for the actual write acknowledgement. */
@Composable
internal fun ContentOpacitySettingsContent(
    stored: ChatAppearance,
    onNavigateBack: () -> Unit,
    onSearchChange: (Float) -> Unit,
    onThinkingChange: (Float) -> Unit,
    onBodyChange: (Float) -> Unit,
    onInfoChange: (Float) -> Unit
) {
    var search by remember { mutableStateOf<Float?>(null) }
    var thinking by remember { mutableStateOf<Float?>(null) }
    var body by remember { mutableStateOf<Float?>(null) }
    var info by remember { mutableStateOf<Float?>(null) }
    val preview = stored.copy(
        searchOpacity = search ?: stored.searchOpacity,
        thinkingOpacity = thinking ?: stored.thinkingOpacity,
        bodyOpacity = body ?: stored.bodyOpacity,
        infoOpacity = info ?: stored.infoOpacity
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            SettingsPageHeader(stringResource(R.string.opacity_category), onNavigateBack)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Keep the example visible while scrolling to the lower sliders, including on short screens.
                val previewHeight = (maxHeight * 0.42f).coerceAtMost(280.dp)
                Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(R.string.appearance_preview),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp)
                    )
                    OpacityChatPreview(preview, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(previewHeight))
                    Text(
                        stringResource(R.string.appearance_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                    Column(Modifier.weight(1f).fillMaxWidth().testTag("opacity-controls").verticalScroll(rememberScrollState())) {
                        SettingsGroup {
                            Box(Modifier.testTag("opacity-slider-search")) {
                                AppearancePercentSlider(stringResource(R.string.opacity_search), stored.searchOpacity,
                                    stringResource(R.string.opacity_hint), onSearchChange, onPreview = { search = it })
                            }
                            SettingsItemDivider()
                            Box(Modifier.testTag("opacity-slider-thinking")) {
                                AppearancePercentSlider(stringResource(R.string.opacity_thinking), stored.thinkingOpacity,
                                    stringResource(R.string.opacity_hint), onThinkingChange, onPreview = { thinking = it })
                            }
                            SettingsItemDivider()
                            Box(Modifier.testTag("opacity-slider-body")) {
                                AppearancePercentSlider(stringResource(R.string.opacity_body), stored.bodyOpacity,
                                    stringResource(R.string.opacity_hint), onBodyChange, onPreview = { body = it })
                            }
                            SettingsItemDivider()
                            Box(Modifier.testTag("opacity-slider-info")) {
                                AppearancePercentSlider(stringResource(R.string.opacity_info), stored.infoOpacity,
                                    stringResource(R.string.opacity_hint), onInfoChange, onPreview = { info = it })
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// The same values drive the chat components and the accessible preview description.
internal val PreviewSurfaceOpacity = SemanticsPropertyKey<Float>("PreviewSurfaceOpacity")
private var SemanticsPropertyReceiver.previewSurfaceOpacity by PreviewSurfaceOpacity

@Composable
internal fun OpacityChatPreview(appearance: ChatAppearance, modifier: Modifier = Modifier) {
    val answer = stringResource(R.string.appearance_preview_answer)
    val sample = remember(answer) {
        Message(0, 0, answer, Constants.ROLE_ASSISTANT, createdAt = 0L,
            promptTokens = 1000, completionTokens = 120, cachedTokens = 600)
    }
    val bareDisplay = remember {
        MessageDisplayOptions(showTimestamp = false, showActions = false, showModelName = false,
            showTokenUsage = false, showSpeed = false, showLatency = false)
    }
    val sampleAppearance = appearance.copy(assistantBubble = BubbleStyle.BUBBLE, showUserAvatar = false, showAssistantAvatar = false)
    Box(modifier.clip(AppShapeTokens.SettingsGroup).testTag("opacity-preview")) {
        // A contrasting fallback lets users see transparency before they pick a background image.
        Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        ))))
        ChatBackgroundLayer(appearance, Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().testTag("opacity-preview-scroll").verticalScroll(rememberScrollState()).padding(12.dp)) {
            Box(Modifier.testTag("opacity-preview-search").semantics { previewSurfaceOpacity = appearance.searchOpacity }) {
                SearchBlock(query = stringResource(R.string.appearance_preview_search), sources = emptyList(), opacity = appearance.searchOpacity)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.testTag("opacity-preview-thinking").semantics { previewSurfaceOpacity = appearance.thinkingOpacity }) {
                ReasoningBlock(reasoning = stringResource(R.string.appearance_preview_thinking), thinking = false,
                    bubbleStyle = BubbleStyle.BUBBLE, opacity = appearance.thinkingOpacity)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.testTag("opacity-preview-body").semantics { previewSurfaceOpacity = appearance.bodyOpacity }) {
                MessageBlock(sample, bareDisplay, sampleAppearance, providerLabel = "AfyzHub")
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.testTag("opacity-preview-info").semantics { previewSurfaceOpacity = appearance.infoOpacity }) {
                MessageMetaRow(sample, MessageDisplayOptions(showTimestamp = true, showTokenUsage = true),
                    contrastBackground = true, opacity = appearance.infoOpacity)
            }
        }
    }
}