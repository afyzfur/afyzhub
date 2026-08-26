package com.afyzfur.afyzhub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.afyzfur.afyzhub.ui.chat.ChatScreen
import com.afyzfur.afyzhub.ui.settings.AboutSettingsScreen
import com.afyzfur.afyzhub.ui.settings.AppearanceSettingsScreen
import com.afyzfur.afyzhub.ui.settings.ChangelogScreen
import com.afyzfur.afyzhub.ui.settings.ChatAppearanceSettingsScreen
import com.afyzfur.afyzhub.ui.settings.MessageDisplaySettingsScreen
import com.afyzfur.afyzhub.ui.settings.ApiProfileEditScreen
import com.afyzfur.afyzhub.ui.settings.ApiProfilesScreen
import com.afyzfur.afyzhub.ui.settings.QuickPromptsSettingsScreen
import com.afyzfur.afyzhub.ui.settings.RequestLogScreen
import com.afyzfur.afyzhub.ui.settings.SettingsHomeScreen

/**
 * 导航目的地。
 *
 * 阶段 2 移除了 Home（会话列表改由聊天页抽屉承载）。
 * 阶段 5 把设置由单页拆为一级导航页 + 五个子页面。
 *
 * 改版前：Home（列表）→ Chat/{conversationId} → Settings（单页）
 * 改版后：Chat（根，含抽屉）→ Settings（导航页）→ 各子页面
 */
sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    /** API 配置组列表。原「提供商」入口现在指向这里 */
    object ApiProfiles : Screen("settings/api_profiles")

    /** 单组配置的编辑页，路径参数为组 id */
    object ApiProfileEdit : Screen("settings/api_profiles/{profileId}") {
        fun routeFor(profileId: String) = "settings/api_profiles/$profileId"
    }

    object AppearanceSettings : Screen("settings/appearance")
    object ChatAppearanceSettings : Screen("settings/chat_appearance")
    object MessageDisplaySettings : Screen("settings/message_display")
    object QuickPromptsSettings : Screen("settings/quick_prompts")
    object RequestLog : Screen("settings/request_log")
    object AboutSettings : Screen("settings/about")
    object Changelog : Screen("settings/changelog")
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
                },
                onNavigateToProvider = {
                    navController.navigate(Screen.ApiProfiles.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsHomeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProvider = {
                    navController.navigate(Screen.ApiProfiles.route)
                },
                onNavigateToAppearance = {
                    navController.navigate(Screen.AppearanceSettings.route)
                },
                onNavigateToChatAppearance = {
                    navController.navigate(Screen.ChatAppearanceSettings.route)
                },
                onNavigateToMessageDisplay = {
                    navController.navigate(Screen.MessageDisplaySettings.route)
                },
                onNavigateToQuickPrompts = {
                    navController.navigate(Screen.QuickPromptsSettings.route)
                },
                onNavigateToRequestLog = {
                    navController.navigate(Screen.RequestLog.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.AboutSettings.route)
                }
            )
        }

        composable(Screen.ApiProfiles.route) {
            ApiProfilesScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditProfile = { id ->
                    navController.navigate(Screen.ApiProfileEdit.routeFor(id))
                }
            )
        }

        composable(Screen.ApiProfileEdit.route) { entry ->
            // id 缺失时给空串，编辑页会显示"已被删除"而不是崩掉
            val id = entry.arguments?.getString("profileId").orEmpty()
            ApiProfileEditScreen(
                profileId = id,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppearanceSettings.route) {
            AppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.ChatAppearanceSettings.route) {
            ChatAppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.MessageDisplaySettings.route) {
            MessageDisplaySettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.QuickPromptsSettings.route) {
            QuickPromptsSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.RequestLog.route) {
            RequestLogScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.AboutSettings.route) {
            AboutSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChangelog = {
                    navController.navigate(Screen.Changelog.route)
                }
            )
        }
        composable(Screen.Changelog.route) {
            ChangelogScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
