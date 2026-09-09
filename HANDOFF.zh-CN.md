# Handoff：看视频时收不到心情提醒

*English version: [HANDOFF.md](HANDOFF.md)*

**状态**：核心 bug 已定位并修复，但**用户实测仍未收到通知，真正原因尚未查明**。
诊断卡在手机连不上 adb。所有改动**均未提交**。

最后更新：2026-09-08

---

## 1. 症状

用户看 B 站 / 小红书 / YouTube 视频一小时，**既没有悬浮卡片，也没有通知**。

第二轮反馈（装了第一版修复之后）：
- 看视频期间仍然什么都没有
- **而且比之前更糟**：1.4.0 至少切换到别的 App 时会提示，第一版修复后连这个也没了

---

## 2. 已确认的根因

### 2.1 1.4.0 的通知抑制（原始 bug，已修复）

`MoodNotificationManager.deliver()` 里有个空分支：

```kotlin
val deferredApp = checkpoint.foregroundPackage == latestForegroundPackage && promptState?.dismissed != true
if (deferredApp) {
    // Keep the prompt pending until the user leaves YouTube.
} else if (...) { /* MISSED */ }
else if (...) { /* 真正发通知 */ }
```

只要「产生 checkpoint 时的前台 App」==「当前前台 App」，就走进空分支，通知永远发不出去。看视频时该条件恒真。

同时 `UsageMonitorService.kt` 的注释写着「Keep the heads-up notification audible while the same app remains foreground」，还认真算出了 `silentCheckpointId`——但 `deliver()` 对这种情况根本不发通知。**注释描述的行为是死代码，实现与意图相反。**

引入于 commit `45d9037`（1.4.0）。当时的测试没覆盖：`QuietNotificationTest` 里 checkpoint 包名是 `"test.app"` 而数据库无任何事件，`latestForegroundPackage` 恒为 `null`，`deferredApp` 恒为 false，整个分支从未被测到。

### 2.2 我引入的回归（已修复）

第一版修复里我加了 `DEFERRAL_LIMIT_MS = 15min` 上限，本意是防止 checkpoint 无限挂起。结果：在同一个 App 里超过 15 分钟未回应，checkpoint 被判 MISSED 删除，**用户切走时已经没有东西可弹了**。这正是用户说的「连切换 APP 都不提示了」。

1.4.0 的行为是无限期挂着，所以切走时还在。**这个上限已完全撤销。**

### 2.3 包名变更（与提醒无关，但导致装成新 App）

`applicationId` 在 1.4.2 从 `com.phonemood` 改为 `com.phonemood.app`（为上架 Play）。Android 以 applicationId 作为 App 永久身份，改了就是全新应用，无法更新旧安装。签名不是问题（两边同一个 debug key `dcdd99…c82e`）。

`app/build.gradle.kts` 已加开关，默认值不变：

```bash
./gradlew assembleDebug -PphonemoodApplicationId=com.phonemood   # 侧载测试包，原地更新
./gradlew bundleRelease                                          # Play 用，com.phonemood.app
```

---

## 3. 当前实现（第二版）

判断逻辑集中在 `OverlayPolicy`，是纯函数，可在 JVM 单测里直接验：

```kotlin
fun action(now: Long, prompt: Prompt, foregroundPackage: String?): PromptAction
```

规则：

| 情形 | 行为 |
|---|---|
| 还停留在产生提醒的那个 App | **永不过期**，`WAIT` 或 `NOTIFY` |
| 同 App 内未回应 | 每 `RENOTIFY_INTERVAL_MS`(5min) 重发，上限 `MAX_NOTIFICATIONS`(3) |
| **切到别的 App** | 立刻 `NOTIFY` 一次，且只一次（靠 `notifiedPackage` 去重） |
| 切走后 5 分钟仍无回应 | `EXPIRE` → MISSED |
| 用户点了 × 关闭 | 按普通 5 分钟窗口过期 |

配套改动：

