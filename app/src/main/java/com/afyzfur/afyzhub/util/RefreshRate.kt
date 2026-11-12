package com.afyzfur.afyzhub.util

data class DisplayModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/** Highest-rate mode at the current resolution; switching resolution would relayout the whole window. */
fun highestRefreshMode(current: DisplayModeSpec, supported: List<DisplayModeSpec>): DisplayModeSpec? =
    supported.filter { it.width == current.width && it.height == current.height }
        .maxByOrNull { it.refreshRate }
