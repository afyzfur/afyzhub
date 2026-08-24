package com.afyzfur.afyzhub.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * 加载并显示本地图片文件。
 *
 * 改用 Coil 而非手动 BitmapFactory 解码：原实现每次进入页面都重新解码，
 * 没有内存与磁盘缓存，而头像在消息列表里每条都要显示。
 * Coil 本就是项目依赖（此前判断"未引入图片库"有误）。
 *
 * [version] 参与缓存键。[com.afyzfur.afyzhub.data.image.ImageStore]
 * 用固定文件名保存，换图后路径不变，仅以路径为键会一直命中旧缓存。
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
    val context = LocalContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(path))
            // 路径在换图后不变，用版本号区分缓存条目
            .memoryCacheKey("$path#$version")
            .diskCacheKey("$path#$version")
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}
