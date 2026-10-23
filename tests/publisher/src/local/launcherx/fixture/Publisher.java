package local.launcherx.fixture;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

public final class Publisher extends BroadcastReceiver {
    private static final Uri WATCH = Uri.parse("content://android.media.tv/watch_next_program");
    private static final Uri CHANNELS = Uri.parse("content://android.media.tv/channel");
    private static final Uri PREVIEW = Uri.parse("content://android.media.tv/preview_program");

    @Override public void onReceive(Context context, Intent command) {
        if (!"ranchu".equals(Build.HARDWARE)) {
            throw new IllegalStateException("Test publisher requires the emulator");
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            SharedPreferences ids = context.getSharedPreferences("entries", 0);
            String operation = command.getStringExtra("operation");
            if ("clear".equals(operation) || "seed".equals(operation)) {
                for (Uri table : new Uri[]{WATCH, PREVIEW, CHANNELS}) {
                    try (Cursor rows = resolver.query(table, new String[]{"_id"}, null, null, null)) {
                        while (rows != null && rows.moveToNext()) {
                            resolver.delete(ContentUris.withAppendedId(table, rows.getLong(0)), null, null);
                        }
                    }
                }
                ids.edit().clear().commit();
            }
            if ("seed".equals(operation)) {
                ContentValues channel = new ContentValues();
                channel.put("type", "TYPE_PREVIEW");
                channel.put("display_name", "Fixture recommendations");
                channel.put("input_id", "local.launcherx.fixture/.PlaybackActivity");
                Uri channelUri = resolver.insert(CHANNELS, channel);
                long channelId = ContentUris.parseId(channelUri);
                ids.edit().putString("channel", channelUri.toString()).commit();
                channel.put("display_name", "Hidden fixture channel");
                Uri hidden = resolver.insert(CHANNELS, channel);
                ContentValues hiddenCard = program(context, "Hidden channel card", "hidden-channel", 0);
                hiddenCard.put("channel_id", ContentUris.parseId(hidden));
                resolver.insert(PREVIEW, hiddenCard);
                for (int i = 0; i < 4; i++) {
                    String name = new String[]{"Local movie", "Local episode", "Hidden watch card", "Invalid watch card"}[i];
                    ContentValues values = program(context, name, "watch-" + i, i == 0 ? 180000 : 0);
                    values.put("watch_next_type", 0);
                    values.put("last_engagement_time_utc_millis", System.currentTimeMillis() - i * 1000);
                    values.put("internal_provider_id", "watch-" + i);
                    if (i == 1) {
                        values.put("type", 3);
                        values.put("season_display_number", "1");
                        values.put("episode_display_number", "2");
                        values.put("episode_title", "Test episode");
                    }
                    if (i == 3) values.put("intent_uri", "intent:#Intent;component=missing.package/.Missing;end");
                    Uri uri = resolver.insert(WATCH, values);
                    ids.edit().putString("watch" + i, uri.toString()).commit();
                }
                for (int i = 0; i < 5; i++) {
                    String name = new String[]{"Local recommendation", "Second recommendation", "Hidden preview card", "Invalid preview card", "Duplicate preview card"}[i];
                    ContentValues values = program(context, name, "preview-" + (i == 4 ? 0 : i), 0);
                    values.put("channel_id", channelId);
                    values.put("weight", 100 - i);
                    if (i == 3) values.put("intent_uri", "intent:#Intent;component=missing.package/.Missing;end");
                    Uri uri = resolver.insert(PREVIEW, values);
                    ids.edit().putString("preview" + i, uri.toString()).commit();
                }
                ContentValues clip = program(context, "Local clip", "watch-clip", 60000);
                clip.put("type", 4);
                clip.put("watch_next_type", 0);
                clip.put("last_engagement_time_utc_millis", System.currentTimeMillis() - 10000);
                resolver.insert(WATCH, clip);
            } else if ("add-channel".equals(operation)) {
                ContentValues channel = new ContentValues();
                channel.put("type", "TYPE_PREVIEW");
                channel.put("display_name", "New fixture channel");
                channel.put("input_id", "local.launcherx.fixture/.PlaybackActivity");
                Uri uri = resolver.insert(CHANNELS, channel);
                ContentValues card = program(context, "New channel card", "new-channel", 0);
                card.put("channel_id", ContentUris.parseId(uri));
                resolver.insert(PREVIEW, card);
            } else if ("update".equals(operation)) {
                ContentValues watch = program(context, "Updated local movie", "watch-0", 420000);
                resolver.update(Uri.parse(ids.getString("watch0", "")), watch, null, null);
                ContentValues preview = new ContentValues();
                preview.put("title", "Updated recommendation");
                resolver.update(Uri.parse(ids.getString("preview0", "")), preview, null, null);
            } else if ("delete".equals(operation)) {
                resolver.delete(Uri.parse(ids.getString("watch0", "")), null, null);
                resolver.delete(Uri.parse(ids.getString("preview0", "")), null, null);
                // The duplicate must not become a replacement in the deletion test.
                resolver.delete(Uri.parse(ids.getString("preview4", "")), null, null);
            }
            setResultCode(0);
            setResultData("fixture:" + operation + ":ok");
        } catch (Exception error) {
            setResultCode(1);
            setResultData(error.toString());
        }
    }

    private static ContentValues program(Context context, String title, String id, long position) {
        Intent playback = new Intent(Intent.ACTION_VIEW, Uri.parse("fixture://play/" + id), context, PlaybackActivity.class);
        playback.putExtra("seek_ms", position);
        ContentValues values = new ContentValues();
        values.put("type", 0);
        values.put("title", title);
        values.put("intent_uri", playback.toUri(Intent.URI_INTENT_SCHEME));
        values.put("poster_art_uri", "content://local.launcherx.fixture.art/" + id);
        values.put("poster_art_aspect_ratio", 0);
        values.put("duration_millis", 600000);
        values.put("last_playback_position_millis", position);
        return values;
    }
}
