package local.launcherx.patch;

import android.content.Context;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BooleanSupplier;

/** The newer navigation service uses suspend functions rather than the legacy feed loader. */
final class NavigationDetails {
    static void install(Context context, LauncherSymbols loader, BooleanSupplier enabled,
            List<XC_MethodHook.Unhook> hooks) throws Exception {
        Class<?> service = loader.loadClass("lrs"), continuation = loader.loadClass("addc");
        Method read = service.getDeclaredMethod("l", continuation);
        Method fetch = service.getDeclaredMethod("k", continuation);
        service.getDeclaredMethod("q", loader.loadClass("zhw"));
        Class<?> unit = loader.loadClass("adbk");
        unit.getDeclaredField("a");
        Class<?> factory = loader.loadClass("hsx"), cache = loader.loadClass("vuw");
        Class<?> instant = loader.loadClass("j$.time.Instant");
        hooks.add(XposedBridge.hookMethod(read, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                if (!enabled.getAsBoolean()) return;
                Object home = XposedHelpers.callMethod(factory.getConstructor(Object.class).newInstance(context), "c");
                Object navigation = XposedHelpers.callMethod(hook.thisObject, "q", home);
                Object expiry = XposedHelpers.callStaticMethod(instant, "ofEpochMilli", Long.MAX_VALUE);
                hook.setResult(XposedHelpers.callStaticMethod(cache, "a", navigation, expiry));
                Log.i("LauncherXLocal", "using bundled local navigation without a feed request");
            }
        }));
        hooks.add(XposedBridge.hookMethod(fetch, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                if (enabled.getAsBoolean()) hook.setResult(XposedHelpers.getStaticObjectField(unit, "a"));
            }
        }));
    }
}
