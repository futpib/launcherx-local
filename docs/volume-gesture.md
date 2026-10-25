# Xiaomi volume gesture

Select **System Framework** in LauncherX Local's LSPosed scope and reboot to stop
double volume-down from muting. Uncheck that scope and reboot to restore Xiaomi's
gesture. There is no companion toggle or saved volume preference in version 0.17.

Google TV Home, local content and Xiaomi app-button assignments work independently
through their own scopes. On Xiaomi, TvService also supplies key injection, so
those features do not depend on System Framework. The audio fix does not need the
Google TV or TvService scopes or a companion-app superuser grant.

The hook skips only `com.android.server.audio.MitvAudioService.JudgeMuteTimer()`.
Normal volume changes, the explicit mute key and the separate hold-to-mute
handler remain unchanged. Unsupported firmware keeps its original behavior.

The reviewed target is Xiaomi `ladybird` (MiTV-MOEU0), Android 14, with
`/system/framework/services.jar` SHA-256
`d09f1610b9521b695a74f1703054e0adfd142ff2f5d94fd756ab29a31b4d57ca`.
The hook checks that hash and the method signature before installation.

## Verification

Version 0.17 passed synthetic-input checks on the physical TV: double and triple
volume-down lower the volume without muting, and ordinary volume and explicit
mute/unmute still work. The shortened setup screen has no switches. Existing
button assignments and the original volume were preserved.

The [emulator checks](../validation-volume-gesture.json) compare original Xiaomi
gesture bytecode with and without the production hook. They cover double and
repeated presses, ordinary volume keys, explicit mute/unmute, reboot persistence,
the simplified setup screen and an ignored obsolete preference.

The private fixture extracts two Xiaomi classes from the reviewed firmware. It
relocates their type names, adapts audio calls to the emulator's AOSP AudioService,
and omits Xiaomi analytics. Gesture instructions and timer code remain original.
It compiles `SystemAudio` unchanged and directly invokes installation after an
emulator-only guard. A fixture-only setting selects stock or patched behavior at
boot; production has no such setting and retains all firmware checks.
Proprietary bytecode and the fixture APK stay in ignored build directories.

Build and run with a privately obtained matching firmware JAR:

```sh
python scripts/build.py
python scripts/verify_apk.py
python scripts/build_volume_fixture.py --services /path/to/services.jar
python scripts/volume_gesture_smoke.py
```

The test operates only on the guarded `launcherx_local14` AVD. It removes its
fixture, reboots and restores prior audio and test settings afterward. Physical-TV
deployment and synthetic-input results are recorded separately in
[TV validation](../validation-tv-volume-gesture.json); neither test establishes
Bluetooth/IR remote delivery or audible output.
