package local.launcherx.patch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;

/** Execute bounded key requests in an already privileged, explicitly scoped process. */
final class KeyInjection {
    static final String ACTION = "local.launcherx.patch.INJECT_KEY";
    private static boolean registered;

    static void loadSystem(XC_LoadPackage.LoadPackageParam param) {
        if (!"android".equals(param.processName) || Process.myUid() != Process.SYSTEM_UID) return;
        try {
            XposedHelpers.findAndHookMethod("com.android.server.input.InputManagerService",
                    param.classLoader, "systemRunning", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    try {
                        register((Context) XposedHelpers.getObjectField(hook.thisObject, "mContext"));
                    } catch (Throwable error) {
                        Log.e("LauncherXLocal", "System key hook unavailable", error);
                    }
                }
            });
        } catch (Throwable error) {
            Log.e("LauncherXLocal", "System key hook refused", error);
        }
    }

    static synchronized void register(Context context) throws Exception {
        if (registered) return;
        if (Process.myUid() != Process.SYSTEM_UID || context.checkSelfPermission(
                "android.permission.INJECT_EVENTS") != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Key host requires system UID and INJECT_EVENTS");
        }
        Object input = context.getSystemService(Context.INPUT_SERVICE);
        Method inject = Class.forName("android.hardware.input.InputManager")
                .getMethod("injectInputEvent", InputEvent.class, int.class);
        HandlerThread worker = new HandlerThread("LauncherX-key-input");
        worker.start();
        try {
            HookRequest.register(context, new BroadcastReceiver() {
                @Override public void onReceive(Context unused, Intent intent) {
                    try {
                        HookRequest.validate(this, intent);
                        int code = KeyActions.validate(intent.getIntExtra("code", 0));
                        String operation = intent.getStringExtra("operation");
                        if ("send".equals(operation)) send(input, inject, code);
                        else if (!"prepare".equals(operation)) throw new IllegalArgumentException("Unknown key operation");
                        setResultData("OK");
                    } catch (Exception error) {
                        setResultData("Key request failed: " + error.getMessage());
                    }
                }
            }, ACTION, new Handler(worker.getLooper()));
            registered = true;
            Log.i("LauncherXLocal", "Registered key input in " + context.getPackageName());
        } catch (Exception error) {
            worker.quitSafely();
            throw error;
        }
    }

    private static void send(Object input, Method inject, int code) throws Exception {
        long down = SystemClock.uptimeMillis();
        boolean delivered = true;
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(down, SystemClock.uptimeMillis(), action, code, 0, 0, -1, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_FALLBACK, InputDevice.SOURCE_KEYBOARD);
            delivered &= (Boolean) inject.invoke(input, event, 2);
        }
        if (!delivered) throw new IllegalStateException("Android could not deliver " + KeyEvent.keyCodeToString(code));
    }
}
