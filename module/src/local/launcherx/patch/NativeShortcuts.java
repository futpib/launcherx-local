package local.launcherx.patch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Extend the native chooser and protobuf store without replacing either. */
final class NativeShortcuts {
    private static final String TAG = "LauncherXLocal";
    private static final String RESET_VIEW = "launcherx-reset-buttons";
    private static final String EVENT_SCOPE = "launcherx-native-event";
    private static final int[] OEM_KEYS = {193, 194, 351, 352};
    private static final int[] ALL_KEYS = {313, 189, 190, 193, 194, 351, 352};
    private final Context context;
    private final ClassLoader loader;
    private final Class<?> setupClass;
    private final Class<?> infoClass;
    private final Class<?> accountClass;
    private final Class<?> listenerClass;
    private final Class<?> callbackClass;
    private final Map<KeyEvent, PendingIntent> fallbacks = Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<KeyEvent> eventScope = new ThreadLocal<>();
    private volatile Object setup;

    private static final class RestoreControls extends LinearLayout {
        Object peer;
        View previousFocus;
        int code;

        RestoreControls(Context context) {
            super(context);
        }
    }

    NativeShortcuts(Context context, ClassLoader loader) throws Exception {
        this.context = context;
        this.loader = loader;
        setupClass = loader.loadClass("tzk");
        infoClass = loader.loadClass("tze");
        accountClass = loader.loadClass("gbp");
        listenerClass = loader.loadClass("lma");
        callbackClass = loader.loadClass("llx");
        setupClass.getDeclaredMethod("c", loader.loadClass("tzg"));
        setupClass.getDeclaredMethod("i", Context.class, int.class, Integer.class, String.class);
        setupClass.getDeclaredField("b");
        loader.loadClass("tzf").getDeclaredMethod("b", int.class);
        listenerClass.getDeclaredMethod("a", accountClass, infoClass);
        listenerClass.getDeclaredMethod("b", accountClass, infoClass, boolean.class);
        listenerClass.getDeclaredMethod("c", loader.loadClass("gcf"), infoClass);
        listenerClass.getDeclaredField("j");
        loader.loadClass("ibv").getDeclaredField("a");
    }

    static boolean oem(int code) {
        for (int key : OEM_KEYS) if (key == code) return true;
        return false;
    }

    static boolean supported(int code) {
        for (int key : ALL_KEYS) if (key == code) return true;
        return false;
    }

