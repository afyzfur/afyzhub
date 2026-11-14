package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.BubbleStyle
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.util.Constants
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessageLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun segmentedAnswersAndCacheHaveSeparateBoundsAtLargeFont() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.4f)) {
                MaterialTheme {
                    Box(Modifier.requiredWidth(280.dp)) {
                        MessageBlock(
                            message = Message(id = 1, conversationId = 1,
                                content = "第一段正文<web_search>重庆天气</web_search><sources>天气 :: https://example.test</sources>第二段正文",
                                role = Constants.ROLE_ASSISTANT, createdAt = 0L, promptTokens = 1000, completionTokens = 20, cachedTokens = 600),
                            displayOptions = MessageDisplayOptions(showTimestamp = false, showTokenUsage = true),
                            appearance = ChatAppearance(assistantBubble = BubbleStyle.BUBBLE, showUserAvatar = false, showAssistantAvatar = false),
                            providerLabel = "test"
                        )
                    }
                }
            }
        }
        val first = compose.onNodeWithText("第一段正文").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("第二段正文").fetchSemanticsNode().boundsInRoot
        val cache = compose.onNodeWithText("cache: 600 (hit: 60%)").fetchSemanticsNode().boundsInRoot
        assertTrue("Text segments must have spacing", second.top > first.bottom)
        assertTrue("Cache row must stay below the last answer", cache.top > second.bottom)
        assertTrue("Cache text must fit the available width", cache.width <= 280f * compose.density.density)
    }
}