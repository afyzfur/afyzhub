package com.afyzfur.afyzhub.ui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.components.LocalImage
import kotlin.math.abs

/**
 * 图片裁剪对话框。
 *
 * 直接在预览图上拖动裁剪框的边，不再用滑块。
 *
 * 滑块方案改了四轮都不跟手，原因是它有两处结构性的别扭：一是取值
 * 约束与 Slider 内部的位置状态互相打架，手指越过上限后 thumb 就跟
 * 不上；二是四条边各一个滑块，与"在图上框出一块区域"这件事之间隔了
 * 一层映射，用户得先想清楚"收右边界"对应哪条滑块往哪拖。
 *
 * 直接拖边把这两层都去掉了。判定抓哪条边只在手势按下时做一次，
 * 之后整个拖动过程都作用于同一条边——之前担心的"抓错边"恰恰是因为
 * 每一帧都在重新判定，手指稍微斜一点就跳到另一条边上。
 */
@Composable
fun ImageCropDialog(
    path: String,
    version: Long,
    onConfirm: (left: Float, top: Float, right: Float, bottom: Float) -> Unit,
    onDismiss: () -> Unit
) {
    // 只解图片头部拿宽高，不解像素。预览框与图片同比例是遮罩位置
    // 正确的前提；读失败时退回 1:1
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

    // 本次手势抓住的是哪条边。按下时定一次，抬起时清空
    var grabbed by remember { mutableStateOf<Grab?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("裁剪图片") },
        text = {
            Column {
                Text(
                    text = "拖动方框的边或角调整范围",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        // 固定高度、宽度由比例算出、横向居中。
                        // 不能同时用 fillMaxWidth 与限高：aspectRatio 满足
                        // 不了两个约束，竖幅图会被压扁、遮罩跟着错位
                        .align(Alignment.CenterHorizontally)
                        .height(PREVIEW_HEIGHT)
                        .aspectRatio(ratio)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    // 归一化触点，按下时定一次抓哪条边
                                    grabbed = grabOf(
                                        x = pos.x / size.width,
                                        y = pos.y / size.height,
                                        left = left,
                                        top = top,
                                        right = right,
                                        bottom = bottom
                                    )
                                },
                                onDragEnd = { grabbed = null },
                                onDragCancel = { grabbed = null },
                                onDrag = { _, drag ->
                                    val dx = drag.x / size.width
                                    val dy = drag.y / size.height
                                    // 只动本次手势抓住的那条边，且各自
                                    // 留出最小边长，避免收成一条线
                                    when (grabbed) {
                                        Grab.LEFT ->
                                            left = (left + dx)
                                                .coerceIn(0f, right - MIN_SIZE)
                                        Grab.RIGHT ->
                                            right = (right + dx)
                                                .coerceIn(left + MIN_SIZE, 1f)
                                        Grab.TOP ->
                                            top = (top + dy)
                                                .coerceIn(0f, bottom - MIN_SIZE)
                                        Grab.BOTTOM ->
                                            bottom = (bottom + dy)
                                                .coerceIn(top + MIN_SIZE, 1f)
                                        Grab.TOP_LEFT -> {
                                            left = (left + dx)
                                                .coerceIn(0f, right - MIN_SIZE)
                                            top = (top + dy)
                                                .coerceIn(0f, bottom - MIN_SIZE)
                                        }
                                        Grab.TOP_RIGHT -> {
                                            right = (right + dx)
                                                .coerceIn(left + MIN_SIZE, 1f)
                                            top = (top + dy)
                                                .coerceIn(0f, bottom - MIN_SIZE)
                                        }
                                        Grab.BOTTOM_LEFT -> {
                                            left = (left + dx)
                                                .coerceIn(0f, right - MIN_SIZE)
                                            bottom = (bottom + dy)
                                                .coerceIn(top + MIN_SIZE, 1f)
                                        }
                                        Grab.BOTTOM_RIGHT -> {
                                            right = (right + dx)
                                                .coerceIn(left + MIN_SIZE, 1f)
                                            bottom = (bottom + dy)
                                                .coerceIn(top + MIN_SIZE, 1f)
                                        }
                                        // 抓在框内部：整体平移，两侧同时
                                        // 移动并在碰到边界时停住
                                        Grab.INSIDE -> {
                                            val w = right - left
                                            val h = bottom - top
                                            val nl = (left + dx).coerceIn(0f, 1f - w)
                                            val nt = (top + dy).coerceIn(0f, 1f - h)
                                            left = nl
                                            right = nl + w
                                            top = nt
                                            bottom = nt + h
                                        }
                                        null -> Unit
                                    }
                                }
                            )
                        }
                ) {
                    // 框已与图片同比例，Crop 与 Fit 此时等价；
                    // 用 Crop 可避免浮点误差留下的一两像素白边
                    LocalImage(
                        path = path,
                        version = version,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 遮罩与边框都在一次 Canvas 里画完。用 Canvas 而非
                    // 嵌套布局加 weight：weight 是布局参数，拖动时每帧
                    // 都要重跑整棵子树的测量，那是之前卡顿的主因
                    CropOverlay(left, top, right, bottom)
                }
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
 * 裁剪范围之外的遮罩，以及框线与角标。
 *
 * 一次 Canvas 画完：只走绘制阶段，测量与布局完全跳过。
 */
@Composable
private fun CropOverlay(left: Float, top: Float, right: Float, bottom: Float) {
    val shade = Color.Black.copy(alpha = 0.55f)
    val line = Color.White
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val l = left.coerceIn(0f, 1f) * w
        val r = right.coerceIn(0f, 1f) * w
        val t = top.coerceIn(0f, 1f) * h
        val b = bottom.coerceIn(0f, 1f) * h

        // 上下两条通宽，左右两条只占保留区的高度——四块拼起来正好
        // 中间挖空，角上也不会重叠出更深的颜色
        drawRect(color = shade, topLeft = Offset(0f, 0f), size = Size(w, t))
        drawRect(color = shade, topLeft = Offset(0f, b), size = Size(w, h - b))
        drawRect(color = shade, topLeft = Offset(0f, t), size = Size(l, b - t))
        drawRect(color = shade, topLeft = Offset(r, t), size = Size(w - r, b - t))

        // 框线：让"边在哪"看得出来，否则只有明暗交界不好瞄准
        val stroke = 2f
        drawRect(
            color = line,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )

        // 四角加粗一小段，提示这里可以抓
        val armLen = minOf(w, h) * 0.06f
        val armWidth = 5f
        listOf(
            // 左上
            Offset(l, t) to listOf(Offset(armLen, 0f), Offset(0f, armLen)),
            // 右上
            Offset(r, t) to listOf(Offset(-armLen, 0f), Offset(0f, armLen)),
            // 左下
            Offset(l, b) to listOf(Offset(armLen, 0f), Offset(0f, -armLen)),
            // 右下
            Offset(r, b) to listOf(Offset(-armLen, 0f), Offset(0f, -armLen))
        ).forEach { (corner, arms) ->
            arms.forEach { arm ->
                drawLine(
                    color = line,
                    start = corner,
                    end = Offset(corner.x + arm.x, corner.y + arm.y),
                    strokeWidth = armWidth
                )
            }
        }
    }
}

/**
 * 预览区高度。
 *
 * 固定高度而非按宽度算：宽度随图片比例变化，横幅图会很矮、竖幅图
 * 会很高，对话框的整体高度就不可预测，竖幅长图能把按钮挤出屏幕。
 */
private val PREVIEW_HEIGHT = 260.dp

/** 裁剪框的最小边长（归一化），防止收成一条线 */
private const val MIN_SIZE = 0.1f
