package com.afyzfur.afyzhub.di

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
        )
            // 注册迁移而非破坏性重建，升级时保留用户的历史对话。
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    single { get<AppDatabase>().conversationDao() }
    single { get<AppDatabase>().messageDao() }
}
