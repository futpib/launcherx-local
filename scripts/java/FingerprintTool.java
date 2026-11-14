package local.launcherx.patch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashMap;
import java.util.Collections;

/** Host-only profile generation using the same parser as the module. */
public final class FingerprintTool {
    public static void main(String[] args) throws Exception {
        HashSet<String> names = new HashSet<>();
        Map<String, Set<String>> selections = new HashMap<>();
        Map<String, String> requested = new HashMap<>();
        for (String argument : Arrays.copyOfRange(args, 1, args.length)) {
            String[] parts = argument.split("#", -1);
            String name = "L" + parts[0].replace('.', '/') + ";";
            String selection = parts.length == 1 ? "" : parts[1];
            names.add(name); requested.put(name, selection);
            selections.put(selection, selection.isEmpty() ? Collections.emptySet()
                    : new HashSet<>(Arrays.asList(selection.split(","))));
        }
        Map<String, DexFingerprint.Node> nodes = DexFingerprint.read(new String[]{args[0]}, null, names, selections);
        for (Map.Entry<String, String> item : new TreeMap<>(requested).entrySet()) {
            DexFingerprint.Node node = nodes.get(item.getKey() + (item.getValue().isEmpty() ? "" : "#" + item.getValue()));
            if (node == null) throw new IllegalStateException("Missing profile members: " + item.getKey());
            System.out.println(node.name + "\t" + node.shape + "\t" + node.code + "\t" + String.join(",", node.references));
        }
    }
}
