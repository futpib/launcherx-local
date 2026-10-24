package local.launcherx.patch;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Local TvContract data rendered by the pinned launcher's own row/card machinery. */
final class LocalContent implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "LauncherXLocal";
    private static final String HOME = "com.google.android.apps.tv.launcherx.home.HomeActivity";
    private static final Uri WATCH = Uri.parse("content://android.media.tv/watch_next_program");
    private static final Uri CHANNELS = Uri.parse("content://android.media.tv/channel");
    private static final Uri PREVIEW = Uri.parse("content://android.media.tv/preview_program");
    private static final int MAX_ROWS = 40, MAX_CARDS = 40, MAX_SCAN = 1000;
    private static final String[] WATCH_COLUMNS = {"_id", "package_name", "type", "intent_uri",
            "last_engagement_time_utc_millis", "title", "season_display_number", "season_title",
            "episode_display_number", "episode_title", "poster_art_uri", "poster_art_aspect_ratio",
            "internal_provider_id", "duration_millis", "last_playback_position_millis", "series_id"};

    private final Context context;
    private final LauncherSymbols loader;
    private final BooleanSupplier enabled;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Object, Object> tabs = new WeakHashMap<>();
    private final Constructor<?> rowConstructor, rowsConstructor;
    private final Method render, watchConverter;
    private final String[] previewColumns;
    private final Runnable refresh = this::load;
    private Handler worker;
    private Object provider;
    private boolean foreground;
    private List<Object> rows = Collections.emptyList();

    LocalContent(Context context, LauncherSymbols loader, BooleanSupplier enabled) throws Exception {
        this.context = context;
        this.loader = loader;
        this.enabled = enabled;
        Class<?> row = loader.loadClass("lfs"), proto = loader.loadClass("aagk");
        rowConstructor = row.getConstructor(String.class, proto, loader.loadClass("wyc"));
        rowsConstructor = loader.loadClass("lft").getConstructor(List.class);
        render = loader.loadClass("igc").getDeclaredMethod("w", loader.loadClass("lft"), boolean.class);
        watchConverter = loader.loadClass("lhj").getMethod("a", Context.class,
                loader.loadClass("abbc"), loader.loadClass("aaug"), loader.loadClass("aahl"));
        previewColumns = (String[]) XposedHelpers.getStaticObjectField(loader.loadClass("ljh"), "a");
        loader.loadClass("ljf").getConstructor(loader.loadClass("lji"), String.class,
                int.class, boolean.class, loader.loadClass("zej"));
    }

    XC_MethodHook.Unhook install() {
        return XposedBridge.hookMethod(render, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                if (!enabled.getAsBoolean()) return;
                Object destination = XposedHelpers.getObjectField(hook.thisObject, "g");
                Object id = loader.modern ? XposedHelpers.getObjectField(destination, "g")
                        : XposedHelpers.callMethod(destination, "f");
                if (!"error_fallback_destination".equals(id)) return;
                List<?> original = (List<?>) XposedHelpers.getObjectField(hook.args[0], "a");
                List<Object> base = new ArrayList<>();
                for (Object item : original) {
                    if ("apps_channel".equals(XposedHelpers.getObjectField(item, "a"))) base.add(item);
                }
                if (base.isEmpty()) return;
                tabs.put(hook.thisObject, rowsConstructor.newInstance(base));
                if (worker == null) start(hook.thisObject);
                base.addAll(rows);
                hook.args[0] = rowsConstructor.newInstance(base);
            }
        });
    }

    private void start(Object tab) {
        Log.i(TAG, "observing local Watch Next and preview providers");
        provider = XposedHelpers.getObjectField(XposedHelpers.getObjectField(tab, loader.pick("at", "ar")), "c");
        HandlerThread thread = new HandlerThread("LauncherXLocalContent");
        thread.start();
        worker = new Handler(thread.getLooper());
        AppChannels.register(context, worker, enabled);
        ContentObserver observer = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange, Uri uri) { request(); }
        };
        try {
            for (Uri uri : new Uri[]{WATCH, CHANNELS, PREVIEW}) {
                context.getContentResolver().registerContentObserver(uri, true, observer);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Local provider observation unavailable; refreshing on Home resume", error);
        }
        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(this);
        // The first tab may be created after Home's resume callback.
        foreground = true;
        request();
    }

    private void request() {
        if (worker == null || !foreground || !enabled.getAsBoolean()) return;
        worker.removeCallbacks(refresh);
        worker.postDelayed(refresh, 250);
    }

    private void load() {
        if (!enabled.getAsBoolean()) return;
        List<Object> result = new ArrayList<>();
        try {
            AppChannels.enableNew(context);
        } catch (Exception error) {
            Log.w(TAG, "Could not enable new app channels; keeping existing visibility", error);
        }
        try {
            List<Object> cards = watchCards();
            if (!cards.isEmpty()) result.add(row("launcherx-local:watch-next",
                    context.getString(loader.pick(0x7f140654, 2132018634)), cards, false));
        } catch (Throwable error) {
            Log.w(TAG, "Watch Next unavailable; keeping app rows", error);
        }
        try {
            previewRows(result);
        } catch (Throwable error) {
            Log.w(TAG, "Preview channels unavailable; keeping other rows", error);
        }
        main.post(() -> {
            if (!enabled.getAsBoolean()) { rows = Collections.emptyList(); return; }
            if (!foreground) return;
            if (rows.equals(result)) return;
            rows = result;
            Log.i(TAG, "local content refreshed: " + result.size() + " rows");
            for (Map.Entry<Object, Object> tab : new ArrayList<>(tabs.entrySet())) {
                try {
                    Object fragment = XposedHelpers.getObjectField(tab.getKey(), "b");
                    View view = (View) XposedHelpers.getObjectField(fragment, "Q");
                    if (view == null || !view.isAttachedToWindow()) {
                        tabs.remove(tab.getKey());
                        continue;
                    }
                    // Calling through the hooked method preserves the native row diff/focus logic.
                    XposedHelpers.callMethod(tab.getKey(), "w", tab.getValue(), false);
                } catch (Throwable error) {
                    Log.w(TAG, "Local row refresh failed", error);
                }
            }
        });
    }

    private List<Object> watchCards() throws Exception {
        List<Object> cards = new ArrayList<>();
        Set<String> links = new HashSet<>();
        try (Cursor cursor = context.getContentResolver().query(WATCH, WATCH_COLUMNS,
                "browsable=1", null, "last_engagement_time_utc_millis DESC, _id DESC")) {
            int scanned = 0;
            while (cursor != null && cursor.moveToNext() && cards.size() < MAX_CARDS && scanned++ < MAX_SCAN) {
                try {
                    String owner = string(cursor, "package_name");
                    String title = string(cursor, "title");
                    String intent = playbackIntent(owner, string(cursor, "intent_uri"));
                    int type = (int) number(cursor, "type");
                    if (title.isEmpty() || intent == null || type < 0 || type > 12
                            || !links.add(owner + "\n" + intent)) continue;
                    Object entry = builder("abbc");
                    set(entry, "b", owner); set(entry, "c", type <= 3 ? type : 0); set(entry, "d", intent);
                    set(entry, "e", number(cursor, "last_engagement_time_utc_millis"));
                    set(entry, "f", title); set(entry, "g", string(cursor, "season_display_number"));
                    set(entry, "h", string(cursor, "season_title"));
                    set(entry, "i", string(cursor, "episode_display_number"));
                    set(entry, "j", string(cursor, "episode_title"));
                    set(entry, "k", string(cursor, "series_id"));
                    set(entry, "l", string(cursor, "poster_art_uri"));
                    set(entry, "m", (int) number(cursor, "poster_art_aspect_ratio"));
                    String id = string(cursor, "internal_provider_id");
                    set(entry, "n", id.isEmpty() ? "tvprovider:" + number(cursor, "_id") : id);
                    long duration = Math.max(0, number(cursor, "duration_millis"));
                    long position = Math.max(0, number(cursor, "last_playback_position_millis"));
                    set(entry, "o", duration); set(entry, "p", Math.min(position, duration));
                    Object distributor = builder("aaug");
                    set(distributor, "b", "local:" + owner);
                    set(distributor, "c", context.getPackageManager().getApplicationLabel(
                            context.getPackageManager().getApplicationInfo(owner, 0)).toString());
                    Object card = watchConverter.invoke(null, context, finish(entry), finish(distributor), defaultValue("aahl"));
                    // The stock converter understands film/series/season/episode. Other TvContract
                    // media types keep the same native resume card without claiming a film subtype.
                    if (type > 3) card = genericWatchCard(card);
                    cards.add(card);
                } catch (Throwable error) {
                    Log.w(TAG, "Skipping invalid Watch Next entry", error);
                }
            }
        }
        return cards;
    }

    private void previewRows(List<Object> result) throws Exception {
        try (Cursor channels = context.getContentResolver().query(CHANNELS,
                new String[]{"_id", "package_name", "display_name"},
                "type=? AND browsable=1", new String[]{"TYPE_PREVIEW"}, "_id ASC")) {
            int scanned = 0;
            while (channels != null && channels.moveToNext() && result.size() < MAX_ROWS && scanned++ < MAX_SCAN) {
                try {
                    long id = number(channels, "_id");
                    String owner = string(channels, "package_name");
                    String title = string(channels, "display_name");
                    if (title.isEmpty()) title = context.getPackageManager().getApplicationLabel(
                            context.getPackageManager().getApplicationInfo(owner, 0)).toString();
                    List<?> cards = previewCards(id, owner);
                    if (!cards.isEmpty()) result.add(row("launcherx-local:channel:" + id, title, cards, true));
                } catch (Throwable error) {
                    Log.w(TAG, "Skipping unavailable preview channel", error);
                }
            }
        }
    }

    private List<?> previewCards(long id, String owner) throws Exception {
        // Retain the exact projection expected by Google's existing preview-card converter.
        String[] projection = new String[previewColumns.length + 1];
        System.arraycopy(previewColumns, 0, projection, 0, previewColumns.length);
        projection[previewColumns.length] = "package_name";
        try (MatrixCursor valid = new MatrixCursor(previewColumns);
             Cursor cursor = context.getContentResolver().query(PREVIEW, projection,
                     "channel_id=? AND browsable=1", new String[]{Long.toString(id)}, "weight DESC, _id ASC")) {
            Set<String> links = new HashSet<>();
            int scanned = 0;
            while (cursor != null && cursor.moveToNext() && valid.getCount() < MAX_CARDS && scanned++ < MAX_SCAN) {
                if (!owner.equals(string(cursor, "package_name")) || string(cursor, "title").isEmpty()) continue;
                String intent = playbackIntent(owner, string(cursor, "intent_uri"));
                if (intent == null || !links.add(intent)) continue;
                Object[] values = new Object[previewColumns.length];
                for (int i = 0; i < values.length; i++) {
                    values[i] = cursor.isNull(i) ? null : cursor.getString(i);
                    if ("intent_uri".equals(previewColumns[i])) values[i] = intent;
                }
                valid.addRow(values);
            }
            Object converter = XposedHelpers.newInstance(loader.loadClass("ljf"), provider, owner,
                    0, false, loader.emptyBytes());
            return (List<?>) XposedHelpers.callMethod(converter, "a", new Object[]{null, valid});
        }
    }

    private String playbackIntent(String owner, String uri) {
        if (owner.isEmpty() || uri.isEmpty()) return null;
        try {
            Intent intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            if (intent.getSelector() != null || (intent.getPackage() != null
                    && !owner.equals(intent.getPackage())) || (intent.getComponent() != null
                    && !owner.equals(intent.getComponent().getPackageName()))) return null;
            intent.setPackage(owner);
            ResolveInfo info = context.getPackageManager().resolveActivity(intent, 0);
            if (info == null || info.activityInfo == null || !info.activityInfo.exported
                    || !info.activityInfo.enabled || !info.activityInfo.applicationInfo.enabled
                    || !owner.equals(info.activityInfo.packageName)) return null;
            return intent.toUri(Intent.URI_INTENT_SCHEME);
        } catch (Exception error) {
            return null;
        }
    }

    private Object row(String id, String title, List<?> cards, boolean preview) throws Exception {
        Object row = builder("aagk");
        set(row, "e", title); set(row, "o", id);
        set(row, "c", preview ? 24 : 11);
        set(row, "d", defaultValue(preview ? "abbi" : "aaff"));
        XposedHelpers.callMethod(row, loader.pick("bg", "bc"), cards);
        return rowConstructor.newInstance(id, finish(row), null);
    }

    private Object genericWatchCard(Object card) throws Exception {
        Object asset = XposedHelpers.getObjectField(card, "c");
        Object entity = copy(XposedHelpers.getObjectField(asset, "f"));
        set(entity, "c", 0); set(entity, "d", null);
        set(entity, "t", loader.emptyBytes());
        XposedHelpers.callStaticMethod(loader.loadClass("fxr"), "m", entity);
        Object assetCopy = copy(asset);
        set(assetCopy, "f", finish(entity));
        Object cardCopy = copy(card);
        set(cardCopy, "c", finish(assetCopy));
        return finish(cardCopy);
    }

    private Object copy(Object proto) {
        Object result = XposedHelpers.callMethod(proto, "toBuilder");
        loader.copyOnWrite(result);
        return result;
    }

    private Object defaultValue(String name) throws Exception {
        return XposedHelpers.getStaticObjectField(loader.loadClass(name), "a");
    }

    private Object builder(String name) throws Exception {
        return XposedHelpers.callMethod(defaultValue(name), "createBuilder");
    }

    private static void set(Object builder, String field, Object value) {
        XposedHelpers.setObjectField(XposedHelpers.getObjectField(builder, "b"), field, value);
    }

    private Object finish(Object builder) { return loader.finish(builder); }

    private static String string(Cursor cursor, String column) {
        String value = cursor.getString(cursor.getColumnIndexOrThrow(column));
        return value == null ? "" : value;
    }

    private static long number(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    @Override public void onActivityResumed(Activity activity) {
        if (HOME.equals(activity.getClass().getName())) { foreground = true; request(); }
    }
    @Override public void onActivityPaused(Activity activity) {
        if (HOME.equals(activity.getClass().getName())) foreground = false;
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
