# CI and release reproduction

The reference workflows were `futpib/iroh-ssh-android` at
`d7aa5f94d316d19522202f73ca3566ef9df94e59` and `futpib/ScreenCast` at
`f00d76c121fb32c1c001e9585ab557daeacabaf0`. LauncherX follows the former's separate
Test and manual Build APK workflows, Java 25, Android emulator runner, and
date-based release names. ScreenCast supplies the required signing-secret,
serialized release, complete release-history and checksum conventions.

## Setup and publishing

The **Test** workflow runs on pushes and pull requests to `main`, and can also be
started manually. It builds and verifies the APK, tests release versioning, and
uses a stock Android 15 emulator to verify that setup refuses a missing launcher,
an unsupported version and incorrect executable code. DPAD actions must leave
the default Home unchanged. Test APKs and emulator evidence are uploaded as
workflow artifacts. These test APKs use temporary keys; use releases for updates.

The manual **Build APK** workflow follows `iroh-ssh-android`'s release pattern:
date-based names (`YY.MM.DD.HH.MM+code`), increasing Android version codes, and a
signed APK on GitHub Releases. It uses ScreenCast's stricter signing requirement
and checks every previous release to allocate the next code. Releases contain
`launcherx-local.apk`, `SHA256SUMS` and `receipt.json`. One APK supports all CPU
architectures. Obtainium can follow this repository's GitHub releases.

Configure these repository Actions secrets before publishing:

- `KEYSTORE_BASE64`: required base64-encoded persistent signing keystore.
- `KEYSTORE_PASSWORD`: defaults to `android` for the existing local key.
- `KEY_ALIAS`: defaults to `local` for the existing local key.
- `KEY_PASSWORD`: defaults to `KEYSTORE_PASSWORD`, then `android`.

To preserve updates from the already installed local build, upload its existing
key from the machine holding `.cache/local.keystore`, using an authenticated `gh`:

```sh
base64 -w 0 .cache/local.keystore | gh secret set KEYSTORE_BASE64 --repo futpib/launcherx-local
gh workflow run build-apk.yml --repo futpib/launcherx-local --ref main
```

For a different key, configure its password and alias secrets too. Reusing an
iroh-ssh-android key would require `KEY_ALIAS=release` and would not update an
installation signed with LauncherX Local's original key. A release never falls
back to a newly generated key when a signing secret is missing.

Public CI requires neither a proprietary launcher APK nor root. Its stock-emulator
tests establish setup refusal only. Native Home rows, playback links and rollback
continue to use the separate rooted lab documented below; public CI does not
claim equivalent coverage. The reproduction commands follow below.

## Public checks

With JDK 25, Python 3.12+, Android platform 34 and build-tools 37.0.0 installed:

```sh
python3 scripts/fetch_api.py
python3 -m compileall -q scripts tests/ci
python3 -m unittest discover -s tests/ci -v
python3 scripts/build.py
python3 scripts/verify_apk.py
```

The dependency downloader checks the exact Xposed API digest before replacing
the cached file. The APK verifier checks the signature, manifest version,
checksums, receipt, Xposed entry point and DEX class definitions. The module must
contain only project classes and D8's generated lambda annotation, with no
bundled Xposed API, proprietary APK, test publisher, native libraries or signing
keystore.

The stock-emulator test uses API 35 `google_apis`, x86_64, the `pixel_6` profile
and a fresh AVD named `launcherx_ci`:

```sh
python3 scripts/ci_smoke.py --serial emulator-5554
```

For a locally isolated ADB server, add `--server-port PORT` and supply its explicit
emulator serial. Every device command checks both the emulator property and AVD
name. The test refuses an existing launcher/module installation, installs the
module, and checks missing-launcher rejection. It then installs two deliberately
incompatible code-free packages to exercise version and executable rejection.
These packages supply no launcher UI or behavior. Real setup UI and DPAD actions
must refuse Enable and Choose app content, preserve the default Home, and avoid
fatal exceptions. Installed test packages are removed in `finally`.

Evidence is saved in `artifacts/ci-smoke/` and uploaded even when a test fails.
These checks do not load Xposed or establish that launcher hooks work. The real
Google TV APK, Magisk/LSPosed fixture and full integration tests remain in
[the private lab workflow](lab.md).

## Release checks

`release_version.py` consumes tags from every page of the authenticated GitHub
releases API. It chooses a code above both the source manifest and every release
tag's numeric `+code` suffix, and fails if Android's version-code range is
exhausted. API errors fail the workflow; they are not treated as an empty history.
Release jobs are serialized to avoid concurrent code allocation.

The workflow builds only the default `apps` mode, with no emulator transport
override. It requires `KEYSTORE_BASE64`, reads passwords through environment
variables, verifies the signed APK, publishes the exact checked-out commit, and
removes the temporary keystore even on failure. `launcherx-local.apk` keeps a
stable filename for Obtainium. Version overrides affect a generated manifest
only; they never change the Google launcher version/hash gate.

CI test builds use disposable signing keys and must not be used as a persistent
update channel. A missing release secret deliberately fails publication. See the
[signing instructions above](#setup-and-publishing) to retain the
key used by an existing installation.

For local release builds, `scripts/build.py --version-name NAME --version-code CODE`
changes only a generated output manifest. `python3 scripts/fetch_api.py` downloads
the compile-only dependency using the hash already pinned by the normal build.
