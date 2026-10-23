# Local content in Google TV Home

LauncherX Local connects Android's local TV provider to the pinned launcher's
existing row and card renderer. This does not unlock Google's recommendation
backend. It works only while local Home is selected and the launcher sees no
Google account.

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
  bundled apps row at `igc.w`. No replacement launcher UI or remote-navigation
  hooks are installed. These obfuscated names are supported only for the exact
  executable hashes in `compatibility.json`.

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
through `ContentResolver`. The launcher-side receiver requires the sender to hold
`android.permission.DUMP`; setup reaches it through its existing Magisk root
grant. It also checks that local Home is enabled and no Google account is visible.
Channel IDs and values are validated before writes. The chooser also initializes
any newly discovered channels, so its initial checkboxes match Home's defaults.

Watch Next entries appear when apps publish them. Hidden Watch Next entries and
hidden preview programs are respected. Restoring Basic TV makes these hooks
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
