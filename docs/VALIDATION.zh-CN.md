# 历史验证记录 —— PhoneMood 1.0.0，2026 年 9 月 5 日

*English version: [VALIDATION.md](VALIDATION.md)*

## 这份记录的范围

本报告保存的是首个 **1.0.0** debug 构建当时记录的工程检查。此后仓库加入了 1.4.0 的分析功能和更多源码改动。**下文的测试数量和模拟器观察结果，不得被引用为对后续任何源码修订或 APK 的完整验证。** 仅凭版本名无法确定被测试的具体代码。

后来的[分析测试](../app/src/test/java/com/phonemood/analysis/AnalysisTest.kt)、[导出 schema](schemas/phonemood-period-v2.schema.json) 和[合成示例](examples/implemented-exports/README.zh-CN.md)是独立的产物。它们的存在并不说明当前所有检查都已通过。新的验证报告应当标明源码 commit 与本地改动、产物、环境、命令、结果，以及仍然存在的局限。

截至 2026 年 9 月 7 日的文档更新，项目作者报告已进行小规模用户测试。其参与人数、方法和发现均未记录于此。**工程验证和合成示例不能确立可用性、测量效度，或心理／教育层面的有效性。** 当前定位见[项目总览](../README.zh-CN.md)，拟议的评估方案见[文献综述](LITERATURE_REVIEW.zh-CN.md)。

本次文档更新没有重跑任何应用测试；下列历史结果保持原样。

## 原始构建与环境

构建：PhoneMood 1.0.0 debug，Kotlin/Compose，minSdk 29，target/compile SDK 35。
环境：本地 macOS 构建，Gradle 8.13 与 IntelliJ 内置 JDK；Pixel 6 配置的 Android 15 / API 35 ARM64 模拟器。

## 自动化检查

- `:app:assembleDebug`：通过；生成可安装的 debug APK。
- `:app:testDebugUnitTest`：16 项测试通过，零失败／零错误。
- `:app:connectedDebugAndroidTest`：5 项测试通过，零失败／零错误。
- `:app:lintDebug`：零错误。非致命提示涉及更新的依赖／SDK 版本和 Kotlin URI 便利 API。
- `apksigner verify`：debug APK 签名验证通过。
- 已检查 APK manifest：没有 `INTERNET` 权限。WorkManager 引入了 `WAKE_LOCK` 和 `ACCESS_NETWORK_STATE`；网络访问并未启用。

单元测试覆盖：短暂锁屏间隔、达到重置阈值的间隔、应用切换、排除项、重复／乱序重放、错过的轮询边界、稳定的检查点 ID、暂停、关机、配置变更、午夜裁切，以及夏令时的日长度。

设备测试覆盖：Room 检查点／原始事件唯一性；导出的时间线／汇总总计与 schema 形态；重复导出的幂等性；首次回答生效，以及迟到回答对原日期导出文件的替换；记录缺口元数据；防止 Settings.FallbackHome 意外排除掉整个「设置」应用的回归保护。

## 模拟器实测检查

- 全新安装的 Today 界面正常渲染，并能打开 Android 使用情况访问权限设置页。
- 使用情况访问权限和通知权限均可授予；Start monitoring 会创建一个带常驻通知的 `specialUse` 前台服务。
- 真实的「设置」Activity 被记入一个会话，并出现在 Timeline 界面。一次早期冒烟测试发现 Android 的 fallback-home 组件过度排除了「设置」；过滤器已修正为只使用用户选定的桌面，并由一项设备回归测试覆盖。
- Reports → Export today 在 Downloads/PhoneMoodHealth 写出一个完整的 JSON 文件。
- 下载得到的真实模拟器记录中包含 41,286 毫秒的「设置」使用时长。Python 校验工具确认时间线、App 和每日总计三者一致。该日被正确标记为不完整。
- 更新并重新打开已安装的 APK，记录和此前开启的统计状态都得以保留。
- 在设置中暂停、强行停止、再打开应用，暂停状态保持不变；不会重启 UsageMonitorService。
- 这些检查过程中没有观察到 AndroidRuntime 崩溃。

`example-emulator-report.json` 是这台隔离测试模拟器的真实导出，不是用户数据，也不是注入应用的示例。截图展示的是真实的空／初始监控状态，不是伪造的心情历史。

## 仍需完成的真机验证

仍然需要一次真机浸泡测试，覆盖各厂商的电池策略、进程回收、跨多天恢复、重启行为、权限随时间被拒绝／撤销，以及完整间隔的通知时机。本次会话没有在真机上运行 API 29 和 API 36。测试套件验证的是阈值和导出逻辑，**并不能确立电量效率或精确的通知送达时机**。

原始构建同样保留了以下局限：MediaStore 发布、保守的历史恢复、补读时推断的时区偏移，以及全量重放的伸缩性。[真机清单](DEVICE_TESTING.zh-CN.md)包含发布、恢复、时区和长期 profiling 等场景。本历史报告针对的是一个 debug MVP，不是签名的生产／Play 发布版。
