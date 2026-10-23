package local.launcherx.patch;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Preview channels start visible; later visibility choices remain in Android's TV provider. */
final class AppChannels {
    static final String ACTION = "local.launcherx.patch.APP_CHANNELS";
    private static final Uri CHANNELS = Uri.parse("content://android.media.tv/channel");

    static void enableNew(Context context) throws Exception {
        SharedPreferences preferences = context.getSharedPreferences("launcherx-local-channels", Context.MODE_PRIVATE);
        Set<String> seen = new HashSet<>(preferences.getStringSet("seen", Collections.emptySet()));
        int previousSize = seen.size();
        try (Cursor cursor = context.getContentResolver().query(CHANNELS,
                new String[]{"_id", "package_name", "browsable"},
                "type=?", new String[]{"TYPE_PREVIEW"}, "_id DESC")) {
            int scanned = 0;
            while (cursor != null && cursor.moveToNext() && scanned++ < 1000) {
                long id = cursor.getLong(0);
                String owner = cursor.getString(1);
                String key = owner + ":" + id;
                if (seen.contains(key)) continue;
                if (cursor.getInt(2) != 1) {
                    ContentValues values = new ContentValues();
                    values.put("browsable", 1);
                    if (context.getContentResolver().update(ContentUris.withAppendedId(CHANNELS, id),
                            values, "type=? AND package_name=?", new String[]{"TYPE_PREVIEW", owner}) != 1) continue;
                }
                seen.add(key);
            }
        }
        // Persist discovery, not visibility: a later hide in either UI must stay hidden.
        if (seen.size() != previousSize && !preferences.edit().putStringSet("seen", seen).commit()) {
            throw new IllegalStateException("Could not save discovered app channels");
        }
    }

    static void register(Context context, Handler worker, BooleanSupplier enabled) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context unused, Intent intent) {
                PendingResult result = goAsync();
                worker.post(() -> {
                    JSONObject response = new JSONObject();
                    try {
                        if (!enabled.getAsBoolean()) throw new IllegalStateException("Select Incognito in Home first.");
                        enableNew(context);
                        JSONArray channels = list(context);
                        String changes = intent.getStringExtra("changes");
                        if (changes != null && !changes.isEmpty()) {
                            Set<Long> known = new HashSet<>();
                            for (int i = 0; i < channels.length(); i++) known.add(channels.getJSONObject(i).getLong("id"));
                            String[] updates = changes.split(",");
                            if (updates.length > 80) throw new IllegalArgumentException("Too many channel changes");
                            for (String update : updates) {
                                if (!update.matches("[0-9]+:[01]") || !known.contains(Long.parseLong(update.split(":")[0]))) {
                                    throw new IllegalArgumentException("Channel list changed; reopen the chooser");
                                }
                            }
                            for (String update : updates) {
                                String[] fields = update.split(":");
                                ContentValues values = new ContentValues();
                                values.put("browsable", Integer.parseInt(fields[1]));
                                if (context.getContentResolver().update(ContentUris.withAppendedId(CHANNELS,
                                        Long.parseLong(fields[0])), values, "type=?", new String[]{"TYPE_PREVIEW"}) != 1) {
                                    throw new IllegalStateException("Channel disappeared; reopen the chooser");
                                }
                            }
                            channels = list(context);
                        }
                        response.put("channels", channels);
                    } catch (Exception error) {
                        try { response.put("error", error.getMessage()); } catch (Exception ignored) {}
                    } finally {
                        result.setResultData(Base64.encodeToString(response.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                        result.finish();
                    }
                });
            }
        };
        IntentFilter filter = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, "android.permission.DUMP", null, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter, "android.permission.DUMP", null);
        }
    }

    private static JSONArray list(Context context) throws Exception {
        JSONArray result = new JSONArray();
        try (Cursor cursor = context.getContentResolver().query(CHANNELS,
                new String[]{"_id", "package_name", "display_name", "browsable"},
                "type=?", new String[]{"TYPE_PREVIEW"}, "_id ASC")) {
            while (cursor != null && cursor.moveToNext() && result.length() < 80) {
                String owner = cursor.getString(1);
                String label;
                try {
                    label = context.getPackageManager().getApplicationLabel(
                            context.getPackageManager().getApplicationInfo(owner, 0)).toString();
                } catch (android.content.pm.PackageManager.NameNotFoundException error) { continue; }
                JSONObject entry = new JSONObject();
                entry.put("id", cursor.getLong(0));
                entry.put("title", shortText(cursor.getString(2)));
                entry.put("app", shortText(label));
                entry.put("enabled", cursor.getInt(3) == 1);
                result.put(entry);
            }
        }
        return result;
    }

    private static String shortText(String text) {
        return text == null ? "" : text.substring(0, Math.min(text.length(), 120));
    }
}
