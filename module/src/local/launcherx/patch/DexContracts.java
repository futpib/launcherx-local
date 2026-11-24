package local.launcherx.patch;

import java.util.*;

/** Explicit member contracts, not whole-class equality or similarity scoring. */
final class DexContracts {
    static final class Field {
        String owner, name, type;
        int flags, opcode;
        Field(String owner, String name, String type, int flags) {
            this.owner = owner; this.name = name; this.type = type; this.flags = flags;
        }
    }
    static final class Method {
        String owner, name, result;
        List<String> parameters = new ArrayList<>(), strings = new ArrayList<>(), types = new ArrayList<>();
        List<Field> accesses = new ArrayList<>();
        List<String> accessAnchors = new ArrayList<>();
        int flags, offset;
    }
    static final class Definition {
        String name, parent;
        int flags;
        List<String> interfaces = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        List<Method> methods = new ArrayList<>();
        Set<String> strings = new HashSet<>();
    }
    static final class Member {
        String id = "", name = "", type;
        int isStatic;
        List<String> parameters;
        List<String> strings = new ArrayList<>();
        Map<Integer, String> typeReferences = new HashMap<>();
        List<Access> references = new ArrayList<>();
    }
    static final class Access {
        String id, method, owner, type, name = "", after = "", exclude = "";
        int opcode;
        boolean first;
    }
    static final class Rule {
        String alias, name = "", parent = "";
        int isInterface = -1;
        List<String> strings = new ArrayList<>();
        List<Member> fields = new ArrayList<>(), methods = new ArrayList<>();
        List<Access> accesses = new ArrayList<>();
    }
    static final class Match {
        final Map<String, String> classes = new TreeMap<>(), members = new TreeMap<>();
    }

    private static boolean type(String expected, String actual, Map<String, Set<String>> candidates) {
        if (expected.startsWith("[")) return actual.startsWith("[") && type(expected.substring(1), actual.substring(1), candidates);
        if (expected.startsWith("@")) {
            Set<String> values = candidates == null ? null : candidates.get(expected.substring(1));
            return values == null ? actual.startsWith("L") : values.contains(actual);
        }
        if (expected.equals("?")) return actual.startsWith("L") && actual.indexOf('/') < 0;
        return expected.equals(actual);
    }

    private static boolean field(Member member, Field field, Map<String, Set<String>> candidates) {
        return (member.name.isEmpty() || member.name.equals(field.name))
                && ((field.flags & 8) != 0 ? 1 : 0) == member.isStatic && type(member.type, field.type, candidates);
    }

    private static boolean method(Member member, Method method, Map<String, Set<String>> candidates, boolean code) {
        boolean anyParameters = member.parameters.equals(Collections.singletonList("..."));
        if ((!member.name.isEmpty() && !member.name.equals(method.name))
                || ((method.flags & 8) != 0 ? 1 : 0) != member.isStatic
                || !type(member.type, method.result, candidates) || (!anyParameters && member.parameters.size() != method.parameters.size())) return false;
        for (int i = 0; !anyParameters && i < member.parameters.size(); i++)
            if (!type(member.parameters.get(i), method.parameters.get(i), candidates)) return false;
        if (code) {
            if (!method.strings.containsAll(member.strings)) return false;
            for (Map.Entry<Integer, String> ref : member.typeReferences.entrySet())
                if (ref.getKey() < 0 || ref.getKey() >= method.types.size()
                        || !type(ref.getValue(), method.types.get(ref.getKey()), candidates)) return false;
            for (Access ref : member.references) {
                boolean found = false;
                for (Field access : method.accesses) if ((ref.name.isEmpty() || ref.name.equals(access.name))
                        && ref.opcode == access.opcode && type(ref.owner, access.owner, candidates)
                        && type(ref.type, access.type, candidates)) { found = true; break; }
                if (!found) return false;
            }
        }
        return true;
    }

