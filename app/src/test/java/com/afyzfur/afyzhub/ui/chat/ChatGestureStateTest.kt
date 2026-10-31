package com.afyzfur.afyzhub.ui.chat

import org.junit.Assert.*
import org.junit.Test

class ChatGestureStateTest {
    private fun state(edge: Boolean = true, open: Boolean = false) =
        ChatGestureState(8f, 32f, 72f, edge, open)

    @Test fun edgeRightSwipeOpensBeforeFingerUp() {
        val s = state()
        assertTrue(s.move(10f, 1f).consume)
        assertEquals(true, s.move(24f, 0f).drawerTarget)
        assertTrue(s.drawerTargetOpen)
    }
    @Test fun shortReverseClosesWithoutHalfWidthRequirement() {
        val s = state(open = true)
        assertEquals(false, s.move(-33f, 0f).drawerTarget)
        assertFalse(s.drawerTargetOpen)
    }
    @Test fun drawerCanOpenCloseOpenInOnePointerSequence() {
        val s = state()
        assertEquals(true, s.move(40f, 0f).drawerTarget)
        assertEquals(false, s.move(-40f, 0f).drawerTarget)
        assertEquals(true, s.move(40f, 0f).drawerTarget)
    }
    @Test fun keyboardCanShowHideShowInOnePointerSequence() {
        val s = state()
        assertEquals(-1, s.move(0f, -73f).keyboardAction)
        assertEquals(0, s.move(0f, -200f).keyboardAction)
        // Reversing needn't undo the preceding 200px.
        assertEquals(1, s.move(0f, 73f).keyboardAction)
        assertEquals(-1, s.move(0f, -73f).keyboardAction)
    }
    @Test fun verticalAtEdgeNeverConsumesOrOpensDrawer() {
        val s = state()
        val step = s.move(4f, -80f)
        assertFalse(step.consume)
        assertNull(step.drawerTarget)
        assertEquals(-1, step.keyboardAction)
        assertFalse(s.move(300f, 0f).consume)
    }
    @Test fun middleHorizontalDoesNotOpenOrChangeKeyboard() {
        val step = state(edge = false).move(100f, 1f)
        assertFalse(step.consume)
        assertNull(step.drawerTarget)
        assertEquals(0, step.keyboardAction)
    }
    @Test fun slopAccumulationIsNotLost() {
        val s = state()
        repeat(3) { assertEquals(0, s.move(0f, -2f).keyboardAction) }
        assertEquals(-1, s.move(0f, -67f).keyboardAction)
    }
}
