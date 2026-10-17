package com.afyzfur.afyzhub.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.afyzfur.afyzhub.ui.chat.ChatScreen
import com.afyzfur.afyzhub.ui.settings.AboutSettingsScreen
import com.afyzfur.afyzhub.ui.settings.ChangelogScreen
import com.afyzfur.afyzhub.ui.settings.MessageDisplaySettingsScreen
import com.afyzfur.afyzhub.ui.settings.ApiProfileEditScreen
import com.afyzfur.afyzhub.ui.settings.ApiProfilesScreen
import com.afyzfur.afyzhub.ui.settings.ApiProfileModelsScreen
import com.afyzfur.afyzhub.ui.settings.PromptAdvancedScreen
import com.afyzfur.afyzhub.ui.browser.InAppBrowserScreen
import com.afyzfur.afyzhub.ui.settings.QuickPromptsSettingsScreen
import com.afyzfur.afyzhub.ui.settings.RequestLogScreen
import com.afyzfur.afyzhub.ui.settings.SettingsHomeScreen
import com.afyzfur.afyzhub.ui.settings.BrowserSettingsScreen

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

    /** 配置组的模型管理页，从编辑页进入 */
    object PromptAdvanced : Screen("settings/api_profiles/{profileId}/prompt_advanced") {
        fun routeFor(profileId: String) = "settings/api_profiles/$profileId/prompt_advanced"
    }
    object ApiProfileModels : Screen("settings/api_profiles/{profileId}/models") {
        fun routeFor(profileId: String) = "settings/api_profiles/$profileId/models"
    }

    /** 应用内浏览器。URL 经 URL-encode 后作为路径参数，避免 query 串截断路由 */
    object Browser : Screen("browser/{url}") {
        fun routeFor(url: String) = "browser/${android.net.Uri.encode(url)}"
    }

    object AppearanceSettings : Screen("settings/appearance")
    object ChatAppearanceSettings : Screen("settings/chat_appearance")
    object MessageDisplaySettings : Screen("settings/message_display")
    /** 内置浏览器设置: 链接打开方式与联网搜索引擎 */
    object BrowserSettings : Screen("settings/browser")
    object QuickPromptsSettings : Screen("settings/quick_prompts")
    object RequestLog : Screen("settings/request_log")
    object AboutSettings : Screen("settings/about")
    object Changelog : Screen("settings/changelog")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    // 内置浏览器总开关: 链接点开后走内置还是系统浏览器
    val settingsRepo: com.afyzfur.afyzhub.data.settings.SettingsRepository =
        org.koin.java.KoinJavaComponent.get(
            com.afyzfur.afyzhub.data.settings.SettingsRepository::class.java
        )
    val browserSettings by settingsRepo.settings.collectAsState()
    // StateFlow 总有值, 但为类型安全仍判空兜底
    val browserEnabled: Boolean = browserSettings?.inAppBrowserEnabled ?: true

    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToProvider = {
                    navController.navigate(Screen.ApiProfiles.route)
                },
                onOpenBrowser = { url ->
                    if (browserEnabled) {
                        navController.navigate(Screen.Browser.routeFor(url))
                    } else {
                        // 总开关关闭: 交给系统浏览器处理
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { ctx.startActivity(intent) }
                    }
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
                },
                onNavigateToBrowser = {
                    navController.navigate(Screen.BrowserSettings.route)
                }
            )
        }

        composable(Screen.ApiProfiles.route) {
            // 配置列表页面

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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = {
                    navController.navigate(Screen.ApiProfileModels.routeFor(id))
                },
                onNavigateToAdvancedPrompt = {
                    navController.navigate(Screen.PromptAdvanced.routeFor(id))
                }
            )
        }

        composable(Screen.PromptAdvanced.route) { entry ->
            val id = entry.arguments?.getString("profileId").orEmpty()
            PromptAdvancedScreen(profileId = id, onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.ApiProfileModels.route) { entry ->
            val id = entry.arguments?.getString("profileId").orEmpty()
            ApiProfileModelsScreen(
                profileId = id,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Browser.route) { entry ->
            val raw = entry.arguments?.getString("url").orEmpty()
            val url = android.net.Uri.decode(raw)
            InAppBrowserScreen(
                initialUrl = url,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppearanceSettings.route) {
            AppearanceSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.MessageDisplaySettings.route) {
            MessageDisplaySettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.BrowserSettings.route) {
            BrowserSettingsScreen(onNavigateBack = { navController.popBackStack() })
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
