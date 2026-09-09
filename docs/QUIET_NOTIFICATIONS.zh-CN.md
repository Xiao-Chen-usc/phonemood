# 1.3.1：安静统计

*English version: [QUIET_NOTIFICATIONS.md](QUIET_NOTIFICATIONS.md)*

- 后台通知改为“PhoneMood · 使用时长统计”，英文为“PhoneMood · Usage tracking”。不再使用 listening 文案。
- 后台通知使用低重要性、静默、无振动、不重复提醒、不显示时间；新建后台通知渠道也关闭声音、振动和角标。
- 后台通知和心情提醒设置为 `VISIBILITY_SECRET`，请求在安全锁屏隐藏。保留原通知渠道，尊重用户已有的系统通知设置；厂商系统和用户设置仍可能影响实际锁屏显示。如升级后锁屏仍可见，可在系统的 PhoneMood 通知设置里将“使用情况记录”设为锁屏不显示。
- 屏幕关闭或锁定时不投递新的心情通知。解锁后由下一轮检查处理有效提醒，保留原来五分钟有效期；过期提醒不会补弹。悬浮卡片原有的锁屏隐藏行为保持不变。
- 前台服务继续运行，后台统计与自动启动方式不变。Android 的运行中应用列表仍可能显示 PhoneMood。

覆盖安装 `dist/PhoneMood-1.3.1-debug.apk`，不要先卸载旧版。
