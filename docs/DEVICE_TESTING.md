# Device validation

Use an emulator or a spare Android 10+ device. Never substitute sample data for real records in production screens.

- Fresh install: Today shows zero usage and setup; no monitor starts before explicit user action.
- Usage Access grant: return to app, start monitoring, confirm foreground notification.
- Notification permission: deny, confirm tracking continues with permission guidance; grant and verify a later prompt.
- Use another app for an active interval. Verify the mood notification, tap a score, and confirm it survives activity/process recreation. Double tap should not create a second response.
- Switch between two apps, the launcher, keyboard, and PhoneMood; verify excluded time is omitted.
- Lock for two minutes: active time excludes the break and the session continues. Lock for five minutes or longer: a new session starts.
- Kill the process without force-stop; reopen and verify replay adds no duplicate checkpoints or usage. Reboot after starting and after pausing; only the enabled case may resume.
- Revoke Usage Access mid-session; verify status and report gaps. Restore it; verify no continuous usage is inferred through the gap.
- Revoke notifications or mute the check-in channel; verify quality notes and no crash.
- Export today, open the file in Downloads, parse JSON, and compare timeline duration sums with daily summary and app totals.
- Rebuild the same report repeatedly; verify one canonical filename and no truncated JSON. Interrupt publication; retry and check recovery.
- Submit a response shortly after midnight for a pre-midnight prompt; verify the previous day's file is rebuilt.
- Test a timezone change: real-time segments retain their zone; backfilled offsets are marked inferred.
- Test large font settings, TalkBack, narrow and landscape screens. All icon-only actions have descriptions.
- Check battery consumption over at least a full day on the target manufacturer's device. Long-term replay cost requires profiling before extended deployment.

A debug build is not a Google Play release. Distribution requires release signing and review of the special-use foreground-service declaration.

## Floating cards (1.1)

- Allow Display over other apps; run Preview in 5 seconds and switch to Chrome. Verify the card appears without opening PhoneMood and all ten buttons can be tapped.
- Leave the overlay permission off: ordinary notifications and the rating Activity remain usable.
- At a real checkpoint, tap a score directly on the overlay; verify the response, notification cancellation, and export metadata.
- Tap Later, restart the process, and verify no early reappearance. After one minute it can return while within the five-minute prompt lifetime.
- Dismiss a card and restart: that checkpoint must not automatically reopen a card.
- Lock the phone, revoke overlay permission, disable floating cards, or stop monitoring while a card is visible; verify it is removed and no crash occurs.
- Confirm the area outside the card still accepts touch, and check landscape, large fonts, screen rotation, and split-screen on your target phone.
