# PhoneMood 1.3: Automatic Start and Guided Permission Setup

*Chinese version: [AUTOMATIC_SETUP.zh-CN.md](AUTOMATIC_SETUP.zh-CN.md)*

Install `dist/PhoneMood-1.3.0-debug.apk`. It installs over the previous version and keeps
existing data.

The home screen and Settings gained a "Quick permission setup" action. One tap walks through the
permissions that are still missing, in order: Usage Access, notifications, floating cards, and
the battery optimization exemption. Each one still has to be confirmed on Android's own system
page. Declining one does not force a repeated prompt; setup can be run again later.

The first time Usage Access is granted and the user returns to PhoneMood, tracking starts
immediately — there is no longer a separate "Start monitoring" tap. After that, switching apps
keeps tracking alive through a foreground service with a persistent notification, and a device
restart attempts to restore tracking that was previously enabled. A manual pause is preserved and
is **not** undone by reopening the app. A system "Force stop", and some manufacturers'
background restrictions, can still stop tracking; recovering from that needs the app to be opened
again or the manufacturer's auto-start setting to be adjusted.

The other entries in the overlay permission list are other applications, not multiple PhoneMood
permissions. Selecting PhoneMood and enabling its single switch is enough to draw a card above
other apps that permit overlays. On Android 11 and later, the standard permission entry point may
show the full application list; this does not let an app grant the permission on the user's
behalf. Reference: [Android permission notes](https://developer.android.com/about/versions/11/privacy/permissions).

Verification for this change: the APK build, unit tests and lint pass. New database tests cover
that tracking does not start without permission, that it starts exactly once after permission is
granted, and that a manual pause is preserved. The full guided-permission flow was walked through
on an Android 15 emulator: "Recording" appeared without any "Start monitoring" tap, the service
remained a foreground service after switching to Chrome, and the battery optimization exemption
took effect.

Screenshot: `screenshots/quick-setup-1.3.png`.