- `setOnlyAlertOnce(false)`：否则重发只会静默更新通知栏里那条，不会再响
- `PollingPolicy.PENDING_PROMPT_MS = 5_000`：有待回应提醒时轮询从 10s 收紧到 5s，让「切走立刻提示」实际在 5 秒内。注意 `poll()` 每次都会全量重放事件历史 + 清空重建 segments，**这是已知的性能隐患，不宜再调快**
- `MoodOverlayController`：卡片贴在自己那个 App 上方时不再写 `overlayShownUtc`（无法确认可见），改记 `lastOverlayError = "OVERLAY_MAY_BE_COVERED"`，避免导出数据里出现虚假的「已送达」
- `Database.lastResume()`：替掉每轮 3 次的 `events()` 全表扫描

---

## 3.5 第三版：积压合并 + 通话排除（2026-09-08 新增）

### 症状
打电话或看视频期间什么都没有；45 分钟通话结束、切到别的 App 时，**3 条通知加一张卡片一起涌上来**。

### 根因（纯代码可解释，不是未解之谜）
1. `SessionEngine.advance()` 按 `interval`（默认 **15 分钟**）持续产出 checkpoint。45 分钟 = 15/30/45 三个 → 正好三条。
2. 第二版的「同 App 内永不过期」把它们全部挂起：通话时息屏 → `screenAvailable()` 为假不发；看视频时发了但被沉浸式全屏吞掉。
3. `deliver()` 对 **每一个** PENDING checkpoint 独立走策略。用户一切走，`leftTheApp` 对三个同时成立 → 三条一起响；`reconcile()` 再叠一张卡片。

也就是说，第二版修好了「不提示」，却把它变成了「憋着一起提示」。

### 改法
| 文件 | 改动 |
|---|---|
| `OverlayPolicy.kt` | 新增纯函数 `live()`（一批挂起中只留最新一个）与 `retirement()`（替换掉的那些怎么记账） |
| `MoodNotificationManager.deliver()` | 先选出 `live`，其余就地退休并 `cancel()` 通知；只有最新那条继续走原策略 |
| `UsageEventReader.AppFilter` | 通话界面加入自动排除集：`com.android.incallui` / `com.samsung.android.incallui` / `com.android.server.telecom` + `TelecomManager.defaultDialerPackage`（无需新权限） |
| `values*/strings.xml` | 排除说明补上「通话界面」 |

**为什么只留最新一条**：最新那条的 `checkpointMinutes` 本来就是整段会话的累计分钟数（45），文案已经说得对，它可以替前面几条发言。

**退休状态分两种，别混为一谈**：
- `MISSED` —— 问过了，用户没答（导出里应算未响应）
- `SUPERSEDED` —— **从没问过**，被新的顶掉了；不能计入未响应率

`DailyReportGenerator` 的 `missed_count` 只数 `MISSED`，所以 SUPERSEDED 不会污染统计；`PeriodExport` 原样写出 `response_state`。分析代码里没有对状态做穷举匹配，**无需数据库迁移**。

**通话排除的连带效果**：RESUME 到通话界面 → `interrupt()` → 超过 `reset`(5min) 会话关闭。所以 45 分钟通话不再产生任何 checkpoint，通话后是一段全新的会话，从 0 开始计时。这是想要的语义：通话不是这个 App 关心的「刷手机」。

### 验证
- `./gradlew testDebugUnitTest` —— **58 项全过**（新增 2 项：`live()` 选最新、`retirement()` 区分两种退休）
- `./gradlew assembleDebugAndroidTest` —— 编译通过
- 新增 `QuietNotificationTest.aBacklogOfHeldOpenCheckInsArrivesAsOneNudge`：造三个挂起 checkpoint（第一个已发过通知），切换 App 后断言 `MISSED` / `SUPERSEDED` / `PENDING`，且通知栏只剩一条
- ⚠️ **设备测试未跑**（仍无可用真机/未启模拟器）。合并前需要 `./gradlew connectedDebugAndroidTest -PphonemoodApplicationId=com.phonemood`

### 仍未解决
看视频时 heads-up 被沉浸式全屏吞掉，是 Android 行为，代码层面绕不过去。现在的保证降级为：**看完切走时，会收到且只收到一条**。§6 的取证仍然值得做——如果连这一条都收不到，问题在服务存活或权限，不在这一层。

---

## 3.6 第四版：让提醒真的响一声（2026-09-08 新增）

### 症状
「看视频时并没有推送，至少也要像微信一样梆啷一声。」

