# Local content in Google TV Home

LauncherX Local connects Android's local TV provider to the pinned launcher's
existing row and card renderer. This does not unlock Google's recommendation
backend. Since 0.6 it works while Home is active and **Incognito** is selected,
even when Google accounts are saved on the TV. Google profiles use stock Home's
content selection; the local preview artwork correction also applies there.
These provider rows and visibility settings are shared within the Android user.

## Data and rendering

- Watch Next: `content://android.media.tv/watch_next_program`, with `browsable=1`,
  newest engagement first. Google's `lhj` converter produces native resume cards,
  artwork, episode labels and progress. Generic media such as clips uses the same
  resume card with the film subtype removed.
- App rows: browsable `TYPE_PREVIEW` records in `content://android.media.tv/channel`,
  with browsable programs from `content://android.media.tv/preview_program`, sorted
  by descending weight. Channels default to enabled on first discovery. Google's
  `ljf` converter formats the published cards.
- Cards are wrapped in native `aagk` / `lfs` / `lft` objects and appended after the
  bundled apps row at `igc.w`. The content integration uses native remote navigation and card UI. These obfuscated names are supported only for the exact
  executable hashes in `compatibility.json`. These names describe build 702401;
  `LauncherSymbols` selects the reviewed equivalents for 835645.

Provider reads and conversion run on a worker thread. Debounced content observers
watch all three tables. The main thread updates only attached Home fragment
views, using the native adapter. Resume triggers another read so changes made
while Home was paused appear on return. Empty or unavailable sources produce no
content row and leave the apps row usable.

Read work is bounded: at most 40 rows, 40 cards per row and 1,000 examined records
per cursor. Each playback intent must resolve to an enabled, exported activity in
the publishing package. Cross-package or selector intents are ignored. The
validated intent and its playback extras are handed to the stock action handler.
Links are deduplicated within each row. The module does not read application-private
history, invent viewing history, or publish recommendations itself.

## Filling preview artwork

Version **0.12** adds the launcher's bundled Glide `CenterCrop` transformation to
local preview-card image requests. This fills each card while preserving image
proportions. A 640x480 JPEG containing a centered 640x360 picture loses its top
and bottom bands in a 16:9 card. Already matching 16:9 artwork keeps its framing.
This is ordinary center cropping, not border detection; other aspect mismatches
can crop meaningful content.

`LocalArtwork` hooks the native asset factory and UI-entity adapter, and only
changes IDs beginning with `local:asset:`. The asset factory also requires the
`ROLE_LOCAL_ASSET` role. It leaves Watch Next, app icons and remote assets alone.
It applies in both Incognito and Google profiles whenever those paths render a
local preview card. Signed-in content availability has not been validated.

The stock image loader has a center-crop flag, but its explicit transform path
can be skipped when target override dimensions are zero. Therefore the patch
uses the native builder's additional transformation, rather than just flipping
that flag or changing a recycled ImageView's scale type. Glide includes the
transformation in its cache key, so no cache clearing is needed. Published URLs,
metadata and provider rows remain unchanged.

| # | Launcher build | Asset factory | UI-entity factory | Builder transform | Native crop |
| --- | --- | --- | --- | --- | --- |
| 1 | 702401 | `nnf.f(aapk, aaoz, int)` | `frd.f(Context)` | `ito.j(dkp)` | `dqh` |
| 2 | 835645 | `otk.h(zqu, zqj, ImageView, int)` | `gfc.f(Context, ImageView)` | `jgd.j(eig)` | `enw` |

All bindings are resolved after the executable hash check. The newer factories
return an immutable request, which is copied into a builder; the older factories
return a builder, which is cloned. The original request is only replaced after
the transformation has been added successfully. Disabling the module restores
the stock rendering on launcher restart.

Physical-TV verification on 835645 compared the same SmartTube 32.56 records
before and after installation: Subscriptions and History filled their cards,
and three sampled Recommended image interiors were pixel-identical. Six local
rows rendered and provider IDs, URLs and aspect metadata were unchanged. Both
versions' hook signatures and crop classes were checked in raw DEX; runtime crop
coverage is limited to 835645. See [the validation record](../validation-tv-fill.json).

## Choosing app rows

Starting with **0.5**, Home enables each preview channel when first discovered,
including channels that already exist during an upgrade. It remembers the
publishing package and channel ID in a separate launcher preference file. Later
visibility changes are left alone, including channels hidden through the setup
dialog or another Android TV interface. Refreshes, process restarts and reboots
do not re-enable those channels. Newly published channels are discovered through
the existing content observer and Home resume path. Discovery scans at most
1,000 channels per read, newest first, under the same account-free Home guard.

The setup app's **Choose app content** dialog lists up to 80 published preview
channels. Saving changes updates only the selected channels' `browsable` flags
through `ContentResolver`. Starting with **0.13**, the launcher-side receiver
requires the module-defined signature permission. The companion app sends an
ordered broadcast directly, without `su`, `am` or a superuser grant. Requests
expire after ten seconds and are limited to the owner user. It also checks that Home is enabled and Incognito is selected.
Channel IDs and values are validated before writes. The chooser also initializes
any newly discovered channels, so its initial checkboxes match Home's defaults.

Watch Next entries appear when apps publish them. Hidden Watch Next entries and
hidden preview programs are respected. Disabling the module makes these hooks
inactive; channel visibility choices remain in the ordinary Android TV provider.

## Verification

Run from the repository root with the guarded emulator configured as described
in [the lab guide](lab.md):

```sh
python3 scripts/build.py
python3 scripts/content_smoke.py
python3 scripts/setup_smoke.py
python3 scripts/smoke.py
```

`content_smoke.py` requires Pillow and Tesseract for screenshot inspection. It
builds and installs a separate test APK from `tests/publisher`. That app writes
real TvContract entries and supplies local PNG artwork through a content provider.
Its playback activity displays the received URI and seek extra. This verifies
native rendering and the actual app handoff, not media playback in a third-party
player. The fixture is never packaged in the module.

The checks cover empty providers; automatic visibility for existing and newly
published channels; remote hiding of one channel without changing others; movie,
episode and clip cards; both kinds of playback link; updated progress/extras;
live edits and removals; hidden channels/programs; broken and duplicate links;
return from another app; blocked launcher IPv4/IPv6 egress; and persistence of
both populated rows and explicit channel hides after reboot.
The publisher and all its records are removed afterward, and temporary firewall
rules are removed in `finally`. Every device operation uses the emulator guard
and private ADB server. No physical-TV operations occur.

UIAutomator can leave a stale accessibility focus in this stock Android 14
launcher and trigger `ViewRootImpl.getAccessibilityFocusedRect` when navigating
away or inspecting another activity while Home is paused. Tests restart Home
after inspecting its hierarchy, then run remote actions and live provider changes
without intervening UI dumps. Chooser, playback-receiver and resumed-Home deletion
checks use screenshots and OCR to avoid attaching accessibility inspection to a
paused Home process or a card that has just been removed.
Screenshots and activity records capture the uninterrupted sequences. This does
not establish TalkBack support.

The Android provider contracts are documented in [Watch Next attributes](https://developer.android.com/training/tv/discovery/watch-next-programs)
and [TvContract.PreviewPrograms](https://developer.android.com/reference/android/media/tv/TvContract.PreviewPrograms).
The implementation does not assume cloud-only publishers also populate these
local tables.
