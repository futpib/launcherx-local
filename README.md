# LauncherX Local

Use the official Google TV Home without a Google account, with installed apps,
Continue watching, content rows published by TV apps, and an app-store picker.
The original app tiles, cards, remote navigation, app launching and quick settings
stay inside Google's launcher. The launcher APK is not modified or re-signed.

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

Physical-device validation covers the initial **0.2** setup, the **0.4** store
picker, and the **0.5** update that enables app channels by default. The complete
content-row suite runs in the isolated emulator. At the latest TV check,
SmartTube's channels were enabled but its local program tables were empty, so
third-party content cards and playback links remain unverified on that TV.

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

To update an existing installation, install the new APK signed with the same key,
then choose **Enable local Home** again. With Magisk and LSPosed already active,
this restarts only the launcher; a TV reboot is not needed.

On TVs where Magisk's file picker silently closes, install
[Material Files from F-Droid](https://f-droid.org/packages/me.zhanghai.android.files/)
first. It supplies a working file picker. This was necessary on the tested Xiaomi
TV, whose built-in picker resolves to a stub. APKs and ZIPs can be transferred to
Downloads or obtained using a TV browser. Magisk and LSPosed's phone-oriented
interfaces may need a pointer; the LauncherX Local setup screen works with DPAD.

This follows the [standard LSPosed installation and module activation flow](https://github.com/LSPosed/LSPosed/wiki/How-to-use-it).
No framework databases, system files, or Google account records are edited directly.
Root is used by the setup app to switch and restart Home and authorize changes
to app-channel visibility through Android's TV provider. Running hooks use
LSPosed. Root permission can be revoked afterward and granted again for setup.

## Local content

Open a compatible TV app and enable its Home recommendations or Watch Next
integration. **Continue watching** reads the app-published Watch Next entries,
ordered by most recent engagement, and shows native artwork, episode information,
and resume progress. Selecting a card opens the app's playback link with its
extras intact. The app is responsible for resuming playback and updating entries.

App content channels are **enabled by default** when Home first discovers them,
including existing channels on the first run of version **0.5**. New channels
published later are enabled automatically too. To hide a row, open LauncherX
Local, choose **Choose app content**, uncheck the channel, and press **Save**.
This works with the remote. Your later visibility choices survive refreshes,
launcher restarts and reboots. Hidden individual programs stay hidden. An empty
chooser means no installed app has published preview channels yet.

The module shows up to 40 local rows and 40 cards per row. It refreshes after
provider changes and when returning Home, skips invalid/unavailable playback
links and duplicate links within a row, and hides empty rows. Apps remain first.
Local metadata needs no Google feed; app artwork may still load from URLs
published by the app.

These are Android TV's local **Watch Next** and **preview-channel** APIs. Apps
that publish only to Google's cloud/Engage feed do not gain local rows. The test
publisher verifies the real provider, native UI and playback-link handoff; it
does not establish which third-party apps currently publish compatible data or
verify video playback inside those apps. See the [implementation and test
notes](docs/local-content.md).

## Add apps

Select **Add apps** at the end of **Your apps** to choose an installed app store
with the remote. Stores appear automatically when they advertise Android's
`MAIN` + `APP_MARKET` entry point; there is no package-name list to maintain.
F-Droid, Aurora Store and Google Play were checked in the emulator.

With one available store, Android opens it directly. With several, the system
chooser appears on each click. With none, Home explains that an app store needs
to be installed or enabled. Installing, disabling or removing stores updates the
next click without restarting Home. Each store keeps its own setup, account and
installation requirements. Apps that do not advertise `APP_MARKET`, including
the inspected Obtainium build, remain accessible through their own app tile.

See the [store-picker implementation and validation](docs/store-picker.md).

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
fallback destination, keeps `apps_channel`, appends nonempty local content rows,
removes the cloud-loading error panel, and labels the existing pseudonymous
profile **Local TV**. Every behavior change
requires local Home to be selected and the launcher's account view to contain zero
Google accounts. An account lookup error leaves the original behavior.

There is no forged Google account, credential injection, or signature-check
bypass. Personalized recommendations, purchases, Google account services, and
Google Play itself still require Google authentication. This is not
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
[`validation-tv-update.json`](validation-tv-update.json) records the later store
picker check and the **0.5** channel-default update on the TV.
[`validation-content.json`](validation-content.json) records the local-content
tests with the real emulator UI and a separate TV-provider publisher.
[`validation-market.json`](validation-market.json) records store discovery and
remote-controlled selection, including real F-Droid and Aurora entry screens.
The [lab guide](docs/lab.md) describes the guarded emulator, smoke tests, rollback,
negative compatibility tests, and the separate full-feed experiment.

The installation artifacts and full logs are excluded from Git. Normal installation
uses only the module APK and the separately obtained framework; never install the
emulator system overlay or its network-workaround research build on a TV.

## GitHub CI and releases

[![Test](https://github.com/futpib/launcherx-local/actions/workflows/test.yml/badge.svg)](https://github.com/futpib/launcherx-local/actions/workflows/test.yml)

Pushes and pull requests run the **Test** workflow. The manual **Build APK**
workflow publishes a signed universal `launcherx-local.apk`, checksums and a
build receipt to [GitHub Releases](https://github.com/futpib/launcherx-local/releases).
Configure the persistent signing key before publishing; see [CI setup, signing
and local reproduction](docs/ci.md). Public CI verifies building and setup
refusal on stock Android; full Home integration uses the private rooted lab.