### 根因：渠道从创建那天起就是哑的，而且改不了
`NotificationChannel` 的**声音与震动在创建后不可修改**（只有名称、描述、分组可改）。原来的 `"mood"` 渠道创建时：

```kotlin
NotificationChannel("mood", …, IMPORTANCE_HIGH).apply {
    description = …
    lockscreenVisibility = VISIBILITY_SECRET
}
```

没有 `setSound()`，没有 `enableVibration()`。渠道默认给的是**系统默认提示音 + 不震动**。在播放视频的场景里，媒体音量占满、通知音量常年偏低、又完全没有震动 —— 「梆啷一声」缺的正是这个物理反馈。

更关键的是：**在 `createNotificationChannel` 里加 `enableVibration(true)` 对用户手机毫无作用**，因为渠道已经存在于 1.3/1.4 的安装里，系统会忽略除名称外的一切修改。

### 改法
| 文件 | 改动 |
|---|---|
| `MoodNotificationManager.kt` | 渠道 id 换成 `mood.v2`，显式 `setSound(DEFAULT_NOTIFICATION_URI, USAGE_NOTIFICATION)` + `enableVibration(true)` + 震动模式；旧的 `"mood"` 渠道 `deleteNotificationChannel` 退休 |
| 同上 | 新增 `reach(): Reach`，四态：`READY` / `DISABLED` / `SILENCED` / `SUPPRESSED` |
| 同上 | 新增 `preview()`：在真实渠道上发一条真实形态的测试提醒，不写任何数据 |
| `UsageMonitorService.kt` | 新增 `ACTION_TEST_NOTIFICATION`，延迟 5 秒发出（够时间切回视频） |
| `MainActivity.kt` | 权限卡片显示四态文案；新增「测试提醒 / 试一下」一行 |
| `values*/strings.xml` | 7 条新文案 ×2 语言 |

⚠️ **换渠道 id 的代价**：用户在旧渠道上做过的任何个性化设置（关闭、改铃声、设为静音）不会带过来，新渠道是全新的默认状态。这里是好事——它正是要摆脱那个哑掉的旧渠道。

### `canPrompt()` 原来太宽松
```kotlin
fun canPrompt() = areNotificationsEnabled() && channel?.importance != IMPORTANCE_NONE
```
`IMPORTANCE_LOW` / `IMPORTANCE_DEFAULT` 都能通过 —— 而这两档**不会横幅弹出、不会响**。也就是说，用户或三星 ROM 把渠道降级之后，App 会认为一切正常并继续「成功」发送哑通知。同样，勿扰模式完全没被检查。

现在拆开了：
- `reach()` 给出真实状态（用 `areNotificationsPaused()` 检测勿扰，API 28+，无需权限）
- `canPrompt()` 仍然只在 `DISABLED` 时才拦（哑的提醒也好过没有）
- `MonitoringGap` 的 reason 分三种：`NOTIFICATIONS_UNAVAILABLE` / `NOTIFICATIONS_SILENCED` / `NOTIFICATIONS_SUPPRESSED_BY_DO_NOT_DISTURB`

这直接给 §6 的「未解之谜」加了一路证据：导出里现在能区分「App 没发」和「手机不让发」。

### 「测试提醒」按钮 —— 不用 adb 的取证手段
设置 → 系统权限 → **测试提醒 → 试一下** → 5 秒后切进 B 站全屏。

- **响了** → 通道链路完好，问题在监测侧（服务被杀 / UsageStats / checkpoint 没生成），按 §6 第 1、4、5 项查
- **不响** → 问题在送达侧，看权限卡片那一行现在显示什么（无声送达 / 勿扰拦截）

这比 `scripts/diagnose_device.sh` 好用，因为它不需要手机连上 adb —— 而 adb 正是当前的卡点。

### 验证
- `./gradlew testDebugUnitTest` 58 项全过；`assembleDebug` / `assembleDebugAndroidTest` 均通过
- 新增 `QuietNotificationTest.theCheckInChannelAsksToBeHeardAndRetiresTheSilentOne`：断言旧渠道被删除、新渠道 `IMPORTANCE_HIGH` + 有声 + 震动 + 锁屏保密
- ⚠️ 设备测试仍未跑（无真机/未启模拟器）

