# PhoneMood 1.3：自动开始与快捷权限配置

*English version: [AUTOMATIC_SETUP.md](AUTOMATIC_SETUP.md)*

安装 `dist/PhoneMood-1.3.0-debug.apk`，可直接覆盖安装并保留已有数据。

首页和设置页新增“快捷配置权限”。点击一次后，应用依次打开尚未完成的使用情况访问权限、通知、悬浮卡片和电池优化豁免申请。每项仍需要在 Android 的系统页面确认；拒绝某项不会反复强制弹出，之后可再次配置。

首次开启使用情况访问权限并返回 PhoneMood 后，立即自动开始统计，无需再点 Start monitoring。之后切换应用仍通过带有常驻通知的前台服务在后台统计；系统重启后尝试恢复之前已开启的记录。手动暂停会保留，不会因重新打开应用而自动恢复。系统“强行停止”和部分厂商的后台限制仍可能停止记录，需要重新打开应用或调整厂商的自启动设置。

悬浮权限授权列表中的其他条目是其他应用，不是 PhoneMood 的多个权限。只需选择 PhoneMood 并开启一个开关，即可在允许悬浮窗的其他应用上方显示卡片。Android 11 及之后的标准权限入口可能显示应用总列表，不能代替用户批量授权。参考：[Android 权限说明](https://developer.android.com/about/versions/11/privacy/permissions)。

本次验证：APK 构建、单元测试和 lint 通过；新增数据库测试验证未授权不启动、授权后只启动一次、保留手动暂停。Android 15 模拟器实走了完整快捷授权流程，未点击 Start monitoring 即显示“正在记录”；切到 Chrome 后服务仍处于前台服务状态，电池优化豁免已生效。

界面截图：`screenshots/quick-setup-1.3.png`。
