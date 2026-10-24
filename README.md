# LauncherX Local

Use the official Google TV Home with **Incognito as the default profile**, installed
apps, Continue watching, app-published content rows, and an app-store picker.
Version **0.9** adds Play/Pause and other Android key actions to the independent
shortcuts for the six app buttons on the supported Xiaomi remote.
Home's native profile chooser also offers Google sign-in and saved Google profiles.
The original app tiles, cards, remote navigation, app launching and quick settings
stay inside Google's launcher. The launcher APK is not modified or re-signed.

This is an **LSPosed module APK**, hosted by Magisk and Zygisk. It is not a ZIP to
flash directly in Magisk. Enabling it in LSPosed activates Home on the next
launcher start. The companion app only has **Choose app content**, plus setup
instructions and automatic compatibility status. No ADB commands are needed.

## Compatibility

- Google TV package `com.google.android.apps.tv.launcherx`, version
  **1.0.785572138 / 702401**, with the exact executable hashes in
  [`compatibility.json`](compatibility.json).
- Tested on a Xiaomi **MiTV-MOEU0**, Android 14, ARM32, and an isolated Google TV
  Android 14 emulator running the same original launcher APK.
- Tested framework: **Magisk 30.7**, Zygisk enabled, **LSPosed 1.9.2 / 7024**.
- Owner user only. Version **0.6** supports choosing Incognito while retaining
  Google accounts; the authenticated switching round trip remains untested.

Physical-device validation covers the initial **0.2** setup, the **0.4** store
picker, the **0.5** channel defaults, and the **0.6** update. On the TV, 0.6
activates Incognito Home on launcher restart, opens the native profile chooser,
and returns to Home when Incognito is selected. App-published cards are visible
in Subscriptions and Recommended rows. The complete content-row suite, playback
links, sign-in cancellation and reboot persistence remain emulator-tested only;
authenticated Google switching remains unverified.

Version **0.9** is installed on the TV, with Google TV and TvService selected in
LSPosed. Its remote receiver and Xiaomi bridge reloaded without a TV reboot;
existing assignments were preserved. New key-action delivery and complete
six-button assignment, persistence and restoration tests remain emulator-only.
The prior **0.8** check observed Live TV and Xiaomi TV+ fallback handling, and
the user confirmed YouTube, Netflix and Prime long-press customization.
See the [0.9 deployment record](validation-tv-key-actions.json) and
[prior physical remote checks](validation-tv-remote.json) for the boundaries.

Other launcher versions and device/framework combinations are not yet supported
by evidence from this project. An unfamiliar version or DEX hash is refused
before hooks are installed or Home is activated. Keep launcher updates in mind:
a new launcher build needs a new compatibility review, not just an edited version
number. Disable the module in LSPosed and restart Google TV to use stock behavior.

## Install

