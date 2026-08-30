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

    /**
     * 悬浮式输入区外层容器。
     *
     * 四角全圆：这个样式四周都留边、不贴屏幕底边，下方圆角露出的是
     * 刻意留出的间隙而非缺口。半径比通栏样式略小——悬浮的块本身更窄，
     * 28dp 在窄块上会显得过分圆。
     */
    val FloatingInputContainer = RoundedCornerShape(24.dp)

    /** 助手消息块。全宽，四角统一 */
    val AssistantMessage = RoundedCornerShape(20.dp)

    /**
     * 用户消息块。与助手一致的四角统一圆角。
     *
     * 此前右下角收窄至 4dp 作为"气泡尾巴的克制版本"，
     * 但两侧形状不同看起来割裂——来源方向已由对齐与底色区分，
     * 不需要再靠形状表达。
     */
    val UserMessage = RoundedCornerShape(20.dp)

    /** chip、选中态、筛选器 —— 全圆角胶囊 */
    val Pill = RoundedCornerShape(percent = 50)

    /** 发送按钮 —— 正圆 */
    val CircleButton = RoundedCornerShape(percent = 50)

    /**
     * 暂停按钮里的方块。
     *
     * 略带圆角而非直角：直角方块在圆形按钮内部显得生硬，
     * 2dp 足够柔化又不至于看成圆形。
     */
    val StopSquare = RoundedCornerShape(2.dp)

    /** 设置项分组容器 */
    val SettingsGroup = RoundedCornerShape(20.dp)

    /**
     * 设置项右侧的下拉按钮。
     *
     * 用 Pill 的 percent = 50 而非固定半径。此前担心 percent 按短边算
     * 会让矮按钮半径不足，于是固定 24dp——但固定值在按钮实际高度超过
     * 48dp 时反而不够圆，两端仍是直边。percent = 50 保证两端始终是
     * 完整的半圆，无论按钮多高。
     */
    val DropdownButton = RoundedCornerShape(percent = 50)

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
