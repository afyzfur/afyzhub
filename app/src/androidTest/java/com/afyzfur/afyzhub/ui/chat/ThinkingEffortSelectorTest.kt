package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThinkingEffortSelectorTest {
    @get:Rule val compose = createComposeRule()
    private val saved = mutableListOf<ThinkingEffort>()

    private fun show(enabled: Boolean = true, rtl: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    AnimatedThinkingEffortSelector(ThinkingEffort.LOW, enabled) { saved.add(it) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun progress(): Float = compose.onNodeWithTag("effort-track")
        .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test fun labelClickAnimatesFromOldPositionAndSavesWithoutWaiting() {
        show()
        compose.onNodeWithTag("effort-max").performClick()
        compose.runOnIdle { assertEquals(listOf(ThinkingEffort.MAX), saved) }
        assertTrue(progress() < 0.1f)
        compose.mainClock.advanceTimeBy(100)
        assertTrue(progress() in 0.2f..3.8f)
        compose.mainClock.advanceTimeBy(500)
        assertEquals(4f, progress(), 0.01f)
    }

    @Test fun repeatedTrackTapsPersistAndDoNotTeleport() {
        show()
        val track = compose.onNodeWithTag("effort-track")
        track.performTouchInput { click(Offset(width * 0.9f, height / 2f)) }
        compose.runOnIdle { assertEquals(ThinkingEffort.MAX, saved.last()) }
        assertTrue(progress() < 0.1f)
        compose.mainClock.advanceTimeBy(600)
        track.performTouchInput { click(Offset(width * 0.3f, height / 2f)) }
        compose.runOnIdle { assertEquals(ThinkingEffort.MEDIUM, saved.last()) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(1f, progress(), 0.01f)
        track.performTouchInput { click(Offset(width * 0.7f, height / 2f)) }
        compose.runOnIdle { assertEquals(ThinkingEffort.XHIGH, saved.last()) }
        compose.mainClock.advanceTimeBy(600)
        assertEquals(3f, progress(), 0.01f)
    }

    @Test fun rapidSelectionsKeepLatestTargetAndAlignmentIsShared() {
        show()
        compose.onNodeWithTag("effort-max").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("effort-medium").performClick()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(1f, progress(), 0.01f)
        compose.runOnIdle { assertEquals(listOf(ThinkingEffort.MAX, ThinkingEffort.MEDIUM), saved) }
        val track = compose.onNodeWithTag("effort-track").fetchSemanticsNode().boundsInRoot
        ThinkingEffort.entries.forEachIndexed { index, effort ->
            val label = compose.onNodeWithTag("effort-${effort.id}").fetchSemanticsNode().boundsInRoot
            val expected = track.left + track.width * (0.1f + 0.2f * index)
            assertEquals(expected, label.center.x, 1.5f)
            if (index > 0) {
                val previous = compose.onNodeWithTag("effort-${ThinkingEffort.entries[index - 1].id}").fetchSemanticsNode().boundsInRoot
                assertTrue(previous.right <= label.left)
            }
        }
    }

    @Test fun dragCommitsOnceAtReleaseAndDisplayFollowsWithAnimation() {
        show()
        compose.onNodeWithTag("effort-track").performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            moveTo(Offset(width * 0.9f, height / 2f))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(ThinkingEffort.MAX), saved) }
        assertTrue(progress() < 0.1f)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(4f, progress(), 0.01f)
    }

    @Test fun heldDragTracksWithinShortDelayAndDoesNotPersistUntilRelease() {
        show()
        val track = compose.onNodeWithTag("effort-track")
        track.performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
        }
        compose.mainClock.advanceTimeBy(120)
        assertTrue("Drag should not wait for a full click tween", progress() > 1.8f)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        track.performTouchInput { moveTo(Offset(width * 0.9f, height / 2f)) }
        compose.mainClock.advanceTimeBy(120)
        assertTrue("Retargeted drag should retain motion", progress() > 3.8f)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        track.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(ThinkingEffort.MAX), saved) }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(4f, progress(), 0.02f)
    }

    @Test fun clickSettlesWithinThreeHundredMillisecondsWithoutTeleporting() {
        show()
        compose.onNodeWithTag("effort-track").performTouchInput {
            click(Offset(width * 0.9f, height / 2f))
        }
        compose.runOnIdle { assertEquals(listOf(ThinkingEffort.MAX), saved) }
        assertTrue(progress() < 0.1f)
        compose.mainClock.advanceTimeBy(100)
        assertTrue(progress() in 0.2f..3.8f)
        compose.mainClock.advanceTimeBy(200)
        assertEquals(4f, progress(), 0.01f)
    }

    @Test fun continuouslyRetargetedDragDoesNotRestartFromRest() {
        show()
        val track = compose.onNodeWithTag("effort-track")
        track.performTouchInput { down(Offset(width * 0.1f, height / 2f)) }
        repeat(12) { index ->
            track.performTouchInput {
                moveTo(Offset(width * (0.1f + 0.8f * (index + 1) / 12f), height / 2f))
            }
            compose.mainClock.advanceTimeBy(16)
        }
        assertTrue("Continuous motion must not repeatedly restart ease-in", progress() > 2.4f)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        compose.mainClock.advanceTimeBy(120)
        assertTrue(progress() > 3.85f)
        track.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(ThinkingEffort.MAX), saved) }
        compose.mainClock.advanceTimeBy(200)
        assertEquals(4f, progress(), 0.02f)
    }

    @Test fun compactCapsuleKeepsTouchTrackAndLabelsAligned() {
        show()
        val track = compose.onNodeWithTag("effort-track").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag("effort-low").fetchSemanticsNode().boundsInRoot
        assertEquals("Compact track retains a 60dp touch height", 1.5f, track.height / label.height, 0.03f)
        ThinkingEffort.entries.forEachIndexed { index, effort ->
            val bounds = compose.onNodeWithTag("effort-${effort.id}").fetchSemanticsNode().boundsInRoot
            assertEquals(track.left + track.width * (0.1f + 0.2f * index), bounds.center.x, 1.5f)
        }
    }

    @Test fun disabledCapsuleCannotChangeSelection() {
        show(enabled = false)
        compose.onNodeWithTag("effort-track").performTouchInput {
            click(Offset(width * 0.9f, height / 2f))
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        assertEquals(0f, progress(), 0.01f)
        compose.onNodeWithTag("effort-max").assertIsNotEnabled()
    }

    @Test fun rtlCapsuleMapsLeftEndpointToMaximum() {
        show(rtl = true)
        compose.onNodeWithTag("effort-track").performTouchInput {
            click(Offset(width * 0.1f, height / 2f))
        }
        compose.runOnIdle { assertEquals(listOf(ThinkingEffort.MAX), saved) }
        compose.mainClock.advanceTimeBy(400)
        assertEquals(4f, progress(), 0.01f)
    }

    @Test fun openedSheetIsVisiblePaintedAndInteractiveAfterReopening() {
        val trackColor = Color(0xFFCBD5E1)
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surfaceContainerHighest = trackColor)) {
                val opened = remember { mutableStateOf(false) }
                val effort = remember { mutableStateOf(ThinkingEffort.LOW) }
                Surface(Modifier.fillMaxSize(), color = Color.White) {
                    Button(onClick = { opened.value = true }) { Text("Open effort") }
                }
                if (opened.value) {
                    ThinkingEffortSheet(
                        thinkingEnabled = true,
                        current = effort.value,
                        thinkingGuidanceEnabled = false,
                        onThinkingEnabledChange = {},
                        onEffortChange = { effort.value = it; saved.add(it) },
                        onGuidanceEnabledChange = {},
                        onDismiss = { opened.value = false }
                    )
                }
            }
        }
        repeat(2) {
            compose.onNodeWithText("Open effort").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("thinking-sheet").assertIsDisplayed()
            val track = compose.onNodeWithTag("effort-track").assertIsDisplayed()
            // Semantics alone can exist while an ancestor clip suppresses every painted pixel.
            val image = track.captureToImage().toPixelMap()
            val pixel = image[(image.width * 0.98f).toInt(), image.height / 2]
            assertEquals("The actual capsule must be painted, not just the dimming scrim", trackColor.red, pixel.red, 0.03f)
            assertEquals(trackColor.green, pixel.green, 0.03f)
            assertEquals(trackColor.blue, pixel.blue, 0.03f)
            compose.onNodeWithTag("effort-max").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(ThinkingEffort.MAX, saved.last()) }
            // Swipe the real modal down so the next iteration exercises its entry animation again.
            compose.onNodeWithTag("thinking-sheet").performTouchInput {
                swipe(Offset(width / 2f, height * 0.04f), Offset(width / 2f, height * 0.96f), 200)
            }
            compose.waitForIdle()
            compose.onNodeWithTag("thinking-sheet").assertDoesNotExist()
        }
    }
}
