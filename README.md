# AfyzHub

<div align="center">

**一个现代化的 AI 聊天客户端**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple.svg)](https://kotlinlang.org)

[下载 APK](https://github.com/afyzfur/afyzhub/releases) · [功能计划](#roadmap) · [技术栈](#tech-stack)

</div>

---

## 📖 项目简介

AfyzHub 是一个开源的 Android AI 聊天客户端，旨在提供简洁、高效的 AI 对话体验。项目采用现代化的 Android 开发技术栈，遵循 Material Design 3 设计规范。

**当前状态**：开发预览版（0.1.0-dev），核心功能已实现，正在持续完善中。

---

## ✨ 功能特性

### 已实现

- ✅ **简洁的聊天界面** - 基于 Jetpack Compose 构建的现代化 UI
- ✅ **OpenAI API 集成** - 支持 GPT 系列模型
- ✅ **对话历史保存** - 本地数据库持久化存储
- ✅ **灵活的设置系统** - API Key、模型参数配置
- ✅ **Material You 支持** - 动态主题色

### 开发中

- 🚧 **Markdown 渲染** - 富文本显示支持
- 🚧 **流式响应（SSE）** - 实时输出，更好的交互体验
- 🚧 **多 AI 提供商支持** - Claude、Gemini 等
- 🚧 **对话管理** - 分组、搜索、导出功能
- 🚧 **主题定制** - 多套预设主题

---

## 🚀 忩速开始

### 下载安装

前往 [Releases](https://github.com/afyzfur/afyzhub/releases) 页面下载最新的 APK：

- **app-debug.apk** - 包含调试信息，体积较大（约 13 MB），适合开发调试
- **app-release-unsigned.apk** - 未签名发布版本（约 2.4 MB），需要自行签名

### 使用要求

- Android 8.0 (API 26) 或更高版本
- 有效的 OpenAI API Key

### 配置步骤

1. 安装 APK 并打开应用
2. 进入「设置」页面
3. 填入你的 OpenAI API Key
4. 选择 AI 模型（默认：gpt-3.5-turbo）
5. 返回聊天页面，开始对话

---

## 🛠 <a name="tech-stack"></a>技术栈

### 核心框架

- **语言**: [Kotlin](https://kotlinlang.org/) 2.0.20
- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) - 声明式 UI
- **架构**: MVVM + Repository 模式

### 主要库

| 库 | 用途 | 版本 |
|---|---|---|
| [Koin](https://insert-koin.io/) | 依赖注入 | 4.0.0 |
| [Retrofit](https://square.github.io/retrofit/) | 网络请求 | 2.11.0 |
| [OkHttp](https://square.github.io/okhttp/) | HTTP 客户端 | 5.0.0-alpha.14 |
| [Room](https://developer.android.com/training/data-storage/room) | 本地数据库 | 2.6.1 |
| [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | 配置存储 | 1.1.1 |
| [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization) | JSON 序列化 | 1.7.3 |

---

## 📦 项目结构

```
app/src/main/java/com/afyzhub/chat/
├── AfyzHubApplication.kt       # 应用入口
├── MainActivity.kt             # 主活动
├── data/                       # 数据层
│   ├── local/                  # 本地数据源
│   │   ├── dao/                # Room DAO
│   │   ├── database/           # 数据库定义
│   │   └── entity/             # 数据库实体
│   ├── remote/                 # 远程数据源
│   │   ├── api/                # API 接口
│   │   └── dto/                # 数据传输对象
│   └── repository/             # 仓库实现
├── di/                         # 依赖注入模块
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   └── NetworkModule.kt
├── domain/                     # 领域层
│   ├── model/                  # 领域模型
│   └── repository/             # 仓库接口
├── ui/                         # UI 层
│   ├── chat/                   # 聊天页面
│   ├── settings/               # 设置页面
│   └── theme/                  # 主题配置
└── util/                       # 工具类
```

---

## 🗺 <a name="roadmap"></a>开发计划

### 阶段一：MVP 核心功能（当前阶段）

- [x] 项目架构搭建
- [x] 基础聊天功能
- [x] OpenAI API 集成
- [x] 本地数据持久化
- [ ] Markdown 渲染
- [ ] 流式响应支持

### 阶段二：功能完善

- [ ] 多 AI 提供商接入（Claude、Gemini、通义千问等）
- [ ] 对话管理（分组、搜索、导出）
- [ ] 图片上传与多模态支持
- [ ] 语音输入
- [ ] 主题定制

### 阶段三：高级特性

- [ ] 插件系统
- [ ] 本地模型支持（llama.cpp）
- [ ] 云端同步
- [ ] 桌面端（KMP 跨平台）

---

## 🔨 本地开发

### 环境要求

- JDK 17 或更高版本
- Android Studio Ladybug (2024.2.1) 或更高版本
- Android SDK 35
- Gradle 8.9

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/afyzfur/afyzhub.git
cd afyzhub

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease

# 运行测试
./gradlew test
```

编译成功后，APK 位于 `app/build/outputs/apk/` 目录。

---

## 🤝 贡献指南

欢迎任何形式的贡献！无论是报告 Bug、提出新功能建议，还是提交代码改进。

### 提交 Issue

- 搜索现有 Issue，避免重复
- 提供清晰的标题和详细描述
- 附上复现步骤、截图或日志

### 提交 Pull Request

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'Add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

---

## 📄 开源协议

本项目采用 [Apache License 2.0](LICENSE) 协议开源。

---

## 👤 作者

**afyzfur**

- GitHub: [@afyzfur](https://github.com/afyzfur)

---

## ⭐ Star History

如果这个项目对你有帮助，请考虑给一个 Star ⭐

---

<div align="center">

**Made with ❤️ by afyzfur**

</div>