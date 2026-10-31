# 21 项仓库审查

基线：54170cc。按源码核实；构建、单测、Lint、模拟器迁移、混淆 Release 的结果以对应 CI 为准。

| 项 | 核实与处理 |
|---|---|
| 1 全包 keep | 存在。删除两条全包规则，保留 Room/序列化规则，验证 R8 Release。 |
| 2 LICENSE | 缺失。补 Apache 2.0 全文；README 的原有授权声明不能简单理解为“完全无效”。 |
| 3 垃圾文件 | 三个文件存在。删除，忽略日志和 zip；Gradle wrapper jar 保留。 |
| 4 fork PR 签名 | 存在。PR 仅运行无 Secrets 的检查与仪器测试；发布限定 main push/dispatch，增加 concurrency 和非空 keystore 校验。 |
| 5 Room 迁移 | 缺 schema、KSP 导出和 assets 配置、CI 未运行。补 1–6 schema、逐段/跨版本升级、数据保留和外键验证，发布依赖模拟器任务。历史 schema 由迁移 SQL 契约重建，6.json 由编译器导出校准。 |
| 6 WebView | remember 不跨 Activity 存活，但这不足以证明全部“不稳定”的原因。补 saveState/restoreState；搜索跟随引擎设置，默认可回百度；禁止混合内容/第三方 Cookie/内容 URI；JavaScript 默认关闭，可手动启用。 |
| 7 Tavily JSON | 存在。用 Serializable DTO；特殊字符、控制字符和 emoji 回归测试。emoji 本身并不会破坏合法 JSON。 |
| 8 密钥明文 | 存在。DataStore 边界使用 Android Keystore AES-GCM 包装，覆盖 profiles、旧 provider Key、Tavily Key；原子迁移失败保留旧文件，增加纯单测与真实 Keystore 测试。 |
| 9 应用标识 | 采用兼容方案：namespace 对齐为 com.afyzhub.chat，同时保留已发布 applicationId=com.afyzhub.chat，不改变安装标识或升级路径；README 已说明。 |
| 10 旧依赖 | 存在。成套升级 AGP 8.9.2 / Gradle 8.11.1 / Kotlin 2.1.21 / KSP 2.1.21-2.0.1 / Compose BOM 2025.05.01 / Room 2.7.2 / Navigation 2.9.0；这是兼容性升级，不宣称所有组件最新。 |
| 11 文案 | 完成第二轮静态 UI 文案资源化；插值、ViewModel 错误、枚举和协议/提示词仍保留在代码中，完整多语言仍不是本轮交付。 |
| 12 流式写库 | 确认 16ms。改 33ms，上调步长保持追赶速度，最后完整内容仍落库。 |
| 13 Regex | 确认循环重复构造；实际 16 个字面表达式。统一预编译，HTML 上游结构变化依然可能使搜索失效。 |
| 14 非空断言 | 五处存在。改局部判空/已验证不变量，图片使用受条件保护的非空路径；删除全部 !!。 |
| 15 死代码 | 存在。删除空 usage 分支及 rawUsageLogged 假日志，注释与 33ms 一致。 |
| 16 Windows wrapper | 缺失。补官方 gradlew.bat，README 增加 Windows 命令。 |
| 17 大文件 | 将流式揭示/写库拆为 ChatStreamCollector，ChatContent 与 AppSettings 已拆出；WebSearchService、SettingsRepository 等仍需后续按职责继续拆分。 |
| 18 静态检查 | 存在。CI 增加 Android Lint，加入 editorconfig；不使用空 baseline 隐藏真实错误。 |
| 19 图标 | PNG 存在。仅使用像素一致的无损 WebP；保留更小的原文件。总量约 543374→398108 字节。 |
| 20 吞解析异常 | 存在。加入限频诊断，记录 provider、异常类型和次数，不写响应正文/密钥；协程取消继续抛出。 |
| 21 设计稿 | 已在 docs/ui-redesign.md，无需移动。README 新增链接，标明设计意图而非功能说明。 |

密钥加密属于升级后的本机存储保护，无法防止已获得进程控制权限的攻击者读取运行时凭证。旧版本不认识加密存储，升级后不应直接降级使用旧版。
