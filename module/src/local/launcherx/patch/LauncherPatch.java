package local.launcherx.patch;

import android.accounts.AccountManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LauncherPatch implements IXposedHookLoadPackage {
    private static final String TARGET = "com.google.android.apps.tv.launcherx";
    private static final String TAG = "LauncherXLocal";
    private static final Set<String> logged = new HashSet<>();
    private Context context;
    private boolean installed;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!TARGET.equals(param.packageName) || !TARGET.equals(param.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                if (installed) return;
                context = (Context) hook.args[0];
                try {
                    verifyBuild(context);
                    installHooks(param.classLoader);
                    installed = true;
                    event("installed mode=" + BuildConfig.MODE + " version=" + BuildConfig.VERSION_CODE);
                } catch (Throwable error) {
                    Log.e(TAG, "Patch refused; launcher remains unmodified", error);
                    XposedBridge.log(error);
                }
            }
        });
    }

    private boolean anonymous() {
        try {
            return AccountManager.get(context).getAccountsByType("com.google").length == 0;
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot verify account state; leaving original behavior", error);
            return false;
        }
    }

    private static void verifyBuild(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(TARGET, 0);
        if (info.getLongVersionCode() != BuildConfig.VERSION_CODE) {
            throw new IllegalStateException("Unsupported launcher version " + info.getLongVersionCode());
        }
        // Gate on executable code, allowing separately documented emulator ABI adaptation.
        try (ZipFile apk = new ZipFile(info.applicationInfo.sourceDir)) {
            Set<String> dexNames = new HashSet<>();
            java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".dex") && !name.contains("/")) dexNames.add(name);
            }
            if (dexNames.size() != BuildConfig.DEX_HASHES.length) {
                throw new IllegalStateException("Unexpected executable code entries");
            }
            for (String[] expected : BuildConfig.DEX_HASHES) {
                ZipEntry entry = apk.getEntry(expected[0]);
                if (entry == null) throw new IllegalStateException("Missing " + expected[0]);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = apk.getInputStream(entry)) {
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                }
                StringBuilder hex = new StringBuilder();
                for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
                if (!expected[1].contentEquals(hex)) throw new IllegalStateException("Code mismatch: " + expected[0]);
            }
        }
    }

    private void installHooks(ClassLoader loader) throws Exception {
        Class<?> activityPeer = loader.loadClass("isa");
        Class<?> rootPeer = loader.loadClass("isn");
        Class<?> appsOnlyObserver = loader.loadClass("isf");
        Class<?> accountCallback = loader.loadClass("kye");
        Class<?> accountVerifier = loader.loadClass("kxv");
        Class<?> localHome = loader.loadClass("hsx");
        Class<?> destinations = loader.loadClass("lhw");
        Class<?> futures = loader.loadClass("xgr");
        Class<?> cacheResult = loader.loadClass("vuw");
        Class<?> instant = loader.loadClass("j$.time.Instant");
        Class<?> channels = loader.loadClass("lft");
        Class<?> profileObserver = loader.loadClass("idj");
        Method profile = profileObserver.getDeclaredMethod("b", Object.class);
        Method theme = activityPeer.getDeclaredMethod("x", String.class);
        Method showHome = rootPeer.getDeclaredMethod("h", boolean.class);
        Method appsOnly = appsOnlyObserver.getDeclaredMethod("b", Object.class);
        Method recovery = accountCallback.getDeclaredMethod("apply", Object.class);
        rootPeer.getDeclaredField("r");
        rootPeer.getDeclaredField("A");
        accountCallback.getDeclaredField("a");
        accountCallback.getDeclaredField("b");
        Method localChannels = localHome.getDeclaredMethod("c");
        Method errorChannels = localHome.getDeclaredMethod("b", String.class);
        Method loadDestinations = destinations.getDeclaredMethod("d");
        Method refreshDestinations = destinations.getDeclaredMethod("b");
        localHome.getDeclaredConstructor(Object.class);
        localHome.getDeclaredMethod("d");
        futures.getDeclaredMethod("W", Object.class);
        cacheResult.getDeclaredMethod("a", Object.class, instant);
        instant.getDeclaredMethod("ofEpochMilli", long.class);
        channels.getDeclaredConstructor(List.class);
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            if (BuildConfig.EMULATOR_NETWORK) hooks.add(EmulatorNetwork.install(context, loader));
            hooks.add(XposedBridge.hookMethod(profile, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                    if (!anonymous()) return;
                    Object item = hook.args[0];
                    if (!"pseudonymous".equals(XposedHelpers.getObjectField(item, "k"))) return;
                    Object builder = XposedHelpers.callMethod(item, "toBuilder");
                    Object value = XposedHelpers.getObjectField(builder, "b");
                    XposedHelpers.setObjectField(value, "d", "Local TV");
                    XposedHelpers.setObjectField(value, "g", "");
                    hook.args[0] = XposedHelpers.callMethod(builder, "r");
                    event("named the existing pseudonymous profile Local TV");
                }
            }));
            if (BuildConfig.APPS_ONLY) {
                XC_MethodHook localDestinations = new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                        if (!anonymous()) return;
                        Object factory = localHome.getConstructor(Object.class).newInstance(context);
                        Object home = XposedHelpers.callMethod(factory, "d");
                        if ("d".equals(hook.method.getName())) {
                            Object expiry = XposedHelpers.callStaticMethod(instant, "ofEpochMilli", Long.MAX_VALUE);
                            home = XposedHelpers.callStaticMethod(cacheResult, "a", home, expiry);
                        }
                        hook.setResult(XposedHelpers.callStaticMethod(futures, "W", home));
                        event("using bundled local home destination without a feed request");
                    }
                };
                hooks.add(XposedBridge.hookMethod(loadDestinations, localDestinations));
                hooks.add(XposedBridge.hookMethod(refreshDestinations, localDestinations));
                XC_MethodHook appsChannelOnly = new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam hook) throws Throwable {
                        if (!anonymous() || hook.hasThrowable()) return;
                        List<?> rows = (List<?>) XposedHelpers.getObjectField(hook.getResult(), "a");
                        for (Object row : rows) {
                            if ("apps_channel".equals(XposedHelpers.getObjectField(row, "a"))) {
                                hook.setResult(channels.getConstructor(List.class)
                                        .newInstance(Collections.singletonList(row)));
                                event("rendering official local apps channel without the cloud error panel");
                                return;
                            }
                        }
                        Log.e(TAG, "Local apps channel missing; retaining original fallback");
                    }
                };
                hooks.add(XposedBridge.hookMethod(localChannels, appsChannelOnly));
                hooks.add(XposedBridge.hookMethod(errorChannels, appsChannelOnly));
            }
            hooks.add(XposedBridge.hookMethod(theme, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (anonymous() && "vanilla_mode".equals(hook.args[0])) {
                        hook.args[0] = "adult";
                        event("anonymous profile uses adult home theme");
                    }
                }
            }));
            hooks.addAll(XposedBridge.hookAllConstructors(rootPeer, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam hook) {
                    if (!anonymous()) return;
                    XposedHelpers.setBooleanField(hook.thisObject, "r", false);
                    event("anonymous profile allowed into GuideHomeFragment");
                }
            }));
            hooks.add(XposedBridge.hookMethod(showHome, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (!anonymous()) return;
                    XposedHelpers.setBooleanField(hook.thisObject, "r", false);
                    XposedHelpers.setBooleanField(hook.thisObject, "A", BuildConfig.APPS_ONLY);
                    event("show official guide; appsOnly=" + BuildConfig.APPS_ONLY);
                }
            }));
            hooks.add(XposedBridge.hookMethod(appsOnly, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    if (anonymous()) hook.args[0] = Boolean.valueOf(BuildConfig.APPS_ONLY);
                }
            }));
            hooks.add(XposedBridge.hookMethod(recovery, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    // This synthetic callback serves many unrelated call sites; only case 1 is recovery.
                    if (XposedHelpers.getIntField(hook.thisObject, "b") == 1
                            && accountVerifier.isInstance(XposedHelpers.getObjectField(hook.thisObject, "a"))
                            && anonymous()) {
                        hook.setResult(null);
                        event("suppressed local no-account recovery to Basic TV");
                    }
                }
            }));
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            throw error;
        }
    }

    private static synchronized void event(String message) {
        if (logged.add(message)) Log.i(TAG, message);
    }

}
