package local.launcherx.patch;

import android.content.Context;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** Fixture only: an ARM app cannot load the x86 Cronet library supplied by emulator GMS. */
final class EmulatorNetwork {
    static XC_MethodHook.Unhook install(Context context, ClassLoader loader) throws Exception {
        Class<?> properties = Class.forName("android.os.SystemProperties");
        String qemu = (String) XposedHelpers.callStaticMethod(properties, "get", "ro.kernel.qemu");
        String avd = (String) XposedHelpers.callStaticMethod(properties, "get", "ro.boot.qemu.avd_name");
        if (!"1".equals(qemu) || !"launcherx_local14".equals(avd)) {
            throw new IllegalStateException("Emulator fixture refused on this device");
        }
        Object provider = loader.loadClass("org.chromium.net.impl.HttpEngineNativeProvider")
                .getConstructor(Context.class).newInstance(context);
        if (!Boolean.TRUE.equals(XposedHelpers.callMethod(provider, "isEnabled"))) {
            throw new IllegalStateException("Emulator HttpEngine unavailable");
        }
        XC_MethodHook.Unhook hook = XposedHelpers.findAndHookMethod("org.chromium.net.ExperimentalCronetEngine", loader,
                "shouldOverrideWithHttpEngine", Context.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam hook) {
                        hook.setResult(true);
                    }
                });
        Log.i("LauncherXLocal", "EMULATOR FIXTURE: using platform HttpEngine for real network requests");
        return hook;
    }
}
