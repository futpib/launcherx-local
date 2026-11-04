# Cast freezer exemption

Version 0.19 uses LSPosed's **System Framework** scope to exempt the owner's
system Google Cast receiver (`com.google.android.apps.mediashell`) from Android
14's cached-app freezer. Other apps retain their original freezer policy.
Reboot after installing the update or changing scope.

The hook runs independently of Home's APK version and compatibility checks.
It sets Android's existing `ProcessCachedOptimizerRecord.setFreezeExempt(true)`
after the main Cast `ProcessRecord` is constructed, before the receiver starts.
Android creates a new record after process death, an app update or a reboot, so
the exemption is applied each time. There is no polling service, root grant for
the companion, Cast APK modification, or persistent global freezer override.
Normal low-memory process termination still applies; the restarted receiver
receives the exemption again.

The hook checks Android SDK 34, the framework constructor and field/method
types, the exact package and main process names, the system-app flag, and the
owner UID. Isolated processes, secondary Android users and other packages are
unchanged. Other Android releases currently retain stock behavior. If the
framework layout changes, the hook logs a refusal under `LauncherXLocal`.

If the freezer was disabled globally as a workaround, restore its default after
installing, rebooting and confirming the receiver's exemption:

```sh
adb shell settings delete global cached_apps_freezer
```

`dumpsys activity settings` should show `use_freezer=true`. The main Cast process
in `dumpsys activity processes` should show `isFreezeExempt=true` and
`isFrozen=false`, including after a receiver restart. Other eligible processes
should still enter the freezer. Disabling System Framework scope and rebooting
removes the Cast exemption along with the other framework hooks.

This fixes process suspension, not network policy. Google TV's low-energy
standby can separately block Cast network traffic while the screen is off.
The module does not change that policy or keep the display awake.

## Validation

The [emulator checks](../validation-cast-freezer.json) exercise real Android 14
System Framework hooks: Cast stays unfrozen while cached, an unrelated cached
app freezes automatically, and a restarted receiver gets a fresh exemption.
The test temporarily removes Assistant's foreground binding and shortens service
inactivity to reach cached state, then restores both settings.

The [physical TV checks](../validation-tv-cast-freezer.json) cover installation,
reboot, native exemption state, receiver restart, restoration of the global
freezer, other frozen apps, LAN discovery, Cast endpoints, and returning to Home.
Physical Cast currently has active service importance; its cached-state test
was performed on the emulator. Phone playback and standby discovery are not
claimed by these checks.

The relevant Android 14 code is in
[ProcessRecord](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/am/ProcessRecord.java),
[OomAdjuster](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/am/OomAdjuster.java)
and [ProcessCachedOptimizerRecord](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/am/ProcessCachedOptimizerRecord.java).
