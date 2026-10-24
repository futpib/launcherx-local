# Google TV Home without a Google account

Feature inventory recorded on 2026-10-23 for Google TV Home
**1.0.785572138 / 702401**. Updated for LauncherX Local **0.9**. The TV has
received the store picker and automatic channel defaults; complete content-row
and playback-link coverage remains emulator-based. The 0.6 Incognito chooser,
Incognito selection and automatic activation on launcher restart are now also
verified on the physical TV, with app-published cards visible on Home.
Version 0.9 is installed with the Google remote receiver and Xiaomi service
hooks reloaded, preserving existing assignments. The prior 0.8 check observed
Live TV and Xiaomi TV+ fallback handling in device logs; complete six-button
assignment and restoration coverage remains emulator-only.
Version 0.9 adds **Send key…** for Play/Pause, TV Home, Back and arbitrary
Android key codes. Media controls use Android's current media consumer; other
keys need root access for LauncherX Local. New key-action delivery is verified
in the emulator. See [key actions](key-actions.md).

The original apps-only screen now also supports local content rows. More local
features look feasible; Google's account-backed feeds need
authentication or a replacement data source. The inspected launcher contains 69
row-renderer types, mostly layout variations, grouped below into user-facing
features. **“Candidate” means code exists, but we haven't made it work
account-free yet.**

