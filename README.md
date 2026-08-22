# AfyzHub

<div align="center">

**一个简洁的 Android AI 聊天客户端**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple.svg)](https://kotlinlang.org)

[下载 APK](https://github.com/afyzfur/afyzhub/releases)

</div>

---

## 📖 项目简介

AfyzHub 是一个开源的 Android AI 聊天应用，采用 Kotlin + Jetpack Compose 构建，遵循 Material Design 3 设计规范。

**当前状态**：v0.1.1-dev 开发预览版

---

## ✨ 功能特性

### 已实现

- ✅ 简洁的聊天界面
- ✅ OpenAI API 集成
- ✅ 对话历史保存
- ✅ API Key 配置
- ✅ Material You 动态主题

### 开发中

- 🚧 Markdown 渲染
- 🚧 流式响应（SSE）
- 🚧 多 AI 提供商支持
- 🚧 对话管理
- 🚧 主题定制

---

## 🚀 快速开始

### 下载安装

前往 [Releases](https://github.com/afyzfur/afyzhub/releases) 页面下载已签名的 APK，直接安装即可。

### 使用要求

- Android 8.0 (API 26) 或更高版本
- 有效的 OpenAI API Key

### 配置步骤

1. 安装并打开应用
2. 进入设置页面
3. 填入 OpenAI API Key
4. 选择模型（默认：gpt-3.5-turbo）
5. 返回聊天页面开始对话

---

## 🛠 技术栈

- **Kotlin** 2.0.20
- **Jetpack Compose** - 声明式 UI 框架
- **Koin** - 依赖注入
- **Retrofit + OkHttp** - 网络请求
- **Room** - 本地数据库
- **DataStore** - 配置存储

---

## 🗺 开发计划

### 阶段一：MVP（当前）

- [x] 基础聊天功能
- [x] OpenAI API 集成
- [x] 对话历史保存
- [ ] Markdown 渲染
- [ ] 流式响应

### 阶段二：功能扩展

- [ ] 多 AI 提供商（Claude、Gemini 等）
- [ ] 对话管理（分组、搜索、导出）
- [ ] 图片上传
- [ ] 语音输入

### 阶段三：高级特性

- [ ] 插件系统
- [ ] 本地模型支持
- [ ] 跨平台（KMP）

---

## 🔨 本地开发

### 环境要求

- JDK 17+
- Android Studio Ladybug (2024.2.1)+
- Android SDK 35
- Gradle 8.9

### 构建步骤

```bash
git clone https://github.com/afyzfur/afyzhub.git
cd afyzhub
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/` 目录。

---

## 📄 开源协议

本项目采用 [Apache License 2.0](LICENSE) 协议开源。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

