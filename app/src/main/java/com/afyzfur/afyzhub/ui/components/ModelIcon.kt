package com.afyzfur.afyzhub.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * 按模型名显示厂商图标。
 *
 * 图标以 SVG 打包在 assets/icons/（约 56KB），不走网络：各厂商
 * 没有稳定的公开 logo 地址，网络拉取既不可靠也要额外处理缓存。
 *
 * 匹配规则见 [matchModelIcon]。匹配不到时退回首字母。
 */
@Composable
fun ModelIcon(
    modelName: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val asset = remember(modelName) { matchModelIcon(modelName) }

    if (asset != null) {
        AsyncImage(
            // file:///android_asset/ 是 Coil 读取打包资源的标准形式
            model = "file:///android_asset/$ICON_DIR/$asset",
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // 单色图标用 currentColor 填充，独立加载时会落到黑色，
            // 深色主题下不可见。用 onSurface 让它跟随主题翻转，
            // 比硬编码黑白更稳——换配色方案时也不用跟着改
            colorFilter = if (needsThemeTint(asset)) {
                ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            } else {
                null
            },
            modifier = modifier.size(size)
        )
    } else {
        FallbackInitial(modelName = modelName, size = size, modifier = modifier)
    }
}

/**
 * 无匹配图标时的首字母占位。
 *
 * 取第一个字母或数字而非第一个字符：中转常给模型名加 `[...]` 前缀，
 * 直接 take(1) 会显示成无意义的方括号。
 */
@Composable
private fun FallbackInitial(
    modelName: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val initial = remember(modelName) {
        modelName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = CircleShape,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

private const val ICON_DIR = "icons"