    static boolean enabled(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(new ComponentName(
                Compatibility.TARGET, Compatibility.TARGET + ".home.HomeActivity"))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private static String label(int key) {
        switch (key) {
            case 313: return "Star";
            case 189: case 190: return "YouTube";
            case 193: return "Netflix";
            case 194: return "Prime Video";
            case 351: return "Live TV";
            case 352: return "Xiaomi TV+";
            default: throw new IllegalArgumentException("Unsupported shortcut " + key);
        }
    }

    private static int key(Object info) {
        return XposedHelpers.getIntField(XposedHelpers.getObjectField(info, "f"), "b");
    }

    private Object blank(String type, String field) throws ClassNotFoundException {
        return XposedHelpers.callMethod(XposedHelpers.getStaticObjectField(loader.loadClass(type), field), "createBuilder");
    }

    private static Object mutable(Object builder) {
        if (!(Boolean) XposedHelpers.callMethod(XposedHelpers.getObjectField(builder, "b"), "isMutable")) {
            XposedHelpers.callMethod(builder, "w");
        }
        return XposedHelpers.getObjectField(builder, "b");
    }

    private Object generic(Object info) {
        Object builder = XposedHelpers.callMethod(info, "toBuilder");
        XposedHelpers.setIntField(mutable(builder), "g", 1);
        return XposedHelpers.callMethod(builder, "r");
    }

    void install(List<XC_MethodHook.Unhook> hooks, boolean core) throws Exception {
        hooks.addAll(XposedBridge.hookAllConstructors(setupClass, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) throws Throwable {
                Object nativeSetup = hook.thisObject;
                Object config = XposedHelpers.getObjectField(nativeSetup, "b");
                Object builder = blank("tzh", "a");
                Object keyMode = XposedHelpers.callStaticMethod(loader.loadClass("tzf"), "b", 1);
                for (Object info : (List<?>) XposedHelpers.getObjectField(config, "b")) {
                    int code = key(info);
                    if (code == 189 || code == 190) {
                        Object changed = XposedHelpers.callMethod(info, "toBuilder");
                        XposedHelpers.callMethod(changed, "az", keyMode);
                        info = XposedHelpers.callMethod(changed, "r");
                    }
                    XposedHelpers.callMethod(builder, "aA", info);
                }
                for (int code : OEM_KEYS) {
                    Object info = XposedHelpers.callMethod(nativeSetup, "i", new Class<?>[] {
                            Context.class, int.class, Integer.class, String.class}, context, code, null, label(code));
                    XposedHelpers.callMethod(builder, "aA", info);
                }
                XposedHelpers.setObjectField(nativeSetup, "b", XposedHelpers.callMethod(builder, "r"));
                setup = nativeSetup;
            }
        }));
        if (core) installDispatch(hooks);
        else installRestore(hooks);
    }

    private void installDispatch(List<XC_MethodHook.Unhook> hooks) throws Exception {
        hooks.addAll(XposedBridge.hookAllConstructors(listenerClass, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                // Keep YouTube's identifier independent of the optional star alias.
                XposedHelpers.setBooleanField(hook.thisObject, "j", false);
            }
        }));
        hooks.add(XposedBridge.hookMethod(loader.loadClass("wga").getDeclaredMethod("onReceive",
                Context.class, Intent.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                Intent intent = (Intent) hook.args[1];
                if (!hook.thisObject.getClass().getName().endsWith(".GlobalKeyListener_Receiver")
                        || !"android.intent.action.GLOBAL_BUTTON".equals(intent.getAction()) || !enabled(context)) return;
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null || !oem(event.getKeyCode()) || !intent.getBooleanExtra(XiaomiRemote.BRIDGE, false)) return;
                // Resolve the real listener before accepting responsibility for an OEM key.
                // If construction or expansion fails, Xiaomi receives no acknowledgement
                // and executes its original handler after the broadcast completes.
                Object entry = XposedHelpers.callStaticMethod(loader.loadClass("uzs"), "v",
                        context, loader.loadClass("lmb"));
                XposedHelpers.callMethod(entry, "at");
                if (setup == null) {
                    hook.setResult(null);
                    return;
                }
                BroadcastReceiver receiver = (BroadcastReceiver) hook.thisObject;
                if (receiver.isOrderedBroadcast()) {
                    receiver.setResultCode(XiaomiRemote.ACCEPTED);
                    Log.i(TAG, "Accepted OEM remote event: " + event.getKeyCode());
                }
            }
        }));
        hooks.add(XposedBridge.hookMethod(listenerClass.getDeclaredMethod("onReceive", Intent.class, int.class),
                new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                Intent intent = (Intent) hook.args[0];
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null || !oem(event.getKeyCode())) return;
                PendingIntent fallback = intent.getParcelableExtra(XiaomiRemote.FALLBACK);
                if (fallback != null && fallback.getCreatorUid() == 1000
                        && XiaomiRemote.PACKAGE.equals(fallback.getCreatorPackage())) fallbacks.put(event, fallback);
            }
        }));
        hooks.add(XposedBridge.hookMethod(callbackClass.getDeclaredMethod("a"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                KeyEvent event = (KeyEvent) XposedHelpers.getObjectField(hook.thisObject, "c");
                hook.setObjectExtra(EVENT_SCOPE, eventScope.get());
                eventScope.set(event);
                if (supported(event.getKeyCode()) && event.isCanceled() && enabled(context)) {
                    hook.setResult(XposedHelpers.callStaticMethod(loader.loadClass("xgr"), "W", false));
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                KeyEvent previous = (KeyEvent) hook.getObjectExtra(EVENT_SCOPE);
                if (previous == null) eventScope.remove();
                else eventScope.set(previous);
            }
        }));
        hooks.add(XposedBridge.hookMethod(listenerClass.getDeclaredMethod("b", accountClass, infoClass, boolean.class),
                new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                if ((Boolean) hook.args[2] || !enabled(context)
                        || "managed_profile".equals(XposedHelpers.getObjectField(hook.args[0], "e"))) return;
                hook.setResult(XposedHelpers.callMethod(hook.thisObject, "a", hook.args[0], generic(hook.args[1])));
            }
        }));
        hooks.add(XposedBridge.hookMethod(listenerClass.getDeclaredMethod("c", loader.loadClass("gcf"), infoClass),
                new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                Object info = hook.args[1];
                int code = key(info);
                if (!enabled(context)) return;
                Object state = ((Map<?, ?>) XposedHelpers.getObjectField(hook.thisObject, "k"))
                        .get(XposedHelpers.getObjectField(info, "f"));
                Integer target = keyAction(state);
                if (target != null && supported(code)) {
                    hook.setResult(null);
                    Object account = XposedHelpers.getObjectField(hook.args[0], "c");
                    if ((account != null && "managed_profile".equals(XposedHelpers.getObjectField(account, "e")))
                            || KeyActions.generated(eventScope.get())) return;
                    try {
                        KeyActions.send(context, target);
                        Log.i(TAG, target == KeyActions.NONE ? "No action for shortcut: " + code
                                : "Sent shortcut key: " + code + " -> " + target);
                    } catch (RuntimeException error) {
                        Log.e(TAG, "Could not send shortcut key", error);
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                Toast.makeText(context, "Could not send key: " + error.getMessage(), Toast.LENGTH_LONG).show());
                    }
                    return;
                }
                if (!oem(code) && code != 189 && code != 190) return;
                Object bytes = XposedHelpers.getObjectField(info, "i");
                if (!(Boolean) XposedHelpers.callMethod(bytes, "C")) {
                    if (state != null && XposedHelpers.getIntField(state, "b") != 0) return;
                }
                if (code == 189 || code == 190) {
                    youtubeDefault(hook.thisObject, hook.args[0], info);
                    hook.setResult(null);
                    return;
                }
                PendingIntent fallback = fallbacks.remove(eventScope.get());
                if (fallback != null) fallback.send();
                Log.i(TAG, "Default remote action: " + label(key(info)) + "; OEM callback=" + (fallback != null));
                hook.setResult(null);
            }
        }));
    }

    private static Integer keyAction(Object state) {
        if (state == null || XposedHelpers.getIntField(state, "b") != 4) return null;
        String value = (String) XposedHelpers.getObjectField(state, "c");
        if (!value.startsWith(KeyActions.PREFIX)) return null;
        if (value.equals(KeyActions.PREFIX + "none")) return KeyActions.NONE;
        return KeyActions.parse(value.substring(KeyActions.PREFIX.length()));
    }

    private void youtubeDefault(Object listener, Object accountState, Object info) throws Exception {
        Object account = XposedHelpers.getObjectField(accountState, "c");
        if (account == null) account = XposedHelpers.getStaticObjectField(accountClass, "a");
        String selected = (String) XposedHelpers.getObjectField(accountState, "d");
        java.util.Collection<?> allowed = (java.util.Collection<?>) XposedHelpers.getStaticObjectField(
                loader.loadClass("ibv"), "a");
        if (selected == null || !allowed.contains(selected)) {
            if ("managed_profile".equals(XposedHelpers.getObjectField(account, "e"))) {
                List<?> children = (List<?>) XposedHelpers.getObjectField(accountState, "e");
                if (children.size() > 1) {
                    XposedHelpers.callMethod(listener, "b", account, info, true);
                    return;
                }
                selected = children.isEmpty() ? "com.google.android.youtube.tvkids" : (String) children.get(0);
            } else {
                selected = "com.google.android.youtube.tv";
            }
        }
        // The native chooser may save its seen/configuration flags before an app
        // is chosen. Those flags are not an assignment: retain the original
        // YouTube selection and its native launch/lock checks until one is saved.
        XposedHelpers.callMethod(XposedHelpers.getObjectField(listener, "d"), "b",
                new Class<?>[] {String.class, Intent.class}, selected, null);
    }

    private void installRestore(List<XC_MethodHook.Unhook> hooks) throws Exception {
        Class<?> peer = loader.loadClass("kna");
        peer.getDeclaredField("o");
        hooks.add(XposedBridge.hookMethod(peer.getDeclaredMethod("g", infoClass), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                if (((List<?>) XposedHelpers.getObjectField(hook.thisObject, "o")).isEmpty()) return;
                // Native restricted YouTube pickers (including children) retain
                // their allowlist-only mode and cannot reset other buttons.
                Object builder = XposedHelpers.callMethod(hook.args[0], "toBuilder");
                Object empty = XposedHelpers.getStaticObjectField(infoClass, "b");
                Object value = mutable(builder);
                XposedHelpers.setIntField(value, "g", 3);
                XposedHelpers.setObjectField(value, "h", XposedHelpers.getObjectField(empty, "h"));
                XposedHelpers.setObjectField(value, "i", XposedHelpers.getObjectField(empty, "i"));
                XposedHelpers.callMethod(builder, "az", XposedHelpers.callStaticMethod(loader.loadClass("tzf"), "b", 3));
                hook.args[0] = XposedHelpers.callMethod(builder, "r");
            }
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                if (!((List<?>) XposedHelpers.getObjectField(hook.thisObject, "o")).isEmpty()) return;
                Object info = hook.args[0];
                if (!supported(key(info)) || !enabled(context)) return;
                View view = (View) XposedHelpers.getObjectField(XposedHelpers.getObjectField(hook.thisObject, "c"), "Q");
                if (view == null) return;
                Context themed = view.getContext();
                while (themed instanceof ContextWrapper && !(themed instanceof Activity)) {
                    themed = ((ContextWrapper) themed).getBaseContext();
                }
                if (!(themed instanceof Activity)) return;
                Activity activity = (Activity) themed;
                ViewGroup content = activity.findViewById(android.R.id.content);
                RestoreControls previous = content.findViewWithTag(RESET_VIEW);
                if (previous != null && previous.code == key(info)) {
                    updateKeyLabel(previous, hook.thisObject);
                    updateRestoreVisibility(hook.thisObject);
                    return;
                }
                if (previous != null) ((ViewGroup) previous.getParent()).removeView(previous);
                int code = key(info);
                RestoreControls row = new RestoreControls(activity);
                row.code = code;
                row.setTag(RESET_VIEW);
                row.setGravity(Gravity.CENTER);
                Button one = button(activity, "Restore " + label(code) + " default");
                one.setOnClickListener(v -> confirmReset(activity, new int[] {code}));
                Button all = button(activity, "Restore all button defaults");
                Button send = button(activity, "Send key…");
                send.setOnClickListener(v -> chooseKey(activity, code));
                all.setOnClickListener(v -> confirmReset(activity, ALL_KEYS));
                Button[] controls = {one, all, send};
                for (Button control : controls) control.setId(View.generateViewId());
                for (int index = 0; index < controls.length; index++) {
                    Button button = controls[index];
                    button.setNextFocusLeftId(controls[Math.max(0, index - 1)].getId());
                    button.setNextFocusRightId(controls[Math.min(controls.length - 1, index + 1)].getId());
                    LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    item.setMargins(8, 0, 8, 0);
                    row.addView(button, item);
                    button.setOnKeyListener((v, keyCode, event) -> {
                        if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN || event.getAction() != KeyEvent.ACTION_DOWN) return false;
                        View previousFocus = row.previousFocus;
                        return previousFocus != null && previousFocus.isShown() && previousFocus.requestFocus();
                    });
                }
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                params.topMargin = Math.round(12 * activity.getResources().getDisplayMetrics().density);
                activity.addContentView(row, params);
                row.peer = hook.thisObject;
                updateKeyLabel(row, hook.thisObject);
                updateRestoreVisibility(hook.thisObject);
            }
        }));
        hooks.add(XposedBridge.hookMethod(peer.getDeclaredMethod("h", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                updateRestoreVisibility(hook.thisObject);
            }
        }));
        hooks.add(XposedBridge.hookMethod(Activity.class.getDeclaredMethod("dispatchKeyEvent", KeyEvent.class),
                new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) {
                Activity activity = (Activity) hook.thisObject;
                if (!activity.getClass().getName().equals(Compatibility.TARGET + ".magicbutton.ui.MagicAppSelectionActivity")) return;
                KeyEvent event = (KeyEvent) hook.args[0];
                if (event.getAction() != KeyEvent.ACTION_DOWN || event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP) return;
                RestoreControls row = activity.getWindow().getDecorView().findViewWithTag(RESET_VIEW);
                View focused = activity.getCurrentFocus();
                if (row == null || !row.isShown() || focused == null || row.hasFocus()) return;
                Object nativePeer = row.peer;
                int page = (Integer) XposedHelpers.callMethod(nativePeer, "a");
                View next = focused.focusSearch(View.FOCUS_UP);
                if (page == 2 || next == null || next == focused || row.indexOfChild(next) >= 0) {
                    row.previousFocus = focused;
                    row.getChildAt(0).requestFocus();
                    hook.setResult(true);
                }
            }
        }));
    }

    private static void updateKeyLabel(RestoreControls row, Object peer) {
        Integer code = keyAction(XposedHelpers.getObjectField(peer, "x"));
        ((Button) row.getChildAt(2)).setText(code == null ? "Send key…"
                : code == KeyActions.NONE ? "No action" : "Key: " + KeyActions.label(code));
    }

    private void chooseKey(Activity activity, int source) {
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Send key for " + label(source))
                .setItems(KeyActions.LABELS, (choice, index) -> {
                    if (index < KeyActions.COMMON.length) confirmKey(activity, source, KeyActions.COMMON[index]);
                    else customKey(activity, source);
                }).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(unused -> {
            dialog.getListView().requestFocus();
            dialog.getListView().setSelection(0);
        });
        dialog.show();
    }

    private void customKey(Activity activity, int source) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setHint("85 or KEYCODE_MEDIA_PLAY_PAUSE");
        AlertDialog dialog = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Android key code")
                .setView(input).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Continue", null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                int target = KeyActions.parse(input.getText().toString());
                if (target == source) throw new IllegalArgumentException("Choose a different key");
                dialog.dismiss();
                confirmKey(activity, source, target);
            } catch (IllegalArgumentException error) {
                input.setError(error.getMessage());
            }
        }));
        dialog.show();
    }

    private void confirmKey(Activity activity, int source, int target) {
        new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Bind " + label(source) + " to " + KeyActions.label(target) + "?")
                .setMessage(target == KeyActions.NONE ? "Short press does nothing. Hold to customize again."
                        : "Short press sends this key. Hold to customize again."
                        + (KeyActions.media(target) ? "" : " Allow LauncherX Local root access in Magisk if prompted."))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save", (dialog, which) -> saveKey(activity, source, target)).show();
    }

    private void saveKey(Activity activity, int source, int target) {
        new Thread(() -> {
            try {
                KeyActions.prepare(activity, target);
                Object state = blank("lmj", "a");
                Object value = mutable(state);
                XposedHelpers.setIntField(value, "b", 4);
                XposedHelpers.setObjectField(value, "c", KeyActions.PREFIX
                        + (target == KeyActions.NONE ? "none" : Integer.toString(target)));
                XposedHelpers.setBooleanField(value, "e", true);
                Object id = blank("tzd", "a");
                XposedHelpers.setIntField(mutable(id), "b", source);
                Object change = blank("tzg", "a");
                Object message = mutable(change);
                XposedHelpers.setObjectField(message, "c", XposedHelpers.callMethod(id, "r"));
                XposedHelpers.setIntField(message, "b", 1);
                XposedHelpers.setIntField(message, "d", 1);
                XposedHelpers.setObjectField(message, "e", XposedHelpers.callMethod(
                        XposedHelpers.callMethod(state, "r"), "toByteString"));
                Future<?> saved = (Future<?>) XposedHelpers.callMethod(setup, "c", XposedHelpers.callMethod(change, "r"));
                saved.get(10, TimeUnit.SECONDS);
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, label(source) + ": " + KeyActions.label(target), Toast.LENGTH_SHORT).show();
                    activity.finish();
                });
            } catch (Exception error) {
                Log.e(TAG, "Could not save shortcut key", error);
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Key was not saved: " + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "LauncherX-save-key").start();
    }

    private static Button button(Activity activity, String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        float density = activity.getResources().getDisplayMetrics().density;
        int padding = Math.round(18 * density);
        button.setPadding(padding, 0, padding, 0);
        StateListDrawable background = new StateListDrawable();
        int[][] states = {{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}};
        int[] colors = {0xffe8f0fe, 0xffe8f0fe, 0xff292c30};
        for (int i = 0; i < states.length; i++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(colors[i]);
            shape.setCornerRadius(24 * density);
            background.addState(states[i], shape);
        }
        button.setBackground(background);
        button.setTextColor(new ColorStateList(states, new int[] {0xff202124, 0xff202124, 0xffe8eaed}));
        return button;
    }

    private static void updateRestoreVisibility(Object peer) {
        View view = (View) XposedHelpers.getObjectField(XposedHelpers.getObjectField(peer, "c"), "Q");
        if (view == null) return;
        View row = view.getRootView().findViewWithTag(RESET_VIEW);
        int page = (Integer) XposedHelpers.callMethod(peer, "a");
        if (row != null) row.setVisibility(page == 1 || page == 2 ? View.VISIBLE : View.GONE);
    }

    private void confirmReset(Activity activity, int[] codes) {
        new AlertDialog.Builder(activity).setTitle(codes.length == 1
                ? "Restore " + label(codes[0]) + " default?" : "Restore all button defaults?")
                .setMessage("Remove the custom assignment and use the TV's original button action. Hold a button to customize it again.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Restore", (dialog, which) -> restore(activity, codes)).show();
    }

    private void restore(Activity activity, int[] codes) {
        Object nativeSetup = setup;
        new Thread(() -> {
            try {
                if (nativeSetup == null) throw new IllegalStateException("Native shortcut store is not ready");
                for (int code : codes) {
                    Object id = blank("tzd", "a");
                    XposedHelpers.setIntField(mutable(id), "b", code);
                    Object change = blank("tzg", "a");
                    Object message = mutable(change);
                    XposedHelpers.setObjectField(message, "c", XposedHelpers.callMethod(id, "r"));
                    XposedHelpers.setIntField(message, "b", 1);
                    XposedHelpers.setIntField(message, "d", code == 189 || code == 190 ? 3 : 1);
                    Future<?> saved = (Future<?>) XposedHelpers.callMethod(nativeSetup, "c", XposedHelpers.callMethod(change, "r"));
                    saved.get(10, TimeUnit.SECONDS);
                }
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "Original button actions restored", Toast.LENGTH_SHORT).show();
                    activity.finish();
                });
            } catch (Exception error) {
                Log.e(TAG, "Could not restore remote defaults", error);
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Could not restore all requested buttons. Try again.", Toast.LENGTH_LONG).show());
            }
        }, "LauncherX-restore-buttons").start();
    }
}
