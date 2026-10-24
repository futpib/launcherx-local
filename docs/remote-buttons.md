# Remote shortcuts and Incognito audit

## Key actions in 0.9

Each button's native chooser now includes **Send key…**, with Play/Pause first,
TV Home and Back presets, and arbitrary Android key codes. Short press sends
the key; long press still opens customization. Media keys use Android's media
routing, while other keys use a root helper in the module APK. See
[key actions](key-actions.md) for access, restoration and validation details.

## Six-button customization in 0.8

Long-press each button to configure its own shortcut using Google's existing
chooser and multiprocess protobuf store. The same store supplies native app
launches. The YouTube button's chooser also accepts installed apps outside the
YouTube family; its original YouTube selection remains the default until an
override is saved.
Opening the YouTube chooser can write native configuration flags before an app
is selected. These flags are not treated as an assignment: cancelling still
uses the native YouTube choice and launch checks. The optional YouTube-to-star
alias is disabled so their saved bindings stay independent.

| # | Button | Android code | Original receiver | Default action |
|---|---|---|---|---|
| 1 | Star | `MACRO_1` / 313 | Google TV | Existing star assignment, or its native chooser when unassigned |
| 2 | YouTube | `BUTTON_3` / 190 | Google TV | Native YouTube selection and launch |
| 3 | Netflix | `BUTTON_6` / 193 | Xiaomi TV service | Original Netflix handler |
| 4 | Prime Video | `BUTTON_7` / 194 | Xiaomi TV service | Original Prime Video handler |
| 5 | Live TV | vendor code 351 | Xiaomi TV service | Original Live TV handler |
| 6 | Xiaomi TV+ | vendor code 352 | Xiaomi TV service | Original TV+ / PatchWall / Live TV fallback logic |

Google's alternate YouTube keycode 189 is supported too. This does not rewrite
keylayout files, manufacture MACRO keycodes, enable disabled apps or change
which keys wake the TV.

**Restore default** and **Restore all button defaults** appear at the top of
the native chooser, with confirmation and cancellation. They remove custom
assignments through the native store API. Star returns to its unassigned
chooser; YouTube returns to its original selection; the four Xiaomi keys
return to their original handlers. Hold-to-customize stays available. Disabling
the module in LSPosed and rebooting restores the full stock handlers, including
their long-press behavior. Removing only the Xiaomi service scope and restarting
that service restores the four OEM keys while retaining the Google TV patch.

`NativeShortcuts` extends the supported button definitions in `tzk`, retaining
separate `tzd` identifiers and the native `tzg` update API. It adds generic app
mode to YouTube's supported modes without discarding its default mode. The
chooser's restore controls are DPAD reachable and do not replace its Save or
Cancel controls. Setup readiness, account readiness, native launch validation
and managed-profile restrictions still execute.
Restricted native YouTube pickers retain their allowlist-only mode and omit
the added restore controls. This guard is source-reviewed; no real child or
authenticated profile was exercised.

`XiaomiRemote` adds one hook to `mitv.service`'s original
`GlobalKeyReceiver.onReceive`, limited to the four codes above, owner/system UID
1000, completed setup and enabled Home. It forwards the original `KeyEvent` in
an ordered protected `GLOBAL_BUTTON` broadcast. Google acknowledges only after
resolving its real listener and successfully extending the native definitions.
Without acknowledgement, Xiaomi runs its original method. An unassigned short
press uses a one-shot immutable PendingIntent back to the original Xiaomi
receiver; releasing a long press does not launch the default app. PendingIntents
are cancelled after the forwarded event completes. Other Xiaomi keys and boot
broadcasts keep their original handlers.

The additional scope is **TvService (`mitv.service`)**, process
`xiaomi.tvservice`, version **1.1.0 / 110**, with `classes.dex` SHA-256
`33d06a3edd7d1e229916b85af034513d33d0060d4ee070b03bd84d62502a0c36`.
The matching firmware and live APK both hashed to
`e6904e5ff54c25e8750853899d284f41459845cf7136541b052ff5b5d509547c`.
Unknown launcher/service code is refused. The bridge is not a generic Xiaomi
service patch for other firmware releases.

Select this additional scope in LSPosed and reboot to load it in the persistent
Xiaomi service. The APK's scope recommendation alone does not enable new hooks.

## Incognito fix in 0.7

Version **0.7** enables the stock star-button and YouTube-button handlers for
Incognito. Long-press the star button to open Google's shortcut chooser, choose
Favorite app, select an app and save. Short-press opens the saved app. With no
assignment, short-press opens the chooser. YouTube retains its separate native
chooser and Google's supported YouTube-app list.

