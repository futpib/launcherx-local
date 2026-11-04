package local.launcherx.patch;

import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Keep the owner's system Cast receiver running when Android caches its process. */
final class CastFreezer {
    private static final String PACKAGE = "com.google.android.apps.mediashell";

    static void load(XC_LoadPackage.LoadPackageParam param) {
        if (!"android".equals(param.processName) || Process.myUid() != Process.SYSTEM_UID) return;
        if (Build.VERSION.SDK_INT != 34) return;
        try {
            Class<?> service = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", param.classLoader);
            Class<?> process = XposedHelpers.findClass("com.android.server.am.ProcessRecord", param.classLoader);
            Class<?> optimizer = XposedHelpers.findClass("com.android.server.am.ProcessCachedOptimizerRecord", param.classLoader);
            Constructor<?> constructor = process.getDeclaredConstructor(service, ApplicationInfo.class,
                    String.class, int.class, String.class, int.class, String.class);
            Field record = process.getDeclaredField("mOptRecord");
            Method exempt = optimizer.getDeclaredMethod("setFreezeExempt", boolean.class);
            if (record.getType() != optimizer || exempt.getReturnType() != void.class
                    || Modifier.isStatic(exempt.getModifiers()) || Modifier.isStatic(record.getModifiers())) {
                throw new IllegalStateException("Unexpected Android freezer record");
            }
            record.setAccessible(true);
            exempt.setAccessible(true);
            XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    if (hook.hasThrowable()) return;
                    ApplicationInfo info = (ApplicationInfo) hook.args[1];
                    int uid = (Integer) hook.args[3];
                    if (info == null || !PACKAGE.equals(info.packageName)
                            || !PACKAGE.equals(hook.args[2]) || uid != info.uid
                            || uid / 100000 != 0 || (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) return;
                    try {
                        // Set Android's own exemption before the process starts or can be frozen.
                        exempt.invoke(record.get(hook.thisObject), true);
                        Log.i("LauncherXLocal", "Exempted owner Cast receiver from cached-app freezer");
                    } catch (Throwable error) {
                        Log.e("LauncherXLocal", "Cast freezer exemption failed", error);
                    }
                }
            });
            Log.i("LauncherXLocal", "Installed Cast freezer exemption");
        } catch (Throwable error) {
            Log.e("LauncherXLocal", "Cast freezer hook refused", error);
        }
    }
}
