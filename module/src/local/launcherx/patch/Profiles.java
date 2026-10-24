package local.launcherx.patch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Keep the native anonymous account available beside Google's real profiles. */
final class Profiles {
    private static final String TAG = "LauncherXLocal";
    private static final String ANONYMOUS = "pseudonymous";
    private final Context context;
    private final LauncherSymbols loader;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Class<?> accountClass;
    private final Class<?> chooserPeerClass;
    private final Method initialSelection;
    private final Method accountChanged;
    private final Method chooserData;
    private final Method accountSelected;
    private volatile Object selector;
    private volatile Object accounts;
    private volatile String activeType;
    private WeakReference<Object> home = new WeakReference<>(null);

    Profiles(Context context, LauncherSymbols loader) throws Exception {
        this.context = context;
        this.loader = loader;
        preferences = context.getSharedPreferences("launcherx-local-profiles", Context.MODE_PRIVATE);
        accountClass = loader.loadClass("vjm");
        chooserPeerClass = loader.loadClass("kxc");
        initialSelection = loader.loadClass("kve").getDeclaredMethod("a", loader.loadClass("vip"));
        accountChanged = loader.loadClass("isa").getDeclaredMethod(loader.pick("g", "e"), loader.loadClass("yjv"));
        chooserData = loader.loadClass("kwx").getDeclaredMethod("b", Object.class);
        accountSelected = loader.loadClass("juj").getDeclaredMethod("a", loader.loadClass("wqw"));
        loader.loadClass("kve").getDeclaredField("c");
        loader.loadClass("kve").getDeclaredField("d");
        if (loader.modern) {
            loader.loadClass("isa").getDeclaredMethod("k", boolean.class, boolean.class,
                    String.class, boolean.class, boolean.class, boolean.class, boolean.class, loader.loadClass("vhe"));
        } else {
            loader.loadClass("isa").getDeclaredMethod("k", boolean.class, boolean.class,
                    String.class, boolean.class, boolean.class, loader.loadClass("vhe"));
        }
        loader.loadClass("vnq").getDeclaredMethod("a", loader.loadClass("vip"));
        loader.loadClass("vjn").getDeclaredMethod("c", loader.loadClass("vhe"));
        Class<?> list = loader.loadClass("wyc"), optional = loader.loadClass("j$.util.Optional");
        if (loader.modern) {
            loader.loadClass("kwf").getDeclaredConstructor(list, list, list, optional, int.class, optional, boolean.class);
        } else {
            loader.loadClass("kwf").getDeclaredConstructor(list, list, list, optional, int.class, optional);
        }
        loader.loadClass("kwe").getDeclaredConstructor(accountClass, Bitmap.class, Object.class,
                int.class, boolean.class);
        chooserPeerClass.getDeclaredMethod("f", accountClass, String.class);
    }

