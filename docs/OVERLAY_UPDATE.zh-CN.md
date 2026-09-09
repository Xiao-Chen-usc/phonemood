# PhoneMood 1.1 — 悬浮心情提醒

*English version: [OVERLAY_UPDATE.md](OVERLAY_UPDATE.md)*

## 手机上怎么开启

1. 安装 `PhoneMood-1.1.0-debug.apk`。可以覆盖安装原来的 1.0.0，保留原有记录；不要先卸载旧版。
2. 打开 PhoneMood → Settings → **FLOATING CHECK-INS**。
3. 点击 **Display over other apps → Allow**，在 Android 设置中允许 PhoneMood 显示在其他应用上层。部分手机会先显示应用列表，需要再选择 PhoneMood。
4. 保持 **Show over other apps** 开启。
5. 点 **Preview in 5 seconds**，立即切换到 Chrome 等 App。约 5 秒后出现预览卡片；预览分数不会写入记录，20 秒后自动收起。
6. 正式使用时，开启 Usage Access 和 Start monitoring。达到有效使用时长后，直接在浮层上点 1–10 分，保存后卡片消失。**Later** 延迟一分钟；**×** 关闭本次卡片，通知仍可用于稍后回答。

## 行为

- 一个有界的 `TYPE_APPLICATION_OVERLAY` 窗口显示在普通应用之上。它不会启动评分 Activity，也不会替换当前任务。
- 窗口在卡片内部可触摸，不获取焦点，不是模态。卡片范围之外的触摸仍然交给下层应用。
- 评分通过与既有评分 Activity 相同的事务式、首次回答生效的 repository 保存。保存过程中按钮禁用；出错时保留卡片以便重试。
- 稍后和关闭状态持久化在 Room 新增的 `MoodPromptState` 表中。轮询和控制器重建都不会撤销一次关闭。稍后会在其存储的截止时间恢复，仍受检查点五分钟有效期约束。
- 息屏立即移除服务的卡片。锁定或非交互状态下无法附加卡片。服务销毁、已回答、已关闭或已过期同样会移除窗口。
- 同一时间只显示一张卡片。没有悬浮窗权限、悬浮卡片被关闭、或附加失败时，保留原有的通知路径。真实卡片成功附加时，其备份通知以静默方式请求。
- 预览作为一次短暂的前台服务操作运行，绝不创建合成的检查点或心情回答。即使使用统计已暂停，预览也可用。
- 预览期间前台服务可能短暂可见，最长约 30 秒，即使统计处于暂停状态。

## 升级与数据

版本号 versionCode 为 2，版本名为 1.1.0。Room 迁移 1→2 新增一张表并保留既有表。不使用破坏性迁移回退。DataStore 新增 `overlayEnabled` 偏好；配置变更在 Room 中留痕。修改这项展示偏好不会重置或中断使用会话。

报告仍保持 schema 版本 1.0，并在心情上下文中新增可选的 `floating_card` 对象，包含首次附加时间、关闭、稍后截止时间和最后一次附加错误。**附加时间戳并不能证明用户看见了卡片。** 预览数据不进入导出。

## 验证

- 构建与 Android lint 全部通过，lint 零错误；仍保留非致命的依赖／版本、URI 便利写法和原生视图未翻译文本提示。
- 21 项 JVM 测试通过：原有 16 项，加 5 项悬浮卡片的可用性／稍后／过期测试。
- 13 项设备测试在 Android 15 / API 35 通过：6 项存储／导出／配置检查，6 项真实悬浮窗界面检查，1 项旧 schema 迁移检查。
- 悬浮界面检查覆盖：在其他应用位于前台时点击评分、单窗口行为、跨控制器重建的关闭状态、稍后恢复、预览不产生记录、权限被撤销、统计暂停、锁屏抑制。
- 实际对一个 1.0.0 安装做了原地升级：其中记录的 41 秒使用时长和暂停状态都完好保留。
- 在 Chrome 中，Settings → Preview in 5 seconds 在 `about:blank` 上方显示了真实的悬浮层，浏览器仍在其下方。见[实际截图](screenshots/overlay-chrome-preview.png)。该截图标注为 PREVIEW，因为它使用的是产品自带的预览功能，而不是伪造的使用或心情历史。

## 平台限制

Android 可以在安全界面上抑制悬浮窗，某些应用也会显式隐藏它们。本文不声称覆盖锁屏、权限对话框、键盘或所有系统窗口。通知回退路径予以保留。卡片不使用无障碍服务特权、后台 Activity 启动或全屏 intent 权限。

悬浮卡片打开时，UsageStats 仍将时间归属于下层的前台应用；悬浮窗不是一次 Activity 切换。时机仍是尽力而为，取决于既有的十秒轮询。各厂商的电池行为，以及更旧或更新的 Android 版本，仍需在真机上验证。

参考：[Android 应用悬浮窗](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)、[悬浮窗权限](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context))、[会隐藏悬浮窗的安全界面](https://developer.android.com/security/fraud-prevention/activities)。
