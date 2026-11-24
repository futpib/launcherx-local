package local.launcherx.patch;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Host transport uses base64 cells so arbitrary DEX strings retain their exact bytes. */
public final class ContractMatchTool {
    public static void main(String[] args) throws Exception {
        Map<String, DexContracts.Rule> rules = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            String[] r = line.split("\t", -1);
            for (int i = 0; i < r.length; i++) r[i] = new String(Base64.getDecoder().decode(r[i]), StandardCharsets.UTF_8);
            DexContracts.Rule rule = rules.get(r[1]);
            if (r[0].equals("class")) {
                rule = new DexContracts.Rule(); rule.alias = r[1]; rule.name = r[2]; rule.parent = r[3];
                rule.isInterface = Integer.parseInt(r[4]);
                rules.put(rule.alias, rule);
            } else if (r[0].equals("string")) rule.strings.add(r[2]);
            else if (r[0].equals("field") || r[0].equals("method")) {
                DexContracts.Member m = new DexContracts.Member(); m.id = r[2]; m.name = r[3]; m.type = r[4]; m.isStatic = Integer.parseInt(r[5]);
                if (r[0].equals("field")) rule.fields.add(m);
                else {
                    m.parameters = r[6].isEmpty() ? Collections.emptyList() : Arrays.asList(r[6].split(","));
                    rule.methods.add(m);
                }
            } else if (r[0].equals("method-string")) rule.methods.get(Integer.parseInt(r[2])).strings.add(r[3]);
            else if (r[0].equals("type-reference")) rule.methods.get(Integer.parseInt(r[2])).typeReferences.put(Integer.parseInt(r[3]), r[4]);
            else if (r[0].equals("member-reference")) {
                DexContracts.Access a = new DexContracts.Access(); a.owner = r[3]; a.type = r[4];
                a.opcode = Integer.parseInt(r[5]); a.name = r[6]; rule.methods.get(Integer.parseInt(r[2])).references.add(a);
            }
            else if (r[0].equals("access")) {
                DexContracts.Access a = new DexContracts.Access(); a.id = r[2]; a.method = r[3]; a.owner = r[4]; a.type = r[5];
                a.opcode = Integer.parseInt(r[6]); a.after = r[7]; a.first = r[8].equals("true"); a.exclude = r[9]; rule.accesses.add(a);
            } else throw new IllegalArgumentException("Unknown profile row");
        }
        List<DexContracts.Rule> profile = new ArrayList<>(rules.values());
        Map<String, DexContracts.Definition> definitions = DexFingerprint.contracts(Arrays.copyOfRange(args, 1, args.length), profile);
        DexContracts.Match match = DexContracts.match(profile, definitions);
        for (Map.Entry<String, String> entry : match.classes.entrySet()) System.out.println("class\t" + entry.getKey() + "\t" + entry.getValue());
        for (Map.Entry<String, String> entry : match.members.entrySet()) System.out.println("member\t" + entry.getKey() + "\t" + entry.getValue());
    }
}
