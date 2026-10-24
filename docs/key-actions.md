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
Menu and Search. **Other Android key…** accepts a numeric Android key code or a
symbol such as `KEYCODE_F8`. These are Android key codes, not Linux scan codes.
The accepted range follows `KeyEvent.getMaxKeyCode()` on the device.

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

Other keys require **LauncherX Local** to have Magisk root access. Saving one
checks that access before changing the binding. The module's own provider runs
its small input helper from the installed APK. It accepts only the verified
Google TV launcher's owner-user UID, checks the pinned launcher code, and accepts
only a validated key code. It does not accept shell commands from callers.
Google TV does not need its own root grant, and System Framework is not scoped.
The same APK works without Xiaomi's service installed.

In **0.11**, the provider checks the caller UID and installed APK identity on
every request. It reuses a successful full DEX verification while the package
version, update time, path and file identity remain unchanged. A module-process
restart clears this cache.

The input worker is prepared when a non-media key is saved, then reused over
private stdin/stdout pipes. It accepts validated integer key codes, not shell
commands, and exposes no listening socket. It exits on pipe closure, after one
minute idle, or after five minutes total. The parent refreshes it before those
deadlines when needed, and closes Magisk's relay as well. Android's cached-app
freezer can defer cleanup of the unprivileged relay until the module wakes;
the root worker still exits independently. New workers go through Magisk again.
An uncertain delivery is not replayed, to avoid duplicate presses.

Warm custom-key delivery avoids repeated APK hashing and Java-process startup.
In the recorded emulator run, F8 delivery fell from a **432.5 ms** median in
0.10 to **5 ms** in 0.11 across eight presses per build. Play/Pause remained
**4.5 ms**. Killed-worker, cold-provider and idle-expiry recovery took 244,
505 and 407 ms respectively; these are individual observations, not bounds.
The first press after a cold start or idle expiry can still take a few hundred
milliseconds. Media keys already use the direct Android API. Short actions
continue to execute on release so holding the source button can customize it.

Each action sends a down/up pair. Application handling and Android policy
determine its effect; this is not a chord or held-key action. Generated keys are
marked so that another Send key binding cannot recursively send more keys.
Managed profiles cannot execute the added key actions.

## Validation

`python3 scripts/key_latency_smoke.py --lifecycle` measures Android uptime from
source-key release to the fixture's actual delivered event. It excludes host
ADB and sender startup time. It checks warm worker reuse, unrelated-caller
rejection after caching, killed-worker recovery, provider restart, idle expiry,
EOF and invalid input. See [validation-key-latency.json](../validation-key-latency.json)
for timings and the recorded 0.10 baseline. These are emulator delivery timings,
not physical-remote or screen-rendering measurements. Version 0.11 has not been
installed on the physical TV.

`python3 scripts/no_action_smoke.py` covers No action on all six Google receiver
paths, process-restart persistence, long-press editing, cancellation, restoration
and replacing it with Play/Pause or a custom key. See
[validation-no-action.json](../validation-no-action.json).

`python3 scripts/key_actions_smoke.py` uses the real native chooser and store,
an Android MediaSession with a silent AudioTrack, and a focused activity that
records delivered keys. It also exercises the module's actual root broker and
input helper. This fixture is not a third-party media player.

The script backs up and restores the native shortcut store and removes its
fixture APK and receiver helper. See
[validation-key-actions.json](../validation-key-actions.json) for the tested
build and checks. Version 0.9 is installed on the physical TV with assignments
preserved and receiver hooks reloaded; its new key actions were not exercised
there during deployment. See [0.9 TV update](../validation-tv-key-actions.json)
and the [prior user-assisted remote checks](../validation-tv-remote.json).
