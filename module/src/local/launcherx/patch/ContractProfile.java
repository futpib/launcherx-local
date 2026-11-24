package local.launcherx.patch;

import java.util.*;
import org.json.*;

final class ContractProfile {
    static List<DexContracts.Rule> parse(JSONObject profile) throws Exception {
        if (profile.getInt("format") != 1 || profile.getLong("binding_version") != 841183) throw new IllegalStateException("Unknown contract format");
        List<DexContracts.Rule> rules = new ArrayList<>(); JSONObject classes = profile.getJSONObject("classes");
        for (Iterator<String> keys = classes.keys(); keys.hasNext();) {
            DexContracts.Rule rule = new DexContracts.Rule(); rule.alias = keys.next();
            JSONObject item = classes.getJSONObject(rule.alias);
            rule.name = item.optString("name"); rule.parent = item.optString("parent");
            if (item.has("interface")) rule.isInterface = item.getBoolean("interface") ? 1 : 0;
            rule.strings = strings(item.optJSONArray("strings"));
            rule.fields = members(item.optJSONArray("fields"), false);
            rule.methods = members(item.optJSONArray("methods"), true);
            JSONArray accesses = item.optJSONArray("accesses");
            if (accesses != null) for (int i = 0; i < accesses.length(); i++) {
                JSONObject value = accesses.getJSONObject(i); DexContracts.Access access = new DexContracts.Access();
                access.id = value.getString("id"); access.method = value.getString("method");
                access.owner = value.getString("owner"); access.type = value.getString("type");
                access.opcode = value.getInt("opcode"); access.after = value.optString("after");
                access.exclude = value.optString("exclude"); access.first = value.optBoolean("first"); rule.accesses.add(access);
            }
            rules.add(rule);
        }
        return rules;
    }

    private static List<String> strings(JSONArray values) throws Exception {
        List<String> result = new ArrayList<>();
        if (values != null) for (int i = 0; i < values.length(); i++) result.add(values.getString(i));
        return result;
    }

    private static List<DexContracts.Member> members(JSONArray values, boolean methods) throws Exception {
        List<DexContracts.Member> result = new ArrayList<>();
        if (values != null) for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.getJSONObject(i); DexContracts.Member member = new DexContracts.Member();
            member.id = value.optString("id"); member.name = value.optString("name");
            member.type = value.getString("type"); member.isStatic = value.optBoolean("static") ? 1 : 0;
            if (methods) member.parameters = strings(value.getJSONArray("parameters"));
            member.strings = strings(value.optJSONArray("strings"));
            JSONArray accesses = value.optJSONArray("references");
            if (accesses != null) for (int j = 0; j < accesses.length(); j++) {
                JSONObject ref = accesses.getJSONObject(j); DexContracts.Access access = new DexContracts.Access();
                access.owner = ref.getString("owner"); access.type = ref.getString("type");
                access.opcode = ref.getInt("opcode"); access.name = ref.optString("name"); member.references.add(access);
            }
            JSONObject references = value.optJSONObject("type_references");
            if (references != null) for (Iterator<String> keys = references.keys(); keys.hasNext();) {
                String index = keys.next(); member.typeReferences.put(Integer.parseInt(index), references.getString(index));
            }
            result.add(member);
        }
        return result;
    }
}
