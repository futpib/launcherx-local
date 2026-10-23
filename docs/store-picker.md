# Add apps through APP_MARKET

LauncherX Local 0.4 redirects the official **Add apps** tile to installed Android
app stores. It runs only in the default apps build, with local Home enabled and
no Google account visible to the launcher, under the existing version/DEX guard.
The physical TV remains on 0.2; this feature is validated in the isolated emulator.

## Discovery and selection

Each click queries `PackageManager` for `ACTION_MAIN` and `CATEGORY_APP_MARKET`.
It deliberately omits `MATCH_DEFAULT_ONLY`: a store can advertise `APP_MARKET`
without `DEFAULT`. Disabled applications/activities, private activities and
activities requiring a permission the launcher lacks are excluded. Duplicate
components are removed, and each remaining component becomes an explicit intent
retaining the original action and category.

Android's chooser receives the first intent plus `EXTRA_ALTERNATE_INTENTS` for
the rest. Its own UI supplies store icons, labels and remote navigation. Multiple
choices show on every click; this does not save a default store. Android opens a
single eligible store directly. With no handlers, Home shows a dismissible
explanation. Discovery is repeated for every click, so installation and enabled
state changes need no module update or launcher restart.

The implementation has no store package allowlist and does not infer store
capability from `market://` links or the ordinary app launcher category. It opens
the store's declared main screen, not a particular app listing. Stores retain
their own onboarding, authentication and Android installer behavior.

## Pinned launcher integration

In the supported launcher, `fva` translates the `ADD_APPS_ASSET_ID` card into a
`fup` action. `iqt` sends that action to `fyt.c(Activity, aaht, vhe, String, fyw,
xnc)`. `StorePicker` hooks this method only for the destination
`https://tv.google.com/destination/apps` and the launcher package. Other links,
including local playback links, follow their existing route.

The method and `aaht.d/e` fields are resolved during hook preflight. Installation
participates in the existing rollback list. Unknown launcher executables are
refused before installing hooks. A runtime discovery/chooser failure is logged
and shown as a short message, leaving Home available.

## Validation

`scripts/market_smoke.py` uses the real pinned launcher, its actual Add apps tile,
Android's system chooser and DPAD input. It checks one, five and zero eligible
handlers; a separate fixture verifies the component/action/category actually
delivered, including a handler without `DEFAULT` and the fifth choice. It also
checks excluded handlers, cancellation, live installation/disabling/removal and
that the launcher PID survives those changes.

The test then installs unmodified F-Droid and Aurora APKs into the emulator,
discovers them without a package list in the module, and opens both entry screens
through the picker. This verifies discovery and handoff, not completion of store
onboarding or downloads. Store versions, hashes, cleanup and check results are
recorded in [`validation-market.json`](../validation-market.json).

Screenshot OCR and ActivityManager records are used during navigation because
attaching UIAutomator can disturb this stock launcher's accessibility focus.
The fixture and store apps are removed afterward, and Google Play's previous
enabled state and the original handler list are checked after restoration.

API references: [APP_MARKET](https://developer.android.com/reference/android/content/Intent#CATEGORY_APP_MARKET),
[chooser alternate intents](https://developer.android.com/reference/android/content/Intent#EXTRA_ALTERNATE_INTENTS).
