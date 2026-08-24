package com.afyzfur.afyzhub.di

import com.afyzfur.afyzhub.data.repository.ChatRepository
import com.afyzfur.afyzhub.data.repository.ChatRepositoryImpl
import com.afyzfur.afyzhub.domain.usecase.SendMessageUseCase
import com.afyzfur.afyzhub.ui.chat.ChatViewModel
import com.afyzfur.afyzhub.ui.chat.ChatHostViewModel
import com.afyzfur.afyzhub.ui.settings.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // Repository：conversationDao, messageDao, clientRegistry, settingsProvider
    single<ChatRepository> { ChatRepositoryImpl(get(), get(), get(), get()) }

    // UseCases
    single { SendMessageUseCase(get()) }

    // ViewModels
    viewModel { ChatHostViewModel(get(), get()) }
    viewModel { ChatViewModel(get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { UiPreferencesViewModel(get()) }
}
