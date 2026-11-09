package com.afyzfur.afyzhub.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import kotlin.math.roundToInt

/** Input owns the target; animation owns rendering. Persistence never drives a pending gesture. */
internal class ThinkingEffortControlState(initial: ThinkingEffort) {
    var target by mutableFloatStateOf(initial.ordinal.toFloat())
        private set
    var selected by mutableStateOf(initial)
        private set
    var followingDrag by mutableStateOf(false)
        private set
    private var pending: ThinkingEffort? = null
    private var interacting = false
    private var persisted = initial

    fun preview(value: Float) {
        interacting = true
        followingDrag = true
        target = value.coerceIn(0f, 4f)
    }

    fun choose(value: Float, fromDrag: Boolean = false): ThinkingEffort? {
        interacting = false
        followingDrag = fromDrag
        val effort = ThinkingEffort.entries[value.roundToInt().coerceIn(0, 4)]
        target = effort.ordinal.toFloat()
        selected = effort
        if (effort == pending || (pending == null && effort == persisted)) return null
        pending = effort
        return effort
    }

    fun cancel() {
        interacting = false
        followingDrag = false
        target = selected.ordinal.toFloat()
    }

    fun acceptExternal(effort: ThinkingEffort) {
        persisted = effort
        if (pending == effort) pending = null
        if (pending != null || interacting) return
        selected = effort
        target = effort.ordinal.toFloat()
    }
}
