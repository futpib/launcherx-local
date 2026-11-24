package local.launcherx.patch;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Resolve an obfuscated string by its verified English value, retaining native translations. */
final class LauncherResources {
    static int string(Context context, String englishValue) throws Exception {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.ENGLISH);
        Resources english = context.createConfigurationContext(configuration).getResources();
        byte[] table;
        try (ZipFile apk = new ZipFile(context.getApplicationInfo().sourceDir)) {
            ZipEntry entry = apk.getEntry("resources.arsc");
            if (entry == null || entry.getSize() < 12 || entry.getSize() > 32 * 1024 * 1024)
                throw new IllegalStateException("Invalid launcher resources");
            try (InputStream input = apk.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (out.size() + count > entry.getSize()) throw new IllegalStateException("Resource size mismatch");
                    out.write(buffer, 0, count);
                }
                table = out.toByteArray();
            }
        }
        int found = 0;
        for (Map.Entry<Integer, Integer> range : ResourceTable.ranges(table).entrySet()) {
            for (int i = 0; i < range.getValue(); i++) {
                int id = range.getKey() | i;
                try {
                    if (!"string".equals(english.getResourceTypeName(id))) break;
                    if (!englishValue.equals(english.getString(id))) continue;
                    if (found != 0) throw new IllegalStateException("Ambiguous launcher string: " + englishValue);
                    found = id;
                } catch (Resources.NotFoundException absent) { /* Sparse resource entry. */ }
            }
        }
        if (found == 0) throw new IllegalStateException("Missing launcher string: " + englishValue);
        return found;
    }
}
