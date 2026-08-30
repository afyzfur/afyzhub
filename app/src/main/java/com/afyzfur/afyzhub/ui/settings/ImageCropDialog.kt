package com.afyzfur.afyzhub.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens
import com.afyzfur.afyzhub.ui.components.LocalImage

/**
 * 图片裁剪对话框。
 *
 * 四条边各配一个滑块，而不是在图上拖手柄。
 *
 * 拖手柄是更常见的做法，但在这个尺寸下不可靠：对话框里的预览只有
 * 一两百 dp 高，四个手柄的触摸区互相重叠，判定"用户想拖哪条边"就
 * 需要各种启发式规则——而任何启发式在边界情形下都会抓错边，表现
 * 出来就是"想拖上边结果下边动了"。滑块没有这个问题，每一条都明确
 * 对应一条边，代价是少了直接操作的手感。
 *
 * 上方预览实时反映当前范围，所以调滑块时看到的就是裁剪结果。
 *
 * 坐标全程 0..1 归一化，与 ImageStore.crop 的入参一致，避免在预览
 * 尺寸与文件像素之间换算。
 */
@Composable
fun ImageCropDialog(
    path: String,
    version: Long,
    onConfirm: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onDismiss: () -> Unit
) {
    // 只解图片头部拿宽高，不解像素——成本极低，但让预览框能与
    // 图片同比例，这是遮罩位置正确的前提。
    // 读失败时退回 1:1，那时遮罩仍与框对应，只是框不像原图
    val ratio = remember(path, version) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (w > 0 && h > 0) w.toFloat() / h.toFloat() else 1f
    }

    // 初始为整张图，用户从这里往里收
    var left by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    var right by remember { mutableFloatStateOf(1f) }
    var bottom by remember { mutableFloatStateOf(1f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("裁剪图片") },
        text = {
            Column {
                // 预览框必须与图片本身同比例。此前固定 16:9 且用 Fit，
                // 竖幅图只占中间一条、两侧留白，而遮罩铺满整个框——
                // 于是"左边界 10%"压暗的是留白区，图片一点没被遮住，
                // 看起来就是遮罩与实际裁剪范围对不上
                // 不能同时用 fillMaxWidth 与 heightIn(max)：aspectRatio 满足
                // 不了"占满宽度"和"限高"两个约束，竖幅图会被压扁，遮罩
                // 又跟图片对不上。改为固定高度、宽度由比例算出，横向居中
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(PREVIEW_HEIGHT)
                        .aspectRatio(ratio)
                ) {
                    // 框已与图片同比例，Crop 与 Fit 此时等价，
                    // 用 Crop 可避免浮点误差留下的一两像素白边
                    LocalImage(
                        path = path,
                        version = version,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 压暗将被裁掉的部分：边框只说明"框在哪"，
                    // 压暗直接说明"会丢掉什么"
                    CropOverlay(left, top, right, bottom)
                }
                Spacer(Modifier.height(16.dp))
                // 填充色一律表示"会被裁掉的那一侧"：左/上从左端填起，
                // 右/下从右端填起
                EdgeSlider("左边界", left, 0f, right - MIN_SIZE) { left = it }
                EdgeSlider(
                    "右边界", right, left + MIN_SIZE, 1f, fillFromEnd = true
                ) { right = it }
                EdgeSlider("上边界", top, 0f, bottom - MIN_SIZE) { top = it }
                EdgeSlider(
                    "下边界", bottom, top + MIN_SIZE, 1f, fillFromEnd = true
                ) { bottom = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(left, top, right, bottom) }) {
                Text("裁剪")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 单条边界的滑块。
 *
 * 轨道自绘，为的是让填充色表示"会被裁掉的那一侧"。
 *
 * Slider 默认的填充恒从轨道左端延伸到 thumb，含义是"从最小值到
 * 当前值"。这对左边界成立——填充部分正是要裁掉的左侧；但右边界
 * 的值 1.0 表示不裁，默认填充会铺满整条轨道，看着像"全都要裁掉"，
 * 与实际相反。[fillFromEnd] 为真时改为从右端填到 thumb。
 *
 * 不用 scaleX = -1 整体镜像：那样连 thumb 的拖动方向也一起反了，
 * 手感与图片上那条边的移动方向不符。
 *
 * valueRange 固定为 0..1，取值约束放在 onChange 里做。
 *
 * 这一点很关键。此前 range 传的是随另一条边算出的动态区间
 * （如 0f..(right - MIN_SIZE)），而 Slider 内部状态与 range 绑定：
 * range 一变就重建状态、丢掉正在进行的手势——表现就是要划好几次
 * 才有反应。range 固定后手势全程连续，越界由 onChange 夹住。
 */
// track 插槽与 SliderState 目前仍是实验性 API。用它是因为要自绘轨道
// 才能控制填充方向，而稳定 API 里没有等价能力
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EdgeSlider(
    label: String,
    value: Float,
    /** 允许的下界，拖到更小时夹住 */
    min: Float,
    /** 允许的上界，拖到更大时夹住 */
    max: Float,
    fillFromEnd: Boolean = false,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        // 滑块吃掉标签之外的剩余宽度。此前用 fillMaxWidth(0.85f)，
        // 那是"占父级宽度的 85%"，与前面标签的宽度叠加后会溢出
        Slider(
            value = value,
            // 夹在这里而非靠 range 限制：range 变动会打断手势
            onValueChange = { onChange(it.coerceIn(min.coerceAtMost(max), max)) },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            track = { state ->
                CropTrack(
                    fraction = state.value.coerceIn(0f, 1f),
                    fillFromEnd = fillFromEnd
                )
            }
        )
    }
}

/**
 * 自绘的滑块轨道。
 *
 * [fraction] 是 thumb 在轨道上的位置比例，[fillFromEnd] 决定填充色
 * 从哪一端延伸到 thumb。
 *
 * 用两个叠放的圆角条而非 Canvas：轨道就是两个矩形，用布局系统的
 * 百分比宽度即可，省掉手工算坐标。
 */
@Composable
private fun CropTrack(fraction: Float, fillFromEnd: Boolean) {
    val f = fraction.coerceIn(0f, 1f)
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    // 同样用 Canvas 而非 fillMaxWidth(fraction)：后者是布局参数，
    // 拖动时每帧都要重新测量这一层
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
    ) {
        val radius = size.height / 2f
        val corner = CornerRadius(radius, radius)
        drawRoundRect(color = base, cornerRadius = corner)

        // 右/下边界时 thumb 在 f 处，要裁掉的是它右边那段，
        // 因此填充从 f 延伸到右端
        val fillWidth = if (fillFromEnd) size.width * (1f - f) else size.width * f
        if (fillWidth > 0f) {
            val startX = if (fillFromEnd) size.width - fillWidth else 0f
            drawRoundRect(
                color = fill,
                topLeft = Offset(startX, 0f),
                size = Size(fillWidth, size.height),
                cornerRadius = corner
            )
        }
    }
}

/**
 * 裁剪范围之外的遮罩。
 *
 * 用 Canvas 一次画四个矩形，而不是嵌套 Column/Row 加 weight。
 *
 * weight 是布局参数：拖动滑块时每一帧都要重跑整棵子树的测量与布局，
 * 而嵌套两层各三个子项意味着每帧多轮测量——这是拖动不跟手的主因，
 * 之前几次都在调 Slider 的参数，方向错了。
 *
 * 改用 Canvas 后只走绘制阶段，测量与布局完全跳过；矩形坐标由比例
 * 直接算出，也不再需要 weight 不能为 0 的那些兜底。
 */
@Composable
private fun CropOverlay(left: Float, top: Float, right: Float, bottom: Float) {
    val shade = Color.Black.copy(alpha = 0.55f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val l = (left.coerceIn(0f, 1f) * w)
        val r = (right.coerceIn(0f, 1f) * w)
        val t = (top.coerceIn(0f, 1f) * h)
        val b = (bottom.coerceIn(0f, 1f) * h)

        // 上下两条通宽，左右两条只占保留区的高度——这样四块拼起来
        // 正好是"中间挖空"，且不会在角上重叠出更深的颜色
        drawRect(color = shade, topLeft = Offset(0f, 0f), size = Size(w, t))
        drawRect(color = shade, topLeft = Offset(0f, b), size = Size(w, h - b))
        drawRect(color = shade, topLeft = Offset(0f, t), size = Size(l, b - t))
        drawRect(color = shade, topLeft = Offset(r, t), size = Size(w - r, b - t))
    }
}

/**
 * 预览区高度。
 *
 * 固定高度而非按宽度算：宽度随图片比例变化，横幅图会很矮、竖幅图
 * 会很高，对话框的整体高度就不可预测，竖幅长图能把按钮挤出屏幕。
 * 定高之后最坏情况也只是宽度变窄。
 */
private val PREVIEW_HEIGHT = 240.dp

/** 滑块轨道高度 */
private val TRACK_HEIGHT = 8.dp

/** 裁剪框的最小边长（归一化），防止收成一条线 */
private const val MIN_SIZE = 0.1f
