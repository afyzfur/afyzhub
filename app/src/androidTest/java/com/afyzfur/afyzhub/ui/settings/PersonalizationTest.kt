package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.afyzfur.afyzhub.data.settings.ChatAppearance
import com.afyzfur.afyzhub.ui.navigation.Screen
import com.afyzhub.chat.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalizationTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun allCategoriesOpenDistinctDestinationsAndReturnToPersonalization() {
        compose.setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = Screen.AppearanceSettings.route) {
                    composable(Screen.AppearanceSettings.route) {
                        AppearanceHomeScreen({}, { nav.navigate(Screen.AppearanceDetail.routeFor(it)) })
                    }
                    composable(Screen.AppearanceDetail.route) { entry ->
                        val section = AppearanceSection.fromId(entry.arguments?.getString("section"))
                        androidx.compose.foundation.layout.Column {
                            SettingsPageHeader(text(section.titleRes)) { nav.popBackStack() }
                            Text(section.id, Modifier.testTag("section-destination"))
                        }
                    }
                }
            }
        }
        for (section in AppearanceSection.entries) {
            compose.onNodeWithText(text(section.subtitleRes)).performScrollTo().performClick()
            compose.onNodeWithTag("section-destination").assertTextEquals(section.id)
            compose.onNodeWithContentDescription(text(R.string.ui_11d024154013)).performClick()
            compose.onNodeWithText(text(section.subtitleRes)).assertExists()
        }
    }

    @Test fun fourSlidersPreviewWhilePressedAndSaveOnlyOnRelease() {
        var stored by mutableStateOf(ChatAppearance())
        val commits = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                ContentOpacitySettingsContent(stored, {},
                    { stored = stored.copy(searchOpacity = it); commits += "search" },
                    { stored = stored.copy(thinkingOpacity = it); commits += "thinking" },
                    { stored = stored.copy(bodyOpacity = it); commits += "body" },
                    { stored = stored.copy(infoOpacity = it); commits += "info" })
            }
        }
        val kinds = listOf("search", "thinking", "body", "info")
        for ((index, kind) in kinds.withIndex()) {
            compose.onNodeWithTag("opacity-slider-$kind").performScrollTo()
            val slider = compose.onNode(hasSetProgressAction() and hasAnyAncestor(hasTestTag("opacity-slider-$kind")))
            slider.performTouchInput {
                down(Offset(width * 0.85f, height / 2f))
                moveTo(Offset(width * 0.3f, height / 2f), 100)
            }
            compose.waitForIdle()
            val value = compose.onNodeWithTag("opacity-preview-$kind", useUnmergedTree = true)
                .fetchSemanticsNode().config[PreviewSurfaceOpacity]
            assertTrue("The example updates while the pointer is still down", value in 0.1f..0.5f)
            assertEquals("No settings write before release", index, commits.size)
            for (untouched in kinds.drop(index + 1)) {
                val other = compose.onNodeWithTag("opacity-preview-$untouched", useUnmergedTree = true)
                    .fetchSemanticsNode().config[PreviewSurfaceOpacity]
                assertEquals("Other sections stay unchanged", 1f, other)
            }
            slider.performTouchInput { up() }
            compose.waitForIdle()
            assertEquals(index + 1, commits.size)
            assertEquals(kind, commits.last())
            compose.onNodeWithTag("opacity-preview").assertIsDisplayed()
        }
    }

    @Test fun previewChangesDrawnBackgroundPixelsAndKeepsText() {
        var appearance by mutableStateOf(ChatAppearance())
        compose.setContent {
            MaterialTheme {
                OpacityChatPreview(appearance, Modifier.fillMaxWidth().height(500.dp))
            }
        }
        val first = compose.onNodeWithTag("opacity-preview").captureToImage().toPixelMap()
        compose.runOnIdle {
            appearance = appearance.copy(searchOpacity = 0f, thinkingOpacity = 0f, bodyOpacity = 0f, infoOpacity = 0f)
        }
        compose.waitForIdle()
        val second = compose.onNodeWithTag("opacity-preview").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until first.height step 8) for (x in 0 until first.width step 8) {
            if (first[x, y] != second[x, y]) changed++
        }
        assertTrue("Real chat surfaces must redraw, not only percentage labels", changed > 50)
        compose.onNodeWithText(text(R.string.appearance_preview_answer)).assertExists()
        compose.onNodeWithText(text(R.string.appearance_preview_search), substring = true).assertExists()
    }
}
