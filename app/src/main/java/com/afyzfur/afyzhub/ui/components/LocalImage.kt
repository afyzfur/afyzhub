package com.afyzfur.afyzhub.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 加载并显示本地图片文件。
 *
 * 项目未引入 Coil 等图片库——只需要显示三张固定的本地图（两个头像加一张背景），
 * 引入一个库及其传递依赖不划算。这里手动解码，由 produceState 缓存结果。
 *
 * [version] 用于在图片内容更新后触发重新解码。
 * [com.afyzfur.afyzhub.data.image.ImageStore] 使用固定文件名保存，
 * 换图后路径不变，若只以路径为 key 会一直显示旧图。
 * 该值由设置层在每次保存图片时递增。
 *
 * 文件不存在或解码失败时不渲染任何内容，由调用方决定回退方案。
 */
@Composable
fun LocalImage(
    path: String,
    version: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap = rememberLocalBitmap(path, version) ?: return

    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}

/**
 * 解码本地图片，在 IO 线程执行。
 *
 * 返回 null 表示尚未加载完成或加载失败——两种情况调用方的处理相同
 * （不渲染或显示回退内容），无需区分。
 */
@Composable
fun rememberLocalBitmap(path: String, version: Long): ImageBitmap? =
    produceState<ImageBitmap?>(initialValue = null, path, version) {
        value = withContext(Dispatchers.IO) {
            try {
                if (!File(path).exists()) return@withContext null
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }.value
