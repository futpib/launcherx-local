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
8. Enable `com.google.android.apps.tv.launcherx/.home.HomeActivity` with root,
   force-stop the launcher, and launch that activity. The launcher itself
   disables its alternative VanillaMode component on this path.

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


## Setup and compatibility tests

The fixture must have LauncherX Local enabled in LSPosed and root granted to
LauncherX Local through Magisk's Superuser UI. Run:

```sh
python3 scripts/setup_smoke.py
python3 scripts/smoke.py --reboot
```

The setup test uses DPAD to restore Basic TV and enable local Home. It also
builds isolated negative variants with an incorrect launcher version and DEX
hash, installs them only in the guarded emulator, and verifies that setup
refuses to switch Home. A `finally` block restores the normal module APK.
The source compatibility manifest and original launcher APK remain unchanged.
The negative builds also exercise building without a proprietary launcher APK.

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

A disabled-framework-module test was performed separately through LSPosed's UI:
with LauncherX Local disabled, Enable refused to change Basic TV; re-enabling
only Google TV scope allowed Enable to restart the launcher and activate Home.
A denied Magisk root request also left Home unchanged.

UIAutomator can disturb this launcher's accessibility focus. On the physical TV,
a ViewRootImpl accessibility-focus exception occurred before this patch was
activated. Preserve the pre-activation log separately from final smoke results;
use screenshots and activity state during DPAD navigation.