    boolean enabled() {
        return BuildConfig.APPS_ONLY && context.getPackageManager().getComponentEnabledSetting(
                new ComponentName(Compatibility.TARGET, Compatibility.TARGET + ".home.HomeActivity"))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    boolean incognito() {
        return enabled() && (activeType == null ? preferredIncognito() : ANONYMOUS.equals(activeType));
    }

    private boolean preferredIncognito() {
        return preferences.getBoolean("incognito", true);
    }

    Object label(Object info) {
        Object builder = XposedHelpers.callMethod(info, "toBuilder");
        Object value = XposedHelpers.getObjectField(builder, "b");
        XposedHelpers.setObjectField(value, "d", "Incognito");
        XposedHelpers.setObjectField(value, "e", "Incognito");
        XposedHelpers.setObjectField(value, "g", "");
        XposedHelpers.setIntField(value, "b", XposedHelpers.getIntField(value, "b") | 2 | 4 | 16);
        return loader.finish(builder);
    }

    void install(List<XC_MethodHook.Unhook> hooks) throws Exception {
        hooks.add(XposedBridge.hookMethod(initialSelection, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                selector = XposedHelpers.getObjectField(hook.thisObject, "d");
                accounts = XposedHelpers.getObjectField(hook.thisObject, "c");
                if (enabled() && preferredIncognito()) {
                    hook.setResult(XposedHelpers.callMethod(selector, "a", hook.args[0]));
                    Log.i(TAG, "selecting native Incognito account by default");
                }
            }
        }));
        hooks.add(XposedBridge.hookMethod(accountChanged, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                Activity activity = (Activity) XposedHelpers.getObjectField(hook.thisObject, "b");
                if (!activity.getClass().getName().equals(Compatibility.TARGET + ".home.HomeActivity")) return;
                home = new WeakReference<>(hook.thisObject);
                if (!enabled()) return;
                activeType = (String) XposedHelpers.callMethod(hook.args[0], loader.pick("co", "ad"));
                preferences.edit().putBoolean("incognito", ANONYMOUS.equals(activeType)).apply();
                Log.i(TAG, "active launcher profile: " + activeType);
            }
        }));
        hooks.add(XposedBridge.hookMethod(View.class.getDeclaredMethod("performClick"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                View view = (View) hook.thisObject;
                Object peer = home.get();
                // This pinned resource is the native account button in both tab layouts.
                if (!enabled() || view.getId() != loader.pick(0x7f0b05fb, 2131429037) || peer == null
                        || !(ANONYMOUS.equals(activeType) || "google".equals(activeType))) return;
                Activity activity = (Activity) XposedHelpers.getObjectField(peer, "b");
                if (activity.isFinishing() || activity.isDestroyed()
                        || view.getRootView() != activity.getWindow().getDecorView().getRootView()) return;
                if (loader.modern) {
                    XposedHelpers.callMethod(peer, "k", false, false, null, false, false, false, false, null);
                } else {
                    XposedHelpers.callMethod(peer, "k", false, false, null, false, false, null);
                }
                hook.setResult(true);
            }
        }));
        hooks.add(XposedBridge.hookMethod(chooserData, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                Object peer = XposedHelpers.getObjectField(hook.thisObject, "a");
                Activity activity = chooserActivity(peer);
                if (!eligible(activity) || selector == null || accounts == null) return;
                Object observer = hook.thisObject;
                Object data = hook.args[0];
                Object generation = new Object();
                XposedHelpers.setAdditionalInstanceField(observer, "launcherx-profile-generation", generation);
                hook.setResult(null);
                worker.execute(() -> {
                    Object updated = data;
                    try {
                        Object id = await(XposedHelpers.callMethod(selector, "a", new Object[]{null}));
                        Object account = await(XposedHelpers.callMethod(accounts, "c", id));
                        updated = withIncognito(data, account);
                    } catch (Exception error) {
                        Log.e(TAG, "Cannot add Incognito to the native chooser", error);
                    }
                    Object result = updated;
                    main.post(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()
                                || XposedHelpers.getAdditionalInstanceField(observer,
                                        "launcherx-profile-generation") != generation) return;
                        try {
                            XposedBridge.invokeOriginalMethod(chooserData, observer,
                                    new Object[]{eligible(activity) ? result : data});
                        } catch (Exception error) {
                            Log.e(TAG, "Cannot update the native profile chooser", error);
                        }
                    });
                });
            }
        }));
        hooks.add(XposedBridge.hookMethod(accountSelected, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                // Keep the shared synthetic callback scoped to its profile chooser case.
                if (XposedHelpers.getIntField(hook.thisObject, loader.pick("b", "$cid")) != loader.pick(19, 5)) return;
                Object peer = XposedHelpers.getObjectField(hook.thisObject, "a");
                if (!chooserPeerClass.isInstance(peer)) return;
                Activity activity = chooserActivity(peer);
                if (!eligible(activity)) return;
                Object account = XposedHelpers.callMethod(hook.args[0], "a");
                Object info = XposedHelpers.getObjectField(account, "b");
                if (!ANONYMOUS.equals(XposedHelpers.getObjectField(info, "k"))) return;
                // Only the local account skips Google's account-specific service queries.
                // Real Google selections retain the stock lock and authentication flow.
                XposedHelpers.callMethod(peer, "f", account, "adult");
                activity.finish();
                hook.setResult(XposedHelpers.getStaticObjectField(loader.loadClass("wrb"), "a"));
            }
        }));
    }

    private Activity chooserActivity(Object peer) {
        Object fragment = XposedHelpers.getObjectField(peer, "b");
        return (Activity) XposedHelpers.callMethod(fragment, loader.pick("G", "F"));
    }

    private boolean eligible(Activity activity) {
        if (!enabled() || activity == null || "managed_profile".equals(activeType)) return false;
        ComponentName caller = activity.getCallingActivity();
        Intent intent = activity.getIntent();
        return caller != null && Compatibility.TARGET.equals(caller.getPackageName())
                && caller.getClassName().equals(Compatibility.TARGET + ".home.HomeActivity")
                && !intent.getBooleanExtra("triggered_from_external_package", false)
                && !intent.getBooleanExtra("triggered_from_kid_account", false)
                && !intent.getBooleanExtra("extra_force_profile_lock", false)
                && !intent.hasExtra("extra_force_profile_lock_display_id");
    }

    private Object withIncognito(Object data, Object account) throws Exception {
        Object info = XposedHelpers.getObjectField(account, "b");
        if (!ANONYMOUS.equals(XposedHelpers.getObjectField(info, "k"))) {
            throw new IllegalStateException("Native anonymous selector returned a different account type");
        }
        Object anonymous = accountClass.getConstructor(loader.loadClass("vhe"), loader.loadClass("vjr"),
                int.class).newInstance(XposedHelpers.getObjectField(account, "a"), label(info),
                        XposedHelpers.getIntField(account, "c"));
        Bitmap avatar = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(avatar);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.DKGRAY);
        canvas.drawCircle(64, 64, 64, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(64);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("I", 64, 64 - (paint.ascent() + paint.descent()) / 2, paint);
        Object row = loader.loadClass("kwe").getConstructor(accountClass, Bitmap.class, Object.class,
                int.class, boolean.class).newInstance(anonymous, avatar, avatar, 128, false);
        List<Object> rows = new ArrayList<>();
        rows.add(row);
        for (Object existing : (List<?>) XposedHelpers.getObjectField(data, "a")) {
            if (!isAnonymous(XposedHelpers.getObjectField(existing, "a"))) rows.add(existing);
        }
        List<Object> choices = new ArrayList<>();
        choices.add(anonymous);
        for (Object existing : (List<?>) XposedHelpers.getObjectField(data, "b")) {
            if (!isAnonymous(existing)) choices.add(existing);
        }
        Class<?> list = loader.loadClass("wyc");
        Class<?> optional = loader.loadClass("j$.util.Optional");
        Object newRows = XposedHelpers.callStaticMethod(list, "o", rows);
        Object newChoices = XposedHelpers.callStaticMethod(list, "o", choices);
        Object children = XposedHelpers.getObjectField(data, loader.pick("f", "g"));
        Object selected = incognito() ? XposedHelpers.callStaticMethod(optional, "of", anonymous)
                : XposedHelpers.getObjectField(data, "c");
        int addAccount = XposedHelpers.getIntField(data, loader.pick("e", "f"));
        Object extra = XposedHelpers.getObjectField(data, "d");
        if (loader.modern) {
            return loader.loadClass("kwf").getConstructor(list, list, list, optional, int.class, optional, boolean.class)
                    .newInstance(newRows, newChoices, children, selected, addAccount, extra,
                            XposedHelpers.getBooleanField(data, "e"));
        }
        return loader.loadClass("kwf").getConstructor(list, list, list, optional, int.class, optional)
                .newInstance(newRows, newChoices, children, selected, addAccount, extra);
    }

    private static boolean isAnonymous(Object account) {
        return ANONYMOUS.equals(XposedHelpers.getObjectField(XposedHelpers.getObjectField(account, "b"), "k"));
    }

    private static Object await(Object future) throws Exception {
        return ((Future<?>) future).get(15, TimeUnit.SECONDS);
    }
}
