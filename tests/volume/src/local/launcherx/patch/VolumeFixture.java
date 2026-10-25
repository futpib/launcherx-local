package local.launcherx.patch;

import android.content.Context;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import local.launcherx.fixture.AudioServiceBridge;

/** Run privately extracted Xiaomi bytecode and the production hook in the lab system_server. */
public final class VolumeFixture implements IXposedHookLoadPackage {
    private Object service;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!"android".equals(param.packageName) || Process.myUid() != Process.SYSTEM_UID) return;
        Class<?> properties = XposedHelpers.findClass("android.os.SystemProperties", null);
        if (!"1".equals(XposedHelpers.callStaticMethod(properties, "get", "ro.kernel.qemu"))
                || !"launcherx_local14".equals(XposedHelpers.callStaticMethod(properties, "get", "ro.boot.qemu.avd_name"))) return;
        Class<?> audioService = XposedHelpers.findClass("com.android.server.audio.AudioService", param.classLoader);
        XposedBridge.hookAllConstructors(audioService, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) { service = hook.thisObject; }
        });
        XposedHelpers.findAndHookMethod("com.android.server.input.InputManagerService", param.classLoader,
                "systemRunning", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                try {
                    if (service == null) throw new IllegalStateException("AudioService was not captured");
                    Context context = (Context) XposedHelpers.getObjectField(hook.thisObject, "mContext");
                    Class<?> xiaomi = Class.forName("local.launcherx.fixture.MitvAudioService", true, getClass().getClassLoader());
                    XposedHelpers.callStaticMethod(xiaomi, "init", context, new AudioServiceBridge(service));
                    // Keep production guards intact; only the fixture directly enters registration.
                    Method register = SystemAudio.class.getDeclaredMethod("register", Context.class, Class.class);
                    register.setAccessible(true);
                    register.invoke(null, context, xiaomi);
                    XposedHelpers.findAndHookMethod(audioService, "handleVolumeKey", KeyEvent.class,
                            boolean.class, String.class, String.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam event) {
                            KeyEvent key = (KeyEvent) event.args[0];
                            boolean tv = (Boolean) event.args[1];
                            if (!tv && key.getAction() != KeyEvent.ACTION_DOWN) return;
                            int mode = tv ? (key.getAction() == KeyEvent.ACTION_DOWN ? 1 : 2) : 0;
                            XposedHelpers.callStaticMethod(xiaomi, "handleVolumeKey", key, tv, event.args[2], event.args[3], mode);
                            event.setResult(null);
                        }
                    });
                    Log.i("LauncherXVolumeFixture", "Original Xiaomi gesture bytecode active in emulator system_server");
                } catch (Throwable error) {
                    Log.e("LauncherXVolumeFixture", "Fixture failed", error);
                }
            }
        });
    }
}
