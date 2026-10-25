package local.launcherx.patch;

import android.os.Build;
import android.os.Process;
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
    private static final String SERVICES_SHA256 = "d09f1610b9521b695a74f1703054e0adfd142ff2f5d94fd756ab29a31b4d57ca";

    static void load(XC_LoadPackage.LoadPackageParam param) {
        if (!"android".equals(param.processName) || Process.myUid() != Process.SYSTEM_UID) return;
        if (Build.VERSION.SDK_INT != 34 || !"ladybird".equals(Build.DEVICE)) return;
        try {
            verifyFirmware();
            Class<?> audio = XposedHelpers.findClass("com.android.server.audio.MitvAudioService", param.classLoader);
            install(audio);
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

    private static void install(Class<?> audio) throws Exception {
        Method timer = audio.getDeclaredMethod("JudgeMuteTimer");
        if (!Modifier.isStatic(timer.getModifiers()) || timer.getReturnType() != void.class) {
            throw new IllegalStateException("Unexpected Xiaomi mute timer signature");
        }
        XposedHelpers.findAndHookMethod(audio, "JudgeMuteTimer", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                // Skip only this gesture's delayed mute; all volume-key handling stays original.
                hook.setResult(null);
            }
        });
        Log.i("LauncherXLocal", "Disabled Xiaomi double volume-down mute");
    }
}
