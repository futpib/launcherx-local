package local.launcherx.patch;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

/** Host-only matcher runner. Profile rows: alias, shape, code, index:target links. */
public final class FingerprintMatchTool {
    public static void main(String[] args) throws Exception {
        Map<String, String[]> profile = new HashMap<>(); Set<String> shapes = new HashSet<>();
        Map<String, Set<String>> selections = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            String[] row = line.split("\t", -1); profile.put(row[0], row); shapes.add(row[1]);
            String selection = row.length < 5 ? "" : row[4];
            selections.put(selection, selection.isEmpty() ? Collections.emptySet() : new HashSet<>(Arrays.asList(selection.split(","))));
        }
        Map<String, DexFingerprint.Node> nodes = DexFingerprint.read(new String[]{args[1]}, shapes, null, selections);
        Map<String, Set<String>> candidates = new HashMap<>(); Map<String, Map<Integer, String>> links = new HashMap<>();
        for (String[] row : profile.values()) {
            Set<String> matches = new HashSet<>();
            Set<String> shapeMatches = new TreeSet<>(), exactNames = new TreeSet<>();
            String selection = row.length < 5 || row[4].isEmpty() ? "" : "#" + row[4];
            for (Map.Entry<String, DexFingerprint.Node> entry : nodes.entrySet()) {
                DexFingerprint.Node node = entry.getValue();
                if (!entry.getKey().equals(node.name + selection) || !row[1].equals(node.shape)) continue;
                shapeMatches.add(node.name);
                if (row[2].equals(node.code)) { matches.add(entry.getKey()); exactNames.add(node.name); }
            }
            if (args.length > 2 && "--diagnose".equals(args[2]))
                System.out.println("candidates\t" + row[0] + "\t" + String.join(",", exactNames) + "\t" + String.join(",", shapeMatches));
            candidates.put(row[0], matches); Map<Integer, String> edges = new HashMap<>();
            if (!row[3].isEmpty()) for (String link : row[3].split(",")) {
                int colon = link.indexOf(':'); edges.put(Integer.parseInt(link.substring(0, colon)), link.substring(colon + 1));
            }
            links.put(row[0], edges);
        }
        for (Map.Entry<String, String> resolved : DexFingerprint.match(nodes, candidates, links).entrySet())
            System.out.println(resolved.getKey() + "=" + resolved.getValue());
    }
}
