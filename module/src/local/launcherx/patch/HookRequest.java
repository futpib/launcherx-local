package local.launcherx.patch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Only apps signed with the module's certificate may call its hooked receivers. */
final class HookRequest {
    static final String PERMISSION = "local.launcherx.patch.permission.CONTROL";
    private static final String MODULE = "local.launcherx.patch";
    private static final String DEADLINE = "local.launcherx.patch.deadline";
    private static Context host;
    private static Signature[] moduleSignatures;

    static synchronized void register(Context context, BroadcastReceiver receiver, String action, Handler handler) {
        if (moduleSignatures == null) {
            try {
                moduleSignatures = context.getPackageManager().getPackageInfo(MODULE, PackageManager.GET_SIGNATURES).signatures;
            } catch (PackageManager.NameNotFoundException error) {
                throw new SecurityException("Module is not installed", error);
            }
            if (moduleSignatures == null || moduleSignatures.length == 0) throw new SecurityException("Module signer is unavailable");
            host = context;
        }
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, PERMISSION, handler, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter, PERMISSION, handler);
        }
    }

    static void validate(BroadcastReceiver receiver, Intent intent) throws Exception {
        // A persistent hook must not trust a replacement permission owner after uninstall.
        PackageManager packages = host.getPackageManager();
        PermissionInfo permission = packages.getPermissionInfo(PERMISSION, 0);
        if (!MODULE.equals(permission.packageName) || permission.protectionLevel != PermissionInfo.PROTECTION_SIGNATURE
                || !Arrays.equals(moduleSignatures, packages.getPackageInfo(MODULE, PackageManager.GET_SIGNATURES).signatures)) {
            throw new SecurityException("Module permission owner changed; reboot after installing the trusted module");
        }
        long remaining = intent.getLongExtra(DEADLINE, 0) - SystemClock.elapsedRealtime();
        int user = (Integer) BroadcastReceiver.class.getMethod("getSendingUserId").invoke(receiver);
        if (user != 0 || remaining <= 0 || remaining > 10000) {
            throw new SecurityException("Invalid or expired owner-user request");
        }
    }

    static String call(Context context, Intent intent) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Hook requests must run off the main thread");
        }
        CountDownLatch ready = new CountDownLatch(1);
        String[] response = new String[1];
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | 0x01000000 /* FLAG_RECEIVER_INCLUDE_BACKGROUND */);
        intent.putExtra(DEADLINE, SystemClock.elapsedRealtime() + 10000);
        context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override public void onReceive(Context unused, Intent result) {
                response[0] = getResultData();
                ready.countDown();
            }
        }, new Handler(Looper.getMainLooper()), 0, null, null);
        if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Hook request timed out");
        if (response[0] == null) throw new IllegalStateException("Hook is unavailable; check LSPosed scope and restart");
        return response[0];
    }
}
