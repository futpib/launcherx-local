package local.launcherx.probe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public final class Probe extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent request) {
        PendingResult result = goAsync();
        boolean keys = request.getBooleanExtra("keys", false);
        boolean volume = request.getBooleanExtra("volume", false);
        Intent intent = new Intent(volume ? "local.launcherx.patch.VOLUME_GESTURE"
                : keys ? "local.launcherx.patch.INJECT_KEY" : "local.launcherx.patch.APP_CHANNELS")
                .setPackage(keys || volume ? "android" : "com.google.android.apps.tv.launcherx")
                .putExtra("operation", volume ? "set" : "send").putExtra("code", 138)
                .putExtra("disabled", false)
                .putExtra("local.launcherx.patch.deadline", SystemClock.elapsedRealtime() + 10000);
        context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override public void onReceive(Context unused, Intent response) {
                result.setResultData("permission=" + context.checkSelfPermission(
                        "local.launcherx.patch.permission.CONTROL") + ";reply=" + getResultData());
                result.finish();
            }
        }, null, 0, null, null);
    }
}
