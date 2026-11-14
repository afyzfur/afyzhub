package com.afyzfur.afyzhub.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.afyzfur.afyzhub.data.memory.MemoryRunState
import com.afyzfur.afyzhub.domain.model.MemoryError
import com.afyzfur.afyzhub.domain.model.MemoryItem
import com.afyzfur.afyzhub.domain.model.MemorySnapshot
import com.afyzhub.chat.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MemoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val added = mutableListOf<String>()
    private val updated = mutableListOf<Pair<String, String>>()
    private val deleted = mutableListOf<String>()
    private var cleared = 0
    private var summaries = 0

    private fun show(state: MemorySnapshot, run: MemoryRunState = MemoryRunState()) {
        compose.setContent {
            MaterialTheme {
                MemoryContent(
                    state = state, run = run, onNavigateBack = {}, onEnabledChange = {}, onFrequencyChange = {},
                    onSummarize = { summaries++ }, onAdd = { added += it }, onUpdate = { id, t -> updated += id to t },
                    onDelete = { deleted += it }, onClear = { cleared++ }
                )
            }
        }
    }

    private val items = listOf(
        MemoryItem("a", "用户是 Android 开发者"),
        MemoryItem("m", "用户偏好简洁回答", manual = true)
    )

    @Test fun addEditDeleteAndClearReachTheRepository() {
        show(MemorySnapshot(loaded = true, enabled = true, items = items))
        compose.onNodeWithText(text(R.string.memory_source_manual)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.memory_add)).performScrollTo().performClick()
        compose.onNodeWithTag("memory-save").assertIsNotEnabled()
        compose.onNodeWithTag("memory-editor").performTextInput("用户住在重庆")
        compose.onNodeWithTag("memory-save").performClick()
        assertEquals(listOf("用户住在重庆"), added)

        compose.onNodeWithText("用户是 Android 开发者").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_edit_hint)).assertIsDisplayed()
        compose.onNodeWithTag("memory-editor").performTextReplacement("用户是 Kotlin 开发者")
        compose.onNodeWithTag("memory-save").performClick()
        assertEquals(listOf("a" to "用户是 Kotlin 开发者"), updated)

        compose.onNodeWithTag("memory-delete-m").performScrollTo().performClick()
        assertEquals(listOf("m"), deleted)

        compose.onNodeWithText(text(R.string.memory_clear)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.memory_clear_action)).performClick()
        assertEquals(1, cleared)
    }

    @Test fun summarizeIsOfferedOnlyWhenEnabledAndIdle() {
        show(MemorySnapshot(loaded = true, enabled = false))
        compose.onNodeWithTag("memory-summarize").assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.memory_empty)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.memory_status_off)).performScrollTo().assertIsDisplayed()
    }

    @Test fun runningAndErrorStatesAreVisible() {
        show(MemorySnapshot(loaded = true, enabled = true), MemoryRunState(running = true))
        compose.onNodeWithTag("memory-summarize").assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.memory_status_running)).assertIsDisplayed()
    }

    @Test fun failedRunExplainsTheCauseAndCanRetry() {
        show(MemorySnapshot(loaded = true, enabled = true), MemoryRunState(error = MemoryError.NO_API))
        compose.onNodeWithTag("memory-error").assertTextEquals(text(R.string.memory_error_no_api))
        compose.onNodeWithTag("memory-summarize").performClick()
        assertEquals(1, summaries)
    }
}