The original 0.7 fix covered only Google's remote receivers; 0.8 adds the four
Xiaomi buttons through the separately pinned service hook described above.
Google Home, Freeplay and hardware input switching have separate requirements;
opening the chooser does not make those services available account-free.

## Cause and implementation

Both `GlobalKeyListener_Receiver` and `GlobalButtonLaunch_Receiver` run in
`com.google.android.apps.tv.launcherx:coreservices`. Earlier versions loaded
hooks only in the main process. The star/YouTube handler checked the native
`pseudonymous` account before distinguishing short and long press, and sent
both to Home with `VANILLA_EXIT_APPS`. Our Home overrides displayed normal
Incognito Home there, obscuring the Basic TV prompt.

`RemoteButtons` loads only in `:coreservices`, for the owner user and default
apps build, after the same version/DEX compatibility checks. It scopes two
changes to the exact callbacks in the pinned launcher:

- `llx.a()` / `GlobalKeyListener`: for `MACRO_1`, `BUTTON_2` and `BUTTON_3`,
  allow the one `nnd.d("pseudonymous")` check to continue into native local
  button handling. The last two are Google's YouTube-button variants.
- `eww.apply()` case 4 / `GlobalAppLaunchManager`: for an actual pseudonymous
  account, skip the one `nnd.b()` first-API-34 rule that can block app launches
  when internet is available. This also covers OEM app-button broadcasts
  delegated to Google's app-launch manager.

Each override is thread-local, consumed by its one matching predicate, and
restored after the callback, including exceptions and nested callbacks. It
requires the full Home component to be enabled. It does not rewrite account
objects, globally mark anonymous accounts as Google accounts, replace the native
chooser, or bypass setup readiness, timed-out/signed-out states, profile locks,
managed-profile dispatch or target-package validation. Hook installation rolls
back on failure. Google profiles and managed profiles keep their stock paths;
that preservation is source-reviewed, not authenticated-device testing.

Assignments are persisted by Google's multiprocess protobuf store under
`files/remote_button_configuration/data/<generation>/content` inside the launcher
data directory. The module never edits this store. The chooser and receiver
exchange updates through the existing native data-store listener.

## Other account checks found

The audit examined all four DEX files pinned in `compatibility.json`: **82
methods in 70 classes** reference `pseudonymous`, `vanilla_mode`, `VANILLA_*`, or
the Basic TV predicates `nnd.c/d/e`. Relevant callers were inspected in decompiled code;
the two patched callback/predicate relationships were also checked in DEX
instructions. This is a bounded audit of those indicators, not proof that every
feature flag, indirect account helper or backend restriction has been found.

To reproduce the reference scan with the host's jadx 1.5.6 all-in-one JAR:

```sh
java -cp /path/to/jadx-1.5.6-all.jar scripts/audit_accounts.java artifacts/launcher-original.apk
```

Optional output-directory and class-list-file arguments decompile selected
classes for local inspection. Keep those proprietary outputs under `artifacts/`.

| # | Area | Finding and status |
|---|---|---|
| 1 | Star shortcut | **Fixed in 0.7.** Both presses previously reached the Basic TV gate. Native configure/save/launch/re-edit and restart persistence are emulator tested. |
| 2 | YouTube button | **Fixed in 0.7.** Same missed gate. Native long-press picker and short-press launch are emulator tested. This does not extend Google's YouTube app allowlist to SmartTube. |
| 3 | OEM-delegated app buttons | **Fixed in 0.7.** The separate first-API-34 internet rule in `eww`/`ibo` blocked an installed app in the emulator. The actual `GlobalButtonLaunch` receiver now launches it. Xiaomi-side package fallbacks and disabled apps are unchanged. |
| 4 | Dashboard profile area | **Remaining UI gap.** `gns.c()` and `gnr.f()` still hide the profile avatar and supply the Basic TV add-account presentation for pseudonymous profiles. Home's patched profile icon is a different entry point. |
| 5 | Fullscreen app grid | **Remaining UI parity gap.** `ihd.g()` still applies Basic TV ordering/filtering to pseudonymous profiles. The working Home app row does not prove this separate screen matches signed-in behavior. |
| 6 | Network notifications | **Remaining gate.** `cys.call()` case 9 can send `VANILLA_BLOCK_INTERNET`; `ggh.apply()` suppresses normal network notifications on first-API-34 anonymous devices. Neither callback is patched by the scoped app-launch fix. Trigger behavior still needs a dedicated network-transition test. |
| 7 | Guide/OEM TV | `lmf.b/c()` already has an explicit anonymous OEM-guide launch path. Bypassing its account check would change routing unnecessarily. Hardware/OEM guide behavior remains untested. |
| 8 | Freeplay | `llu.a()` rejects anonymous accounts. The star override is consumed before this nested handler, so its restriction remains. Its feed and service requirements need separate work. |
| 9 | Settings, Help, feedback, navigation and content providers | Most inspected callers use `nnd.c(context)`, which checks the Basic TV component or Android test-harness state. Home activation already disables that component. This does not establish that all downstream services work anonymously. |
| 10 | Account selection, locks, PINs and child profiles | `kvb`, `eww/ibo`, `gcb`, `kwd`, `txn`, `tvi`, reauthentication activities and account providers use anonymous identity intentionally. Retained. No real Google account, child profile or lock was tested. |
| 11 | Cloud authentication and account storage | `gaj`, `ymb`, `vnu/vms` and related handlers distinguish real credentials from the native anonymous identity. Retained; changing these cannot create authenticated feeds or entitlements. |

