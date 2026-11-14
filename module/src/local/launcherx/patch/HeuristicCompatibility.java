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
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;

/** Require a unique, consistent match for every reviewed class before installing hooks. */
final class HeuristicCompatibility {
    static final class Match {
        final long bindings;
        final Map<String, String> classes;
        Match(long bindings, Map<String, String> classes) { this.bindings = bindings; this.classes = classes; }
    }

    static Match resolve(Context context, ApplicationInfo info, long version) throws Exception {
        List<String> failures = new ArrayList<>();
        for (String[] asset : BuildConfig.FINGERPRINT_ASSETS) {
            JSONObject profile = profile(context, asset);
            long bindings = profile.getLong("binding_version");
            if (bindings > version) continue;
            try {
                Map<String, String> classes = resolve(context, info, profile);
                return new Match(bindings, classes);
            } catch (IllegalStateException mismatch) {
                failures.add(bindings + ": " + mismatch.getMessage());
            }
        }
        throw new IllegalStateException("Heuristic mismatch: " + failures);
    }

    private static JSONObject profile(Context context, String[] asset) throws Exception {
        Context module = context.createPackageContext("local.launcherx.patch", 0);
        try (InputStream input = module.getAssets().open(asset[0])) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (!asset[1].equals(DexFingerprint.hash(text)))
                throw new IllegalStateException("Heuristic profile differs from the loaded module; restart Google TV");
            return new JSONObject(text);
        }
    }

    private static Map<String, String> resolve(Context context, ApplicationInfo info, JSONObject profile) throws Exception {
        int format = profile.getInt("format");
        long bindings = profile.getLong("binding_version");
        if ((format != 1 && format != 2) || (bindings != 837595 && bindings != 841183))
            throw new IllegalStateException("Unknown heuristic profile format");
        JSONObject classes = profile.getJSONObject("classes");
        Set<String> shapes = new HashSet<>();
        Map<String, Set<String>> selections = new HashMap<>();
        Map<String, String> classSelections = new HashMap<>();
        for (Iterator<String> keys = classes.keys(); keys.hasNext();) {
            String alias = keys.next(); JSONObject item = classes.getJSONObject(alias);
            shapes.add(item.getString("shape"));
            List<String> methods = new ArrayList<>(); JSONArray required = item.optJSONArray("methods");
            if (required != null) {
                if (format != 2 || required.length() == 0) throw new IllegalStateException("Invalid method contract");
                for (int i = 0; i < required.length(); i++) methods.add(required.getString(i));
            }
            Collections.sort(methods); String selection = String.join(",", methods);
            selections.put(selection, new HashSet<>(methods)); classSelections.put(alias, selection);
        }
        List<String> paths = new ArrayList<>(); paths.add(info.sourceDir);
        if (info.splitSourceDirs != null) java.util.Collections.addAll(paths, info.splitSourceDirs);
        Map<String, DexFingerprint.Node> nodes = DexFingerprint.read(paths.toArray(new String[0]), shapes, null, selections);
        Map<String, Set<String>> candidates = new HashMap<>();
        Map<String, Map<Integer, String>> links = new HashMap<>();
        for (Iterator<String> keys = classes.keys(); keys.hasNext();) {
            String alias = keys.next(); JSONObject item = classes.getJSONObject(alias);
            Set<String> matches = new HashSet<>();
            for (Map.Entry<String, DexFingerprint.Node> entry : nodes.entrySet()) {
                DexFingerprint.Node node = entry.getValue(); String selection = classSelections.get(alias);
                String key = node.name + (selection.isEmpty() ? "" : "#" + selection);
                if (entry.getKey().equals(key) && node.shape.equals(item.getString("shape"))
                        && node.code.equals(item.getString("code"))) matches.add(key);
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
        if (!"Continue watching".equals(launcher.createConfigurationContext(english).getString(
                bindings == 841183 ? 0x7f1405d5 : 0x7f1405cd)))
            throw new IllegalStateException("Heuristic resource mismatch: Continue watching");
        return resolved;
    }

}
