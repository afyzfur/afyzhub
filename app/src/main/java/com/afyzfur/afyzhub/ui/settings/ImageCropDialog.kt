package com.afyzfur.afyzhub.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 固定 16:9 而非跟随原图比例：对话框高度需要可预测，
                        // 否则竖幅长图会把底部按钮挤出屏幕
                        .aspectRatio(16f / 9f)
                ) {
                    // 必须用 Fit 而非 Crop。Crop 会先裁掉图片一部分来填满
                    // 这个 16:9 的框，于是预览里看到的不是完整原图；而
                    // ImageStore.crop 的归一化坐标是相对完整原图算的，
                    // 两者不一致会导致"拖出来的范围"和"实际裁到的范围"错位
                    LocalImage(
                        path = path,
                        version = version,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 压暗将被裁掉的部分：边框只说明"框在哪"，
                    // 压暗直接说明"会丢掉什么"
                    CropOverlay(left, top, right, bottom)
                }
                Spacer(Modifier.height(16.dp))
                EdgeSlider("左边界", left, 0f, right - MIN_SIZE) { left = it }
                EdgeSlider("右边界", right, left + MIN_SIZE, 1f) { right = it }
                EdgeSlider("上边界", top, 0f, bottom - MIN_SIZE) { top = it }
                EdgeSlider("下边界", bottom, top + MIN_SIZE, 1f) { bottom = it }
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
 * valueRange 的下限可能大于上限——比如上下边界已经贴到最小间距时，
 * 再调另一条边算出的范围会反过来。Slider 遇到这种范围会抛异常，
 * 所以这里做一次兜底。
 */
@Composable
private fun EdgeSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    val lo = min.coerceIn(0f, 1f)
    val hi = max.coerceIn(0f, 1f)
    // 范围退化时不给滑块，避免 Slider 因 start > end 崩溃
    if (hi <= lo) return

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
            value = value.coerceIn(lo, hi),
            onValueChange = onChange,
            valueRange = lo..hi,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 裁剪范围之外的遮罩。
 *
 * 用嵌套的 Column/Row 加权重拼出"中间挖空"。权重不能为 0——
 * 边界贴到极限时差值会是 0，Compose 的 weight 要求正数，
 * 因此统一夹到一个极小值。
 */
@Composable
private fun CropOverlay(left: Float, top: Float, right: Float, bottom: Float) {
    val shade = Color.Black.copy(alpha = 0.55f)
    val eps = 0.0001f

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(top.coerceAtLeast(eps))
                .background(shade)
        )
        Row(modifier = Modifier.weight((bottom - top).coerceAtLeast(eps))) {
            // 这一层只按 weight 分配宽度，高度取满。用 fillMaxSize 会
            // 同时要求占满父级宽度，与 weight 的按比例分宽相冲突
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(left.coerceAtLeast(eps))
                    .background(shade)
            )
            // 保留区：不铺遮罩，让图片原样透出
            Spacer(modifier = Modifier.weight((right - left).coerceAtLeast(eps)))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight((1f - right).coerceAtLeast(eps))
                    .background(shade)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight((1f - bottom).coerceAtLeast(eps))
                .background(shade)
        )
    }
}

/** 裁剪框的最小边长（归一化），防止收成一条线 */
private const val MIN_SIZE = 0.1f
