# Remote shortcuts and Incognito audit

Version **0.7** enables the stock star-button and YouTube-button handlers for
Incognito. Long-press the star button to open Google's shortcut chooser, choose
Favorite app, select an app and save. Short-press opens the saved app. With no
assignment, short-press opens the chooser. YouTube retains its separate native
chooser and Google's supported YouTube-app list.

The original remote mappings and saved assignments stay with Google TV. This
does not turn the other Xiaomi app buttons into additional configurable macros.
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

The audit examined all three DEX files pinned in `compatibility.json`: **82
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
key and version code. The recorded run compares the old hooks from `d1ffefb`
against 0.7, reproducing both Home redirects and the internet app-launch block.

The AVD lacks Xiaomi's global-key overlay. The test-only root helper sends real
`KeyEvent` parcelables through the stock broadcast receivers. DPAD operates the
real chooser; screenshots/OCR, ActivityManager records, native saved storage and
module logs establish the result. The helper refuses other AVDs and physical
devices and is not packaged in the module. The test restores its previous
shortcut store and removes the helper in `finally`.

See [validation-remote.json](../validation-remote.json). This does not verify
Bluetooth, physical keylayout, wake/standby, input hardware, Google Home,
Freeplay, authenticated switching or playback inside launched apps.