| # | Home feature | What it can do without a Google account |
|---|---|---|
| 1 | **Your apps** | **Working on your TV:** display installed TV apps, navigate, and launch them. |
| 2 | **Reorder and app menus** | Local functionality. Reorder UI was observed; persistent reordering wasn’t tested. |
| 3 | **Add apps / app catalogue** | **Implemented in 0.4; picker and F-Droid handoff verified on the TV.** Opens Android's chooser for installed `MAIN` + `APP_MARKET` handlers, including F-Droid and Aurora. One store opens directly; an empty list shows an explanation. Each store retains its own account requirements; complete installation flows remain untested. |
| 4 | **Continue watching / Watch Next** | **Implemented in 0.3; emulator verified.** Native cards, artwork, episode information, resume progress and app playback links from browsable local Watch Next entries. Requires participating apps to publish entries; not yet tested on the physical TV. |
| 5 | **App-provided content rows** | **Enabled by default in 0.5; automatic visibility verified on the TV.** Existing and newly discovered channels start enabled; later hide choices persist. The 0.6 TV check shows cards in Subscriptions and Recommended rows. Live updates, playback links and hide persistence remain emulator verified. |
| 6 | **Featured banners, trailers, promotions** | Rendering exists, but stock content is supplied remotely. We could populate these with another source; merely exposing the UI won’t supply content. |
| 7 | **For You, Movies, Shows, genre rows, top picks/charts** | Google’s feed requests failed authentication in our emulator experiment. No working anonymous feed established. |
| 8 | **Sports, news and themed collections** | Renderers exist. Their individual services haven’t been tested anonymously; they need content feeds. |
| 9 | **Title details, cast, trailers, “Ways to watch”** | Cards and app-launch links could work with supplied metadata. Google’s metadata/service integration isn’t unlocked by our patch. |
| 10 | **Live TV and channel guide** | Antenna/tuner playback doesn’t inherently need Google. Integrating local channels and programme data into this Home is a candidate. |
| 11 | **Google TV Freeplay** | **Unresolved.** Google advertises no additional signup, but that doesn’t establish operation without a Google TV account. Region and backend access need separate testing. [Google documentation](https://support.google.com/googletv/answer/11167990?hl=en-GB) |
| 12 | **Watchlist, likes, watched markers** | Google’s versions are account-backed. A local watchlist could be implemented with separate storage. |
| 13 | **Library, purchases, rentals, Family Library, DVR rows** | Google purchases and family access need account entitlements. Provider DVR access depends on that provider. Local media remains accessible through apps. |
| 14 | **Search and voice assistant** | Disabled by stock apps-only mode. Local app search or another search/voice provider could be added; Google search isn’t proven account-free. [Google’s restrictions](https://support.google.com/googletv/answer/10070784?hl=en) |
| 15 | **Profile chooser and kids profiles** | **Incognito chooser implemented in 0.6; default and Incognito selection verified on the TV.** Incognito uses the native pseudonymous account. With zero accounts, Home opens the stock chooser with Incognito and Add account. Sign-in handoff, cancellation and Incognito persistence are emulator tested; a real Google switching round trip and kids/lock behavior remain unverified. See [profile notes](profiles.md). |
| 16 | **Clock and date** | **Confirmed in the signed-out emulator dashboard.** Uses local system time. |
| 17 | **Quick settings** | Network, Bluetooth, accessibility, audio output, display and input shortcuts are local functions. Signed-out dashboard confirmed in the emulator; available tiles depend on the TV. |
| 18 | **Sleep timer** | Local alarm/power implementation exists. Feature availability and operation haven’t been tested. |
| 19 | **Now playing / playback controls / PiP** | Local media-session and notification-action code exists: resume, play/pause, skip and stop. Depends on app/device support; not yet verified. |
| 20 | **Notifications** | Local system/app notifications can use notification access. Account-specific cloud notifications still need their account. |
| 21 | **Screensaver** | The launch control exists and appears signed out. The chosen screensaver’s requirements are separate; personal Google Photos albums need an account. |
| 22 | **Google Home panel: cameras, lights, locks, thermostats** | Stock integration requires a matching Google Home account and home membership. A separate local smart-home integration would be different work. [Requirements](https://support.google.com/googletv/answer/15158065?hl=en-NZ) |
| 23 | **Custom remote-button shortcuts** | **Independent customization for all six app buttons and per-button/all-button restore controls.** Hold to open the native chooser; short press opens the saved app, sends a saved Android key (0.9), or runs the original default action. Play/Pause and TV Home are presets; arbitrary Android key codes are accepted. YouTube accepts other installed apps too. The additional Xiaomi TV service scope is enabled. Live TV and Xiaomi TV+ fallback handling was observed on the TV; complete assignment/reset coverage remains emulator-only. Wake, input switching and Google Home remain unverified. See [key actions](key-actions.md) and [remote routing](remote-buttons.md). |
| 24 | **Manufacturer tab, tips, help and feedback** | Manufacturer content depends on its provider. Bundled tips can display signed out; feedback submission wasn’t tested. |
| 25 | **Top navigation tabs and destinations** | **Home only while Incognito is selected in 0.6.** The module supplies the bundled local Home destination. Additional tabs need local destination definitions, content routing and remote-navigation tests. Reusing each stock tab account-free remains unverified. |

**Continue watching** and **app-provided content rows** are now connected to Home.
See [local-content implementation and validation](local-content.md) for the data
sources, supported behavior and test boundaries.

**Add apps** now discovers installed stores through `APP_MARKET`. See the
[store-picker notes](store-picker.md) for matching rules and test evidence.

## Top navigation tabs

The pills beside Home are containers for the features above. Their stock
availability varies by country, language and device; Home may be labelled
**For you**. They do not all appear together, and the exact signed-in set has
not been measured on the tested TV. See Google's [layout guide](https://support.google.com/googletv/answer/10467234?hl=en)
and [tab descriptions](https://support.google.com/googletv/answer/10267283).

Our account-free setup retains the launcher's existing pseudonymous profile
(feature **15**); it supplies no Google authentication credentials. In the
earlier full-feed emulator experiment, the request to load destinations returned
`UNAUTHENTICATED`. In 0.6 the module supplies Home locally while Incognito is selected; choosing a
Google profile leaves native destinations and feeds to Google, with signed-in UX
still untested. The app-channel
visibility settings in **5** control rows inside Home, not top navigation tabs.

| # | Tab | Related feature rows | Account-free status and remaining work |
|---|---|---|---|
| 1 | **Home / For you** | **1–9** | Local Home already contains installed apps and nonempty local content rows. Google's personalized For you feed remains unavailable in the tested setup. |
| 2 | **Apps** | **1–3** | Strongest candidate for another useful tab: reuse installed apps and the store picker. Separate-tab integration and navigation are unimplemented; Google app discovery is not supplied by the picker. Reordering retains the test limits in **2**. |
| 3 | **Library** | **12–13** | A local watchlist and saved app links would need new storage and controls. Google purchases, rentals and synchronized watchlists remain account-dependent. |
| 4 | **Live** | **10–11** | Local tuner channels and compatible app-provided channels are candidates; guide integration and playback need investigation. Freeplay requires separate testing. |
| 5 | **Movies / Shows** | **7**, **9**; potentially **5** | No working anonymous Google feed established. App-published entries or another catalogue could supply content, subject to available metadata and playback links. |
| 6 | **Free** | **11**; potentially **5** | Freeplay remains unresolved. Aggregating other free providers would extend the existing inventory and require a usable content source; free access cannot be inferred from an app's preview entries alone. |
| 7 | **Shop** | **13** | No working account-free Google purchase or rental flow established. Exposing a tab would not provide account entitlements. |
| 8 | **Manufacturer tab** | **24** | Depends on the manufacturer's content source and device integration; no account-free tab implementation verified. |

Feature **25** tracks the navigation work shared by these proposals. **Apps
first, then a local Library** is a proposed implementation order: Apps would
mostly reorganize existing functionality, while Library would implement the
local-watchlist candidate in **12**. Neither is a newly verified stock-tab
unlock. Supplying a tab label alone does not supply its content or service access.

The [0.7 account-check audit](remote-buttons.md#other-account-checks-found) found
remaining Basic TV behavior in the dashboard profile area, fullscreen app-grid
ordering/filtering, and network notifications. The working Home profile icon
and app row do not cover those separate paths.

## Validation scope

“Working on your TV” refers to the tested Xiaomi MiTV-MOEU0. The signed-out
dashboard inspection ran only in the emulator. See the [initial physical-device
validation](../validation-tv.json), [TV update checks](../validation-tv-update.json)
and [0.6 TV profile checks](../validation-tv-profiles.json), plus
[TV remote deployment](../validation-tv-remote.json),
[0.9 TV update](../validation-tv-key-actions.json),
[key-action tests](../validation-key-actions.json),
[remote receiver tests](../validation-remote.json),
[0.8 regression tests](../validation-remote-regressions.json) and
[emulator validation](../validation.json), for the exact coverage.
