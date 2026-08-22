package com.afyzfur.afyzhub.di

import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.data.repository.ChatRepositoryImpl
import com.afyzfur.afyzhub.domain.usecase.SendMessageUseCase
import com.afyzfur.afyzhub.ui.chat.ChatViewModel
import com.afyzfur.afyzhub.ui.home.HomeViewModel
import com.afyzfur.afyzhub.ui.settings.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // Repository：conversationDao, messageDao, openAIApi, streamSource, settingsProvider
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), get(), get()) }

    // UseCases
    single { SendMessageUseCase(get()) }

    // ViewModels
    viewModel { HomeViewModel(get()) }
    viewModel { ChatViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
}
