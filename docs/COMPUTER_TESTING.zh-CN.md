# 在电脑上测试 PhoneMood

*English version: [COMPUTER_TESTING.md](COMPUTER_TESTING.md)*

项目已经包含 Android 模拟器配置 `PhoneMood_API35`。如果你使用的是这台 Mac，最简单的方式是双击项目根目录的 [在电脑上测试.command](../在电脑上测试.command)。它会启动可见的 Android 模拟器、安装最新版调试 APK，并打开 PhoneMood；已有的模拟器数据会保留。

第一次启动可能需要一两分钟。模拟器打开后，可以在 PhoneMood 里按提示授权 Usage Access、通知和“显示在其他应用上层”。要测试跨应用悬浮卡片，进入“设置”打开悬浮卡片，然后点击“5 秒后预览”，切换到 Chrome 或其他应用即可看到卡片。

也可以在终端运行：

```bash
cd "/Users/chenlin/Desktop/reminder/ADHD手机拯救计划"
./scripts/build.sh :app:assembleDebug
./scripts/run-emulator.sh
```

如果只想安装已经生成的 APK，可以直接把 [PhoneMood-1.2.0-debug.apk](../dist/PhoneMood-1.2.0-debug.apk) 拖到模拟器窗口，或再次双击启动脚本。

语言跟随 Android 模拟器的系统语言，不跟随 Mac 的语言设置。切换方法是打开模拟器的“设置”→“系统”→“语言”→“系统语言”，选择简体中文或 English，然后重新打开 PhoneMood。应用首页、设置页、心情评分页、悬浮卡片和通知都会使用对应语言；其他系统语言会回退到英文。应用没有额外的语言开关，因此始终跟随系统设置。

模拟器和真实手机的权限行为可能略有不同。模拟器适合检查界面、语言切换、悬浮卡片和数据流程；真实手机还应检查厂商的电池后台限制和锁屏行为。
