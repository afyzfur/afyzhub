package com.afyzfur.afyzhub

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import com.afyzfur.afyzhub.data.log.RequestLogStore
import com.afyzfur.afyzhub.data.settings.SettingsRepository
import com.afyzfur.afyzhub.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val koin = startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AfyzHubApplication)
            modules(appModule, databaseModule, networkModule)
        }.koin

        // 载入上次运行留下的请求记录，随后按保留策略清掉过期的。
        //
        // 放在后台协程里：读文件不该拖慢冷启动，而日志页也不会
        // 在启动后的头几毫秒内被打开。
        //
        // 清理只在启动时做一次，不设定时器：日志的用途是回头排查，
        // 过期与否只在打开列表时才有意义，而启动必然早于查看。
        val logStore = koin.get<RequestLogStore>()
        val settings = koin.get<SettingsRepository>()
        applicationScope.launch {
            logStore.restore()
            logStore.purgeExpired(settings.logRetention.first())
        }
        // 记录开关持续跟随设置。用 collect 而非启动时读一次：
        // 用户在设置页关掉后应当立刻停止记录，不必重启应用
        applicationScope.launch {
            settings.logEnabled.collect { logStore.enabled = it }
        }
    }

    /**
     * Coil 图片加载器。
     *
     * 除 SVG 解码器外，还收紧网络策略：搜索结果里的站点图标是
     * 三方小图，源站不可达时不该让请求长时间挂着——超时后由
     * 调用方的占位逻辑兜住，比转圈十几秒体感好得多。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
            }
            .build()
}