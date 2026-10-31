package com.afyzfur.afyzhub.ui.chat

import kotlin.math.abs

/** Pure gesture reducer: no Compose state, IME timing, or animation callbacks. */
internal class ChatGestureState(
    private val slop: Float,
    private val drawerTrigger: Float,
    private val keyboardTrigger: Float = 72f,
    private val drawerEligible: Boolean,
    drawerInitiallyOpen: Boolean
) {
    // 0 undecided, 1 horizontal drawer, 2 vertical keyboard, 3 ignored horizontal
    var axis: Int = 0
        private set
    private var x = 0f
    private var y = 0f
    private var drawerDistance = 0f
    private var keyboardDistance = 0f
    private var lastKeyboardAction = 0
    var drawerTargetOpen: Boolean = drawerInitiallyOpen
        private set
    var drawerWasActivated: Boolean = false
        private set

    data class Step(
        val consume: Boolean = false,
        val drawerDelta: Float = 0f,
        val drawerTarget: Boolean? = null,
        val keyboardAction: Int = 0 // -1 show, +1 hide
    )

    fun move(dx: Float, dy: Float): Step {
        x += dx
        y += dy
        var drawerDelta = dx
        var keyboardDelta = dy
        if (axis == 0) {
            if (abs(x) <= slop && abs(y) <= slop) return Step()
            axis = if (abs(x) > abs(y)) {
                if (drawerEligible && (drawerTargetOpen || x > 0f)) 1 else 3
            } else 2
            // Preserve movement accumulated before direction lock.
            drawerDelta = x
            keyboardDelta = y
        }
        if (axis == 1) {
            drawerDistance = directionalDistance(drawerDistance, drawerDelta)
            val target = when {
                drawerDistance >= drawerTrigger && !drawerTargetOpen -> true
                drawerDistance <= -drawerTrigger && drawerTargetOpen -> false
                else -> null
            }
            if (target != null) {
                drawerTargetOpen = target
                drawerWasActivated = true
                drawerDistance = 0f
            }
            return Step(consume = true, drawerDelta = drawerDelta, drawerTarget = target)
        }
        if (axis == 2) {
            keyboardDistance = directionalDistance(keyboardDistance, keyboardDelta)
            val action = when {
                keyboardDistance <= -keyboardTrigger && lastKeyboardAction != -1 -> -1
                keyboardDistance >= keyboardTrigger && lastKeyboardAction != 1 -> 1
                else -> 0
            }
            if (action != 0) {
                lastKeyboardAction = action
                keyboardDistance = 0f
            }
            return Step(keyboardAction = action)
        }
        return Step()
    }

    private fun directionalDistance(previous: Float, delta: Float): Float =
        if (previous * delta < 0f) delta else previous + delta
}
