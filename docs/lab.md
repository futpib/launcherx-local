# Emulator lab

Run all commands from the repository root. These helpers refuse physical devices.

## Emulator fixture

Use a dedicated AVD named `launcherx_local14` with
`system-images;android-34;google-tv;x86` revision 3 and device `tv_1080p`.
The tested image is Android 14 build `UTT1.240131.001.F1/11825476`.
It supports the TV APK's ARM32 native libraries through `libndk_translation.so`.

The lab helpers use only `127.0.0.1:5587` (emulator console port 5586), with a
private ADB server on port 5038. Before shell operations they verify both
`ro.kernel.qemu=1` and the exact AVD name. They never select the physical TV.

The actual fixture setup was:

1. Create and boot the new AVD. Download the official
   [Magisk v30.7 APK and host patch script](https://github.com/topjohnwu/Magisk/tree/v30.7/scripts).
   Extract `lib/x86/libbusybox.so` as `busybox`. Push it, the APK as `magisk.apk`,
   `host_patch.sh`, and this AVD image's stock `ramdisk.img` to `/data/local/tmp`.
   Run `sh /data/local/tmp/host_patch.sh /data/local/tmp/ramdisk.img` as ADB shell,
   and pull `ramdisk.img.magisk` as `artifacts/magisk-ramdisk.img`.
2. Cold-start that AVD with the patched ramdisk, `-port 5586 -no-window -no-audio
   -no-snapshot -no-boot-anim -gpu swiftshader_indirect -memory 3072 -cores 4
   -writable-system -feature -Vulkan,-SystemAsRoot`. The original SDK ramdisk is
   untouched. `artifacts/emulator-process.json` records the actual command/PID.
3. Install the Magisk APK, complete its additional setup, grant Shell root in
   its Superuser UI, and enable Zygisk. Root is `/debug_ramdisk/su` in this image.
4. Install official [LSPosed 1.9.2 / 7024, Zygisk release](https://github.com/LSPosed/LSPosed/releases/tag/v1.9.2)
   through Magisk and reboot. ZIP SHA-256:
   `0ebc6bcb465d1c4b44b7220ab5f0252e6b4eb7fe43da74650476d2798bb29622`.
   Extract/install its `manager.apk` for the configuration UI.
5. The emulator's bundled launcher has a different signing certificate. Create
   an emulator-only Magisk module `launcherx_emulator_fixture` with an empty
   `system/product/priv-app/TVLauncherXPrebuilt/.replace` directory. Reboot and
   verify `pm path com.google.android.apps.tv.launcherx` returns no package.
   This lets PackageManager forget the original system package's identity.
6. Add the exact TV APK as
   `system/product/priv-app/TVLauncherXPrebuilt/TVLauncherXPrebuilt.apk` in that
   module, together with a product `privapp-permissions` XML for the permissions
   requested by this APK. Set directories 755, files 644, and system-file labels;
   reboot. Install the **same signed APK** as a data update with
   `pm install -r --abi armeabi-v7a /data/local/tmp/launcher-original.apk`.
   Verify version, original APK hash, and `primaryCpuAbi=armeabi-v7a`.
7. Set `device_provisioned`, `user_setup_complete`, and `tv_user_setup_complete`
   to 1, without adding an account. Install the built module APK. In LSPosed,
   enable **LauncherX Local** with only **Google TV** selected (user 0).
8. Restart Google TV and press Home. Starting with 0.6, the verified module
   selects Home automatically, including when the previous selection was Basic TV.
   No separate companion-app activation or root grant is needed for that switch.

These fixture operations are specific to the isolated emulator. Do not copy the
emulator system overlay to the TV.

Vector 2.2 / 3080 was also tried: its injection crashed `system_server` on this
32-bit emulator before the patch ran. It is disabled in the fixture. Repeated
reboots then left ARM translation disabled after Zygisk's crash rollback.
The stable test used LSPosed 1.9.2 with no native-bridge property override.
This is an emulator compatibility finding, not a claim about Vector on ARM TVs.

## Real UI smoke test

Install the already-downloaded TV Bro 2.1.6 generic/WebView APK into the emulator.
The tested file SHA-256 is
`d8634edfe8d94b4fb9a52005d68a090a7f1e85b7f39c2cf01a25dc9dd60942b2`.
Then, with the module enabled and scoped as above:

```sh
python3 scripts/build.py
python3 scripts/smoke.py --reboot
```

The script installs the default module, verifies zero accounts and the clean
Home UI, navigates to TV Bro using DPAD, checks that Google TV launched it,
returns with Home, checks a process restart, and optionally checks a reboot.
Screenshots, hierarchy dumps, activity records, logs, and the exact output APK
hash are saved in `artifacts/smoke-*`.

**Do not put UIAutomator dumps between DPAD keys.** Attaching its accessibility
service resets this launcher's focus and exits its arrange-apps UI. The test
uses screenshots and ActivityManager records during the actual remote sequence,
then inspects the UI separately. No navigation hooks are needed or shipped.

The full-guide server experiment used:

```sh
python3 scripts/build.py --mode full --emulator-network
```

That separate fixture option selects the launcher's existing Android HttpEngine
transport: an ARM app cannot load the x86 Cronet library supplied by emulator
Play Services. It makes real network requests and does not supply responses or
credentials. It refuses to load outside this named emulator. The default apps
build and its UI smoke test do **not** enable this workaround.


## Activation, profiles and compatibility tests

For the native star/YouTube remote receivers, shortcut storage and app-launch
broker, run `python3 scripts/remote_smoke.py`. The AVD lacks Xiaomi's global-key
overlay, so a root helper injects KeyEvent parcelables into the real receivers;
the chooser is operated with DPAD. See [remote test boundaries](remote-buttons.md).

With Pillow and Tesseract installed, test independent assignments and default
restoration for all six keycodes by running
`python3 scripts/all_shortcuts_smoke.py`. The root helper also sends ordered OEM
events so the actual Google receiver's acknowledgement path executes. It cannot
receive ordered result callbacks from `app_process` (there is no registered app
record), so acknowledgement is observed in the receiver's log. This exercises
Google's side of the protocol; the emulator does not contain Xiaomi's service.
The test checks native DPAD saving, per-key independence, process persistence,
reset confirmation/cancellation, all-button reset and cancelled key-up, then
restores the original native shortcut store. Xiaomi forwarding and original
OEM default launches still require the physical TV.

Run `python3 scripts/key_actions_smoke.py` for key bindings. Its media-session
fixture plays a silent AudioTrack so Android selects it as a media-button
recipient; its activity records arbitrary window keys. The test drives the
native chooser, module root broker and real input-injection helper, then
restores the shortcut store and uninstalls the fixture.

The fixture must have LauncherX Local enabled in LSPosed. The companion app
needs a Magisk root grant for **Choose app content** and non-media key actions.
Run:

```sh
python3 scripts/setup_smoke.py
python3 scripts/profile_smoke.py
python3 scripts/smoke.py
```

The setup test verifies automatic activation from Basic TV without opening the
companion app, then uses the real LSPosed UI to disable the module, check the stock
signed-out screen, re-enable it and check automatic Home activation again. It
asserts the companion app has only Choose app content and passive status text.
Negative variants with incorrect version and DEX metadata must refuse hooks,
activation and channel changes. A `finally` block restores the normal APK and
enabled module. These variants also exercise builds without a proprietary APK.

The profile test uses Tesseract, screenshots and DPAD to select Incognito, open
Google's actual sign-in screen, cancel, restart Home and reboot. It asserts zero
accounts throughout. It does not complete authentication or test switching among
real Google profiles. See [profile test boundaries](profiles.md).

For the local-content features, run `python3 scripts/content_smoke.py`. Its
separate publisher inserts real Watch Next and preview-channel entries, exercises
the setup chooser and native DPAD playback links, and removes its records and
package afterward. It temporarily blocks only the launcher's network traffic
inside the emulator. See [local-content test notes](local-content.md).

For the store picker, download the unmodified store APKs to
`artifacts/market-fdroid.apk` from `https://f-droid.org/F-Droid.apk` and
`artifacts/market-aurora.apk` from
`https://f-droid.org/repo/com.aurora.store_76.apk`. Start with only Google Play
registered as an app store in the emulator, then run:

```sh
python3 scripts/market_smoke.py
```

This uses `tesseract` for screenshot text checks. The test builds a separate
fixture with multiple `APP_MARKET` entry points, then checks real store APKs.
It temporarily disables only emulator Google Play for the empty-list case and
restores its exact enabled state in `finally`. Test packages are uninstalled and
the initial handler list is checked after cleanup. The physical TV is never
selected. See [store-picker test notes](store-picker.md).

UIAutomator can disturb this launcher's accessibility focus. On the physical TV,
a ViewRootImpl accessibility-focus exception occurred before this patch was
activated. Preserve the pre-activation log separately from final smoke results;
use screenshots and activity state during DPAD navigation.
