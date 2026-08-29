package com.afyzfur.afyzhub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.afyzfur.afyzhub.ui.theme.AppShapeTokens

/**
 * 设置页的公共组件。
 *
 * 版式要点（见 docs/ui-redesign.md 4.4）：
 * - 分类标题在容器**外部**，用强调色
 * - 同类项装进一个圆角容器，靠间距分隔，不用分割线
 * - 每项带图标、标题、副标题，副标题说明该项管什么
 */

/** 分类标题。置于分组容器外侧 */
@Composable
fun SettingsCategoryTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** 同类设置项的圆角容器 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = AppShapeTokens.SettingsGroup,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 内容层再裁一次圆角。形状定义在 Surface 上时，组内各项的
        // clickable 拿不到这个形状，涟漪按整行矩形扩散，首尾两项按下
        // 就会在圆角处露出方角。裁剪把涟漪限制在圆角轮廓内。
        //
        // 这里不能再加竖向内边距。加了之后首尾两项与圆角之间会隔开
        // 一段距离，涟漪止步于内边距的直边而非圆角轮廓，看起来仍是
        // 方的——上一版就是这么写的，所以只改了一半。条目自身已有
        // 足够的高度与内边距，不需要在组这一层再补
        Column(modifier = Modifier.clip(AppShapeTokens.SettingsGroup)) {
            content()
        }
    }
}

/**
 * 组内条目之间的分隔线。
 *
 * 由调用方在需要的位置显式插入，而不是让 [SettingsGroup] 自动在
 * 每个子项之间加：Compose 的 content lambda 拿不到子项数量，
 * 无法判断哪里是边界，硬做需要引入子项包装类型。
 *
 * 左侧留出与条目文字对齐的缩进，右侧到底——不通栏是为了让
 * 分隔线读起来属于这一组内部，而非切断整个容器。
 */
@Composable
fun SettingsItemDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 20.dp)
    )
}

/**
 * 文本输入条目。
 *
 * 用 BasicTextField 而非 OutlinedTextField：后者自带描边与浮动标签，
 * 与分组容器的设计语言冲突——容器已经提供了边界，再套一层描边会
 * 出现双重框线。标签改为条目内的固定标题。
 *
 * [trailing] 用于放「默认」这类就地操作，避免为一个按钮单开一行。
 */
@Composable
fun SettingsTextFieldItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    subtitle: String? = null,
    singleLine: Boolean = true,
    /**
     * 编辑对象的标识。它变化时输入框才用外部值重建。
     *
     * 不传则整个界面生命周期内只初始化一次——对于设置项里那些
     * 一对一绑定某个键的输入框，这正是想要的。切换配置组这类
     * "同一个输入框换了编辑对象"的场景必须传，否则会继续显示上一组的值。
     */
    identityKey: Any? = Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }

        Spacer(Modifier.height(4.dp))
        // 用 TextFieldValue 而非 String：String 重载在外部值回填时会把
        // 选区重置到 0。这里的值要经过 DataStore 异步往返才回来，于是每敲
        // 一个字光标就跳回开头，下一个字符插到最前面。
        //
        // 本地状态是打字期间的唯一来源，外部值只在 identityKey 变化时
        // 用来重建。上一版试图比较"外部值是否变了"，但 DataStore 回填的
        // 是上一次的值，快速连打时回填到达那刻 value 与本地记录必然不等，
        // 条件再次成立、光标被拉走，于是仍然打不进字。
        //
        // 关键在于：外部值在打字期间的每次变化都是我们自己刚写出去的回声，
        // 没有任何一次需要反向覆盖本地。真正需要重建的只有换了编辑对象
        // （切换配置组、换一条会话），而那由 identityKey 标识。
        var field by remember(identityKey) {
            mutableStateOf(TextFieldValue(value, TextRange(value.length)))
        }

        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        )

        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // 说明文字不换行，超出部分横向滑动查看。换行会让每个
                // 输入项的高度参差不齐，一屏能看到的项数也少掉几个；
                // 而这些说明本身是一句话，横向读比折成两行更连贯
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

/**
 * 跳转型条目。右侧箭头表明会进入子页面。
 */
@Composable
fun SettingsNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    SettingsRowBase(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 操作型条目。点击就地执行，不跳页，因此右侧不放箭头。
 *
 * [destructive] 为 true 时整行用错误色——删除这类不可撤销的操作
 * 值得在点之前就看出来，而不是只在确认弹窗里才提示。
 */
@Composable
fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 行内开关条目。整行可点，与只点开关等效。
 */
@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsRowBase(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 行内展示型条目，右侧放当前值。
 * 用于取值简单、无需子页面的项，例如颜色模式。
 */
@Composable
fun SettingsValueItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    SettingsRowBase(
        icon = icon,
        title = title,
        subtitle = subtitle,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 下拉选择条目。
 *
 * 相比循环点击：取值超过两三个时，循环要点很多次才能到目标，而且
 * 点之前看不到有哪些选项。相比进子页面：下拉不离开当前页，选完
 * 立刻能看到效果，适合像颜色模式这种"改完要马上看结果"的项。
 *
 * 右侧的胶囊按钮本身显示当前值，兼作展开入口——不额外放一个箭头
 * 图标是因为按钮自带的下箭头已经表明可展开。
 */
@Composable
fun <T> SettingsDropdownItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsRowBase(
        icon = icon,
        title = title,
        subtitle = subtitle
    ) {
        Box {
            Surface(
                onClick = { expanded = true },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = AppShapeTokens.DropdownButton
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // 竖向内边距给到 12dp：按钮更高，24dp 的圆角才有
                    // 足够的弧度显出来，否则上下会被切平
                    modifier = Modifier.padding(
                        start = 18.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    )
                ) {
                    Text(
                        text = label(selected),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(label(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        trailingIcon = {
                            // 只给当前项打勾，让展开后一眼看出选的是哪个
                            if (option == selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 单选条目。用于同一分组内的互斥选项，例如颜色模式。
 *
 * 不带图标：同组选项共用一个语义，逐项配图标反而制造噪音。
 */
@Composable
fun SettingsRadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 条目的共同骨架：图标 + 两行文字 + 尾部控件。
 *
 * 抽出来是为了保证各类条目的图标尺寸、行距、内边距完全一致——
 * 分组容器内部不画分割线，对齐不齐会立刻显脏。
 */
@Composable
private fun SettingsRowBase(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        trailing()
    }
}

/**
 * 子页面的通用外壳：大字标题 + 圆形返回按钮。
 *
 * 不用 TopAppBar 的标准标题栏——参考对象把标题做成独立大字，
 * 返回键是浮于内容之上的圆形按钮，层级感更强。
 */
@Composable
fun SettingsPageHeader(
    title: String,
    onNavigateBack: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = AppShapeTokens.CircleButton,
            // onClick 交给 Surface：此前点击加在内部的 Box 上，
            // 涟漪取 Box 的矩形边界，在圆形按钮上显示为方块
            onClick = onNavigateBack,
            modifier = Modifier.size(40.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
