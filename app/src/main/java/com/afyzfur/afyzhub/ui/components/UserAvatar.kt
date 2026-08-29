package com.afyzfur.afyzhub.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.data.settings.AvatarMode
import com.afyzfur.afyzhub.data.settings.ChatAppearance

/**
 * 用户头像。
 *
 * 抽屉顶部与消息列表都用它，抽成共用组件避免两处各写一份回退逻辑——
 * 那样很容易出现一处支持自定义图片、另一处只显示默认图标的不一致。
 *
 * 自定义图片缺失时回落到内置图标而非留空：留空会让布局出现空洞，
 * 用户也无法判断是没设置还是设置失败。
 */
@Composable
fun UserAvatar(
    appearance: ChatAppearance,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val path = appearance.userAvatarPath
    val useCustom = appearance.avatarMode == AvatarMode.CUSTOM && !path.isNullOrBlank()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // requiredSize 而非 size：后者只是"建议"，父级约束更紧时会被压扁。
            // 消息行里头像与正文竞争宽度，正文占满后头像会被压成一条
            .requiredSize(size)
            .clip(CircleShape)
    ) {
        if (useCustom) {
            LocalImage(
                path = path!!,
                version = appearance.imageVersion,
                contentDescription = null,
                // 与设置页缩略图用同一个值，两处看到的效果才一致
                blur = appearance.avatarBlur,
                modifier = Modifier.size(size)
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(size)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        // 图标略小于容器，留出视觉呼吸空间
                        modifier = Modifier.size(size * 0.56f)
                    )
                }
            }
        }
    }
}
