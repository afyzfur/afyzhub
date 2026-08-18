package com.afyzfur.afyzhub.di

import android.content.Context
import androidx.room.Room
import com.afyzfur.afyzhub.data.local.database.AppDatabase
import com.afyzfur.afyzhub.util.Constants
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        ).build()
    }
    
    single { get<AppDatabase>().conversationDao() }
    single { get<AppDatabase>().messageDao() }
}