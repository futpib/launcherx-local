package local.launcherx.patch;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import de.robv.android.xposed.XposedHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keep assignments outside the native store that stock Home prunes without our definitions. */
final class ShortcutBackup {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 65536;
    private final File directory;
    private final File nativeData;
    private final LauncherSymbols loader;

    ShortcutBackup(Context context, LauncherSymbols loader) {
        directory = new File(context.getFilesDir(), "launcherx_shortcut_backup");
        nativeData = new File(context.getFilesDir(), "remote_button_configuration/data");
        this.loader = loader;
    }

    private static byte[] encode(Object message) {
        return (byte[]) XposedHelpers.callMethod(message, "toByteArray");
    }

    private static Map<Integer, Object> buttons(Object config) {
        Map<Integer, Object> result = new HashMap<>();
        for (Object info : (List<?>) XposedHelpers.getObjectField(config, "b")) {
            int code = XposedHelpers.getIntField(XposedHelpers.getObjectField(info, "f"), "b");
            if (NativeShortcuts.supported(code)) result.put(code, info);
        }
        return result;
    }

    private static boolean supports(Object info, int mode) {
        return info != null && ((List<?>) XposedHelpers.getObjectField(info, "h")).contains(mode);
    }

    private boolean empty(Object info) {
        return (Boolean) XposedHelpers.callMethod(XposedHelpers.getObjectField(info, "i"),
                loader.pick("C", "z", "y"));
    }

    private Object mutable(Object builder) {
        loader.copyOnWrite(builder);
        return XposedHelpers.getObjectField(builder, "b");
    }

    private byte[] currentBytes() throws Exception {
        File[] versions = nativeData.listFiles();
        File latest = null;
        long generation = -1;
        if (versions != null) for (File version : versions) {
            try {
                long number = Long.parseLong(version.getName());
                if (number > generation) {
                    generation = number;
                    latest = new File(version, "content");
                }
            } catch (NumberFormatException ignored) {}
        }
        if (latest == null || !latest.isFile() || latest.length() > MAX_BYTES) return null;
        return Files.readAllBytes(latest.toPath());
    }

    Object reconcile(Object stored, Object normalized) throws Exception {
        synchronized (LOCK) {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IllegalStateException("Cannot create shortcut backup directory");
            }
            try (RandomAccessFile lock = new RandomAccessFile(new File(directory, "lock"), "rw");
                 FileLock held = lock.getChannel().lock()) {
                AtomicFile file = new AtomicFile(new File(directory, "bindings.pb"));
                byte[] previous = null;
                Object saved = null;
                if (file.getBaseFile().exists() || new File(directory, "bindings.pb.bak").exists()) {
                    if (file.getBaseFile().length() > MAX_BYTES) {
                        throw new IllegalStateException("Shortcut backup is too large");
                    }
                    previous = file.readFully();
                    Class<?> type = normalized.getClass();
                    Object instance = XposedHelpers.getStaticObjectField(type, "a");
                    saved = XposedHelpers.callStaticMethod(type.getSuperclass(), "parseFrom",
                            new Class<?>[] {type.getSuperclass(), byte[].class}, instance, previous);
                }
                Map<Integer, Object> originals = buttons(stored);
                Map<Integer, Object> backups = saved == null ? new HashMap<>() : buttons(saved);
                Object builder = XposedHelpers.callMethod(normalized, "toBuilder");
                // Rebuild only the entry list; retain the native envelope and new metadata.
                Object fresh = XposedHelpers.getStaticObjectField(normalized.getClass(), "a");
                XposedHelpers.setObjectField(mutable(builder), "b", XposedHelpers.getObjectField(fresh, "b"));
                for (Object info : (List<?>) XposedHelpers.getObjectField(normalized, "b")) {
                    int code = XposedHelpers.getIntField(XposedHelpers.getObjectField(info, "f"), "b");
                    Object backup = backups.get(code);
                    if (backup != null && !empty(backup) && empty(info)) {
                        int mode = XposedHelpers.getIntField(backup, "g");
                        // An explicit reset retains the supported modes. Only stock pruning
                        // removes our button or mode, including across a rejected update.
                        if (!supports(originals.get(code), mode) && supports(info, mode)) {
                            Object changed = XposedHelpers.callMethod(info, "toBuilder");
                            Object value = mutable(changed);
                            XposedHelpers.setIntField(value, "g", mode);
                            XposedHelpers.setObjectField(value, "i", XposedHelpers.getObjectField(backup, "i"));
                            info = loader.finish(changed);
                            Log.i("LauncherXLocal", "Recovered shortcut after stock cleanup: " + code);
                        }
                    }
                    XposedHelpers.callMethod(builder, loader.pick("aA", "av", "aw"), info);
                }
                Object result = loader.finish(builder);
                byte[] bytes = encode(result);
                if (bytes.length > MAX_BYTES) throw new IllegalStateException("Shortcut state is too large");
                // Listener callbacks in another process can carry an older snapshot. Never
                // let them overwrite a backup of a newer saved edit or explicit reset.
                if (!Arrays.equals(previous, bytes) && Arrays.equals(encode(stored), currentBytes())) {
                    FileOutputStream stream = null;
                    try {
                        stream = file.startWrite();
                        stream.write(bytes);
                        file.finishWrite(stream);
                    } catch (Exception error) {
                        file.failWrite(stream);
                        throw error;
                    }
                }
                return result;
            }
        }
    }
}
