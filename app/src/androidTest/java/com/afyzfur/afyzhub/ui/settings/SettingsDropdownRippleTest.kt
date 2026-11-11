package com.afyzfur.afyzhub.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.afyzfur.afyzhub.ui.chat.thinkingEffortSheetShape
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsDropdownRippleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuFirstAndLastOptionsRemainClickableAfterClipping() {
        val selected = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SettingsDropdownItem(Icons.Default.Info, "Color mode", "Theme", listOf("System", "Light", "Dark"), "System", { it }, { selected.add(it) })
            }
        }
        compose.onNodeWithText("System").performClick()
        val menu = compose.onNodeWithTag("settings-dropdown-content").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithTag("settings-dropdown-option-0").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("settings-dropdown-option-2").fetchSemanticsNode().boundsInRoot
        assertEquals("No blank strip above first ripple", menu.top, first.top, 1f)
        assertEquals("No blank strip below last ripple", menu.bottom, last.bottom, 1f)
        compose.onNodeWithTag("settings-dropdown-option-2").performClick()
        compose.runOnIdle { assertEquals(listOf("Dark"), selected) }
        compose.onNodeWithText("System").performClick()
        compose.onNodeWithTag("settings-dropdown-option-0").performClick()
        compose.runOnIdle { assertEquals(listOf("Dark", "System"), selected) }
    }

    @Test fun menuEndShapesHaveSymmetricExposedCorners() {
        val size = Size(240f, 56f)
        val density = Density(1f)
        val first = settingsDropdownOptionShape(0, 3).createOutline(size, LayoutDirection.Ltr, density) as Outline.Rounded
        val middle = settingsDropdownOptionShape(1, 3).createOutline(size, LayoutDirection.Ltr, density)
        val last = settingsDropdownOptionShape(2, 3).createOutline(size, LayoutDirection.Ltr, density) as Outline.Rounded
        assertEquals(first.roundRect.topLeftCornerRadius, first.roundRect.topRightCornerRadius)
        assertEquals(20f, first.roundRect.topLeftCornerRadius.x, 0.01f)
        assertEquals(0f, first.roundRect.bottomLeftCornerRadius.x, 0.01f)
        assertTrue(middle is Outline.Rectangle || (middle is Outline.Rounded && middle.roundRect.topLeftCornerRadius.x == 0f))
        assertEquals(0f, last.roundRect.topLeftCornerRadius.x, 0.01f)
        assertEquals(20f, last.roundRect.bottomLeftCornerRadius.x, 0.01f)
        assertEquals(last.roundRect.bottomLeftCornerRadius, last.roundRect.bottomRightCornerRadius)
    }

    @Test fun thinkingSheetTopCornersAreEqualInBothDirections() {
        listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { direction ->
            val outline = thinkingEffortSheetShape.createOutline(Size(360f, 420f), direction, Density(1f)) as Outline.Rounded
            assertEquals(outline.roundRect.topLeftCornerRadius, outline.roundRect.topRightCornerRadius)
            assertEquals(28f, outline.roundRect.topLeftCornerRadius.x, 0.01f)
            assertEquals(0f, outline.roundRect.bottomLeftCornerRadius.x, 0.01f)
        }
    }
}
