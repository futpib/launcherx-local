# Ambient Mode and local device settings

Version 0.20 allows Ambient Mode settings while using Incognito Home. Keep
Google TV selected in LSPosed, then restart Google TV and Ambient Mode or reboot.
No additional scope is needed. The module still verifies launcher compatibility first.

## What the disassembly showed

On the tested TV, Ambient Mode already rendered artwork through its native
`Backdrop` dream service. Its settings slice rejected account mode 2, which
Google TV reports for its existing pseudonymous profile. This generated the
message asking the user to set up Google TV. Hiding the message would leave
the settings slice uninitialized.

The launcher hook runs the original `AccountModeContentProvider.query` with its
original caller authorization. For requests from `com.google.android.apps.tv.dreamx`
only, it presents a single empty pseudonymous account row using mode 4, the
native unknown/no-account mode. Ambient supports settings without a Google
account in this mode on the tested build. The account name, identity, lock/network fields and
other data remain unchanged. Other callers still see mode 2. Google account
modes 0/1 and managed profile mode 3 remain untouched. Fabricating modes 0/1
would make Ambient code assume that a real Google account exists.

This hook uses the named provider interface on all three reviewed launchers.
The provider is also included in the future-version structural profile.
It operates only while local Home is selected.

DreamX `3.3.20.999207020` was inspected and tested (556208 on the TV,
556210 on the emulator). Its screensaver controller retries an unknown-mode
account query once, then binds the visuals with a null account. Artwork,
weather and the clock render normally on the TV. The settings editor uses
the TV's existing Cast device credentials; no credentials are synthesized.
An emulator without those credentials can open the entry menu and start the
screensaver, but its settings editor remains empty after a server rejection.
Launcher structural matching does not certify future changes to DreamX.

## Validation

On the physical TV, the native settings entry opens without the setup error.
The full editor loads Art Gallery, gallery categories, slideshow speed and
on-screen info. A slideshow-speed change survived stopping and reopening
DreamX; the original one-minute value was restored. `Start now` binds the
native `Backdrop` dream, renders artwork, weather and time, and exits normally.
Input was injected through ADB. This does not establish automatic idle-timeout
activation or physical-remote behavior.

See [TV validation](../validation-tv-ambient.json) and
[emulator validation](../validation-ambient.json). Run the emulator regression
with `python3 scripts/ambient_smoke.py` after installing the built module in
the guarded rooted AVD. It also runs the cursor probe against Android's actual
`MatrixCursor`, checks unchanged mode 2 for other callers, and verifies that
an unauthorized shell query still fails.

## Other Basic TV gates

The TV settings APK has named `BasicModeFeatureProviderImplX.isBasicMode`
methods in its app and library packages. They query the setup-wizard provider
for `offline_mode`. On the tested TV, that package is disabled and its provider
is unavailable, so these methods already return false. No settings-app patch
or global setup-state change is needed. Disassembly identified these consumers:

- Cast settings visibility in `DevicePrefFragment`.
- App update settings visibility in `AppsFragment` and `PrivacyFragment`.
- Wi-Fi enablement's Basic TV internet-setup redirect in `NetworkFragment`.
- Low-power standby/energy-mode availability in `EnergyModesHelper`; firmware
  and hardware checks still apply.
- Setup suggestions, account-settings routing, Help and quick-setting suggestions.
- Assistant and purchase-settings visibility; their own sign-in requirements remain.

These are settings gates, not proof that every underlying service works.
Personal Google Photos, account purchases, Assistant and Google Home services
still need their real accounts and permissions. The patch does not change
setup-wizard storage, global provisioning flags, authentication, parental
restrictions or power/network policy. Cast settings availability alone does
not establish casting or standby reliability.
