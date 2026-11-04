# Compatibility and launcher updates

LauncherX Local 0.18 supports the reviewed Google TV Home builds 702401,
835645 and 837595. The launcher remains Google's signed APK. The module adapts
its hooks to the installed code before enabling Incognito Home.

Google Play replaced launcher 835645 with 837595 on the tested TV. Module 0.17
refused the unfamiliar version, so its Home hooks stopped loading. The new
bindings include the moved profile, navigation, content and shortcut classes,
changed methods and resource IDs. Older mappings remain available. This update
also corrects the distributor fields used for local Watch Next cards on 835645.
On 837595 it suppresses the native signed-out network-block redirect that could
interrupt the shortcut chooser after a network reconnect. Other Home navigation
and account flows remain native.

## Matching order

1. A reviewed version must have exactly the DEX entries and hashes recorded in
   `compatibility.json`. A mismatch stops here; it cannot use the heuristic path.
2. An unfamiliar version must have Google's pinned current signing certificate.
   If its entire executable code matches a reviewed profile, it uses that
   profile's bindings. Launcher 837597 has the same four DEX files as 837595
   and exercises this path in the x86 emulator. Executable split APKs prevent
   an exact-code match.
3. A version newer than 837595 with different executable code can use the
   structural profile in `module/assets/compatibility-837595.json`. All 86
   required classes must match uniquely and consistently before any launcher
   hooks or Home activation are installed. Missing, conflicting or ambiguous
   targets refuse the patch.

Structural fingerprints retain class/member layouts, method and field names,
instruction opcodes and operands, strings, constants, static values and exception
handlers. They normalize DEX pool indices and obfuscated class names. References
between profiled classes constrain the matches. The native profile tab layout
and its peer are included to protect the account-button resource ID; the
Continue watching string resource is checked independently in English.

This can survive class renaming, pool reordering and changes to unrelated code.
Changed hook bodies, member names, layouts, resource IDs or unsupported DEX
instructions generally require another review. Dependencies outside the profile
can also change behavior: a match is evidence of structural compatibility, not
proof of every runtime behavior or compatibility with an unreleased version.
Google certificate rotation also requires review. The matcher has bounded DEX
input sizes and rejects duplicate classes.

The companion status says **Launcher matched automatically; this version has
not been reviewed** for the heuristic path. Logs distinguish `reviewed`,
`identical-code` and `heuristic`, with the actual version and selected bindings.
The bundled profile digest is compiled into the module so an old loaded process
cannot silently use a newer installation's profile asset. Restart Google TV
after updating the module.

## Reviewing another build

Pull the original base and split APKs from the identified device and record their
signer, version and hashes. Compare raw DEX declarations and method bodies before
adding mappings; decompiler-renamed identifiers are not runtime member names.
Do not enable a version just by changing its version number or expected hash.

After reviewing the class aliases and indirect dependencies, generate the
fingerprint asset using the same parser that runs on Android:

```sh
python3 scripts/fingerprint_profile.py reviewed-base.apk reviewed-bindings.json profile.json
```

`reviewed-bindings.json` maps each canonical alias to its reviewed class name.
The `name` fields in the existing profile record the current mapping. The asset
contains fingerprints and reference links, not copies of proprietary method code.
The generator currently writes the 837595 binding format; a new binding baseline
also requires updating the module's resolver and resource checks.

Build and validate the module:

```sh
python3 scripts/build.py
python3 scripts/verify_apk.py
python3 -m unittest discover -s tests/ci -v
```

The host tests cover renaming, unrelated code, changed literals/strings/static
values/exception handlers, reference disambiguation, ambiguous matches and
malformed DEX. The rooted `scripts/setup_smoke.py` suite on the latest launcher
withholds its exact profile to exercise heuristic activation, then checks
structural and reviewed-hash rejection. The disposable stock-emulator CI suite
checks rejection of unfamiliar signing certificates. Neither substitutes for
the real launcher content, profile, shortcut and device checks.

Parser reference: Android's [DEX format](https://source.android.com/docs/core/runtime/dex-format)
and [Dalvik bytecode](https://source.android.com/docs/core/runtime/dalvik-bytecode).
