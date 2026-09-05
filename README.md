# PhoneMood｜手机情绪觉察

一个完全本地运行的 Android 应用，用来帮助你观察「手机主动使用时间」与「自我报告情绪」之间的关系。

它不需要账号、服务器、遥测、广告 SDK 或网络权限。所有使用记录、情绪评分和报告都保存在手机本地。

## 功能

- 统计主动使用时间和各 App 使用情况
- 按使用时长触发 1–10 分情绪自评提醒
- 支持通知和悬浮卡片
- 支持稍后提醒、关闭、暂停监控和 App 排除
- 查看 Today、Timeline 和每日统计报告
- 将每日数据导出为 JSON
- 支持进程重启、手机重启和数据恢复

## 用电脑测试

要求：JDK 17 或更高版本、Android SDK 35，以及 Android 10（API 29）或更高版本的模拟器或实体设备。

在项目目录执行：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./scripts/run-emulator.sh app/build/outputs/apk/debug/app-debug.apk
```

也可以用 Android Studio 打开项目目录，运行 `app` 配置。

首次使用需要在 Android 设置中授予：

1. Usage Access（使用情况访问权限）
2. 通知权限
3. Display over other apps（显示在其他应用上层）

完整测试清单见 [`docs/DEVICE_TESTING.md`](docs/DEVICE_TESTING.md)。

## 构建与测试

```bash
./gradlew :app:assembleDebug       # 编译 debug APK
./gradlew :app:testDebugUnitTest  # 运行单元测试
./gradlew :app:lintDebug          # 运行代码检查
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

## 数据与隐私

PhoneMood 没有 `INTERNET` 权限。数据保存在 Android 本地数据库中。报告导出到：

```text
Downloads/PhoneMoodHealth/YYYY-MM-DD-phone-mood.json
```

报告可能包含 App 包名、使用时间和情绪评分；分享时请确认接收方和范围。

## 项目结构

- `app/src/main/java/com/phonemood/ui/`：Compose 用户界面
- `app/src/main/java/com/phonemood/monitoring/`：使用情况读取和 session 计算
- `app/src/main/java/com/phonemood/mood/`：情绪提醒、通知和悬浮卡片
- `app/src/main/java/com/phonemood/data/`：Room 数据库和数据仓库
- `app/src/main/java/com/phonemood/report/`：日报生成和导出
- `app/src/test/`：单元测试
- `app/src/androidTest/`：Android 模拟器/实体设备测试
- `docs/`：设计、验证和测试文档

## 设计边界

这是一个自我观察工具，不提供医疗诊断、因果判断或临床建议。后台运行、UsageStats 数据完整性、电池策略和通知投递受 Android 系统限制。

---

# PhoneMood | Phone Mood Awareness

PhoneMood is a local-only Android app for noticing the relationship between active phone use and self-reported mood.

It requires no account, server, telemetry, advertising SDK, or network permission. Usage records, mood ratings, and reports stay on the device.

## Features

- Summarizes active phone time and usage by app
- Prompts for a 1–10 mood rating after configured usage intervals
- Supports notification and floating overlay check-ins
- Supports rating, snoozing, dismissing, and pausing monitoring
- Provides Today, Timeline, and daily report views
- Exports daily data as JSON
- Reconciles data after process death, reboot, and late responses

## Computer testing

Requirements: JDK 17+, Android SDK 35, and an Android 10/API 29 or newer emulator or device.

From the project directory:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
./scripts/run-emulator.sh app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio and run the `app` configuration. The first run requires Usage Access, notification permission, and Display over other apps permission. See [`docs/DEVICE_TESTING.md`](docs/DEVICE_TESTING.md) for the full checklist.

## Build and test

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Data and privacy

PhoneMood does not request the `INTERNET` permission. Data is stored in a local Android database. Reports are exported under `Downloads/PhoneMoodHealth/` and may contain package names, usage durations, and mood ratings.

## Project structure

- `app/src/main/java/com/phonemood/ui/`: Jetpack Compose UI
- `app/src/main/java/com/phonemood/monitoring/`: usage reading and session calculation
- `app/src/main/java/com/phonemood/mood/`: prompts, notifications, and overlays
- `app/src/main/java/com/phonemood/data/`: Room database and repository
- `app/src/main/java/com/phonemood/report/`: daily report generation and export
- `app/src/test/`: unit tests
- `app/src/androidTest/`: emulator/device tests
- `docs/`: design, validation, and device-testing documentation

## Scope and limitations

PhoneMood is a self-observation tool, not a medical, diagnostic, causal, or clinical system. Background execution, UsageStats completeness, battery policies, and notification delivery are subject to Android system behavior.
