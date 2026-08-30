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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.components.LocalImage

/**
 * 图片裁剪对话框。
 *
 * 直接在预览图上拖动裁剪框的边、角，或按住框内平移。
 *
 * 判定抓哪个部位只在手势按下时做一次，之后整个拖动都作用于同一处；
 * 每帧重新判定会让手指稍微斜一点就跳到另一条边。
 *
 * 手势区比图片本身大一圈（[TOUCH_MARGIN]）。裁剪框贴到图片边缘时，
 * 把手的一半落在图片之外，只在图片范围内接手势的话那一半点不到，
 * 边界处就特别难拖。外扩之后边缘与中间一样好抓。
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

    var rect by remember { mutableStateOf(initialCrop()) }

    // 本次手势抓住的部位。按下时定一次，抬起时清空
    var grabbed by remember { mutableStateOf<Grab?>(null) }

    val marginPx = with(LocalDensity.current) { TOUCH_MARGIN.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("裁剪图片") },
        text = {
            Column {
                Text(
                    text = "拖动边框或四角调整，按住框内可整体移动",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                // 外层只负责接手势，比图片大一圈。图片与遮罩在内层，
                // 尺寸不受影响——把外扩做在内边距上而不是放大图片，
                // 遮罩的坐标换算才不用跟着变
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(PREVIEW_HEIGHT + TOUCH_MARGIN * 2)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    // 减去外扩边距换算成图片内的比例。
                                    // 触点可能落在 0..1 之外，判定函数
                                    // 允许这种情况
                                    val iw = size.width - marginPx * 2
                                    val ih = size.height - marginPx * 2
                                    if (iw <= 0f || ih <= 0f) return@detectDragGestures
                                    grabbed = grabOf(
                                        x = (pos.x - marginPx) / iw,
                                        y = (pos.y - marginPx) / ih,
                                        rect = rect
                                    )
                                },
                                onDragEnd = { grabbed = null },
                                onDragCancel = { grabbed = null },
                                onDrag = { _, drag ->
                                    val g = grabbed ?: return@detectDragGestures
                                    val iw = size.width - marginPx * 2
                                    val ih = size.height - marginPx * 2
                                    if (iw <= 0f || ih <= 0f) return@detectDragGestures
                                    rect = applyDrag(
                                        rect = rect,
                                        grab = g,
                                        dx = drag.x / iw,
                                        dy = drag.y / ih
                                    )
                                }
                            )
                        }
                        .padding(TOUCH_MARGIN)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .height(PREVIEW_HEIGHT)
                            .aspectRatio(ratio)
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
                        // 遮罩、框线、三分线、把手都在一次 Canvas 里
                        // 画完，只走绘制阶段。嵌套布局加 weight 的话每帧
                        // 都要重跑整棵子树的测量，那是之前卡顿的主因
                        CropOverlay(rect)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(rect.left, rect.top, rect.right, rect.bottom)
            }) {
                Text("裁剪")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 裁剪范围之外的遮罩，以及框线、三分线与把手。
 *
 * 可视度靠四层叠加：外部压暗、白色框线、内部三分线、四角与四边中点
 * 的加粗把手。单靠明暗交界在深色图上几乎看不出边界在哪，而把手的位置
 * 同时告诉用户哪里能抓——这两件事是一起的。
 */
@Composable
private fun CropOverlay(rect: CropRect) {
    val shade = Color.Black.copy(alpha = 0.62f)
    val line = Color.White
    val thin = Color.White.copy(alpha = 0.45f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val l = rect.left.coerceIn(0f, 1f) * w
        val r = rect.right.coerceIn(0f, 1f) * w
        val t = rect.top.coerceIn(0f, 1f) * h
        val b = rect.bottom.coerceIn(0f, 1f) * h

        // 上下两条通宽，左右两条只占保留区的高度——四块拼起来正好
        // 中间挖空，角上也不会重叠出更深的颜色
        drawRect(color = shade, topLeft = Offset(0f, 0f), size = Size(w, t))
        drawRect(color = shade, topLeft = Offset(0f, b), size = Size(w, h - b))
        drawRect(color = shade, topLeft = Offset(0f, t), size = Size(l, b - t))
        drawRect(color = shade, topLeft = Offset(r, t), size = Size(w - r, b - t))

        // 框线
        drawRect(
            color = line,
            topLeft = Offset(l, t),
            size = Size(r - l, b - t),
            style = Stroke(width = BORDER_WIDTH)
        )

        // 三分线：让框内也有参照，构图时好对齐，也进一步强调框的范围
        val thirdW = (r - l) / 3f
        val thirdH = (b - t) / 3f
        for (i in 1..2) {
            val x = l + thirdW * i
            drawLine(
                color = thin,
                start = Offset(x, t),
                end = Offset(x, b),
                strokeWidth = THIRD_LINE_WIDTH
            )
            val y = t + thirdH * i
            drawLine(
                color = thin,
                start = Offset(l, y),
                end = Offset(r, y),
                strokeWidth = THIRD_LINE_WIDTH
            )
        }

        // 四角把手：两段短线沿框边向内延伸。长度取框短边的比例并设上限，
        // 框收得很小时臂长跟着缩，否则两个角的臂会连成一整条边
        val armLen = (minOf(r - l, b - t) * 0.22f).coerceAtMost(ARM_MAX)
        val corners = listOf(
            Offset(l, t) to Pair(armLen, armLen),
            Offset(r, t) to Pair(-armLen, armLen),
            Offset(l, b) to Pair(armLen, -armLen),
            Offset(r, b) to Pair(-armLen, -armLen)
        )
        corners.forEach { (c, arm) ->
            drawLine(
                color = line,
                start = c,
                end = Offset(c.x + arm.first, c.y),
                strokeWidth = HANDLE_WIDTH,
                cap = StrokeCap.Round
            )
            drawLine(
                color = line,
                start = c,
                end = Offset(c.x, c.y + arm.second),
                strokeWidth = HANDLE_WIDTH,
                cap = StrokeCap.Round
            )
        }

        // 四边中点把手：单独拖一条边时抓这里最直观，角上的把手容易被
        // 当成"只能拖角"
        val midX = (l + r) / 2f
        val midY = (t + b) / 2f
        val edgeLen = (minOf(r - l, b - t) * 0.16f).coerceAtMost(ARM_MAX)
        drawLine(
            color = line,
            start = Offset(midX - edgeLen, t), end = Offset(midX + edgeLen, t),
            strokeWidth = HANDLE_WIDTH, cap = StrokeCap.Round
        )
        drawLine(
            color = line,
            start = Offset(midX - edgeLen, b), end = Offset(midX + edgeLen, b),
            strokeWidth = HANDLE_WIDTH, cap = StrokeCap.Round
        )
        drawLine(
            color = line,
            start = Offset(l, midY - edgeLen), end = Offset(l, midY + edgeLen),
            strokeWidth = HANDLE_WIDTH, cap = StrokeCap.Round
        )
        drawLine(
            color = line,
            start = Offset(r, midY - edgeLen), end = Offset(r, midY + edgeLen),
            strokeWidth = HANDLE_WIDTH, cap = StrokeCap.Round
        )
    }
}

/**
 * 预览区高度。
 *
 * 固定高度而非按宽度算：宽度随图片比例变化，横幅图会很矮、竖幅图
 * 会很高，对话框的整体高度就不可预测，竖幅长图能把按钮挤出屏幕。
 */
private val PREVIEW_HEIGHT = 240.dp

/**
 * 手势区在图片四周的外扩量。
 *
 * 取值参照手指触点的实际大小：把手贴在图片边缘时，一半的触摸区在
 * 图片之外，24dp 足够把那一半接回来。
 */
private val TOUCH_MARGIN = 24.dp

/** 框线宽度（像素） */
private const val BORDER_WIDTH = 3f

/** 三分线宽度（像素），比框线细以免抢过边界 */
private const val THIRD_LINE_WIDTH = 1.5f

/** 把手线宽（像素） */
private const val HANDLE_WIDTH = 7f

/** 把手臂长上限（像素），框较大时不必无限加长 */
private const val ARM_MAX = 46f
