# Send a key from a remote shortcut

Version **0.9** adds **Send key…** to the native shortcut chooser for each of the
six supported buttons. Hold the source button, press Up to reach the added
controls, then move right to Send key. Select a key and confirm Save. Short press
sends the chosen key; long press still opens customization.

Version **0.10** adds **No action** to this list. It consumes short press without
launching an app, sending a key or invoking the button's default handler. Long
press still opens customization, and the chooser displays **No action** as the
current assignment. It needs no root access. Restore default re-enables the
original action; No action is a saved assignment, not an unassigned button.
This applies to the app shortcut handler, not the TV's hardware wake behavior.

Play/Pause is first in the list. Other presets include Play, Pause, Next,
Previous, Stop, Fast forward, Rewind, TV home, Back, Recent apps, volume, mute,
Menu and Search. **Other Android key…** accepts a single letter, a numeric
Android key code or a symbol such as `KEYCODE_F8`. For example, `f`, `F`, `34`
and `KEYCODE_F` all select the F key. Names are case-insensitive and the
`KEYCODE_` prefix is optional. For Space, enter `SPACE` or `62`.
Numbers are interpreted as key codes; use
`KEYCODE_1` to select the digit 1 key. These are Android key codes, not Linux
scan codes. The accepted range follows `KeyEvent.getMaxKeyCode()` on the device.

**TV home** sends the Home key. The native **Google Home** shortcut is a
different action: it opens the smart-home panel or its setup instructions.

The current key appears on the added chooser control. Cancelling a replacement
keeps the saved action. Choosing a favorite app replaces the key action, and
Restore default / Restore all button defaults remove it through the same native
store used for app shortcuts.

Before disabling or uninstalling the module, restore any key or No action
assignments using these controls. Stock Google TV cannot execute the module's
saved key actions; normal favorite-app assignments remain native Google TV data.

## Delivery and access

Media controls use Android's media-button routing to the current media consumer.
They do not need another root grant or Xiaomi's service. Which player receives
the key depends on Android's media-session and audio-playback state.

Starting with **0.13**, non-media keys run in an explicitly scoped privileged
process. On the supported Xiaomi TV, select **TvService** (`mitv.service`) in
LSPosed; its existing system UID already has `INJECT_EVENTS`. Without that service,
select **System Framework**. Reboot after adding either scope or updating its code.
The companion app and Google TV need no Magisk superuser grant.

The companion's provider accepts only the verified owner-user Google TV UID. It
checks the installed APK identity on every call and caches full DEX verification
until the package version, update time or file identity changes. Requests to the
input host require the module-defined signature permission; an unrelated app
cannot obtain it just by requesting it. Persistent hooks also verify that the
installed module signer and permission owner have not changed. Only validated integer key codes and
`prepare`/`send` operations are accepted. Expired requests are rejected.

Saving a non-media binding probes TV service first, then System Framework. It
refuses to change the binding if neither hook is available. Delivery uses only
the selected host and is never retried after an uncertain result. The input host
uses a separate handler thread, so delivery does not block the system main thread.
There are no `su` commands, subprocesses, listening sockets or root-worker relays.
Stopping the companion app discards its cached host and launcher verification;
the next press reconnects through the same authenticated path.

The historical 0.11 emulator measurements (5 ms median F8 delivery) described the
previous root worker. Current delivery measurements and cold-companion recovery
are recorded separately in [the latency report](../validation-key-latency.json).
Short actions still execute on release so holding the source button can customize it.

Each action sends a down/up pair. Application handling and Android policy
determine its effect; this is not a chord or held-key action. Generated keys are
marked so that another Send key binding cannot recursively send more keys.
Managed profiles cannot execute the added key actions.

## Validation

`python3 scripts/key_latency_smoke.py --lifecycle` measures Android uptime from
source-key release to the fixture's actual delivered event. It excludes host
ADB and sender startup time. It checks caller rejection after caching, companion
restart, invalid keys, unknown operations and expired requests. See [validation-key-latency.json](../validation-key-latency.json)
for current timings. These are emulator delivery timings,
not physical-remote or screen-rendering measurements. Version 0.11 is installed
on the physical TV; physical remote latency has not been measured.

`python3 scripts/no_action_smoke.py` covers No action on all six Google receiver
paths, process-restart persistence, long-press editing, cancellation, restoration
and replacing it with Play/Pause or a custom key. See
[validation-no-action.json](../validation-no-action.json).

`python3 scripts/key_actions_smoke.py` uses the real native chooser and store,
an Android MediaSession with a silent AudioTrack, and a focused activity that
records delivered keys. It also exercises the module's authenticated broker and
scoped input hook, including saving and delivering `f`, `F`, `34` and `KEYCODE_F`
through the custom-key dialog. This fixture is not a third-party media player.

The script backs up and restores the native shortcut store and removes its
fixture APK and receiver helper. See
[validation-key-actions.json](../validation-key-actions.json) for the tested
build and checks. Version 0.11 is installed on the physical TV with assignments
preserved and receiver hooks reloaded; key actions and No action were not
exercised there during deployment. See [0.11 TV update](../validation-tv-key-latency.json)
and the [prior user-assisted remote checks](../validation-tv-remote.json).