1. Start with an already rooted TV. In Magisk Settings, enable **Zygisk**.
2. Download the official [LSPosed 1.9.2 Zygisk ZIP](https://github.com/LSPosed/LSPosed/releases/tag/v1.9.2).
   In Magisk, choose **Modules → Install from storage**, select the ZIP, and reboot.
3. Open LSPosed and confirm it says **Activated**. Its notification opens the
   manager. If TV notifications are inconvenient, install `manager.apk` extracted
   from that same ZIP and open the installed LSPosed manager.
4. Install `launcherx-local.apk` as a regular Android app. Allow installation from
   your chosen file manager in Android Settings when prompted.
5. In LSPosed's **Modules** page, enable **LauncherX Local** with **Google TV**
   selected. On the supported Xiaomi TV, also select **TvService**
   (`mitv.service`) for the Netflix, Prime Video, Live TV and Xiaomi TV+ buttons.
   Do not select System Framework or other packages.
6. Restart Google TV (force stop it in Android Settings, then press Home), or
   reboot. The verified module selects Home automatically inside the launcher.
   There is no separate activation button or Magisk root prompt for this step.

To update, install the new APK signed with the same key and restart Google TV.
When adding or updating the Xiaomi service hooks, reboot so that persistent
service also reloads the module. Existing installations must select the newly
recommended TV service scope; updating the APK does not select it automatically.
Magisk and LSPosed do not need to be reinstalled. The companion app's compatibility
status checks the installed launcher; it does not certify that LSPosed is active.

On TVs where Magisk's file picker silently closes, install
[Material Files from F-Droid](https://f-droid.org/packages/me.zhanghai.android.files/)
first. It supplies a working file picker. This was necessary on the tested Xiaomi
TV, whose built-in picker resolves to a stub. APKs and ZIPs can be transferred to
Downloads or obtained using a TV browser. Magisk and LSPosed's phone-oriented
interfaces may need a pointer; the LauncherX Local content chooser works with DPAD.

This follows the [standard LSPosed installation and module activation flow](https://github.com/LSPosed/LSPosed/wiki/How-to-use-it).
No framework databases, system files, or Google account records are edited directly.
Running hooks use LSPosed. Home activation uses the launcher's own permission
to select its activities. The companion app requests root for **Choose app
content** and for sending non-media keys. Play/Pause and other media controls
use Android's media-button API directly.

## Profiles

Select the profile icon next to Home to open the native account chooser.
**Incognito** is selected initially. Choose **Add account** for Google's normal
sign-in screen, choose a saved Google profile, or choose **Incognito** again.
The module remembers whether Incognito is selected; Google's native selector
remembers the Google profile. Cancelling sign-in leaves the selection unchanged.

Incognito uses the launcher's existing pseudonymous account. It does not remove
Google accounts from the TV or sign other apps out. Apps keep their own history,
and local TV-provider rows and channel visibility are shared within the Android
user. This is not a private browsing session or a telemetry blocker.

Local Home overrides apply only to Incognito. Google profile selections retain
stock account authentication and profile-lock handling. Child and forced-lock
chooser flows do not receive an Incognito shortcut. Real Google sign-in completion,
Google-to-Incognito switching, and kids/profile locks still need authenticated
testing. See [profile implementation and test boundaries](docs/profiles.md).

## Remote shortcuts

Hold **Star, YouTube, Netflix, Prime Video, Live TV or Xiaomi TV+** to open
Google's native shortcut chooser. Select **Favorite app** if a mode screen is
shown, choose an installed TV app and save. Each button has its own assignment;
the YouTube button can also choose apps outside Google's YouTube family.

Choose **Send key…** in the added controls to bind a button to **Play/Pause**,
**TV home**, **Back**, or another Android key. Custom numeric key codes and
`KEYCODE_` names are supported. Non-media keys require granting LauncherX Local
root access in Magisk. See [key actions and delivery rules](docs/key-actions.md).

Until customized, branded buttons retain their original actions. An unassigned
star opens the native chooser. At the top of the chooser, **Restore default**
resets one button and **Restore all button defaults** resets all six, after
confirmation. Press Up to reach these controls and Down to return to the chooser.
Restoring defaults retains hold-to-customize. To restore the complete stock
handlers, including their long presses, disable the module in LSPosed and reboot.

The earlier Basic TV internet-block fix remains. Hardware wake/input behavior,
Google Home services and authenticated/managed profiles are not covered. See the
[remote-button tests and remaining account checks](docs/remote-buttons.md).

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

Use **Restore default** or **Restore all button defaults** to remove any Send
key assignments first. Stock Google TV cannot execute those module actions.

Disable **LauncherX Local** in LSPosed and restart Google TV, or reboot. With
no Google accounts, the stock launcher shows its signed-out setup screen. This
disable/re-enable flow is tested in the emulator. With a Google account, stock
account selection applies, but that path has not been tested here.

You can then uninstall the module APK. No launcher APK restoration or firmware
flash is needed. Channel visibility choices and Google accounts remain intact.

## What works and what does not

While Incognito is selected, the default build uses the launcher's bundled
apps-only Home destination, keeps `apps_channel`, appends nonempty local content
rows, and removes the cloud-loading error panel. Selecting a Google profile stops
these overrides and leaves Google's Home feeds and tabs to the stock launcher.

There is no forged Google account, credential injection, or signature-check
bypass. Incognito does not provide Google recommendations, purchases, or account
services. The emulator tests reach Google's actual sign-in screen and cancel
without adding an account; signed-in behavior remains unverified.

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
[`validation-tv-profiles.json`](validation-tv-profiles.json) records the **0.6**
TV update, automatic activation, native Incognito chooser and retained app button.
[`validation-remote.json`](validation-remote.json) records native remote-receiver
tests. [`validation-all-shortcuts.json`](validation-all-shortcuts.json) covers
independent six-button assignments and default restoration in **0.9**.
[`validation-key-actions.json`](validation-key-actions.json) records **0.9**
media-session delivery, arbitrary input injection, persistence and restoration.
[`validation-remote-regressions.json`](validation-remote-regressions.json) records
the **0.8** activation, compatibility-refusal and Incognito profile reruns.
[`validation-tv-remote.json`](validation-tv-remote.json) records the **0.8** TV
installation, hook loading, observed OEM fallback paths and user-assisted
long-press checks.
[`validation-tv-key-actions.json`](validation-tv-key-actions.json) records the
**0.9** update, hook reload, unchanged assignments and retained foreground task.
[`validation-profiles.json`](validation-profiles.json) records Incognito selection,
Google sign-in cancellation, persistence, and the remaining authenticated test gap.
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