The remaining dashboard, app-grid and notification findings are code evidence,
not newly reproduced physical-TV bugs. They are deliberately not hidden behind
a global anonymous-account bypass.

## Verification

Use the guarded rooted `launcherx_local14` fixture from [lab.md](lab.md):

```sh
python3 scripts/build.py --launcher artifacts/launcher-original.apk
python3 scripts/remote_smoke.py
```

An optional `--baseline-apk` accepts an unpatched module rebuilt with the same
key and version code. The original 0.7 run compared the old hooks from `d1ffefb`
against 0.7, reproducing both Home redirects and the internet app-launch block.
That historical report is preserved in commit `00049dd`.

The AVD lacks Xiaomi's global-key overlay. The test-only root helper sends real
`KeyEvent` parcelables through the stock broadcast receivers. DPAD operates the
real chooser; screenshots/OCR, ActivityManager records, native saved storage and
module logs establish the result. The helper refuses other AVDs and physical
devices and is not packaged in the module. The test restores its previous
shortcut store and removes the helper in `finally`.

See [validation-remote.json](../validation-remote.json) and
[validation-all-shortcuts.json](../validation-all-shortcuts.json). The six-button
test uses the actual native chooser, DPAD, store, receiver and ordered-broadcast
acknowledgement on Google's side. It does not run the Xiaomi service, deliver
the ordered result to Xiaomi, or invoke its PendingIntent fallback. These
emulator tests do not verify
Bluetooth, physical keylayout, wake/standby, input hardware, Google Home,
Freeplay, authenticated switching or playback inside launched apps.

The same APK also passed the activation/LSPosed toggle, version/DEX refusal and
Incognito profile suites, including real Google sign-in cancellation and
emulator reboot persistence. See
[validation-remote-regressions.json](../validation-remote-regressions.json).

Version 0.8 was installed over 0.7 on the physical MiTV-MOEU0. The installed APK
hash matches the emulator-tested build; the stock launcher and Xiaomi service
APKs are unchanged. TvService was selected through LSPosed's normal UI, then
Google TV's main/core processes and the persistent Xiaomi service were reloaded
without a TV reboot. Logs confirm all three processes loaded their hooks.

During deployment, incoming Live TV and Xiaomi TV+ events were observed crossing
the bridge and invoking the original Xiaomi handlers through their PendingIntent
fallbacks. ActivityManager also recorded a Live TV launch. The native shortcut
chooser appeared in the foreground activity record. These observations were
not a controlled six-button test and do not establish complete assignment,
restoration or wake/standby behavior on the physical remote.

The user subsequently confirmed that YouTube, Netflix and Prime long-press
customization worked after all hooks finished reloading. Earlier stock-picker
and Play Store observations overlapped deployment: a Play Store launch at
16:49:52 TV local time preceded the new Google core hooks at 16:49:56 and Xiaomi
hooks at 16:49:57. Raw remote events and receiver logs were captured during the
retest, then both temporary monitors were stopped.

Selecting **Home** in the native shortcut chooser opens Google's smart-home
panel setup, not the TV launcher's home screen. The TV displayed the native
Google Home setup instructions inside `MagicAppSelectionActivity`; this does
not establish account-free smart-home support.

SmartTube retained its process, activity and task identities and was foreground
at the final check; playback was paused. The LSPosed visit and subsequent TV
navigation mean uninterrupted playback is not claimed. Deployment did not
write shortcut assignments or inject app-button KeyEvents. See
[validation-tv-remote.json](../validation-tv-remote.json).
