package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.MessageDisplayOptions
import com.afyzfur.afyzhub.domain.model.Message
import com.afyzfur.afyzhub.util.Constants
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MetadataWidthTest {
    @get:Rule val compose = createComposeRule()
    @Test fun userTimestampBackdropWrapsTextAndStaysRightAligned() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredWidth(280.dp)) {
                    MessageMetaRow(Message(1, 1, "hello", Constants.ROLE_USER, createdAt = System.currentTimeMillis()),
                        MessageDisplayOptions(showTimestamp = true), contrastBackground = true)
                }
            }
        }
        val semantics = compose.onAllNodesWithTag("metadata-chip").fetchSemanticsNodes()
        assertTrue("The timestamp has its own bounded background", semantics.isNotEmpty())
        val chip = semantics.first().boundsInRoot
        assertTrue("Timestamp background must not occupy the full row", chip.width < 140f * compose.density.density)
        assertTrue("User timestamp remains aligned to the right", chip.right >= 280f * compose.density.density - 2f)
    }
}
