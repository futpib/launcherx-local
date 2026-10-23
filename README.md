# LauncherX Local

Use the official Google TV Home as a local app launcher without a Google account.
The original app tiles, remote navigation, app launching and quick settings stay
inside Google's launcher. The launcher APK is not modified or re-signed.

This is an **LSPosed module APK**, hosted by Magisk and Zygisk. It is not a ZIP to
flash directly in Magisk. The setup app has TV remote controls for enabling local
Home and restoring Basic TV. No ADB commands are needed for normal setup.

## Compatibility

- Google TV package `com.google.android.apps.tv.launcherx`, version
  **1.0.785572138 / 702401**, with the exact executable hashes in
  [`compatibility.json`](compatibility.json).
- Tested on a Xiaomi **MiTV-MOEU0**, Android 14, ARM32, and an isolated Google TV
  Android 14 emulator running the same original launcher APK.
- Tested framework: **Magisk 30.7**, Zygisk enabled, **LSPosed 1.9.2 / 7024**.
- Owner user only, with no Google account visible to the launcher.

Other launcher versions and device/framework combinations are not yet supported
by evidence from this project. An unfamiliar version or DEX hash is refused
before hooks are installed or setup switches Home. Keep launcher updates in mind:
a new launcher build needs a new compatibility review, not just an edited version
number. Restore Basic TV remains available when the launcher is incompatible.

## Install

1. Start with an already rooted TV. In Magisk Settings, enable **Zygisk**.
2. Download the official [LSPosed 1.9.2 Zygisk ZIP](https://github.com/LSPosed/LSPosed/releases/tag/v1.9.2).
   In Magisk, choose **Modules → Install from storage**, select the ZIP, and reboot.
3. Open LSPosed and confirm it says **Activated**. Its notification opens the
   manager. If TV notifications are inconvenient, install `manager.apk` extracted
   from that same ZIP and open the installed LSPosed manager.
4. Install `launcherx-local.apk` as a regular Android app. Allow installation from
   your chosen file manager in Android Settings when prompted.
5. In LSPosed's **Modules** page, enable **LauncherX Local**. Leave only its
   recommended **Google TV** scope checked. Do not select System Framework.
6. Open **LauncherX Local**, select **Enable local Home**, and grant its Magisk
   root request. Setup checks the launcher, restarts it to load the module, checks
   the live hook and account state, and then opens local Home.

On TVs where Magisk's file picker silently closes, install
[Material Files from F-Droid](https://f-droid.org/packages/me.zhanghai.android.files/)
first. It supplies a working file picker. This was necessary on the tested Xiaomi
TV, whose built-in picker resolves to a stub. APKs and ZIPs can be transferred to
Downloads or obtained using a TV browser. Magisk and LSPosed's phone-oriented
interfaces may need a pointer; the LauncherX Local setup screen works with DPAD.

This follows the [standard LSPosed installation and module activation flow](https://github.com/LSPosed/LSPosed/wiki/How-to-use-it).
No framework databases, system files, or Google account records are edited.
Root is used by the setup app to switch and restart Home; the running hooks use
LSPosed. Root permission can be revoked afterward and granted again for rollback.

## Restore or uninstall

Open LauncherX Local and choose **Restore Basic TV**. This disables local Home,
restores the stock Basic TV activity, and restarts the launcher. The hooks become
inactive even while the module remains enabled in LSPosed.

Then disable LauncherX Local in LSPosed and uninstall its APK if desired. Restore
Basic TV **before uninstalling** the setup app. There is no launcher APK to restore
and no firmware flash is needed. Denied root access or an unavailable hook is
reported in the setup screen; enable does not switch Home in those cases.

## What works and what does not

The default build selects the launcher's built-in apps-only Home, uses its bundled
fallback destination, keeps `apps_channel`, removes the cloud-loading error panel,
and labels the existing pseudonymous profile **Local TV**. Every behavior change
requires local Home to be selected and the launcher's account view to contain zero
Google accounts. An account lookup error leaves the original behavior.

There is no forged Google account, credential injection, or signature-check
bypass. Personalized recommendations, purchases, Google account services, and the
Play Store's **Add apps** action still require Google authentication. This is not
a telemetry blocker; unrelated background Google-service requests may continue.
Signed-in account behavior has not been tested.

The `full` research build preserves the cloud feed; real requests returned
`UNAUTHENTICATED` and `PERMISSION_DENIED`. It is not the normal install artifact.

The [account-free Home feature inventory](docs/account-free-home.md) lists local
features, candidates for further work, and account-dependent services.

## Build and signing

Requires Python 3, a JDK, Android SDK platform 34 and build-tools 37.0.0.
`ANDROID_HOME` defaults to `~/Android/Sdk`.

Place the compile-only Xposed API 82 JAR at `artifacts/xposed-api-82.jar`.
The tested copy came from [this Maven mirror](https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar)
when the original repository was unavailable. The build verifies SHA-256
`f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`.
The JAR and proprietary launcher are not bundled in the output.

```sh
python3 scripts/build.py
```

Outputs: `build/apps/launcherx-local.apk`, `SHA256SUMS`, and `receipt.json`.
A proprietary launcher APK is **not required** to build. If you have your own
copy, `--launcher /path/to/launcher.apk` verifies it against the recorded hashes.

Local builds use `.cache/local.keystore`. Keep that private key for compatible
updates; it is excluded from Git. For release signing, supply an existing key:

```sh
# Set LAUNCHERX_STORE_PASS and LAUNCHERX_KEY_PASS in your environment.
python3 scripts/build.py --keystore /private/release.p12 --key-alias release
```

Keep the same signing key for published updates. Do not commit signing keys or
passwords. Sources and artifacts are local; no repository or release is published
by these scripts.

## Validation

[`validation.json`](validation.json) records emulator results;
[`validation-tv.json`](validation-tv.json) records physical-device evidence.
The [lab guide](docs/lab.md) describes the guarded emulator, smoke tests, rollback,
negative compatibility tests, and the separate full-feed experiment.

The installation artifacts and full logs are excluded from Git. Normal installation
uses only the module APK and the separately obtained framework; never install the
emulator system overlay or its network-workaround research build on a TV.
