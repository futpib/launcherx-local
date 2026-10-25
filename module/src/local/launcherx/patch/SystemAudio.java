package local.launcherx.patch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;

/** Optional Xiaomi audio fix, independent of launcher compatibility and input injection. */
final class SystemAudio {
    static final String ACTION = "local.launcherx.patch.VOLUME_GESTURE";
    private static final String SETTING = "launcherx_disable_double_volume_down_mute";
    private static final String SERVICES_SHA256 = "d09f1610b9521b695a74f1703054e0adfd142ff2f5d94fd756ab29a31b4d57ca";
    private static volatile boolean disabled;

    static void load(XC_LoadPackage.LoadPackageParam param) {
        if (!"android".equals(param.processName) || Process.myUid() != Process.SYSTEM_UID) return;
        if (Build.VERSION.SDK_INT != 34 || !"ladybird".equals(Build.DEVICE)) return;
        try {
            verifyFirmware();
            Class<?> audio = XposedHelpers.findClass("com.android.server.audio.MitvAudioService", param.classLoader);
            Method timer = audio.getDeclaredMethod("JudgeMuteTimer");
            if (!Modifier.isStatic(timer.getModifiers()) || timer.getReturnType() != void.class) {
                throw new IllegalStateException("Unexpected Xiaomi mute timer signature");
            }
            XposedHelpers.findAndHookMethod("com.android.server.input.InputManagerService",
                    param.classLoader, "systemRunning", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    try {
                        Context context = (Context) XposedHelpers.getObjectField(hook.thisObject, "mContext");
                        register(context, audio);
                    } catch (Throwable error) {
                        disabled = false;
                        Log.e("LauncherXLocal", "Xiaomi volume gesture hook unavailable", error);
                    }
                }
            });
        } catch (Throwable error) {
            Log.e("LauncherXLocal", "Xiaomi volume gesture hook refused", error);
        }
    }

    private static void verifyFirmware() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream("/system/framework/services.jar")) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
        if (!SERVICES_SHA256.contentEquals(hex)) throw new IllegalStateException("Unreviewed services.jar");
    }

    private static void register(Context context, Class<?> audio) {
        disabled = Settings.Global.getInt(context.getContentResolver(), SETTING, 0) == 1;
        XC_MethodHook.Unhook timer = XposedHelpers.findAndHookMethod(audio, "JudgeMuteTimer", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                if (!disabled) return;
                // Skip only this gesture's delayed mute; all volume-key handling stays original.
                hook.setResult(null);
                Log.i("LauncherXLocal", "Suppressed Xiaomi double volume-down mute");
            }
        });
        try {
            HookRequest.register(context, new BroadcastReceiver() {
                @Override public void onReceive(Context unused, Intent intent) {
                    try {
                        HookRequest.validate(this, intent);
                        String operation = intent.getStringExtra("operation");
                        if ("set".equals(operation)) {
                            if (!intent.hasExtra("disabled")) throw new IllegalArgumentException("Missing setting");
                            boolean value = intent.getBooleanExtra("disabled", false);
                            if (!Settings.Global.putInt(context.getContentResolver(), SETTING, value ? 1 : 0)) {
                                throw new IllegalStateException("Cannot save volume gesture setting");
                            }
                            disabled = value;
                        } else if (!"get".equals(operation)) {
                            throw new IllegalArgumentException("Unknown volume gesture operation");
                        }
                        setResultData(disabled ? "disabled" : "enabled");
                    } catch (Exception error) {
                        setResultData("Volume gesture request failed: " + error.getMessage());
                    }
                }
            }, ACTION, new Handler(Looper.getMainLooper()));
            Log.i("LauncherXLocal", "Registered Xiaomi volume gesture control; disabled=" + disabled);
        } catch (RuntimeException error) {
            timer.unhook();
            disabled = false;
            throw error;
        }
    }
}