### 诚实说明：这未必是全部原因
沉浸式全屏是否吞掉横幅，取决于 ROM。三星还有「游戏助推器屏蔽通知」「通知弹出样式」等设置，代码层面碰不到。这一版做的是**排除掉可以确定的那一层**（哑渠道），并**让剩下的那层可观测**（`reach()` + 测试按钮）。用户按一次「试一下」，就能知道该往哪边查。

---

## 4. 数据库变更 3 → 4

`MoodPromptState` 新增三列，用于把**重发**和**首次送达**分开记录（`MoodCheckpoint.notifiedUtc` 保持首次送达语义，是导出证据）：

```kotlin
val lastNotifiedUtc: Long? = null
@ColumnInfo(defaultValue = "0") val notifyCount: Int = 0
val notifiedPackage: String? = null
```

- `MIGRATION_3_4` 已写，已在 `PhoneMoodApp` 和 `MigrationTest` 注册
- `app/schemas/…/4.json` 已生成（未提交，untracked）
- `MigrationTest.versionThreeFactsSurviveTheRepeatDeliveryColumns` 覆盖 3→4

⚠️ **用户手机上是 v3 数据**。装新包会触发迁移。装之前建议先在 App 内导出一份 JSON 存档。

---

## 5. 验证状态

| 检查 | 结果 |
|---|---|
| JVM 单测 | 56 项全过 |
| 完整设备测试（模拟器 API 35） | 23/23 全过，**连跑两轮** |
| 覆盖安装保数据 | 已实测：1.4.0 → 新包，只有一个 `com.phonemood`，数据库哈希不变 |
| **反向验证（第一版）** | 把 1.4.0 逻辑放回去，新测试确实失败；改回即通过 |

**诚实说明**：中途出现过两次设备测试失败，两次挂的是**不同的测试**。我怀疑过后台服务抢窗口，但查模拟器进程列表是空的，该猜测不成立。我做了三件事——给 `OverlayTest` 里唯一一处不等界面就数窗口的断言补上 `device.wait`（先天竞态）、把轮询从 3s 放宽到 5s、清掉残留进程——之后两轮全过。**这只是「没复现」，不等于「已根除」。**

---

## 6. ⚠️ 未解之谜（最重要）

**用户装了第一版修复后，看视频时仍然一条通知都没有。**

按代码逻辑，第一版就应该在 checkpoint 时刻推一条有声通知（`IMPORTANCE_HIGH` + `setSilent(false)`）。用户说完全没有。这说明**可能还有一层没被发现的问题**，第二版的重发和切换补推也救不了。

待排除的假设，按可能性排序：

