package local.launcherx.patch;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/** Require a unique, consistent match for every reviewed class before installing hooks. */
final class HeuristicCompatibility {
    static Map<String, String> resolve(Context context, ApplicationInfo info) throws Exception {
        Context module = context.createPackageContext("local.launcherx.patch", 0);
        JSONObject profile;
        try (InputStream input = module.getAssets().open("compatibility-837595.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (!BuildConfig.FINGERPRINT_SHA256.equals(DexFingerprint.hash(text)))
                throw new IllegalStateException("Heuristic profile differs from the loaded module; restart Google TV");
            profile = new JSONObject(text);
        }
        if (profile.getInt("format") != 1 || profile.getLong("binding_version") != 837595)
            throw new IllegalStateException("Unknown heuristic profile format");
        JSONObject classes = profile.getJSONObject("classes");
        Set<String> shapes = new HashSet<>();
        for (Iterator<String> keys = classes.keys(); keys.hasNext();) shapes.add(classes.getJSONObject(keys.next()).getString("shape"));
        List<String> paths = new ArrayList<>(); paths.add(info.sourceDir);
        if (info.splitSourceDirs != null) java.util.Collections.addAll(paths, info.splitSourceDirs);
        Map<String, DexFingerprint.Node> nodes = DexFingerprint.read(paths.toArray(new String[0]), shapes, null);
        Map<String, Set<String>> candidates = new HashMap<>();
        Map<String, Map<Integer, String>> links = new HashMap<>();
        for (Iterator<String> keys = classes.keys(); keys.hasNext();) {
            String alias = keys.next(); JSONObject item = classes.getJSONObject(alias);
            Set<String> matches = new HashSet<>();
            for (DexFingerprint.Node node : nodes.values()) {
                if (node.shape.equals(item.getString("shape")) && node.code.equals(item.getString("code"))) matches.add(node.name);
            }
            candidates.put(alias, matches);
            Map<Integer, String> edges = new HashMap<>(); JSONObject references = item.getJSONObject("links");
            for (Iterator<String> indices = references.keys(); indices.hasNext();) {
                String index = indices.next(); edges.put(Integer.parseInt(index), references.getString(index));
            }
            links.put(alias, edges);
        }
        Map<String, String> resolved = DexFingerprint.match(nodes, candidates, links);
        // The native label is an opaque resource ID, so its meaning must also survive.
        Context launcher = context.createPackageContext(Compatibility.TARGET, 0);
        Configuration english = new Configuration(launcher.getResources().getConfiguration()); english.setLocale(Locale.ENGLISH);
        if (!"Continue watching".equals(launcher.createConfigurationContext(english).getString(0x7f1405cd)))
            throw new IllegalStateException("Heuristic resource mismatch: Continue watching");
        return resolved;
    }

}
