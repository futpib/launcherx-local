# Compatibility and launcher updates

LauncherX Local 0.23 supports Google TV Home 844979 through shared member
contracts. The same rules match 841183. Earlier reviewed builds 702401, 835645,
837595 and 841183 retain their exact-code profiles. Google's APK stays signed
and unmodified.

## Matching order

1. A reviewed version must have exactly the DEX entries and hashes recorded in
   `compatibility.json`. A mismatch stops here.
2. An unfamiliar version must have Google's pinned signing certificate. If its
   complete executable code matches a reviewed profile, it uses that profile's
   bindings. This covers the tested 837597 x86 variant of 837595.
3. Other versions at least 841183 use `module/assets/contracts.json`. Every one
   of its 85 required classes and every named member must resolve uniquely.
   Missing, overlapping or ambiguous targets refuse the patch.
4. Unfamiliar versions between 837595 and 841183 retain the older fingerprint
   matcher and its eligible baselines. Older unfamiliar builds are refused.

The contracts identify roles through stable diagnostic strings, required method
signatures, field types, protobuf schema descriptors and references between
classes. They allow additional fields and methods and changed unrelated bodies.
They derive renamed helper methods by signature and moved fields from selected
code paths, including the anonymous-profile flag and remote-button state maps.
Shared callbacks are scoped by their captured receiver's type instead of a
numeric synthetic case ID.

The profile button is recognized through its native view type. Continue
watching resolves its native string using the English value and the APK's
resource table, retaining translations. Neither depends on a fixed resource ID.

These are explicit contracts, not a similarity score. A method whose name is
still part of a contract must retain that name; changed required signatures,
anchors, schemas or relationships need another review. A unique match cannot
prove unchanged runtime behavior in an unreleased build. Certificate rotation
also requires review. DEX and resource parsing are bounded; duplicate DEX
classes are rejected. See [regression evidence](../validation-contracts.json).

The companion app identifies automatic matches as unreviewed. Logs distinguish
`reviewed`, `identical-code`, `contracts` and `heuristic` modes. The contract
asset's digest is compiled into the module, so an old loaded process refuses a
new installation's different asset. Restart Google TV after updating.

The separate [shortcut checkpoint](key-actions.md#persistence-across-updates)
recovers saved assignments that stock Home prunes during a compatibility gap.
Intentional resets remain reset.

## Checking another build

Pull the original base and splits from the device and record their signer,
version and hashes. Run the same parser and contract matcher used on Android:

```sh
python3 scripts/match_contracts.py new-base.apk split.apk --output matches.json
```

The report lists resolved classes and named members or the reason for refusal.
This host command does not verify the signer, resources or runtime behavior.
Compare raw DEX declarations and code before changing a contract; decompiler
renames are not runtime identifiers. Do not enable a version just by changing
its version number or expected hash.

The older fingerprint tooling remains available for reviewed profiles:

```sh
python3 scripts/match_profile.py new-base.apk --output candidates.json
python3 scripts/fingerprint_profile.py reviewed-base.apk reviewed-bindings.json profile.json \
  --binding-version 841183 --methods reviewed-methods.json
```

Whole-class fingerprints retain member names, method bodies, constants and
exception handlers while normalizing obfuscated class names and pool indices.
The 841183 fingerprint baseline selects required methods in four merged utility
classes. Layout-only candidates from `match_profile.py` are review leads.

Build and check the module:

```sh
python3 scripts/build.py
python3 scripts/verify_apk.py
python3 -m unittest discover -s tests/ci -v
```

Host tests cover class/member renaming, unrelated changes, moved field roles,
reference disambiguation, missing/ambiguous targets, schema-signature changes,
malformed DEX and resource-ID renumbering. Emulator suites cover Home, content,
profiles, shortcuts, persistence, Ambient and activation/refusal. The setup
suite exercises automatic matching, a missing required anchor and a mismatched
reviewed hash. These checks complement real-device validation.

Use `--emulator-network` for suites running the original ARM launcher on the
isolated x86 AVD. That guarded network fixture must never be installed on the TV.

Parser references: Android's [DEX format](https://source.android.com/docs/core/runtime/dex-format),
[Dalvik bytecode](https://source.android.com/docs/core/runtime/dalvik-bytecode) and
[resource table structures](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/androidfw/include/androidfw/ResourceTypes.h).
