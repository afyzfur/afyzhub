package com.afyzfur.afyzhub.data.settings

import com.afyzfur.afyzhub.ui.theme.ThemePalette

/**
 * 消息的呈现形态。
 *
 * 改版初期助手消息一律无容器、用户消息一律有气泡，靠不对称区分双方。
 * 但这个不对称本身会让人觉得两边"不是一套东西"，是否接受因人而异，
 * 因此改为两边各自可选。
 */
enum class BubbleStyle(val id: String, val label: String) {
    /** 有底色容器，宽度随内容 */
    BUBBLE("bubble", "气泡"),

    /**
     * 无容器，占满可用宽度。
     *
     * 助手消息用这个的好处是代码块与表格能获得完整宽度；
     * 代价是与用户消息的区分只剩对齐方向。
     */
    PLAIN("plain", "无气泡");

    companion object {
        fun fromId(id: String?, default: BubbleStyle): BubbleStyle =
            entries.firstOrNull { it.id == id } ?: default
    }
}

/**
 * 头像显示方式。
 *
 * 不做「自动获取模型 logo」：各提供商没有稳定的公开 logo 地址，
 * 网络拉取既不可靠也会引入额外请求与缓存问题。内置图标按提供商区分，
 * 用户也可以自选图片。
 */
enum class AvatarMode(val id: String, val label: String) {
    /**
     * 不显示头像。
     *
     * 保留以兼容旧存值，但不再作为可选项呈现——"是否显示"已由
     * showUserAvatar / showAssistantAvatar 两个开关表达，
     * 三处控制同一件事会互相干扰：此前 NONE 会静默拦住那两个开关，
     * 用户打开开关却看不到任何变化。
     */
    NONE("none", "不显示"),

    /** 内置图标：助手按当前提供商取标识，用户用通用人形图标 */
    BUILTIN("builtin", "默认"),

    /** 用户自选图片，未设置时回落到内置图标 */
    CUSTOM("custom", "自定义图片");

