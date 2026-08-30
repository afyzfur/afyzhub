package com.afyzfur.afyzhub.di

import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.data.repository.ChatRepositoryImpl
import com.afyzfur.afyzhub.data.image.ImageStore
import com.afyzfur.afyzhub.domain.usecase.GenerateTitleUseCase
import com.afyzfur.afyzhub.domain.usecase.SendMessageUseCase
import com.afyzfur.afyzhub.ui.chat.ChatViewModel
import com.afyzfur.afyzhub.ui.chat.ChatHostViewModel
import com.afyzfur.afyzhub.ui.settings.RequestLogViewModel
import com.afyzfur.afyzhub.ui.settings.ApiProfilesViewModel
import com.afyzfur.afyzhub.ui.settings.ProfileModelsViewModel
import com.afyzfur.afyzhub.ui.settings.SettingsViewModel
import com.afyzfur.afyzhub.ui.settings.UiPreferencesViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // Repository：conversationDao, messageDao, clientRegistry, settingsProvider
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), get()) }

    // UseCases
    single { SendMessageUseCase(get()) }
    // 标题与总结生成：需要网络客户端与当前设置
    single { GenerateTitleUseCase(get(), get()) }

    // ViewModels
    viewModel { ChatHostViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ApiProfilesViewModel(get()) }
    viewModel { ProfileModelsViewModel(get()) }
    // 图片存取需要 Context，用 androidContext() 注入
    single { ImageStore(androidContext()) }
    viewModel { UiPreferencesViewModel(get(), get()) }
    viewModel { RequestLogViewModel(get(), get()) }
}
