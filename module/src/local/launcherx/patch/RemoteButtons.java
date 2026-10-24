package local.launcherx.patch;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.KeyEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.List;

/** Keep local remote actions available to the native anonymous account. */
final class RemoteButtons {
    private static final String TAG = "LauncherXLocal";
    private static final String EXTRA = "launcherx-remote-scope";
    private final Context context;
    private final Method keyCallback;
    private final Method launchCallback;
    private final Method anonymousCheck;
    private final Method basicInternetRule;
    private final Class<?> launchManager;
    private final boolean modern;
    private final ThreadLocal<Gate> current = new ThreadLocal<>();

    private static final class Gate {
        final Gate previous;
        final Method predicate;
        boolean consumed;

        Gate(Gate previous, Method predicate) {
            this.previous = previous;
            this.predicate = predicate;
        }
    }

    RemoteButtons(Context context, LauncherSymbols loader) throws Exception {
        this.context = context;
        modern = loader.modern;
        launchManager = loader.loadClass("ibq");
        loader.loadClass("gbp").getDeclaredField("e");
        if (modern) {
            // NativeShortcuts temporarily uses generic mode during anonymous
            // YouTube dispatch, before the inlined YouTube-only account gate.
            keyCallback = null;
            anonymousCheck = null;
            Class<?> launch = loader.loadClass("inw");
            launchCallback = launch.getDeclaredMethod("a", Object.class);
            if (launch.getDeclaredField("a").getType() != launchManager) {
                throw new IllegalStateException("Unexpected app launch callback");
            }
            basicInternetRule = loader.loadClass("oro").getDeclaredMethod("b", Context.class);
            return;
        }
        Class<?> callback = loader.loadClass("llx");
        keyCallback = callback.getDeclaredMethod("a");
        if (callback.getDeclaredField("c").getType() != KeyEvent.class) {
            throw new IllegalStateException("Unexpected global key callback");
        }
        Class<?> launch = loader.loadClass("eww");
        launchCallback = launch.getDeclaredMethod("apply", Object.class);
        launch.getDeclaredField("e");
        launch.getDeclaredField("a");
        Class<?> checks = loader.loadClass("nnd");
        anonymousCheck = checks.getDeclaredMethod("d", String.class);
        basicInternetRule = checks.getDeclaredMethod("b");
    }

    private boolean enabled() {
        return context.getPackageManager().getComponentEnabledSetting(new ComponentName(
                Compatibility.TARGET, Compatibility.TARGET + ".home.HomeActivity"))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    void install(List<XC_MethodHook.Unhook> hooks) {
        try {
            if (!modern) hooks.add(XposedBridge.hookMethod(keyCallback, scope(anonymousCheck)));
            hooks.add(XposedBridge.hookMethod(launchCallback, scope(basicInternetRule)));
            XC_MethodHook predicate = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam hook) {
                    Gate gate = current.get();
                    if (gate == null || gate.consumed || !gate.predicate.equals(hook.method)) return;
                    // Each pinned callback has one such check. Consume it before nested
                    // handlers run, leaving Freeplay, authentication and locks untouched.
                    gate.consumed = true;
                    if (hook.method.equals(anonymousCheck)
                            && !"pseudonymous".equals(hook.args[0])) return;
                    hook.setResult(false);
                    Log.i(TAG, "Incognito local remote action: "
                            + (hook.method.equals(anonymousCheck) ? "button" : "app launch"));
                }
            };
            if (!modern) hooks.add(XposedBridge.hookMethod(anonymousCheck, predicate));
            hooks.add(XposedBridge.hookMethod(basicInternetRule, predicate));
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : hooks) hook.unhook();
            throw error;
        }
    }

    private XC_MethodHook scope(Method predicate) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                if (!enabled()) return;
                if (predicate.equals(anonymousCheck)) {
                    KeyEvent event = (KeyEvent) XposedHelpers.getObjectField(hook.thisObject, "c");
                    int code = event.getKeyCode();
                    if (!NativeShortcuts.supported(code)) return;
                } else {
                    // The legacy merged callback needs an additional case guard.
                    if ((!modern && XposedHelpers.getIntField(hook.thisObject, "e") != 4)
                            || !launchManager.isInstance(XposedHelpers.getObjectField(hook.thisObject, "a"))
                            || !"pseudonymous".equals(XposedHelpers.getObjectField(hook.args[0], "e"))) return;
                }
                Gate gate = new Gate(current.get(), predicate);
                hook.setObjectExtra(EXTRA, gate);
                current.set(gate);
            }

            @Override protected void afterHookedMethod(MethodHookParam hook) {
                Gate gate = (Gate) hook.getObjectExtra(EXTRA);
                if (gate == null) return;
                if (gate.previous == null) current.remove();
                else current.set(gate.previous);
            }
        };
    }
}
