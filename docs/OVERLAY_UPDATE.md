# PhoneMood 1.1 — Floating check-ins

## 手机上怎么开启

1. 安装 `PhoneMood-1.1.0-debug.apk`。可以覆盖安装原来的 1.0.0，保留原有记录；不要先卸载旧版。
2. 打开 PhoneMood → Settings → **FLOATING CHECK-INS**。
3. 点击 **Display over other apps → Allow**，在 Android 设置中允许 PhoneMood 显示在其他应用上层。部分手机会先显示应用列表，需要再选择 PhoneMood。
4. 保持 **Show over other apps** 开启。
5. 点 **Preview in 5 seconds**，立即切换到 Chrome 等 App。约 5 秒后出现预览卡片；预览分数不会写入记录，20 秒后自动收起。
6. 正式使用时，开启 Usage Access 和 Start monitoring。达到有效使用时长后，直接在浮层上点 1–10 分，保存后卡片消失。**Later** 延迟一分钟；**×** 关闭本次卡片，通知仍可用于稍后回答。

## Behavior

- A bounded `TYPE_APPLICATION_OVERLAY` window appears over ordinary apps. It does not launch a rating Activity or replace the current task.
- The window is touchable inside the card, non-focusable, and non-modal. Touches outside its bounds remain available to the app underneath.
- A score is saved using the same transactional, first-response-wins repository as the existing rating Activity. UI buttons disable while saving; errors retain the card for retry.
- Snooze and dismissal persist in Room's new `MoodPromptState` table. Polling and controller recreation do not undo a dismissal. A snooze resumes at its stored deadline, subject to the checkpoint's five-minute lifetime.
- Screen-off immediately removes the service's card. Cards cannot be attached on a locked/non-interactive phone. Service destruction, answer, dismissal, or expiry also removes the window.
- One card is shown at a time. No overlay permission, disabled floating cards, or a failed attachment retains the normal notification path. When a real card is attached, its backup notification is requested silently.
- Preview runs as a short foreground-service operation and never creates synthetic checkpoints or mood responses. It also works while usage monitoring is paused.
- The foreground service can be temporarily visible for up to approximately 30 seconds during a preview, even when monitoring is paused.

## Upgrade and data

Version code is 2; version name is 1.1.0. Room migration 1→2 adds a single table and preserves existing tables. No destructive migration fallback is used. DataStore adds an `overlayEnabled` preference; configuration changes are audited in Room. Changing this presentation preference does not reset or interrupt a usage session.

Reports retain schema version 1.0 and add the optional `floating_card` object in mood contexts, containing first attachment time, dismissal, snooze deadline, and last attachment error. An attachment timestamp does not prove the user saw the card. Preview data is not included in exports.

## Verification

- Build and Android lint completed successfully, with zero lint errors; nonfatal dependency/version, URI convenience, and untranslated native-view text notices remain.
- 21 JVM tests passed: the original 16 plus five overlay eligibility/snooze/expiry tests.
- 13 instrumented tests passed on Android 15/API 35: six storage/export/configuration checks, six actual overlay UI checks, and one old-schema migration check.
- Overlay UI checks cover clicking a score while another app is foreground, one-window behavior, dismissal across controller recreation, snooze resumption, preview without records, permission revocation, paused monitoring, and locked-screen suppression.
- An actual 1.0.0 installation was upgraded in place: its 41 seconds of recorded usage and paused state remained present.
- In Chrome, Settings → Preview in 5 seconds displayed the real overlay above `about:blank`; the browser remained underneath. See [actual screenshot](screenshots/overlay-chrome-preview.png). The screenshot is labeled PREVIEW because it uses the production preview feature, not fabricated usage or mood history.

## Platform limits

Android can suppress overlays on secure surfaces, and certain apps explicitly hide them. No claim is made to cover lock screens, permission dialogs, the keyboard, or every system window. Notification fallback is retained. The card does not use accessibility-service privileges, background Activity launches, or full-screen-intent permissions.

UsageStats continues to attribute time to the underlying foreground app while a floating card is open; a floating window is not an Activity transition. Timing remains best-effort with the existing ten-second monitoring poll. Manufacturer-specific battery behavior and older/newer Android device versions still need physical-device validation.

References: [Android application overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY), [overlay permission](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)), [secure screens that hide overlays](https://developer.android.com/security/fraud-prevention/activities).
