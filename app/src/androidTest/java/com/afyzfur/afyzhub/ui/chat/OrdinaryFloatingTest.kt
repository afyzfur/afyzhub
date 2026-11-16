package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.domain.model.SendPhase
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import com.afyzfur.afyzhub.ui.settings.InputPreview
import com.afyzfur.afyzhub.util.Constants
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OrdinaryFloatingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualChatFloatsWithDeepModeOffAndReservesRoomForMessages() {
        var appearance by mutableStateOf(ChatAppearance(inputBarFloating = true,
            inputBarDeepSeeThrough = false, showUserAvatar = false, showAssistantAvatar = false))
        val message = Message(1, 1, "last message", Constants.ROLE_ASSISTANT, createdAt = 0L)
        compose.setContent {
            MaterialTheme {
                ChatContent(title = "test", modelLabel = "test", providerLabel = "test", modelName = "test",
                    searchEngine = "bing", thinkingEnabled = false, thinkingEffort = ThinkingEffort.DEFAULT,
                    onOpenThinkingSettings = {}, appearance = appearance, quickPrompts = emptyList(), shufflePrompts = false,
                    displayOptions = MessageDisplayOptions(showTimestamp = false, showTokenUsage = false),
                    messages = listOf(message), isLoading = false, sendPhase = SendPhase.IDLE, error = null,
                    listState = rememberLazyListState(), focusRequestToken = 0, inputText = "", onInputChange = {},
                    onOpenDrawer = {}, onSend = {}, onStop = {}, onPickModel = {}, onOpenBrowser = {},
                    onLongPress = {}, onPromptClick = {}, onRetry = {}, onClearError = {})
            }
        }
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val floating = compose.onNodeWithTag("chat-input-surface").fetchSemanticsNode().boundsInRoot
        val inset = with(compose.density) { 12.dp.toPx() }
        assertEquals(root.left + inset, floating.left, 1.5f)
        assertEquals(root.right - inset, floating.right, 1.5f)
        assertTrue("Bottom margin must exist without deep mode", floating.bottom < root.bottom)
        val viewport = compose.onNodeWithTag("chat-message-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Ordinary floating input must end the message viewport above its surface", viewport.bottom <= floating.top)

        compose.onNodeWithText("last message").performScrollTo()
        val last = compose.onNodeWithText("last message").fetchSemanticsNode().boundsInRoot
        assertTrue("The final reply must be above the input surface", last.bottom <= floating.top)
        compose.runOnIdle { appearance = appearance.copy(inputBarFloating = false) }
        compose.waitForIdle()
        val fullWidth = compose.onNodeWithTag("chat-input-surface").fetchSemanticsNode().boundsInRoot
        assertEquals(root.width, fullWidth.width, 1.5f)
        compose.runOnIdle { appearance = appearance.copy(inputBarFloating = true, inputBarDeepSeeThrough = true) }
        compose.waitForIdle()
        val enhanced = compose.onNodeWithTag("chat-input-surface").fetchSemanticsNode().boundsInRoot
        val enhancedViewport = compose.onNodeWithTag("chat-message-viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(floating.left, enhanced.left, 1.5f)
        assertEquals(floating.width, enhanced.width, 1.5f)
        assertTrue("Enhanced mode lets messages continue behind the input surface", enhancedViewport.bottom > enhanced.top)
    }

    private fun snapshot(): PixelMap = compose.onNodeWithTag("backdrop").captureToImage().toPixelMap()

    @Test fun ordinaryTransparencyChangesOnlyBackgroundAndKeepsFloatingCornersClear() {
        var transparent by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().background(Color.Magenta).testTag("backdrop")) {
                    ChatInputBar(value = "Clear text", onValueChange = {}, onSend = {}, onStop = {},
                        providerLabel = "test", modelName = "test", isLoading = false, floating = true,
                        deepSeeThrough = false, transparent = transparent, seeThrough = 0.9f,
                        previewOnly = true, applyWindowInsets = false)
                }
            }
        }
        val opaque = snapshot()
        compose.runOnIdle { transparent = true }
        compose.waitForIdle()
        val clear = snapshot()
        val surface = compose.onNodeWithTag("chat-input-surface").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("backdrop").fetchSemanticsNode().boundsInRoot
        val left = (surface.left - root.left).toInt()
        val top = (surface.top - root.top).toInt()
        assertEquals("Left outer margin has no rectangular fill", Color.Magenta, clear[1, clear.height / 2])
        assertEquals("Rounded corner is not painted as a square", Color.Magenta, clear[left + 1, top + 1])
        assertNotEquals("Input surface still has its own controllable background", clear[1, clear.height / 2], clear[clear.width / 2, top + 3])
        assertNotEquals("Transparency changes the input surface", opaque[opaque.width / 2, top + 3], clear[clear.width / 2, top + 3])
        val textBounds = compose.onNodeWithText("Clear text").fetchSemanticsNode().boundsInRoot
        // Fully covered glyph pixels must remain identical: the content layer must not be faded to 10%.
        var unchangedText = 0

        for (y in (textBounds.top - root.top).toInt() until (textBounds.bottom - root.top).toInt()) {
            for (x in (textBounds.left - root.left).toInt() until (textBounds.right - root.left).toInt()) {
                val c = opaque[x, y]
                if (c.red < 0.3f && c.green < 0.3f && c.blue < 0.3f && c == clear[x, y]) unchangedText++
            }
        }
        assertTrue("Text glyphs remain opaque when deep mode is off", unchangedText > 10)
    }

    @Test fun previewAlsoFloatsWithDeepModeOff() {
        var appearance by mutableStateOf(ChatAppearance(inputBarFloating = false, inputBarDeepSeeThrough = false))
        compose.setContent {
            MaterialTheme { Box(Modifier.fillMaxWidth().testTag("preview-root")) {
                InputPreview(appearance)
            } }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val text = context.getString(com.afyzhub.chat.R.string.appearance_preview_input)
        val full = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { appearance = appearance.copy(inputBarFloating = true) }
        compose.waitForIdle()
        val floating = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        val inset = with(compose.density) { 12.dp.toPx() }
        assertEquals("Preview adds the same left floating margin", full.left + inset, floating.left, 1.5f)
        assertEquals("Preview adds the same right floating margin", full.right - inset, floating.right, 1.5f)
    }
    @Test fun floatingOutlineClipsMultilineContentAndNeverPaintsExternalPadding() {
        var deep by mutableStateOf(false)
        var text by mutableStateOf("First line\nSecond line\nThird line")
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().background(Color.Magenta).testTag("backdrop")) {
                    ChatInputBar(value = text, onValueChange = {}, onSend = {}, onStop = {},
                        providerLabel = "test", modelName = "test", isLoading = false,
                        thinkingEnabled = true, floating = true, transparent = true, seeThrough = 0.4f,
                        deepSeeThrough = deep, previewOnly = true, applyWindowInsets = false)
                }
            }
        }
        for (enhanced in listOf(false, true)) {
            compose.runOnIdle { deep = enhanced }
            compose.waitForIdle()
            val pixels = snapshot()
            val outer = compose.onNodeWithTag("backdrop").fetchSemanticsNode().boundsInRoot
            val inner = compose.onNodeWithTag("chat-input-surface").fetchSemanticsNode().boundsInRoot
            val x0 = (inner.left - outer.left).toInt()
            val y0 = (inner.top - outer.top).toInt()
            val x1 = (inner.right - outer.left).toInt()
            val y1 = (inner.bottom - outer.top).toInt()
            for (y in 0 until pixels.height step 3) for (x in 0 until pixels.width step 3) {
                if (x < x0 || x >= x1 || y < y0 || y >= y1) {
                    val outside = pixels[x, y]
                    assertTrue("No external border/backing plate at $x,$y", outside.red > 0.92f && outside.blue > 0.92f && outside.green < 0.12f)
                }
            }
            val cornerStart = pixels[x0 + 1, y0 + 1]
            val cornerEnd = pixels[x1 - 2, y1 - 2]
            assertTrue("Rounded upper start stays transparent", cornerStart.red > 0.92f && cornerStart.blue > 0.92f && cornerStart.green < 0.12f)
            assertTrue("Rounded lower end stays transparent", cornerEnd.red > 0.92f && cornerEnd.blue > 0.92f && cornerEnd.green < 0.12f)
        }
        val multiline = compose.onNodeWithTag("chat-input-surface").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { text = "One line" }
        compose.waitForIdle()
        val single = compose.onNodeWithTag("chat-input-surface").fetchSemanticsNode().boundsInRoot
        assertTrue("Rounded backing follows actual input height", single.height < multiline.height)
        assertEquals(single.width, multiline.width, 1f)
    }

}
