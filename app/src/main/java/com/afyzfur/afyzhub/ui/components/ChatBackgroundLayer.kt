package com.afyzfur.afyzhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import com.afyzfur.afyzhub.data.settings.ChatAppearance

/**
 * 聊天背景层：图片 + 按所选效果叠加的处理。
 *
 * 抽成组件而不是在聊天页与设置页各写一份，是为了让预览与实际效果
 * 必然一致。两处各自实现的话，加一种新效果就要改两个地方，漏掉
 * 一处就出现"预览里是这样、进聊天页是那样"——而预览的全部价值
 * 就在于它可信。
 *
 * 遮罩用主题的 surface 色而非纯黑：浅色主题下压黑会让整体发灰，
 * 而用 surface 叠加相当于"往主题底色靠拢"，浅色与深色都合理。
 */
@Composable
fun ChatBackgroundLayer(
    appearance: ChatAppearance,
    modifier: Modifier = Modifier
) {
    if (!appearance.hasBackgroundImage) return
    val effect = appearance.backgroundEffect

    // 外层裁剪：LocalImage 在 API 31+ 用 Unbounded 边缘处理，模糊会
    // 溢出到容器之外，不裁的话会盖住相邻内容
    Box(modifier = modifier.clipToBounds()) {
        LocalImage(
            path = appearance.backgroundPath!!,
            version = appearance.imageVersion,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // 效果不含模糊时传 0，避免白白做一次变换
            blur = if (effect.usesBlur) appearance.backgroundBlur else 0f,
            modifier = Modifier.fillMaxSize()
        )
        if (effect.usesDim) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.surface
                            .copy(alpha = appearance.backgroundDim)
                    )
            )
        }
    }
}
