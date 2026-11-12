package com.afyzfur.afyzhub.ui.chat

import android.os.Handler
import android.os.HandlerThread
import android.os.Bundle
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.afyzfur.afyzhub.domain.model.ThinkingEffort
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Diagnostic rendering samples; emulator timing is not a device performance threshold. */
class ThinkingEffortFrameTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun recordRenderedFramesDuringClicksAndDrags() {
        val durations = java.util.concurrent.CopyOnWriteArrayList<Long>()
        val worker = HandlerThread("effort-frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        compose.activityRule.scenario.onActivity {
            it.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
        }
        try {
            compose.setContent {
                MaterialTheme {
                    val effort = remember { mutableStateOf(ThinkingEffort.LOW) }
                    AnimatedThinkingEffortSelector(effort.value, true) { effort.value = it }
                }
            }
            repeat(10) { index ->
                compose.onNodeWithTag(if (index % 2 == 0) "effort-max" else "effort-low").performClick()
                compose.waitForIdle()
            }
            repeat(4) {
                compose.onNodeWithTag("effort-track").performTouchInput { swipeLeft(durationMillis = 200) }
                compose.waitForIdle()
            }
            // Barrier drains callbacks already queued on the metrics worker.
            val drained = java.util.concurrent.CountDownLatch(1)
            Handler(worker.looper).post { drained.countDown() }
            assertTrue(drained.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val sorted = durations.filter { it > 0L }.sorted()
            assertTrue("Window must supply actual rendered frame samples", sorted.isNotEmpty())
            val report = "samples=${sorted.size},p95_total_ms=${sorted[((sorted.size - 1) * 0.95).toInt()] / 1_000_000.0},max_total_ms=${sorted.last() / 1_000_000.0},over_16_7ms=${sorted.count { it > 16_700_000L }}"
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "EffortFrames $report\n") })
            android.util.Log.i("EffortFrames", report)
        } finally {
            compose.activityRule.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            worker.quitSafely()
        }
    }
}
