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

支持 OpenAI、Anthropic Claude 与 Google Gemini 三家服务，各自的密钥与配置独立保存，可随时切换。

**当前状态**：v0.2.1-dev 开发预览版

---

## ✨ 功能特性

### 已实现

- ✅ 简洁的聊天界面
- ✅ 多 AI 提供商：OpenAI、Anthropic Claude、Google Gemini
- ✅ 各提供商的密钥、模型与地址独立保存，切换互不影响
- ✅ 模型列表从服务端动态获取并本地缓存，也可手动输入模型名
- ✅ 对话历史保存
- ✅ 多轮对话上下文
- ✅ 流式响应（SSE，可在设置中关闭）
- ✅ Markdown 渲染（代码块、列表、标题、链接等）
- ✅ 自定义 API 地址，支持中转服务
- ✅ 发送失败重试
- ✅ 设置自动保存
- ✅ 会话抽屉，按时间分组并显示末条消息摘要
- ✅ 消息元信息：时间戳、模型名、token 用量、生成速度、响应耗时（可分项开关）
- ✅ 请求日志，可查看接口请求与响应以排查失败原因（密钥已脱敏）
- ✅ 深色模式与动态取色开关
- ✅ 首屏提示词可自定义
- ✅ Material You 动态主题

### 开发中

- 🚧 对话搜索与导出
- 🚧 系统提示词设置
- 🚧 消息编辑与重新生成

---

## 🚀 快速开始

### 下载安装

前往 [Releases](https://github.com/afyzfur/afyzhub/releases) 页面下载已签名的 APK，直接安装即可。

### 使用要求

- Android 8.0 (API 26) 或更高版本
- 至少一家服务的 API Key（OpenAI、Claude 或 Gemini）

### 配置步骤

1. 安装并打开应用
2. 进入设置页面
3. 选择服务提供商
4. 填入对应的 API Key
5. 点击「获取模型列表」，从结果中选择模型；也可直接输入模型名
6. 返回聊天页面开始对话

设置会自动保存，无需手动确认。API Key 仅存储在本机。

---

## 🛠 技术栈

- **Kotlin** 2.0.20
- **Jetpack Compose** - 声明式 UI 框架
- **Koin** - 依赖注入
- **OkHttp** - 网络请求与 SSE 流式读取
- **Kotlin Serialization** - JSON 序列化
- **Room** - 本地数据库
- **DataStore** - 配置存储

---

## 🗺 开发计划

### 阶段一：MVP

- [x] 基础聊天功能
- [x] OpenAI API 集成
- [x] 对话历史保存
- [x] 流式响应
- [x] Markdown 渲染

### 阶段二：功能扩展

- [x] 多 AI 提供商（Claude、Gemini）
- [ ] 对话管理（分组、搜索、导出）
- [ ] 系统提示词设置
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

## 📝 更新日志

1.0.0 正式版之前的所有版本均为开发版，带 `-dev` 后缀并以预发行版形式发布。

各版本的具体更新内容见 [CHANGELOG.md](CHANGELOG.md)，也可在对应的
[Release 详情页](https://github.com/afyzfur/afyzhub/releases)直接查看。

---

## 📄 开源协议

本项目采用 [Apache License 2.0](LICENSE) 协议开源。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！