1. **监测服务根本没在跑** — 三星杀后台很凶；`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 有申请，但用户未必授予
2. **通知渠道被拦** — `canPrompt()` 只检查 `areNotificationsEnabled()` 和 channel importance，拦不住 ROM 级别的通知管理
3. **沉浸式全屏吞掉 heads-up** — 通知已 post（`notifiedUtc` 会有值）但用户看不见，状态栏是隐藏的
4. **UsageStats 没在记** — 使用记录访问权限被撤销，或 ROM 限制
5. **checkpoint 压根没生成** — 会话统计有问题

**第 1、3 项可以靠数据库直接区分**：`notifiedUtc` 有值 = 通知发出去了（问题在可见性）；为空 = 根本没发（问题在服务或权限）。

---

## 7. 下一步

### 7.1 取证（当前卡点）

`scripts/diagnose_device.sh` 已写好，一次性取回：App 版本、服务存活状态、四项权限实际值、通知渠道状态、standby 分组、完整数据库副本。

**卡在手机连不上 adb。** 用户手机是三星（设备序列号已从文档中移除），USB 上可见，但只暴露 PTP(`0x06`) 和 CDC(`0x02`/`0x0A`) 接口，**没有 ADB 的 `0xFF/0x42/0x01`**，所以 `adb devices` 连 `unauthorized` 都不显示。

需要用户操作：
1. 设置 → 开发者选项 → **USB 调试**打开（开发者模式打开 ≠ USB 调试打开）
2. 设置 → 安全和隐私 → **Auto Blocker 关闭**（One UI 6.1+ 会明确拦截 USB 命令，症状完全吻合）
3. USB 模式从**传输图片(PTP)** 改为**传输文件(MTP)**
4. 手机上弹「是否允许 USB 调试」→ 允许

备选取证方式（不需要 adb）：让用户在 App 的分析页**导出当天 JSON**，里面有 `notification_delivered_at`、`response_status`、`floating_card.last_error`、`monitoring_gaps`，足以区分上面第 1/3 项假设。

### 7.2 拿到数据之后

按 §6 的假设树走。若确认是「服务被杀」，需要考虑保活策略；若是「通知不可见」，需要考虑全屏场景下的替代通道。

---

## 8. 待用户决策

1. **Play 迁移**：`com.phonemood.app` 已上传 Play，包名永久锁定。将来用户从 Play 装会再次变成第二个 App，且 release 包不可调试，`run-as` 迁移路子会失效。**App 需要一个数据导入功能**（现在只有导出）。`scripts/migrate_app_data.sh` 只在两边都是 debug 包时有效。
2. **重发节奏**：现在是 5 分钟 / 最多 3 次。多个 checkpoint 同时挂起时可能偏吵，未与用户确认过。
3. **一个未处理的老问题**：在悬浮卡上点 × 关闭后，通知仍会照发。1.3.x 就存在，不属于本次回归，改动会牵涉「关闭」的语义。

---

## 9. 文件清单

### 本次改动（全部未提交）

| 文件 | 改了什么 |
|---|---|
| `mood/OverlayPolicy.kt` | 核心：`action()` / `represent()` / `answerableUntil()` 纯函数 |
| `mood/MoodNotificationManager.kt` | 同 App 不再吞通知；重发；切换补推 |
| `mood/MoodOverlayController.kt` | 撤销延迟上限；被遮挡时不记送达证据 |
| `monitoring/PollingPolicy.kt` | `PENDING_PROMPT_MS = 5s` |
| `monitoring/UsageMonitorService.kt` | 传 `promptPending`；用 `lastResume()` |
| `data/Database.kt` | schema 3→4；`lastResume()`；`pendingCheckpoints()` |
| `PhoneMoodApp.kt` | 注册 `MIGRATION_3_4` |
| `build.gradle.kts` | `-PphonemoodApplicationId` 开关 |
| `test/…/OverlayPolicyTest.kt` | 13 项策略测试 |
| `androidTest/…/QuietNotificationTest.kt` | 同 App 送达 + 切换补推 |
| `androidTest/…/OverlayTest.kt` | 遮挡取证 + 修一处竞态 |
| `androidTest/…/MigrationTest.kt` | 3→4 升级测试 |
| `schemas/…/4.json` | 新增，untracked |
| `scripts/migrate_app_data.sh` | 新增，包名迁移 |
| `scripts/diagnose_device.sh` | 新增，真机取证 |

### 非本次改动（进入本次会话前就已未提交）

`README.md` · `README.zh-CN.md` · `.gitignore` · `analysis/PeriodDataset.kt` · `analysis/PeriodExport.kt` · `analysis/StatisticalEngine.kt` · `ui/AnalysisScreen.kt` · `ui/PeriodMoodSummary.kt` · `res/values*/analysis.xml` · `test/…/AnalysisTest.kt` · `androidTest/…/AnalysisFeatureTest.kt` · `docs/` 下若干

⚠️ `build.gradle.kts` 是混合的：`applicationId` 改 `com.phonemood.app`、`compileSdk 36`、签名配置是进入会话前就有的；只有 `-PphonemoodApplicationId` 开关是本次加的。

---

## 10. 常用命令

```bash
# 侧载测试包（原地更新用户手机上的 com.phonemood）
./gradlew assembleDebug -PphonemoodApplicationId=com.phonemood
# 产物：app/build/outputs/apk/debug/app-debug.apk
# 已复制到：dist/PhoneMood-1.4.4-checkin-fix2-debug.apk（64MB，超过 30MiB 无法直接发送）

./gradlew testDebugUnitTest                                        # 56 项
./gradlew connectedDebugAndroidTest -PphonemoodApplicationId=com.phonemood   # 23 项

ADB=~/Library/Android/sdk/platform-tools/adb   # adb 不在 PATH 上
scripts/diagnose_device.sh                     # 真机取证
```
