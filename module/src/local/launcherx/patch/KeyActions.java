package local.launcherx.patch;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Media keys use Android's session routing; other keys use the module's root broker. */
final class KeyActions {
    static final String PREFIX = "launcherx-key:";
    static final Uri BROKER = Uri.parse("content://local.launcherx.patch.keys");
    static final int[] COMMON = {85, 126, 127, 87, 88, 86, 90, 89, 3, 4, 187, 24, 25, 164, 82, 84};
    static final String[] LABELS = {"Play / Pause", "Play", "Pause", "Next track", "Previous track",
            "Stop", "Fast forward", "Rewind", "TV home", "Back", "Recent apps", "Volume up",
            "Volume down", "Mute", "Menu", "Search", "Other Android key…"};

    static int validate(int code) {
        if (code <= 0 || code > KeyEvent.getMaxKeyCode()) {
            throw new IllegalArgumentException("Enter an Android key code between 1 and " + KeyEvent.getMaxKeyCode());
        }
        return code;
    }

    static int parse(String value) {
        String text = value.trim();
        try {
            return validate(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            text = text.toUpperCase(java.util.Locale.ROOT);
            return validate(KeyEvent.keyCodeFromString(text.startsWith("KEYCODE_") ? text : "KEYCODE_" + text));
        }
    }

    static String label(int code) {
        for (int i = 0; i < COMMON.length; i++) if (COMMON[i] == code) return LABELS[i];
        return KeyEvent.keyCodeToString(code) + " (" + code + ")";
    }

    static boolean media(int code) {
        switch (code) {
            case 79: case 85: case 86: case 87: case 88: case 89: case 90:
            case 126: case 127: case 130: case 222: return true;
            default: return false;
        }
    }

    static boolean generated(KeyEvent event) {
        return event != null && (event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0;
    }

    static void prepare(Context context, int code) {
        validate(code);
        if (!media(code)) broker(context, "prepare", code);
    }

    static void send(Context context, int code) {
        validate(code);
        if (media(code)) {
            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            long time = SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0));
        } else {
            broker(context, "send", code);
        }
    }

    private static void broker(Context context, String method, int code) {
        Bundle result = context.getContentResolver().call(BROKER, method, Integer.toString(code), null);
        if (result == null || !result.getBoolean("ok")) {
            throw new IllegalStateException(result == null ? "LauncherX Local key service is unavailable"
                    : result.getString("error", "Allow LauncherX Local root access in Magisk"));
        }
    }
}
