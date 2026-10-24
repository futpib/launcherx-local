# Send a key from a remote shortcut

Version **0.9** adds **Send key…** to the native shortcut chooser for each of the
six supported buttons. Hold the source button, press Up to reach the added
controls, then move right to Send key. Select a key and confirm Save. Short press
sends the chosen key; long press still opens customization.

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

Before disabling or uninstalling the module, restore any key actions using
these controls. Stock Google TV cannot execute the module's saved key actions;
normal favorite-app assignments remain native Google TV data.

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

Each action sends a down/up pair. Application handling and Android policy
determine its effect; this is not a chord or held-key action. Generated keys are
marked so that another Send key binding cannot recursively send more keys.
Managed profiles cannot execute the added key actions.

## Validation

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
