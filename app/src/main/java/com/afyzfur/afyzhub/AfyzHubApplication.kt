package com.afyzfur.afyzhub

import android.app.Application
import com.afyzfur.afyzhub.di.appModule
import com.afyzfur.afyzhub.di.databaseModule
import com.afyzfur.afyzhub.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AfyzHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AfyzHubApplication)
            modules(appModule, databaseModule, networkModule)
        }
    }
}