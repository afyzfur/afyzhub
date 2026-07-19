# afyzhub

一个简洁的 Android AI 聊天客户端，首版支持 **OpenAI Chat Completions 兼容接口**。界面基于 Jetpack Compose 与 Material 3，聊天数据只保存在设备本地。

> 版本 `0.1.0` · 包名 `com.afyzhub.aichat` · 最低 Android 8.0（API 26）

## 功能

- 配置 OpenAI 或兼容服务的 HTTPS Base URL、API Key 与模型 ID
- `/chat/completions` SSE 流式输出，支持随时停止生成
- 处理 401、403、404、429、5xx、空响应及损坏的 SSE 数据
- 新建、选择、删除本地会话，消息持久化
- 复制任意消息，代码块（含 ```）等宽显示
- 可设置 System Prompt、Temperature、最大输出 Token
- Material 3 界面，支持 Android 12+ 动态取色与深浅色跟随系统
- API Key 使用 Android Keystore 的 AES/GCM 在本设备加密保存

## 配置步骤

1. 首次启动后点击右上角“配置”。
2. 填写 HTTPS Base URL，例如 `https://api.openai.com/v1`。
3. 填写 API Key 与模型 ID（例如 `gpt-4o-mini`）。
4. 保存后返回聊天页即可发送消息。

应用不会把 API Key 或聊天记录上传到 afyzhub 自身的服务器，请求只发送到你所配置的服务商。为避免密钥经明文网络泄露，应用拒绝 HTTP Base URL。

## 构建

```bash
./gradlew assembleDebug
```

输出 APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 安全与隐私

- API Key 不写入源码、日志或 Git 仓库。
- API Key 由 Android Keystore 支持的 AES/GCM 在设备本地加密。
- 会话与普通配置仅保存于应用私有存储。
- 使用第三方兼容 API 前，请自行核实其隐私政策与服务条款。

## 当前限制与后续计划

首版仅支持文本 Chat Completions 协议，暂不含图片、文件、语音、工具调用、云同步与多 Provider 管理。后续计划：将本地会话迁移至 Room、增加 `/models` 模型发现与多配置切换、加入消息重新生成与编辑重发的完整交互。

## 许可证

本项目采用 [Apache-2.0](LICENSE) 许可证。