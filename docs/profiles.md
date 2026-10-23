# Incognito and Google profiles

Version **0.6** makes Incognito the initial launcher profile and adds it to the
stock account chooser. Open the profile icon beside Home, then choose Incognito,
a saved Google profile, or Add account. The launcher remembers the selection.
Cancelling Google's sign-in screen leaves the current profile unchanged.

Incognito uses the launcher's native pseudonymous account. It leaves saved
Google accounts on the device and does not sign apps out. Watch Next, preview
channels and their visibility are shared within the Android user; app histories
are not cleared. Google feeds and services still require their own authentication.

## Native integration

These names refer only to the executable hashes pinned in `compatibility.json`:

- `kve.a(vip)` selects the account on Home startup. Initially, or when the last
  active profile was Incognito, the module delegates to the native `vnq` selector.
  Otherwise the stock Google selector runs unchanged, including its remembered
  profile. The module never manufactures a Google account or token.
- `isa.g(yjv)` reports the active Home account. Its type controls local Home,
  content rows, the store picker and the pseudonymous theme override. The module
  persists an Incognito boolean in `launcherx-local-profiles`; it does not persist
  Google email addresses or credentials. The initial default is true.
- Home's native profile button opens `ProfileChooserActivity` through `isa.k`.
  Both supported tab layouts use the pinned profile button ID. The module opens
  the full chooser consistently; other views and child-profile controls are not
  redirected.
- `kwx.b(kwf)` receives the chooser's account data. A worker obtains the real
  pseudonymous account through `vnq` and `vjn`, gives its display copy the name
  Incognito, and adds one row ahead of Google's entries. The native add-account
  state, Google entries, locks and parent data are retained. Stale asynchronous
  results are discarded; a lookup failure delivers the original chooser data.
- In the chooser's `juj` case 19, only a pseudonymous selection takes the local
  path: `kxc.f` returns the selected native account ID to Home. Google selections
  continue through the stock authentication, lock and service-query path.

Incognito is added only to a chooser called by Home, excluding external-package,
child, and forced-lock requests. These conditions limit where the shortcut is
available; they do not establish tested child-profile or lock support.

The local overrides follow the **active profile**, not the total count of Google
accounts. Choosing a Google profile lets stock Home load its normal destinations,
feeds and account services. Removing an account and any resulting fallback remain
native account-controller behavior. Both cases still need authenticated testing.

## Activation and removal

There is no separate activation control in the companion app. After all hooks
pass compatibility checks, the module enables Home and disables the alternate
Basic TV component through PackageManager in the launcher's own process. This
requires no separate root request. If Basic TV was already being launched, its
first resume hands off to Home; subsequent native recovery is not redirected.
Basic TV's constructor disables Home, so that initial handoff must select Home
again before starting it.

Disable the module in LSPosed and restart Google TV to remove the hooks. The
stock launcher renders its signed-out setup screen. In the tested emulator
this still runs inside HomeActivity; restoring the original component flags is
not claimed. The native no-account recovery code (`kye` case 1 / `kxv`) can also
select the Basic TV activity, but that callback was not observed in the disable
test. Re-enabling and restarting selects Incognito Home again. With real Google accounts present,
the launcher uses its stock account flow; that removal case is not yet tested.

The companion app has only **Choose app content**, alongside setup instructions
and compatibility status. Its root request is limited to the protected channel
visibility receiver. Compatibility failures prevent activation before any
component state is changed. Installation of hooks participates in the existing
rollback list.

## Verification

Run against the guarded `launcherx_local14` emulator:

```sh
python3 scripts/build.py
python3 scripts/setup_smoke.py
python3 scripts/profile_smoke.py
python3 scripts/smoke.py
python3 scripts/content_smoke.py
```

`profile_smoke.py` requires Tesseract and the existing account-free lab fixture.
It clears only the new module profile preference to exercise the initial default,
saving its previous contents in the artifacts directory. It tests native DPAD
Incognito selection, Google's actual Play Services sign-in screen, cancellation,
process restart and reboot. It checks for crashes before and after reboot and
verifies that no accounts were created. It leaves the emulator at Incognito Home.

`setup_smoke.py` uses LSPosed's actual enable switch to test disable/re-enable,
checks automatic activation from a Basic TV starting point, checks the single
remaining app button, and rejects negative version/DEX variants. It restores the
normal APK and enabled module in `finally`. No framework database is edited.

Profile sequences use screenshots, OCR and ActivityManager records. UIAutomator
can leave stale accessibility focus in this stock launcher, so it is not attached
between remote keys or to the paused Home. These tests do not establish TalkBack
support. The module does not ship test accounts, a replacement sign-in UI, or
simulated Google responses.

**No real Google account was used.** Successful sign-in, Google-to-Incognito-to-
Google switching, multiple Google profiles, account removal, child profiles,
profile locks and authenticated cloud feeds remain unverified. The physical TV
has not received 0.6. See `validation-profiles.json` and `validation.json` for
exact APK hashes and completed checks.
