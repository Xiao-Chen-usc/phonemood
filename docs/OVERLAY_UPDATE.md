# PhoneMood 1.1 — Floating check-ins

*Chinese version: [OVERLAY_UPDATE.zh-CN.md](OVERLAY_UPDATE.zh-CN.md)*

## Turning it on, on the phone

1. Install `PhoneMood-1.1.0-debug.apk`. It installs over 1.0.0 and keeps existing records — do
   not uninstall the old version first.
2. Open PhoneMood → Settings → **FLOATING CHECK-INS**.
3. Tap **Display over other apps → Allow** and permit PhoneMood to draw over other apps in
   Android's settings. Some phones show a list of applications first, in which case PhoneMood has
   to be selected from it.
4. Leave **Show over other apps** enabled.
5. Tap **Preview in 5 seconds** and immediately switch to Chrome or another app. The preview card
   appears after about five seconds. A preview score is never written to the records, and the card
   retracts automatically after 20 seconds.
6. For real use, enable Usage Access and Start monitoring. Once enough active use has accumulated,
   a score of 1–10 can be tapped straight on the floating card; the card disappears once saved.
   **Later** defers by one minute. **×** closes this card, leaving the notification available for
   answering later.

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
