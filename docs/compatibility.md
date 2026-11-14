# Compatibility and launcher updates

LauncherX Local 0.22 supports the reviewed Google TV Home builds 702401,
835645, 837595 and 841183. The launcher remains Google's signed APK. The module adapts
its hooks to the installed code before enabling Incognito Home.

Google Play replaced launcher 835645 with 837595 on the tested TV. Module 0.17
refused the unfamiliar version, so its Home hooks stopped loading. The new
bindings include the moved profile, navigation, content and shortcut classes,
changed methods and resource IDs. Older mappings remain available. This update
also corrects the distributor fields used for local Watch Next cards on 835645.
On 837595 it suppresses the native signed-out network-block redirect that could
interrupt the shortcut chooser after a network reconnect. Other Home navigation
and account flows remain native.

Version 0.21 also keeps a separate shortcut checkpoint. Compatibility refusal
still leaves unfamiliar launcher code unhooked, but assignments stripped by
stock Home can be recovered when compatible hooks return. See
[binding persistence](key-actions.md#persistence-across-updates).

Google Play subsequently installed **1.0.1018641540 / 841183** on the TV.
Module 0.21 loaded normally but refused its DEX with `Heuristic mismatch: fyt`.
That was the first failing alias, not the only changed target: **52 of 86**
targets had no exact old fingerprint. The update shifted resource IDs, merged
the app-launch callback into another class and changed several method names.
Its native shortcut store also pruned the added button definitions while the
independent checkpoint retained the assignments.

Version 0.22 reviews those changes and improves the matching machinery. The
local-content renderer is found by its complete method signature, and the
preview provider by a unique path of field types. This avoids reading an
unrelated service when obfuscation reuses an old field name. Both searches
refuse missing or ambiguous matches before installing Home hooks.

## Matching order

1. A reviewed version must have exactly the DEX entries and hashes recorded in
   `compatibility.json`. A mismatch stops here; it cannot use the heuristic path.
2. An unfamiliar version must have Google's pinned current signing certificate.
   If its entire executable code matches a reviewed profile, it uses that
   profile's bindings. Launcher 837597 has the same four DEX files as 837595
   and exercises this path in the x86 emulator. Executable split APKs prevent
   an exact-code match.
3. A version newer than 837595 with different executable code can use the
   structural profiles in `module/assets/compatibility-*.json`, trying the
   newest eligible baseline first. A baseline newer than the installed version
   is never used. All 86
   required classes must match uniquely and consistently before any launcher
   hooks or Home activation are installed. Missing, conflicting or ambiguous
   targets refuse the patch.

Whole-class structural fingerprints retain class/member layouts, method and field names,
instruction opcodes and operands, strings, constants, static values and exception
handlers. They normalize DEX pool indices and obfuscated class names. References
between profiled classes constrain the matches. The native profile tab layout
and its peer are included to protect the account-button resource ID; the
Continue watching string resource is checked independently in English.

The 841183 baseline uses **selected-method contracts** for four merged utility
classes: the local Home factory, immediate/completed futures, application entry
points and account type. These retain the class header, every field, static
values and the exact declarations, bodies and reference links of the selected
methods. A constructor can be selected by its complete descriptor. Unrelated
methods and constructor overloads can change without rejecting those targets.
Other classes still require whole-class matches. These contracts are explicit
review decisions; the runtime never substitutes a merely similar candidate.

This can survive class renaming, pool reordering and changes to unrelated code.
Changed required bodies, member names, layouts, resource IDs or unsupported DEX
instructions generally require another review. Dependencies outside the profile
can also change behavior: a match is evidence of structural compatibility, not
proof of every runtime behavior or compatibility with an unreleased version.
Google certificate rotation also requires review. The matcher has bounded DEX
input sizes and rejects duplicate classes.

The companion status says **Launcher matched automatically; this version has
not been reviewed** for the heuristic path. Logs distinguish `reviewed`,
`identical-code` and `heuristic`, with the actual version and selected bindings.
Every bundled profile digest is compiled into the module so an old loaded process
cannot silently use a newer installation's profile asset. Restart Google TV
after updating the module.

## Reviewing another build

Pull the original base and split APKs from the identified device and record their
signer, version and hashes. Compare raw DEX declarations and method bodies before
adding mappings; decompiler-renamed identifiers are not runtime member names.
Do not enable a version just by changing its version number or expected hash.

Inspect all candidates with the same parser and graph matcher used on Android:

```sh
python3 scripts/match_profile.py new-base.apk --output candidates.json
```

The report lists exact candidates, a bounded list of layout-only candidates,
all initially missing aliases and the final graph result. Layout-only candidates
are review leads, never approved bindings. This host command does not check
Android resources, the APK signer or runtime behavior.

After reviewing the class aliases and indirect dependencies, generate the
fingerprint asset using the same parser that runs on Android:

```sh
python3 scripts/fingerprint_profile.py reviewed-base.apk reviewed-bindings.json profile.json \
  --binding-version 841183 --methods reviewed-methods.json
```

`reviewed-bindings.json` maps each canonical alias to its reviewed class name.
The `name` fields in the existing profile record the current mapping. The asset
contains fingerprints and reference links, not copies of proprietary method code.
Omit `--methods` for whole-class fingerprints. The optional file maps canonical
aliases to method names or complete descriptors, as recorded in the 841183
asset's `methods` entries. A new binding baseline also requires reviewing the
module's resolver, indirect dependencies and resource checks.

Build and validate the module:

```sh
python3 scripts/build.py
python3 scripts/verify_apk.py
python3 -m unittest discover -s tests/ci -v
```

The host tests cover renaming, unrelated methods and constructor overloads,
wrong-type field decoys, ambiguous field paths and methods, changed literals/strings/static
values/exception handlers, reference disambiguation, ambiguous matches and
malformed DEX. The rooted `scripts/setup_smoke.py` suite on the latest launcher
withholds its exact profile to exercise heuristic activation, then checks
structural and reviewed-hash rejection. The disposable stock-emulator CI suite
checks rejection of unfamiliar signing certificates. Neither substitutes for
the real launcher content, profile, shortcut and device checks.

Use `--emulator-network` for setup, content, profiles, Ambient, all-shortcuts and shortcut
persistence when running the original ARM launcher on the guarded x86 AVD.
That fixture's network adapter must never be installed on the physical TV.

Parser reference: Android's [DEX format](https://source.android.com/docs/core/runtime/dex-format)
and [Dalvik bytecode](https://source.android.com/docs/core/runtime/dalvik-bytecode).
