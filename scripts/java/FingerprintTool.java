package local.launcherx.patch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

/** Host-only profile generation using the same parser as the module. */
public final class FingerprintTool {
    public static void main(String[] args) throws Exception {
        HashSet<String> names = new HashSet<>();
        for (String name : Arrays.copyOfRange(args, 1, args.length)) names.add("L" + name.replace('.', '/') + ";");
        Map<String, DexFingerprint.Node> nodes = DexFingerprint.read(new String[]{args[0]}, null, names);
        if (!nodes.keySet().equals(names)) throw new IllegalStateException("Missing profile classes");
        for (DexFingerprint.Node node : new TreeMap<>(nodes).values()) {
            System.out.println(node.name + "\t" + node.shape + "\t" + node.code + "\t" + String.join(",", node.references));
        }
    }
}
