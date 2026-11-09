package com.afyzfur.afyzhub.ui.chat

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

    private fun show() {
        compose.setContent {
            MaterialTheme {
                AnimatedThinkingEffortSelector(ThinkingEffort.LOW, true) { saved.add(it) }
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
        compose.mainClock.advanceTimeBy(210)
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
}
