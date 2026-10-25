package local.launcherx.patch;

import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;

/** Delegate just the four branded Xiaomi shortcuts, retaining the original fallback. */
final class XiaomiRemote {
    static final String PACKAGE = "mitv.service";
    static final String BRIDGE = "local.launcherx.remote.bridge";
    static final String FALLBACK = "local.launcherx.remote.fallback";
    static final int ACCEPTED = 0x4c58;
    private static final String RECEIVER = "mitv.service.features.globalkey.GlobalKeyReceiver";
    private static final String TAG = "LauncherXLocal";

    static void load(XC_LoadPackage.LoadPackageParam param) {
        if (!"xiaomi.tvservice".equals(param.processName)
                || android.os.Process.myUid() != 1000) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                Context context = (Context) hook.args[0];
                try {
                    Compatibility.verify(context);
                    Compatibility.verify(context, PACKAGE, 110, new String[][] {{"classes.dex",
                            "33d06a3edd7d1e229916b85af034513d33d0060d4ee070b03bd84d62502a0c36"}});
                    install(param.classLoader);
                    try {
                        KeyInjection.register(context);
                    } catch (Exception error) {
                        Log.w(TAG, "Xiaomi key input unavailable; System Framework scope can provide it", error);
                    }
                    Log.i(TAG, "installed Xiaomi shortcut bridge");
                } catch (Throwable error) {
                    Log.e(TAG, "Xiaomi shortcut bridge refused; retaining original handler", error);
                }
            }
        });
    }

    private static void install(ClassLoader loader) throws Exception {
        Method receive = loader.loadClass(RECEIVER).getDeclaredMethod("onReceive", Context.class, Intent.class);
        XposedBridge.hookMethod(receive, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                Context context = (Context) hook.args[0];
                Intent original = (Intent) hook.args[1];
                if (!"android.intent.action.GLOBAL_BUTTON".equals(original.getAction())
                        || original.getBooleanExtra(FALLBACK, false)) return;
                KeyEvent event = original.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null || !NativeShortcuts.oem(event.getKeyCode())
                        || Settings.Secure.getInt(context.getContentResolver(), "tv_user_setup_complete", 0) == 0
                        || !NativeShortcuts.enabled(context)) return;
                BroadcastReceiver receiver = (BroadcastReceiver) hook.thisObject;
                Intent forwarded = new Intent(original).setClassName(Compatibility.TARGET,
                        Compatibility.TARGET + ".remotecontrol.GlobalKeyListener_Receiver");
                forwarded.putExtra(BRIDGE, true);
                PendingIntent fallback = event.getAction() == KeyEvent.ACTION_UP
                        ? PendingIntent.getBroadcast(context, (int) event.getDownTime() * 31 + event.getKeyCode(),
                            new Intent(original).setClassName(PACKAGE, RECEIVER).putExtra(FALLBACK, true),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                                    | PendingIntent.FLAG_ONE_SHOT) : null;
                if (fallback != null) forwarded.putExtra(FALLBACK, fallback);
                // This verified service is persistent. Finish the incoming broadcast
                // normally so the result callback can run in the same process.
                try {
                    context.sendOrderedBroadcast(forwarded, null, new BroadcastReceiver() {
                        @Override public void onReceive(Context unused, Intent result) {
                            try {
                                if (getResultCode() != ACCEPTED) {
                                    XposedBridge.invokeOriginalMethod(receive, receiver,
                                            new Object[] {context, original});
                                }
                            } catch (Throwable error) {
                                Log.e(TAG, "Original Xiaomi shortcut failed", error);
                            } finally {
                                if (fallback != null) fallback.cancel();
                            }
                        }
                    }, new Handler(Looper.getMainLooper()), 0, null, null);
                } catch (RuntimeException error) {
                    if (fallback != null) fallback.cancel();
                    XposedBridge.invokeOriginalMethod(receive, receiver, new Object[] {context, original});
                    Log.w(TAG, "Google TV unavailable; used original Xiaomi shortcut", error);
                }
                hook.setResult(null);
            }
        });
    }
}
