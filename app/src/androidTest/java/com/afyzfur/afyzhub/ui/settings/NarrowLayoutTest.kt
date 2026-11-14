package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.memory.MemoryRunState
import com.afyzfur.afyzhub.domain.model.MemorySnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NarrowLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun memoryStatusAndActionDoNotOverlapAtLargeFontOnNarrowScreen() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                MaterialTheme {
                    Box(Modifier.requiredWidth(280.dp)) {
                        MemoryContent(
                            state = MemorySnapshot(loaded = true, enabled = true), run = MemoryRunState(),
                            onNavigateBack = {}, onEnabledChange = {}, onFrequencyChange = {},
                            onSummarize = {}, onAdd = {}, onUpdate = { _, _ -> }, onDelete = {}, onClear = {}
                        )
                    }
                }
            }
        }
        val status = compose.onNodeWithTag("memory-status").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithTag("memory-summarize").fetchSemanticsNode().boundsInRoot
        assertTrue("Status must remain above the action, even if it wraps", status.bottom <= button.top)
    }
}