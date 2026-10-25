package local.launcherx.patch;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;

/** All key bindings use Android input dispatch through an authenticated hook request. */
final class KeyActions {
    static final String PREFIX = "launcherx-key:";
    static final int NONE = -1;
    static final Uri BROKER = Uri.parse("content://local.launcherx.patch.keys");
    static final int[] COMMON = {85, 126, 127, 87, 88, 86, 90, 89, 3, 4, 187, 24, 25, 164, 82, 84, NONE};
    static final String[] LABELS = {"Play / Pause", "Play", "Pause", "Next track", "Previous track",
            "Stop", "Fast forward", "Rewind", "TV home", "Back", "Recent apps", "Volume up",
            "Volume down", "Mute", "Menu", "Search", "No action", "Other Android key…"};

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

    static boolean generated(KeyEvent event) {
        return event != null && (event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0;
    }

    static void prepare(Context context, int code) {
        if (code == NONE) return;
        validate(code);
        broker(context, "prepare", code);
    }

    static void send(Context context, int code) {
        if (code == NONE) return;
        validate(code);
        broker(context, "send", code);
    }

    private static void broker(Context context, String method, int code) {
        Bundle result = context.getContentResolver().call(BROKER, method, Integer.toString(code), null);
        if (result == null || !result.getBoolean("ok")) {
            throw new IllegalStateException(result == null ? "LauncherX Local key service is unavailable"
                    : result.getString("error", "Check the key input scope in LSPosed"));
        }
    }
}
