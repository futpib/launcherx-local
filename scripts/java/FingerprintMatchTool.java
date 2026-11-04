package local.launcherx.patch;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Host-only matcher runner. Profile rows: alias, shape, code, index:target links. */
public final class FingerprintMatchTool {
    public static void main(String[] args) throws Exception {
        Map<String, String[]> profile = new HashMap<>(); Set<String> shapes = new HashSet<>();
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            String[] row = line.split("\t", -1); profile.put(row[0], row); shapes.add(row[1]);
        }
        Map<String, DexFingerprint.Node> nodes = DexFingerprint.read(new String[]{args[1]}, shapes, null);
        Map<String, Set<String>> candidates = new HashMap<>(); Map<String, Map<Integer, String>> links = new HashMap<>();
        for (String[] row : profile.values()) {
            Set<String> matches = new HashSet<>();
            for (DexFingerprint.Node node : nodes.values()) if (row[1].equals(node.shape) && row[2].equals(node.code)) matches.add(node.name);
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
