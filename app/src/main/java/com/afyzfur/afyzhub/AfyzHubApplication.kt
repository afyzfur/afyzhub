package com.afyzfur.afyzhub

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.afyzfur.afyzhub.di.appModule
import com.afyzfur.afyzhub.di.databaseModule
import com.afyzfur.afyzhub.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * 实现 [ImageLoaderFactory] 以注册 SVG 解码器。
 *
 * 厂商图标以 SVG 打包，而 Coil 默认只处理位图格式。不注册解码器时
 * 这些图标会静默加载失败、显示为空白，界面上没有任何报错线索。
 */
class AfyzHubApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AfyzHubApplication)
            modules(appModule, databaseModule, networkModule)
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
}