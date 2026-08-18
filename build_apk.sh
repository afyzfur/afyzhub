#!/bin/bash
# AfyzHub 快速编译脚本

set -e

echo "================================================"
echo "  AfyzHub - 快速编译脚本"
echo "================================================"
echo ""

# 检查项目目录
if [ ! -f "settings.gradle.kts" ]; then
    echo "❌ 错误：请在项目根目录运行此脚本"
    exit 1
fi

echo "✅ 项目目录确认"

# 检查 JDK
echo ""
echo "🔍 检查 JDK 版本..."
java -version 2>&1 | head -1

# 清理旧构建
echo ""
echo "🧹 清理旧构建文件..."
rm -rf .gradle build app/build

# 生成 Gradle Wrapper
if [ ! -f "gradlew" ]; then
    echo ""
    echo "📦 生成 Gradle Wrapper..."
    gradle wrapper --gradle-version=8.7
fi

# 赋予执行权限
chmod +x gradlew

# 开始编译
echo ""
echo "🚀 开始编译 Debug APK..."
echo ""
./gradlew clean assembleDebug --stacktrace

# 检查编译结果
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo ""
    echo "================================================"
    echo "  ✅ 编译成功！"
    echo "================================================"
    echo ""
    echo "📱 APK 位置："
    echo "   $(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    ls -lh app/build/outputs/apk/debug/app-debug.apk
    echo ""
    echo "📦 安装命令："
    echo "   adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
else
    echo ""
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi