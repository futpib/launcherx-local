# Google TV Home without a Google account

Feature inventory recorded on 2026-10-23 for Google TV Home
**1.0.785572138 / 702401** and LauncherX Local **0.2**.

The current apps-only screen is the first version implemented and verified, not
the limit. More local features look feasible; Google's account-backed feeds need
authentication or a replacement data source. The inspected launcher contains 69
row-renderer types, mostly layout variations, grouped below into user-facing
features. **“Candidate” means code exists, but we haven't made it work
account-free yet.**

| # | Home feature | What it can do without a Google account |
|---|---|---|
| 1 | **Your apps** | **Working on your TV:** display installed TV apps, navigate, and launch them. |
| 2 | **Reorder and app menus** | Local functionality. Reorder UI was observed; persistent reordering wasn’t tested. |
| 3 | **Add apps / app catalogue** | Stock installation uses Google Play. F-Droid and Obtainium work separately; we could redirect this button. |
| 4 | **Continue watching / Watch Next** | **Strong candidate.** There is a reader for Android’s local watch-history database. Requires participating apps to publish entries. |
| 5 | **App-provided content rows** | **Strong candidate.** The launcher has a local TV-provider channel loader. Could show content published by compatible apps. |
| 6 | **Featured banners, trailers, promotions** | Rendering exists, but stock content is supplied remotely. We could populate these with another source; merely exposing the UI won’t supply content. |
| 7 | **For You, Movies, Shows, genre rows, top picks/charts** | Google’s feed requests failed authentication in our emulator experiment. No working anonymous feed established. |
| 8 | **Sports, news and themed collections** | Renderers exist. Their individual services haven’t been tested anonymously; they need content feeds. |
| 9 | **Title details, cast, trailers, “Ways to watch”** | Cards and app-launch links could work with supplied metadata. Google’s metadata/service integration isn’t unlocked by our patch. |
| 10 | **Live TV and channel guide** | Antenna/tuner playback doesn’t inherently need Google. Integrating local channels and programme data into this Home is a candidate. |
| 11 | **Google TV Freeplay** | **Unresolved.** Google advertises no additional signup, but that doesn’t establish operation without a Google TV account. Region and backend access need separate testing. [Google documentation](https://support.google.com/googletv/answer/11167990?hl=en-GB) |
| 12 | **Watchlist, likes, watched markers** | Google’s versions are account-backed. A local watchlist could be implemented with separate storage. |
| 13 | **Library, purchases, rentals, Family Library, DVR rows** | Google purchases and family access need account entitlements. Provider DVR access depends on that provider. Local media remains accessible through apps. |
| 14 | **Search and voice assistant** | Disabled by stock apps-only mode. Local app search or another search/voice provider could be added; Google search isn’t proven account-free. [Google’s restrictions](https://support.google.com/googletv/answer/10070784?hl=en) |
| 15 | **Profile chooser and kids profiles** | Our “Local TV” label works. It is a pseudonymous local profile, not a Google identity. Google profiles, family controls and synchronization remain account-backed. |
| 16 | **Clock and date** | **Confirmed in the signed-out emulator dashboard.** Uses local system time. |
| 17 | **Quick settings** | Network, Bluetooth, accessibility, audio output, display and input shortcuts are local functions. Signed-out dashboard confirmed in the emulator; available tiles depend on the TV. |
| 18 | **Sleep timer** | Local alarm/power implementation exists. Feature availability and operation haven’t been tested. |
| 19 | **Now playing / playback controls / PiP** | Local media-session and notification-action code exists: resume, play/pause, skip and stop. Depends on app/device support; not yet verified. |
| 20 | **Notifications** | Local system/app notifications can use notification access. Account-specific cloud notifications still need their account. |
| 21 | **Screensaver** | The launch control exists and appears signed out. The chosen screensaver’s requirements are separate; personal Google Photos albums need an account. |
| 22 | **Google Home panel: cameras, lights, locks, thermostats** | Stock integration requires a matching Google Home account and home membership. A separate local smart-home integration would be different work. [Requirements](https://support.google.com/googletv/answer/15158065?hl=en-NZ) |
| 23 | **Custom remote-button shortcuts** | App/input shortcut code exists. Potentially local; depends on compatible remote hardware and feature flags. |
| 24 | **Manufacturer tab, tips, help and feedback** | Manufacturer content depends on its provider. Bundled tips can display signed out; feedback submission wasn’t tested. |

The most promising next additions are **Continue watching** and **app-provided
content rows**. This APK includes a `TvContractWatchNextProvider` reader
(`defpackage.lhr`, querying `content://android.media.tv/watch_next_program`) and
a `LocalChannelsDataServiceImpl` loader (`defpackage.lju` / `defpackage.ljt`).
Connecting these sources to Home remains implementation and verification work.

“Working on your TV” refers to the tested Xiaomi MiTV-MOEU0. The additional
signed-out dashboard inspection ran only in the emulator; the physical TV was
untouched during this inventory. See [physical-device validation](../validation-tv.json)
and [emulator validation](../validation.json) for the installed module's checks.
