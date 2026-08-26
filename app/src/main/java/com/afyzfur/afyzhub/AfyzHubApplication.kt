package com.afyzfur.afyzhub

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.afyzfur.afyzhub.data.log.RequestLogStore
import com.afyzfur.afyzhub.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

        val koin = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AfyzHubApplication)
            modules(appModule, databaseModule, networkModule)
        }.koin

        // 载入上次运行留下的失败请求记录。
        // 放在后台协程里：读文件不该拖慢冷启动，而日志页也不会
        // 在启动后的头几毫秒内被打开
        val logStore = koin.get<RequestLogStore>()
        CoroutineScope(Dispatchers.IO).launch { logStore.restore() }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
}