# Xiaomi volume gesture

Version 0.15 adds **Disable double volume-down mute** to the companion app.
It is off by default. Select **System Framework** in the module's LSPosed scope,
reboot, then enable the switch. Later changes apply immediately and survive reboot.
Turning it off restores Xiaomi's gesture. Disabling the whole module and rebooting
also restores the original handler.

This option is independent of Google TV Home compatibility, local content,
app-button assignments and key injection. Those features do not require it or
its System Framework scope. The volume option needs neither the Google TV nor
TvService scopes, and does not request a companion-app superuser grant.

The hook skips only `com.android.server.audio.MitvAudioService.JudgeMuteTimer()`
when enabled. It leaves normal volume changes, the explicit mute key and Xiaomi's
separate hold-to-mute behavior in place. The setting is stored as
`launcherx_disable_double_volume_down_mute` in Android's global settings;
the signature-protected module receiver changes the setting and the live hook
together. Unsupported firmware leaves the original audio behavior intact.

The reviewed target is Xiaomi `ladybird` (MiTV-MOEU0), Android 14, with
`/system/framework/services.jar` SHA-256
`d09f1610b9521b695a74f1703054e0adfd142ff2f5d94fd756ab29a31b4d57ca`.
The hook checks that hash and the target method's signature before registering.
Additional firmware versions need a separate review.

## Verification

The physical TV reproduced stock double-tap muting before the change. At the
user's request, patch verification uses the isolated `launcherx_local14` emulator;
the updated System Framework hook has not been loaded on the physical TV.
The [gesture checks](../validation-volume-gesture.json) and existing
[setup and key-action regressions](../validation-no-superuser.json) passed.

The private fixture extracts the two original Xiaomi audio classes from the
reviewed firmware. It relocates their type names, adapts their audio calls to the
emulator's actual AOSP AudioService, and omits Xiaomi analytics. Gesture
instructions and timer code remain original. The fixture compiles the production
`SystemAudio` and `HookRequest` sources unchanged and directly invokes registration
after an emulator-only guard. The release APK retains its firmware checks.
Proprietary bytecode and the fixture APK stay in ignored build directories.

Build and run with a privately obtained matching firmware JAR:

```sh
python scripts/build.py
python scripts/verify_apk.py
python scripts/build_volume_fixture.py --services /path/to/services.jar
python scripts/volume_gesture_smoke.py
```

The smoke test operates only on the repository's guarded AVD. It installs the
fixture, enables its System Framework scope through LSPosed, and tests stock mute,
the companion switch in both positions, normal volume keys, repeated presses,
explicit mute/unmute, persistence and unauthorized callers. It removes its
fixtures, reboots and restores the prior volume, mute and setting afterward.
This exercises real ART hooks, Android input dispatch, audio state and the setup
UI in the emulator; it does not establish physical remote or TV deployment behavior.
