<div align="center">

# AfyzHub

**把聊天界面还给内容本身。**

一个 Android AI 聊天客户端，Kotlin + Jetpack Compose 构建。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.20-purple.svg)](https://kotlinlang.org)
[![Release](https://img.shields.io/badge/version-v0.3.7--dev-orange.svg)](https://github.com/afyzfur/afyzhub/releases)

[下载 APK](https://github.com/afyzfur/afyzhub/releases) · [更新日志](CHANGELOG.md)

</div>

---

## 为什么是 AfyzHub

多数 AI 客户端把功能做成了面板堆砌。AfyzHub 的思路相反：界面为聊天内容服务，每一处设计都可以回答"为什么要出现在这里"——

- 发送状态只在输入栏出现一行字，气泡下不再挂"发送中"
- 思考过程折叠在正文旁，不与回答混排
- 流式回复逐字呈现，进度由内容本身说明

## 功能

### 对话

流式输出逐字呈现（SSE，可关闭），任何阶段可暂停且保留已生成内容。支持 Markdown 渲染、思考过程展示、失败重试、编辑重发、消息回滚（内容自动放回输入栏）、删除撤回。消息元信息（模型、token 用量、耗时）可分项开关。

### 模型与配置

OpenAI、Anthropic Claude、Google Gemini 三家，各家密钥与地址独立。支持任意多组配置同存——不同额度、不同中转、测试与生产互不覆盖。模型列表从服务端拉取并缓存，可筛选只显示常用的；输入栏左下角点一下即弹出半屏选择器，选模型的同时切好配置组。Claude 与 Gemini 的思考程度（token 预算）也已接入，输入栏按钮对三家同样有效。

### 会话管理

抽屉式会话列表，按时间分组、显示末条摘要，支持置顶、星标、自定义分组。可按标题、总结、简介、分组搜索，多个关键词空格分隔。

### 外观

六套预设配色（石墨为默认，品牌橙只留在图标上）加 Material You 动态取色。深色模式、消息气泡样式、双侧头像、聊天背景均可自定义；背景支持遮罩与模糊叠加、四边裁剪，设置页预览与聊天页所见一致。输入栏透视分两档，增强档连文字一起半透，能看见压在下面的消息。

### 联网与日志

Gemini 原生联网搜索，设置页一键开关。请求日志记录提供商、模型与实际主机名（密钥脱敏），成功的请求也保留，可按时间、模型、提供商、只看失败叠加筛选。

### 隐私

API Key 仅存储在本机，不经过任何第三方服务。

## 快速开始

1. 从 [Releases](https://github.com/afyzfur/afyzhub/releases) 下载 APK 安装（Android 8.0+）
2. 设置 → API 配置，新建一组配置
3. 填入任一服务的 Key（自定义地址支持中转）
4. 拉取模型列表并选择，返回聊天

无需注册，无需服务器。

## 技术栈

| 层 | 选择 |
|---|---|
| UI | Jetpack Compose, Material 3 |
| 架构 | MVVM, Koin |
| 网络 | OkHttp（含 SSE 流式） |
| 存储 | Room, DataStore |
| 序列化 | Kotlin Serialization |

## 支持开发

AfyzHub 是完全免费开源的项目。如果它对你有帮助，欢迎通过爱发电赞助开发者，助力项目持续更新：

<div align="center">

[**⚡ 前往爱发电赞助**](https://afdian.com/a/afyzhub)

[![afdian](https://pic1.afdiancdn.com/static/img/welcome/button-sponsorme-hi.png)](https://afdian.com/a/afyzhub)

</div>

> 你的每一份支持都会变成更快的更新节奏和更多的功能。

## 本地构建

```bash
git clone https://github.com/afyzfur/afyzhub.git
cd afyzhub
./gradlew assembleDebug
```

需要 JDK 17+、Android SDK 35。产物在 `app/build/outputs/apk/`。

## 版本说明

1.0.0 之前均为开发版（`-dev` 后缀，语义化版本），以预发行版形式发布。完整更新记录见 [CHANGELOG.md](CHANGELOG.md)。

## 协议与贡献

[Apache License 2.0](LICENSE)。欢迎 Issue 与 Pull Request。

---

<div align="center">

**AfyzHub** · 界面为内容服务

</div>
> 开发状态：当前版本仍在开发中，内置浏览器功能存在已知问题，不建议作为稳定版本使用。
> 当前开发状态：Tavily 搜索已接入，Markdown 表格已支持；内置浏览器仍存在已知问题，当前版本不建议作为稳定版本使用。
