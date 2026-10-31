package com.afyzfur.afyzhub.ui.browser

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.compose.runtime.saveable.Saver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
// 外部浏览器按钮使用文本图标，避免依赖可选图标集
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 应用内浏览器。
 *
 * 用系统 WebView（Chromium 内核，由 Google Play 独立更新到最新版），
 * 而非打包独立内核：后者体积 70MB+ 起步，且更新必须随应用发版，
 * 跟不上安全补丁。系统 WebView 两头都占。
 *
 * 来源：聊天气泡里的 Markdown 链接点击后在此打开，替代直接丢给
 * 系统浏览器——读文档读到一半被扔到外部应用，回来要重新找位置。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserScreen(
    initialUrl: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val savedWebState = rememberSaveable(initialUrl, saver = Saver<Bundle, Bundle>(save = { it }, restore = { it })) { Bundle() }
    val browserSettings: com.afyzfur.afyzhub.data.settings.SettingsRepository = org.koin.java.KoinJavaComponent.get(com.afyzfur.afyzhub.data.settings.SettingsRepository::class.java)
    val searchSettings by browserSettings.settings.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var inputText by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var javaScriptEnabled by rememberSaveable(initialUrl) { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 保留同一页面中的 WebView 实例；工厂里不再无条件重载 URL，
    // 避免 AndroidView 重建时把页面重置到初始地址。
    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = javaScriptEnabled
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        }
    }

    LaunchedEffect(javaScriptEnabled) {
        webView.settings.javaScriptEnabled = javaScriptEnabled
    }

    // WebView 的状态同步回调：URL 变化/导航能力变化时更新 Compose 状态
    fun syncState(view: WebView) {
        currentUrl = view.url ?: currentUrl
        inputText = currentUrl
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    fun load(url: String) {
        val raw = url.trim()
        if (raw.isEmpty()) return
        val target = when {
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
            raw.contains(' ') || !raw.contains('.') -> when (searchSettings?.searchEngine) {
                "google" -> "https://www.google.com/search?q=${Uri.encode(raw)}"
                "bing" -> "https://www.bing.com/search?q=${Uri.encode(raw)}"
                else -> "https://www.baidu.com/s?wd=${Uri.encode(raw)}"
            }
            else -> "https://$raw" // 裸域名默认 https
        }
        loadError = null
        progress = 0
        inputText = target
        webView.loadUrl(target)
    }

    LaunchedEffect(webView, initialUrl) {
        val normalizedInitial = initialUrl.trim().let {
            if (it.startsWith("http://", true) || it.startsWith("https://", true)) it else "https://$it"
        }
        if (webView.url == null && webView.originalUrl == null) {
            if (webView.restoreState(savedWebState) == null) webView.loadUrl(currentUrl.ifBlank { normalizedInitial })
            syncState(webView)
        }
    }

    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) webView.saveState(savedWebState)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            webView.saveState(savedWebState)
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 页面退出时释放 WebView，避免 Activity/Context 被长期持有。
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

    fun goBackInPage() {
        if (webView.canGoBack()) webView.goBack() else onNavigateBack()
    }

    // 系统返回键优先走页面历史, 走完才退出浏览器
    BackHandler { goBackInPage() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶栏: 返回 + 地址输入 + 外部打开
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(onClick = { onNavigateBack() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_2574cdf66c41)
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { load(inputText.trim()) }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                )
                IconButton(onClick = { webView.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_38108eaa1d32))
                }
                IconButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                        }.onFailure { loadError = it.message ?: com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_aa8c25b80c21) }
                    }
                ) {
                    Text(
                        text = "↗",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            androidx.compose.material3.TextButton(onClick = {
                javaScriptEnabled = !javaScriptEnabled
                webView.settings.javaScriptEnabled = javaScriptEnabled
                webView.reload()
            }) {
                Text(if (javaScriptEnabled) "JavaScript: ON" else "JavaScript: OFF")
            }
            // 导航条: 网页内前进后退
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(
                    onClick = { if (webView.canGoBack()) { webView.goBack(); syncState(webView) } },
                    enabled = canGoBack
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_4cf4c11a1b0b))
                }
                IconButton(
                    onClick = { if (webView.canGoForward()) { webView.goForward(); syncState(webView) } },
                    enabled = canGoForward
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_320ffeefca2c))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (progress < 100) "$progress%" else pageTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 12.dp, top = 12.dp)
                )
            }

            // 加载进度条
            if (progress < 100) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            loadError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            AndroidView(
                factory = {
                    webView.apply {
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { pageTitle = it }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                val scheme = uri.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                return try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    true
                                } catch (_: Exception) {
                                    loadError = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_ca819302da03)
                                    true
                                }
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                loadError = null
                                progress = 0
                                syncState(view)
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                syncState(view)
                                progress = 100
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                super.onReceivedError(view, request, error)
                                if (request.isForMainFrame) {
                                    loadError = error.description?.toString() ?: com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_08b2377d1c74)
                                    progress = 100
                                }
                            }
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                // 不绕过证书错误，安全失败并提示用户。
                                handler.cancel()
                                loadError = com.afyzfur.afyzhub.util.AppText.get(com.afyzfur.afyzhub.R.string.ui_c3b37d90812d)
                                progress = 100
                            }
                        }
                    }
                },
                update = { view ->
                    // Navigation callbacks are authoritative; never reload from Compose state here.
                    if (view.url != null && view.url != currentUrl) syncState(view)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
