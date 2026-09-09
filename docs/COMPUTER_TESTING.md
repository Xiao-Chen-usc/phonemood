# Testing PhoneMood on a Computer

*Chinese version: [COMPUTER_TESTING.zh-CN.md](COMPUTER_TESTING.zh-CN.md)*

The project ships an Android emulator configuration named `PhoneMood_API35`. On the development
Mac, the simplest route is to double-click [在电脑上测试.command](../在电脑上测试.command) in the
project root. It boots a visible Android emulator, installs the latest debug APK and opens
PhoneMood; existing emulator data is preserved.

The first boot can take a minute or two. Once the emulator is up, PhoneMood's own prompts walk
through granting Usage Access, notifications and "display over other apps". To test the
cross-application floating card, open Settings, turn on floating cards, tap "Preview in 5
seconds", and switch to Chrome or any other app to see the card appear.

The same thing from a terminal:

```bash
cd "/Users/chenlin/Desktop/reminder/ADHD手机拯救计划"
./scripts/build.sh :app:assembleDebug
./scripts/run-emulator.sh
```

To install an APK that has already been built, drag
[PhoneMood-1.2.0-debug.apk](../dist/PhoneMood-1.2.0-debug.apk) onto the emulator window, or run
the launch script again.

**The app's language follows the emulator's system language, not the Mac's.** To change it, open
the emulator's Settings → System → Languages → System languages and pick Simplified Chinese or
English, then reopen PhoneMood. The home screen, Settings, the mood rating screen, the floating
card and the notifications all follow that choice; any other system language falls back to
English. The app has no separate language switch of its own.

Permission behavior on an emulator can differ from a physical phone. The emulator is well suited
to checking the interface, language switching, the floating card and the data flow. A physical
phone is still needed to check manufacturer battery restrictions and lock-screen behavior.
