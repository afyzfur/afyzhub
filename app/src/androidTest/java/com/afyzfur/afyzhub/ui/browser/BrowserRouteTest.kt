package com.afyzfur.afyzhub.ui.browser

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.afyzfur.afyzhub.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrowserRouteTest {
    @get:Rule val compose = createComposeRule()
    @Test fun encodedUrlsSurviveTheActualNavigationRoute() {
        val url = "https://example.test/a%2Fb?q=a%26b%3Dc&redirect=https%3A%2F%2Fother.test%2F%3Fx%3D1%26y%3D2#part"
        var received: String? = null
        compose.setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "start") {
                composable("start") {
                    Text("start")
                    LaunchedEffect(Unit) { nav.navigate(Screen.Browser.routeFor(url)) }
                }
                composable(Screen.Browser.route) { entry ->
                    SideEffect { received = entry.arguments?.getString("url") }
                }
            }
        }
        compose.waitUntil(5000) { received != null }
        compose.runOnIdle { assertEquals(url, received) }
    }
}
