# LauncherX Local

A version-specific Xposed module that opens the official Google TV Home UI
without a Google account. The default build uses the launcher's own local
"Your apps" channel. Its original app tiles, remote navigation, app launching,
reorder UI, and quick settings remain in the Google TV process.

Target: `com.google.android.apps.tv.launcherx`, version **1.0.785572138 / 702401**.
The APK remains original and signed by its original signer. The module changes
Java behavior in memory through a Magisk-hosted Xposed framework.

This was tested in an isolated Google TV Android 14 emulator. It has **not been
deployed to the physical TV**. Real Google-account behavior and physical-device
compatibility remain untested.

## What the patch does

Every behavior change requires the launcher's AccountManager view to contain
zero `com.google` accounts. An account lookup error leaves original behavior.
Version and all top-level DEX hashes must match before hooks are installed.

- Select the adult Home theme and `GuideHomeFragment` for a pseudonymous profile.
- Select apps-only mode and stop the local no-account recovery to Basic TV.
- Supply the bundled fallback Home destination through the existing data source.
  The cached-result wrapper has the type and lifetime expected by that source.
- Keep its `apps_channel` and omit its cloud-loading error channel.
- Rename the existing pseudonymous profile to "Local TV" in the Home header.
  Account identifiers and account type remain unchanged.

There is no replacement launcher, forged Google account, credential injection,
or signature-check bypass. Disabling the module removes the runtime changes.
The stock launcher can still make unrelated background Google-service requests;
this is not a telemetry-blocking module.

The `full` research build opens the full guide and preserves its cloud-loading
behavior. Real requests returned `UNAUTHENTICATED` and `PERMISSION_DENIED`.
Personalized feeds, purchases, Google account services, and the Play Store's
"Add apps" path are not made account-free by this patch.

## Build

Requires Python 3, a JDK, Android SDK platform 34 and build-tools 37.0.0.
`ANDROID_HOME` defaults to `~/Android/Sdk`.

Put these inputs in `artifacts/` (not committed):

- `launcher-original.apk`: the matching APK from
  `/product/priv-app/TVLauncherXPrebuilt/TVLauncherXPrebuilt.apk`.
  SHA-256 `3187adb10afe8c242d1b67d3d4f16fb77f600a0d72686339a4a3ff75db882ab5`.
- `xposed-api-82.jar`: compile-only Xposed API 82.
  SHA-256 `f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`.
  The original repository was unavailable; the tested copy came from
  [this Maven mirror](https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar).
  It is not bundled in the output APK.

```sh
python3 scripts/build.py
```

Output: `build/apps/launcherx-local.apk` and `receipt.json`.
A local development signing key is created in `.cache/`; keep that key for
subsequent module updates. The module's key is unrelated to the launcher key.

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

## Rollback

Disable LauncherX Local in LSPosed and force-stop the launcher. For the original
Basic TV entry point, enable `.home.VanillaModeHomeActivity`, disable
`.home.HomeActivity`, and start VanillaModeHomeActivity with root. Re-enable the
module and HomeActivity to return to the experiment. No launcher APK restore or
firmware flash is necessary.

The source and build outputs are local; nothing is pushed or deployed to the TV.

## Recorded validation

The final default apps build passed `python3 scripts/smoke.py --reboot` on
2026-10-23. Tested APK SHA-256:
`1d64b6ee7eed7ae97afcccec4379f19d3e86864970e00356ca2be43d2d5f12d1`.

- Zero accounts before and after reboot; original launcher APK hash unchanged.
- Official clean Home, including the sideloaded TV Bro tile.
- Native DPAD navigation launched TV Bro from Google TV; Home returned correctly.
- Launcher process restart and emulator reboot returned to patched Home.
- Disabling the module and restoring VanillaModeHomeActivity returned to the
  stock account-setup prompt; re-enabling passed the full smoke test again.
- The original arrange-apps UI opened. Persistent reorder changes were not tested.
- No `AndroidRuntime` fatal exception in the final smoke run. Unrelated background
  cloud-service errors remain possible, including the documented emulator Cronet
  ABI mismatch in the default build.

`validation.json` records the result and local evidence paths. The APKs, logs,
large SDK assets, and screenshots are excluded from Git.
