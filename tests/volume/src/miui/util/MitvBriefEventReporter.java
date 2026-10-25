package miui.util;

import java.util.Map;

/** The emulator has no Xiaomi analytics service. */
public final class MitvBriefEventReporter {
    public static void reportCountEvent(String category, String event, Map<?, ?> values) {}
}
