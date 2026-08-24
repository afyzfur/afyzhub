package com.afyzfur.afyzhub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.afyzfur.afyzhub.ui.chat.ChatScreen
import com.afyzfur.afyzhub.ui.settings.SettingsScreen

/**
 * 导航目的地。
 *
 * 阶段 2 变更：移除 Home。会话列表改由聊天页的抽屉承载，
 * 聊天页成为启动目标，因此不再需要 conversationId 路由参数——
 * 当前会话由 ChatHostViewModel 持有。
 *
 * 改版前：Home（列表）→ Chat/{conversationId} → Settings
 * 改版后：Chat（根，内含抽屉）→ Settings
 */
sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
