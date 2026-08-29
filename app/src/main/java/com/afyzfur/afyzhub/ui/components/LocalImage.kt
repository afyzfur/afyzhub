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
    // 能否用绘制层模糊只取决于系统版本，与当前强度无关。
    //
    // 这两件事必须分开判断。此前把"有没有模糊"也算进来，导致强度从
    // 0% 变到 1% 时这个值由 false 翻成 true，缓存键跟着变、图片重新
    // 解码——表现就是那一下闪烁
    val canGpuBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // 只有拿不到绘制层模糊时才退回解码阶段处理
    val decodeBlur = !canGpuBlur && blur > 0.01f

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(File(path))
            // 路径在换图后不变，用版本号区分缓存条目。
            //
            // 模糊强度只在低版本进缓存键。走 GPU 模糊时图片本身与
            // 强度无关，把强度放进键会让每次拖动滑块都重新解码整张图，
            // 拖动过程因此明显卡顿——这正是之前预览不跟手的原因
            .memoryCacheKey(cacheKey(path, version, blur, canGpuBlur))
            .diskCacheKey(cacheKey(path, version, blur, canGpuBlur))
            .apply {
                // 低版本才用 transformation：它每次强度变化都要重新解码，
                // 但那是这些设备上唯一能做到的方式
                if (decodeBlur) transformations(BlurTransformation(blur))
            }
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        // 支持绘制层模糊的系统上恒定套一层 blur，半径为 0 时无视觉
        // 效果。不按强度切换 modifier 链的结构：链一变就要重建绘制
        // 节点，0% 与 1% 之间来回切时同样会闪
        modifier = if (canGpuBlur) {
            // 半径映射到 dp。上限 24dp 与低版本的降采样强度大致相当，
            // 让两条路径的观感接近；曲线同样用平方，低段才有可用区间。
            //
            // edgeTreatment 用 Unbounded 并在外部裁剪：默认的 Rectangle
            // 会把采样限制在边界内，导致边缘一圈明显比中间清晰
            modifier.blur(
                radius = (blur.coerceIn(0f, 1f).let { it * it } * 24f).dp,
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
 * [canGpuBlur] 为真时不把强度算进去：那种情况下解码结果与强度无关，
 * 混进去只会让缓存失效、白白重新解码。
 *
 * 注意判断的是"能否用 GPU 模糊"而非"当前有没有模糊"。后者会随强度
 * 在 0 附近翻转，键跟着变就会闪。
 */
private fun cacheKey(
    path: String,
    version: Long,
    blur: Float,
    canGpuBlur: Boolean
): String = if (canGpuBlur) "$path#$version" else "$path#$version#$blur"
