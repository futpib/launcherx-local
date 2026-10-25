package local.launcherx.fixture;

import android.os.Process;
import de.robv.android.xposed.XposedHelpers;

/** Adapt Xiaomi's three gesture-facing audio calls to the actual AOSP audio service. */
public final class AudioServiceBridge {
    private final Object service;

    public AudioServiceBridge(Object service) { this.service = service; }

    public int getStreamVolumeForMute(int stream) {
        return (Integer) XposedHelpers.callMethod(service, "getLastAudibleStreamVolume", stream);
    }

    public boolean isStreamMute(int stream) {
        return (Boolean) XposedHelpers.callMethod(service, "isStreamMute", stream);
    }

    public void adjustSuggestedStreamVolume(int direction, int stream, int flags, String caller, String attribution, int mode) {
        XposedHelpers.callMethod(service, "adjustSuggestedStreamVolume", direction, stream, flags,
                caller, attribution, Process.SYSTEM_UID, Process.myPid(), true, mode);
    }
}