    static boolean accepts(Rule rule, Definition definition, Map<String, Set<String>> candidates, boolean code) {
        if (!rule.name.isEmpty() && !rule.name.equals(definition.name)) return false;
        if (rule.isInterface != -1 && rule.isInterface != ((definition.flags & 512) == 0 ? 0 : 1)) return false;
        if (!rule.parent.isEmpty() && !type(rule.parent, definition.parent, candidates)) return false;
        if (code && !definition.strings.containsAll(rule.strings)) return false;
        for (Member member : rule.fields) {
            boolean found = false;
            for (Field field : definition.fields) if (field(member, field, candidates)) { found = true; break; }
            if (!found) return false;
        }
        for (Member member : rule.methods) {
            boolean found = false;
            for (Method method : definition.methods) if (method(member, method, candidates, code)) { found = true; break; }
            if (!found) return false;
        }
        return true;
    }

    static Match match(List<Rule> rules, Map<String, Definition> definitions) {
        Map<String, Set<String>> candidates = new TreeMap<>();
        Set<String> aliases = new HashSet<>();
        for (Rule rule : rules) {
            if (!aliases.add(rule.alias)) throw new IllegalStateException("Duplicate contract alias " + rule.alias);
            Set<String> names = new TreeSet<>();
            for (Definition definition : definitions.values())
                if (accepts(rule, definition, null, true)) names.add(definition.name);
            candidates.put(rule.alias, names);
        }
        for (Rule rule : rules) {
            validateType(rule.parent, aliases);
            for (Member member : rule.fields) validateType(member.type, aliases);
            for (Member member : rule.methods) {
                validateType(member.type, aliases);
                for (String parameter : member.parameters) validateType(parameter, aliases);
                for (String target : member.typeReferences.values()) validateType(target, aliases);
                for (Access access : member.references) { validateType(access.owner, aliases); validateType(access.type, aliases); }
            }
            for (Access access : rule.accesses) { validateType(access.owner, aliases); validateType(access.type, aliases); }
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : candidates.entrySet())
            if (entry.getValue().isEmpty()) missing.add(entry.getKey());
        // Report the cause before disconnected graph nodes become large ambiguous sets.
        if (!missing.isEmpty()) throw new IllegalStateException("Contract mismatch: missing targets " + missing);
        boolean changed;
        do {
            changed = false;
            for (Rule rule : rules) {
                for (Iterator<String> i = candidates.get(rule.alias).iterator(); i.hasNext();) {
                    if (!accepts(rule, definitions.get(i.next()), candidates, true)) { i.remove(); changed = true; }
                }
                if (candidates.get(rule.alias).size() == 1) {
                    Definition definition = definitions.get(candidates.get(rule.alias).iterator().next());
                    if (!rule.parent.isEmpty()) changed |= narrow(rule.parent, Collections.singleton(definition.parent), candidates);
                    for (Member member : rule.fields) {
                        Set<String> types = new HashSet<>();
                        for (Field field : definition.fields) if (field(member, field, candidates)) types.add(field.type);
                        changed |= narrow(member.type, types, candidates);
                    }
                    for (Member member : rule.methods) {
                        List<Method> matches = new ArrayList<>();
                        for (Method method : definition.methods) if (method(member, method, candidates, true)) matches.add(method);
                        Set<String> types = new HashSet<>();
                        for (Method method : matches) types.add(method.result);
                        changed |= narrow(member.type, types, candidates);
                        for (int i = 0; !member.parameters.equals(Collections.singletonList("...")) && i < member.parameters.size(); i++) {
                            types = new HashSet<>();
                            for (Method method : matches) types.add(method.parameters.get(i));
                            changed |= narrow(member.parameters.get(i), types, candidates);
                        }
                        for (Map.Entry<Integer, String> ref : member.typeReferences.entrySet()) {
                            types = new HashSet<>();
                            for (Method method : matches) types.add(method.types.get(ref.getKey()));
                            changed |= narrow(ref.getValue(), types, candidates);
                        }
                    }
                }
            }
        } while (changed);
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : candidates.entrySet())
            if (entry.getValue().size() != 1) failures.add(entry.getKey() + "=" + entry.getValue().size() + " candidates");
        if (!failures.isEmpty()) throw new IllegalStateException("Contract mismatch: " + failures);
        Match match = new Match(); Set<String> used = new HashSet<>();
        Map<String, Method> resolvedMethods = new HashMap<>();
        for (Rule rule : rules) {
            String name = candidates.get(rule.alias).iterator().next();
            if (!used.add(name)) throw new IllegalStateException("Contract classes overlap: " + name);
            match.classes.put(rule.alias, name.substring(1, name.length() - 1).replace('/', '.'));
            Definition definition = definitions.get(name);
            for (Member member : rule.methods) {
                if (member.id.isEmpty()) continue;
                Method selected = null;
                for (Method method : definition.methods) if (method(member, method, candidates, true)) {
                    if (selected != null) throw new IllegalStateException("Ambiguous contract method " + rule.alias + "." + member.id);
                    selected = method;
                }
                resolvedMethods.put(rule.alias + "." + member.id, selected);
                put(match, rule.alias, member.id, selected.name);
            }
            for (Member member : rule.fields) {
                if (member.id.isEmpty()) continue;
                Field selected = null;
                for (Field field : definition.fields) if (field(member, field, candidates)) {
                    if (selected != null) throw new IllegalStateException("Ambiguous contract field " + rule.alias + "." + member.id);
                    selected = field;
                }
                put(match, rule.alias, member.id, selected.name);
            }
        }
        for (Rule rule : rules) {
            for (Access access : rule.accesses) {
                Method method = resolvedMethods.get(rule.alias + "." + access.method);
                if (method == null) throw new IllegalStateException("Unknown access method " + access.method);
                Set<String> names = new HashSet<>();
                for (int i = 0; i < method.accesses.size(); i++) {
                    Field field = method.accesses.get(i);
                    if (field.opcode != access.opcode || !type(access.owner, field.owner, candidates)
                            || !type(access.type, field.type, candidates)
                            || (!access.after.isEmpty() && !access.after.equals(method.accessAnchors.get(i)))) continue;
                    names.add(field.name);
                    if (access.first) break;
                }
                if (!access.exclude.isEmpty()) {
                    Method excluded = resolvedMethods.get(access.exclude);
                    if (excluded == null) throw new IllegalStateException("Unknown excluded method " + access.exclude);
                    for (Field field : excluded.accesses) if (type(access.owner, field.owner, candidates)
                            && type(access.type, field.type, candidates)) names.remove(field.name);
                }
                if (names.size() != 1) throw new IllegalStateException("Ambiguous or missing field access " + rule.alias + "." + access.id + ": " + names);
                put(match, rule.alias, access.id, names.iterator().next());
            }
        }
        return match;
    }

    private static boolean narrow(String expected, Set<String> actual, Map<String, Set<String>> candidates) {
        if (expected.startsWith("[")) {
            Set<String> elements = new HashSet<>();
            for (String name : actual) if (name.startsWith("[")) elements.add(name.substring(1));
            return narrow(expected.substring(1), elements, candidates);
        }
        if (!expected.startsWith("@")) return false;
        Set<String> values = candidates.get(expected.substring(1));
        if (values == null) throw new IllegalStateException("Unknown contract alias " + expected);
        return values.retainAll(actual);
    }

    private static void validateType(String type, Set<String> aliases) {
        while (type.startsWith("[")) type = type.substring(1);
        if (type.startsWith("@") && !aliases.contains(type.substring(1))) throw new IllegalStateException("Unknown contract alias " + type);
    }

    private static void put(Match match, String alias, String id, String value) {
        if (match.members.put(alias + "." + id, value) != null)
            throw new IllegalStateException("Duplicate member slot " + alias + "." + id);
    }
}
