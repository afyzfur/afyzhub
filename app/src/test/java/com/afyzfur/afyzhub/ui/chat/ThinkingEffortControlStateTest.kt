package com.afyzfur.afyzhub.ui.chat

import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import org.junit.Assert.*
import org.junit.Test

class ThinkingEffortControlStateTest {
    @Test fun repeatedClicksCommitEveryNewSelection() {
        val state = ThinkingEffortControlState(ThinkingEffort.LOW)
        assertEquals(ThinkingEffort.MAX, state.choose(4f))
        assertEquals(ThinkingEffort.HIGH, state.choose(2f))
        assertEquals(ThinkingEffort.LOW, state.choose(0f))
        assertNull(state.choose(0f))
    }

    @Test fun previewDoesNotCommitAndReleaseRoundsLatestTarget() {
        val state = ThinkingEffortControlState(ThinkingEffort.LOW)
        state.preview(2.8f)
        assertEquals(ThinkingEffort.LOW, state.selected)
        assertEquals(2.8f, state.target, 0.001f)
        assertEquals(ThinkingEffort.XHIGH, state.choose(state.target))
        assertEquals(3f, state.target, 0.001f)
    }

    @Test fun stalePersistenceCannotOverridePendingSelection() {
        val state = ThinkingEffortControlState(ThinkingEffort.LOW)
        state.choose(4f)
        state.choose(2f)
        state.acceptExternal(ThinkingEffort.MAX)
        assertEquals(2f, state.target, 0.001f)
        state.acceptExternal(ThinkingEffort.HIGH)
        state.acceptExternal(ThinkingEffort.MEDIUM)
        assertEquals(1f, state.target, 0.001f)
    }

    @Test fun cancelAndExternalUpdatesDoNotInterruptGesture() {
        val state = ThinkingEffortControlState(ThinkingEffort.MEDIUM)
        state.preview(3.7f)
        state.acceptExternal(ThinkingEffort.LOW)
        assertEquals(3.7f, state.target, 0.001f)
        state.cancel()
        assertEquals(1f, state.target, 0.001f)
    }

    @Test fun dragReleaseKeepsSpringModeButNextClickUsesTween() {
        val state = ThinkingEffortControlState(ThinkingEffort.LOW)
        state.preview(2.7f)
        assertTrue(state.followingDrag)
        state.choose(2.7f, fromDrag = true)
        assertTrue(state.followingDrag)
        assertEquals(ThinkingEffort.MAX, state.choose(4f))
        assertFalse(state.followingDrag)
        state.preview(1f)
        state.cancel()
        assertFalse(state.followingDrag)
    }
}
