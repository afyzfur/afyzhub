package com.afyzfur.afyzhub.ui.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.afyzfur.afyzhub.data.settings.AvatarMode
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzhub.chat.R
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SectionPreviewTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private fun capture(tag: String): PixelMap = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
    private fun differences(first: PixelMap, second: PixelMap): Int {
        var changed = 0
        for (y in 0 until minOf(first.height, second.height) step 4) {
            for (x in 0 until minOf(first.width, second.width) step 4) {
                if (first[x, y] != second[x, y]) changed++
            }
        }
        return changed
    }

    private fun avatarFile(name: String, color: Int): File {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        for (y in 0 until 96) for (x in 0 until 96) {
            bitmap.setPixel(x, y, if ((x / 12 + y / 12) % 2 == 0) color else AndroidColor.WHITE)
        }
        return File(context.cacheDir, name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun customAvatarVisibilityBlurAndImageVersionUpdateTheActualChatPreview() {
        val user = avatarFile("preview-user.png", AndroidColor.RED)
        val assistant = avatarFile("preview-assistant.png", AndroidColor.BLUE)
        try {
            var appearance by mutableStateOf(ChatAppearance(avatarMode = AvatarMode.CUSTOM,
                userAvatarPath = user.absolutePath, assistantAvatarPath = assistant.absolutePath))
            compose.setContent {
                MaterialTheme { Box(Modifier.fillMaxWidth().testTag("avatars")) { AvatarPreview(appearance) } }
            }
            // Local image decoding is asynchronous, wait for both sides to draw their colored bitmaps.
            compose.waitUntil(10_000) {
                val pixels = capture("avatars")
                var red = 0; var blue = 0
                for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
                    val c = pixels[x, y]
                    if (c.red > 0.8f && c.green < 0.2f && c.blue < 0.2f) red++
                    if (c.blue > 0.8f && c.red < 0.2f && c.green < 0.2f) blue++
                }
                red > 3 && blue > 3
            }
            val visible = capture("avatars")
            compose.runOnIdle { appearance = appearance.copy(showUserAvatar = false, showAssistantAvatar = false) }
            compose.waitForIdle()
            assertTrue("Avatar switches must change the rendered message layout", differences(visible, capture("avatars")) > 20)
            compose.runOnIdle { appearance = appearance.copy(showUserAvatar = true, showAssistantAvatar = true, avatarBlur = 0.9f) }
            compose.waitUntil(10_000) { differences(visible, capture("avatars")) > 10 }
            // Updating a fixed image path with a new version must not retain the old cached bitmap.
            avatarFile("preview-assistant.png", AndroidColor.GREEN)
            compose.runOnIdle { appearance = appearance.copy(imageVersion = 1L, avatarBlur = 0f) }
            compose.waitUntil(10_000) {
                val pixels = capture("avatars")
                var green = 0
                for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
                    val c = pixels[x, y]
                    if (c.green > 0.8f && c.red < 0.2f && c.blue < 0.2f) green++
                }
                green > 3
            }
        } finally { user.delete(); assistant.delete() }
    }

    @Test fun topBarTransparencyInputTransparencyAndFloatingRedrawWithoutFocusingInput() {
        var appearance by mutableStateOf(ChatAppearance(transparentTopBar = false))
        var keyboardShows = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { keyboardShows++ }
            override fun hide() = Unit
        }
        compose.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                MaterialTheme { Box(Modifier.fillMaxWidth().testTag("input-preview")) { InputPreview(appearance) } }
            }
        }
        val opaque = capture("input-preview")
        compose.runOnIdle { appearance = appearance.copy(transparentTopBar = true) }
        compose.waitForIdle()
        val transparentTop = capture("input-preview")
        assertTrue("Top bar must reveal the preview background", differences(opaque, transparentTop) > 30)
        compose.runOnIdle { appearance = appearance.copy(transparentInputBar = true, inputBarSeeThrough = 0.85f) }
        compose.waitForIdle()
        val transparentInput = capture("input-preview")
        assertTrue("Input background must respond to strength", differences(transparentTop, transparentInput) > 30)
        compose.runOnIdle { appearance = appearance.copy(inputBarDeepSeeThrough = true, inputBarFloating = true) }
        compose.waitForIdle()
        assertTrue("Floating/see-through mode must visibly change input", differences(transparentInput, capture("input-preview")) > 30)
        compose.onNodeWithText(text(R.string.appearance_preview_input)).assertIsNotEnabled().performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("Preview input must not request the software keyboard", 0, keyboardShows)
        compose.onNode(isFocused()).assertDoesNotExist()
    }

    @Test fun sliderUpdatesPinnedPreviewBeforeSavingAndRetainsItAfterRelease() {
        var stored by mutableStateOf(0f)
        var drag by mutableStateOf<Float?>(null)
        var commits = 0
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    InputPreview(ChatAppearance(transparentInputBar = true, inputBarSeeThrough = drag ?: stored))
                    Box(Modifier.padding(8.dp).testTag("strength")) {
                        AppearancePercentSlider("strength", stored, "",
                            onChange = { stored = it; commits++ }, onPreview = { drag = it })
                    }
                }
            }
        }
        val before = capture("preview-input-bar")
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetProgress))
        slider.performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            moveTo(Offset(width * 0.8f, height / 2f), 100)
        }
        compose.waitForIdle()
        assertEquals("No DataStore action while dragging", 0, commits)
        assertTrue("Preview changes immediately, before the write callback", differences(before, capture("preview-input-bar")) > 30)
        slider.performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, commits)
        assertTrue(stored > 0.5f)
        assertTrue(differences(before, capture("preview-input-bar")) > 30)
    }
}