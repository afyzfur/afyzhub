package com.afyzfur.afyzhub.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * 加载并显示本地图片文件。
 *
 * 改用 Coil 而非手动 BitmapFactory 解码：原实现每次进入页面都重新解码，
 * 没有内存与磁盘缓存，而头像在消息列表里每条都要显示。
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
    contentScale: ContentScale = ContentScale.Crop,
    /** 模糊强度 0..1，0 表示不模糊 */
    blur: Float = 0f
) {
    val context = LocalContext.current
    val blurred = blur > 0.01f
    // API 31 起可用绘制层模糊，走 GPU 且不影响图片解码；
    // 低版本只能在解码阶段做，代价是改强度要重新解码
    val gpuBlur = blurred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(path))
            // 路径在换图后不变，用版本号区分缓存条目。
            //
            // 模糊强度只在低版本进缓存键。走 GPU 模糊时图片本身与
            // 强度无关，把强度放进键会让每次拖动滑块都重新解码整张图，
            // 拖动过程因此明显卡顿——这正是之前预览不跟手的原因
            .memoryCacheKey(cacheKey(path, version, blur, gpuBlur))
            .diskCacheKey(cacheKey(path, version, blur, gpuBlur))
            .apply {
                // 低版本才用 transformation：它每次强度变化都要重新解码，
                // 但那是这些设备上唯一能做到的方式
                if (blurred && !gpuBlur) transformations(BlurTransformation(blur))
            }
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = if (gpuBlur) {
            // 半径映射到 dp。上限 24dp 与低版本的降采样强度大致相当，
            // 让两条路径的观感接近；曲线同样用平方，低段才有可用区间。
            //
            // edgeTreatment 用 Unbounded 并在外部裁剪：默认的 Rectangle
            // 会把采样限制在边界内，导致边缘一圈明显比中间清晰
            modifier.blur(
                radius = (blur * blur * 24f).dp,
                edgeTreatment = BlurredEdgeTreatment.Unbounded
            )
        } else {
            modifier
        }
    )
}

/**
 * 图片缓存键。
 *
 * [gpuBlur] 为真时不把强度算进去：那种情况下解码结果与强度无关，
 * 混进去只会让缓存失效、白白重新解码。
 */
private fun cacheKey(
    path: String,
    version: Long,
    blur: Float,
    gpuBlur: Boolean
): String = if (gpuBlur) "$path#$version" else "$path#$version#$blur"