    companion object {
        /**
         * 默认显示内置图标。
         *
         * 此前默认 NONE，与「显示我的头像」「显示助手头像」两个开关冲突：
         * 那两项默认开启，却被这里的 NONE 全部拦住，
         * 用户设了自定义头像也看不到变化，只会以为功能没生效。
         */
        val DEFAULT = BUILTIN

        fun fromId(id: String?): AvatarMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** 聊天背景的显示方式。 */
/**
 * 背景图的视觉处理方式。
 *
 * 遮罩与模糊解决的是同一个问题——背景图削弱文字对比度——但手段
 * 不同：遮罩压低整体亮度，保留画面细节；模糊抹掉高频细节，保留
 * 色调与明暗关系。图片本身花哨时模糊更有效，而想看清画面内容时
 * 遮罩更合适，所以让用户自己选而不是替他决定。
 *
 * 两者可以同时生效（[BOTH]）：深色图配深色文字时，单靠模糊不够，
 * 还得压暗。
 */
enum class ChatBackgroundEffect(val id: String, val label: String) {
    /** 只压暗 */
    DIM("dim", "遮罩"),

    /** 只模糊 */
    BLUR("blur", "模糊"),

    /** 先模糊再压暗 */
    BOTH("both", "模糊 + 遮罩");

    /** 该效果是否用到遮罩强度 */
    val usesDim: Boolean get() = this != BLUR

    /** 该效果是否用到模糊强度 */
    val usesBlur: Boolean get() = this != DIM

    companion object {
        /**
         * 默认只用遮罩。
         *
         * 与旧版本行为一致：此前只有遮罩这一种处理，升级上来的用户
         * 不该因为默认值变化而看到背景突然变模糊。
         */
        val DEFAULT = DIM

        fun fromId(id: String?): ChatBackgroundEffect =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

enum class ChatBackgroundMode(val id: String, val label: String) {
    /** 纯色，取主题的 surfaceContainer */
    NONE("none", "跟随主题"),

    /** 用户自选图片 */
    IMAGE("image", "自定义图片");

    companion object {
        val DEFAULT = NONE

        fun fromId(id: String?): ChatBackgroundMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** 颜色模式。 */
enum class ColorMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        val DEFAULT = SYSTEM
        fun fromId(id: String?): ColorMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        /**
         * 按 跟随系统 → 浅色 → 深色 循环。
         *
         * 三个取值点两次就能到任意一个，比进子页面选再退出来更快，
         * 所以设置首页那一项直接点着切。
         */
        fun next(current: ColorMode): ColorMode =
            entries[(entries.indexOf(current) + 1) % entries.size]
    }
}

/**
 * 消息元信息的显示开关。
 *
 * 分项而非单个总开关：参考对象把 token 用量、缓存命中、吐字速度、耗时、
 * 完整时间戳和操作行全部常驻，一条消息带三行元信息，默认状态偏拥挤。
 * 这里把暴露程度交给用户决定——默认只开时间戳与操作行，
 * 需要调试的用户可以全部打开。
 *
 * [showTokenUsage] / [showSpeed] / [showLatency] 依赖阶段 6 的 Room 字段，
 * 历史消息缺少数据时对应项不显示。
 */
data class MessageDisplayOptions(
    val showTimestamp: Boolean = true,
    val showActions: Boolean = true,
    val showModelName: Boolean = false,
    val showTokenUsage: Boolean = false,
    val showSpeed: Boolean = false,
    val showLatency: Boolean = false
) {
    /** 是否有任何依赖 usage 数据的项被打开，用于跳过整行渲染 */
    val anyUsageShown: Boolean
        get() = showTokenUsage || showSpeed || showLatency
}

/**
 * 聊天界面的外观设置。
 *
 * 单独成组而非平铺进 [UiPreferences]：这几项只影响消息列表的渲染，
 * 由聊天页整体接收，避免为每项单独穿参。
 */
data class ChatAppearance(
    /**
     * 用户消息默认有气泡。
     *
     * 曾改为默认无气泡以求两侧形态一致，但那样在有背景图时几乎
     * 分不出哪句是自己说的——文字直接浮在图上，没有边界。气泡
     * 提供了这个边界，这比形态统一更要紧。
     */
    val userBubble: BubbleStyle = BubbleStyle.BUBBLE,
    /**
     * 助手消息默认有气泡。
     *
     * 代价是代码块与表格用不满整个宽度，但可读性优先——尤其配
     * 背景图时，无气泡的正文与背景混在一起很难读。
     */
    val assistantBubble: BubbleStyle = BubbleStyle.BUBBLE,
    val avatarMode: AvatarMode = AvatarMode.DEFAULT,
    /** 自定义用户头像的本地文件路径，未设置为 null */
    val userAvatarPath: String? = null,
    /** 自定义助手头像的本地文件路径，未设置为 null */
    val assistantAvatarPath: String? = null,
    /**
     * 是否显示用户头像。
     *
     * 与 [avatarMode] 分工：模式决定"用什么图"（默认图标还是自定义图片），
     * 这两个开关决定"哪一侧显示"。分开是因为两个诉求正交——
     * 有人只想看助手头像，不希望自己的重复出现在每条消息旁。
     *
     * 默认开启：用户设置了自定义头像却看不到它在哪，会以为没生效。
     */
    val showUserAvatar: Boolean = true,
    /** 是否显示助手头像。默认开启 */
    val showAssistantAvatar: Boolean = true,
    val backgroundMode: ChatBackgroundMode = ChatBackgroundMode.DEFAULT,
    /** 背景图的本地文件路径 */
    val backgroundPath: String? = null,
    /**
     * 背景图暗化程度（0..1）。
     *
     * 背景图会削弱文字对比度，尤其浅色图配深色文字。
     * 叠一层可调的遮罩比要求用户自己处理图片实际。
     */
    val backgroundDim: Float = 0.35f,
    /**
     * 顶栏是否透明，仅在有背景图时有意义。
     *
     * 默认透明：顶栏本就没有独立内容，铺色会把背景图切掉一条。
     */
    val transparentTopBar: Boolean = true,
    /**
     * 输入栏是否透明。
     *
     * 默认不透明：输入栏承载文本输入，背景图透上来会明显影响
     * 输入内容与占位文字的可读性。想要通透观感的用户可以打开。
     */
    val transparentInputBar: Boolean = false,
    /**
     * 输入栏的透视强度。
     *
     * 与 [transparentInputBar] 并存而非取代：那个开关决定"要不要透"，
     * 这个决定"透多少"。合成一个 0..1 的值看着更简洁，但那样关掉再打开
     * 就丢了原先调好的强度。
     */
    val inputBarSeeThrough: Float = 0.35f,
    /**
     * 输入栏是否毛玻璃。
     *
     * 单纯降低不透明度会让背景图的细节直接透上来，压住光标和占位文字；
     * 模糊之后底下只剩色块，文字才看得清。API 31 以下无效，那里降级为
     * 只降不透明度。
     */
    val inputBarBlur: Boolean = false,
    /**
     * 背景图的处理方式：遮罩、模糊或两者。
     */
    val backgroundEffect: ChatBackgroundEffect = ChatBackgroundEffect.DEFAULT,
    /**
     * 背景图模糊半径（0..1，映射到实际 dp）。
     *
     * 默认给一个中等值而非 0：用户切到模糊效果时应当立刻看到区别，
     * 若默认为 0 会以为切换没生效。
     */
    val backgroundBlur: Float = 0.4f,
    /**
     * 头像模糊半径（0..1）。
     *
     * 默认 0 即不模糊。这个选项的用途是让自定义头像不那么抢眼——
     * 有人想用一张画面复杂的图当头像，又不希望它盖过消息内容。
     */
    val avatarBlur: Float = 0f,
    /**
     * 图片内容的版本号，每次保存图片递增。
     *
     * ImageStore 用固定文件名保存，换图后路径不变，渲染层无法从路径
     * 判断内容已更新。这个值参与图片解码的缓存 key，使换图后能立即刷新。
     */
    val imageVersion: Long = 0L
) {
    /** 是否需要渲染背景图层 */
    val hasBackgroundImage: Boolean
        get() = backgroundMode == ChatBackgroundMode.IMAGE && !backgroundPath.isNullOrBlank()

    /**
     * 是否需要为消息预留头像位。
     *
     * 只看两个显示开关，不再看 [avatarMode]。后者现在只决定"用什么图"，
     * 旧存值里的 NONE 不再拦住显示——那个耦合导致开关看似无效，
     * 且靠迁移修复的方案在标记已置而值未改的状态下无法自愈。
     */
    val showAvatars: Boolean get() = showUserAvatar || showAssistantAvatar
}

/**
 * 界面偏好。与 [AppSettings] 分开：后者是"以什么配置发请求"，
 * 这里是"界面怎么显示"，两者的读取方与变更频率都不同。
 */
data class UiPreferences(
    val colorMode: ColorMode = ColorMode.DEFAULT,
    /**
     * Android 12+ 生效，取系统壁纸配色。
     *
     * 开启时 [palette] 不生效——动态取色的整套色板来自壁纸，
     * 无法与预设配色叠加。
     */
    val dynamicColor: Boolean = true,
    /** 关闭动态取色时使用的预设配色 */
    val palette: ThemePalette = ThemePalette.DEFAULT,
    val messageDisplay: MessageDisplayOptions = MessageDisplayOptions(),
    val chatAppearance: ChatAppearance = ChatAppearance(),
    /** 空会话首屏的提示词候选池 */
    val quickPrompts: List<String> = DefaultQuickPrompts,
    /**
     * 是否每次进入空会话时随机抽取一批提示词。
     *
     * 关闭时按配置顺序取前若干条，行为可预期；开启时同一份配置
     * 每次展示不同组合，适合候选池较大的情况。
     */
    val shufflePrompts: Boolean = true
)

/**
 * 首次安装时的内置提示词。
 *
 * 数量明显多于首屏一次展示的条数（[QUICK_PROMPT_DISPLAY_COUNT]），
 * 使随机抽取有实际意义——若候选与展示数相同，"每次进入换一批"等于没换。
 */
val DefaultQuickPrompts = listOf(
    "帮我总结一段文字",
    "解释一个概念",
    "润色这段话",
    "写一段代码",
    "翻译成英文",
    "列个提纲",
    "找出这段代码的问题",
    "换种说法",
    "帮我起个名字",
    "拆解一个问题",
    "写条提交信息",
    "对比两个方案"
)

/** 首屏一次展示的提示词条数。 */
const val QUICK_PROMPT_DISPLAY_COUNT = 4
