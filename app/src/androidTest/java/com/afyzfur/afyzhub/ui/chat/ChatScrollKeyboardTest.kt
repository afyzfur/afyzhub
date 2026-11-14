package com.afyzfur.afyzhub.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatScrollKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun ChatScrollDemo(onKeyboard: () -> Unit) {
        val listState = rememberLazyListState()
        val atBottom = remember { mutableStateOf(true) }
        LaunchedEffect(listState) {
            androidx.compose.runtime.snapshotFlow {
                val info = listState.layoutInfo
                info.visibleItemsInfo.any { it.index == info.totalItemsCount - 1 }
            }.collect { atBottom.value = it }
        }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val canShow = atBottom.value && down.position.y >= size.height - 120.dp.toPx()
                val reducer = ChatGestureState(
                    slop = viewConfiguration.touchSlop,
                    drawerTrigger = 32.dp.toPx(),
                    keyboardTrigger = 144.dp.toPx(),
                    drawerEligible = false,
                    drawerInitiallyOpen = false,
                    keyboardCanShow = canShow
                )
                var last = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.position - last
                    last = change.position
                    if (reducer.move(delta.x, delta.y).keyboardAction < 0) onKeyboard()
                }
            }
        }) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items((0..30).toList()) { androidx.compose.material3.Text("row $it") }
            }
        }
    }

    @Test fun upwardSwipeWhileReadingDoesNotRequestKeyboard() {
        var requests = 0
        compose.setContent { MaterialTheme { ChatScrollDemo { requests++ } } }
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.65f, endY = height * 0.25f) }
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun keyboardOpensOnlyAfterBottomSwipeBeginsInBottomGestureArea() {
        var requests = 0
        compose.setContent { MaterialTheme { ChatScrollDemo { requests++ } } }
        compose.waitForIdle()
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.92f, endY = height * 0.55f) }
        compose.runOnIdle { assertEquals(1, requests) }
    }
}