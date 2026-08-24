package com.afyzfur.afyzhub.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 形状系统。
 *
 * 整体向大圆角靠拢，比 M3 默认值各档提高一级。圆角是本次改版里最直接的
 * 视觉信号——旧界面用 Card + 4dp 圆角 + 阴影，属于 Material 2 语言；
 * 新界面用大圆角 + tonal 色阶 + 零阴影。
 */
val AppShapes = Shapes(
    // 小型元素：标签、徽章
    extraSmall = RoundedCornerShape(8.dp),
    // 输入框内的小控件、下拉项
    small = RoundedCornerShape(12.dp),
    // 通用容器默认档，多数场景走这一档
    medium = RoundedCornerShape(16.dp),
    // 设置项分组容器、列表卡片
    large = RoundedCornerShape(20.dp),
    // 底部弹窗、大面积容器
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 语义化形状常量。
 *
 * 与 [AppShapes] 的区别：Shapes 是 M3 组件自动取用的默认值，
 * 这里的是特定组件手动引用的，写死语义以免各处出现魔法数字。
 */
object AppShapeTokens {

    /**
     * 输入区外层容器。
     *
     * 只圆上方两角：容器铺满宽度并贴住屏幕底边，下方圆角会露出页面背景，
     * 形成视觉上多余的缺口。
     */
    val InputContainer = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** 助手消息块。全宽，四角统一 */
    val AssistantMessage = RoundedCornerShape(20.dp)

    /**
     * 用户消息块。右下角收窄至 4dp 指示消息来源方向，
     * 这是气泡尾巴的克制版本，不画三角
     */
    val UserMessage = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 4.dp
    )

    /** chip、选中态、筛选器 —— 全圆角胶囊 */
    val Pill = RoundedCornerShape(percent = 50)

    /** 发送按钮 —— 正圆 */
    val CircleButton = RoundedCornerShape(percent = 50)

    /** 设置项分组容器 */
    val SettingsGroup = RoundedCornerShape(20.dp)

    /** 抽屉。左侧贴边，右侧收圆角，宽度不占满屏 */
    val Drawer = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 24.dp,
        bottomEnd = 24.dp
    )

    /** 代码块。比正文容器略小的圆角，避免与外层重叠时显得臃肿 */
    val CodeBlock = RoundedCornerShape(12.dp)

    /** 无圆角，用于需要贴边的场景 */
    val None = RoundedCornerShape(CornerSize(0.dp))
}
