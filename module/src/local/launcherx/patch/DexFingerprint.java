package local.launcherx.patch;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Conservative DEX fingerprints: ignore pool indices and obfuscated class names,
 * but retain member names, instructions, registers, literals and exception handlers. */
final class DexFingerprint {
    static final class Node {
        final String name, shape, code;
        final List<String> references;
        Node(String name, String shape, String code, List<String> references) {
            this.name = name; this.shape = shape; this.code = code; this.references = references;
        }
    }

    static Map<String, Node> read(String[] paths, Set<String> shapes, Set<String> names) throws Exception {
        Map<String, Node> result = new HashMap<>();
        Set<String> seen = new HashSet<>();
        long total = 0;
        for (String path : paths) try (ZipFile apk = new ZipFile(path)) {
            Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".dex") || entry.getName().contains("/")) continue;
                if (entry.getSize() < 112 || entry.getSize() > 64 * 1024 * 1024
                        || (total += entry.getSize()) > 128 * 1024 * 1024) {
                    throw new IllegalStateException("DEX size limit");
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = apk.getInputStream(entry)) {
                    byte[] buffer = new byte[32768]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (out.size() + count > entry.getSize()) throw new IllegalStateException("DEX size mismatch");
                        out.write(buffer, 0, count);
                    }
                }
                new Reader(out.toByteArray()).classes(result, shapes, names, seen);
            }
        }
        return result;
    }

    static String hash(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append("0123456789abcdef".charAt((value & 255) >>> 4))
                .append("0123456789abcdef".charAt(value & 15));
        return result.toString();
    }

    static Map<String, String> match(Map<String, DexFingerprint.Node> nodes, Map<String, Set<String>> candidates,
            Map<String, Map<Integer, String>> links) {
        boolean changed;
        do {
            changed = false;
            for (String alias : candidates.keySet()) {
                Set<String> sources = candidates.get(alias);
                for (Map.Entry<Integer, String> edge : links.get(alias).entrySet()) {
                    Set<String> targets = candidates.get(edge.getValue());
                    if (targets == null) throw new IllegalStateException("Unknown fingerprint link");
                    Set<String> possible = new HashSet<>();
                    for (Iterator<String> it = sources.iterator(); it.hasNext();) {
                        DexFingerprint.Node node = nodes.get(it.next()); int index = edge.getKey();
                        if (index < 0 || index >= node.references.size() || !targets.contains(node.references.get(index))) {
                            it.remove(); changed = true;
                        } else possible.add(node.references.get(index));
                    }
                    if (targets.retainAll(possible)) changed = true;
                }
                if (sources.isEmpty()) throw new IllegalStateException("Heuristic mismatch: " + alias);
            }
        } while (changed);
        Map<String, String> result = new HashMap<>(); Set<String> used = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : candidates.entrySet()) {
            if (entry.getValue().size() != 1) throw new IllegalStateException("Ambiguous heuristic target: " + entry.getKey());
            String name = entry.getValue().iterator().next();
            if (!used.add(name)) throw new IllegalStateException("Heuristic targets overlap");
            result.put(entry.getKey(), name.substring(1, name.length() - 1).replace('/', '.'));
        }
        return result;
    }

    private static final class Part implements Comparable<Part> {
        final StringBuilder text = new StringBuilder();
        final List<String> refs = new ArrayList<>();
        final String self;
        Part(String self) { this.self = self; }
        void add(Object value) {
            String s = String.valueOf(value);
            text.append(s.length()).append(':').append(s);
        }
        void type(String value) {
            int start = value.lastIndexOf('[') + 1;
            String scalar = value.substring(start);
            refs.add(scalar);
            if (scalar.equals(self)) add(value.substring(0, start) + "L@;");
            else if (scalar.startsWith("L") && scalar.indexOf('/') < 0) add(value.substring(0, start) + "L?;");
            else add(value);
        }
        void append(Part other) { add(other.text); refs.addAll(other.refs); }
        @Override public int compareTo(Part other) { return text.toString().compareTo(other.text.toString()); }
    }

    private static final class Reader {
        final byte[] data;
        final String[] strings, types;
        final int protos, fields, methods;
        Reader(byte[] data) {
            this.data = data;
            if (data.length < 112 || data[0] != 'd' || data[1] != 'e' || data[2] != 'x'
                    || data[3] != '\n' || data[7] != 0 || u32(36) != 112 || u32(40) != 0x12345678
                    || u32(32) != data.length) throw new IllegalStateException("Unsupported DEX header");
            int count = table(56, 4), offset = u32(60);
            strings = new String[count];
            for (int i = 0; i < count; i++) strings[i] = string(u32(offset + i * 4));
            count = table(64, 4); offset = u32(68); types = new String[count];
            for (int i = 0; i < count; i++) types[i] = strings[u32(offset + i * 4)];
            table(72, 12); protos = u32(76);
            table(80, 8); fields = u32(84);
            table(88, 8); methods = u32(92);
            table(96, 32);
        }
        int table(int header, int width) {
            int count = u32(header), offset = u32(header + 4);
            if (count < 0 || offset < 0 || (long) offset + (long) count * width > data.length)
                throw new IllegalStateException("Invalid DEX table");
            return count;
        }
        int u16(int p) { return (data[p] & 255) | ((data[p + 1] & 255) << 8); }
        int u32(int p) { return u16(p) | (u16(p + 2) << 16); }
        final class Cursor {
            int p;
            Cursor(int p) { this.p = p; }
            int leb() {
                int result = 0;
                for (int shift = 0; shift < 35; shift += 7) {
                    int v = data[p++] & 255; result |= (v & 127) << shift;
                    if ((v & 128) == 0) return result;
                }
                throw new IllegalStateException("Invalid DEX LEB128");
            }
            int signed() {
                int result = 0, shift = 0, v;
                do { v = data[p++] & 255; result |= (v & 127) << shift; shift += 7;
                    if (shift > 35) throw new IllegalStateException("Invalid signed LEB128");
                } while ((v & 128) != 0);
                return shift < 32 && (v & 64) != 0 ? result | (-1 << shift) : result;
            }
        }
        String string(int p) {
            Cursor c = new Cursor(p); int length = c.leb();
            if (length < 0 || length > data.length) throw new IllegalStateException("Invalid DEX string");
            StringBuilder out = new StringBuilder(length);
            while (true) {
                int a = data[c.p++] & 255;
                if (a == 0) break;
                if (a < 128) out.append((char) a);
                else if ((a & 224) == 192) out.append((char) (((a & 31) << 6) | (data[c.p++] & 63)));
                else if ((a & 240) == 224) out.append((char) (((a & 15) << 12) | ((data[c.p++] & 63) << 6) | (data[c.p++] & 63)));
                else throw new IllegalStateException("Invalid DEX MUTF-8");
            }
            if (out.length() != length) throw new IllegalStateException("Invalid DEX string length");
            return out.toString();
        }
        void typeList(Part out, int offset) {
            int count = offset == 0 ? 0 : u32(offset); out.add(count);
            for (int i = 0; i < count; i++) out.type(types[u16(offset + 4 + i * 2)]);
        }
        void proto(Part out, int index) {
            int p = protos + 12 * index;
            out.type(types[u32(p + 4)]); typeList(out, u32(p + 8));
        }
        void field(Part out, int index) {
            int p = fields + 8 * index;
            out.type(types[u16(p)]); out.add(strings[u32(p + 4)]); out.type(types[u16(p + 2)]);
        }
        void method(Part out, int index) {
            int p = methods + 8 * index;
            out.type(types[u16(p)]); out.add(strings[u32(p + 4)]); proto(out, u16(p + 2));
        }
        void value(Part out, Cursor c, int depth) {
            if (depth > 32) throw new IllegalStateException("Deep DEX value");
            int header = data[c.p++] & 255, kind = header & 31, arg = header >>> 5;
            out.add(kind);
            if (kind == 28) {
                int count = c.leb(); out.add(count);
                for (int i = 0; i < count; i++) value(out, c, depth + 1);
            } else if (kind == 29) {
                out.type(types[c.leb()]); int count = c.leb(); out.add(count);
                for (int i = 0; i < count; i++) { out.add(strings[c.leb()]); value(out, c, depth + 1); }
            } else if (kind == 30 || kind == 31) out.add(arg);
            else {
                long v = 0; for (int i = 0; i <= arg; i++) v |= (long) (data[c.p++] & 255) << (i * 8);
                if (kind == 23) out.add(strings[(int) v]);
                else if (kind == 24) out.type(types[(int) v]);
                else if (kind == 25 || kind == 27) field(out, (int) v);
                else if (kind == 26) method(out, (int) v);
                else if (kind == 21) proto(out, (int) v);
                else if (kind == 22) throw new IllegalStateException("DEX method handle unsupported");
                else { out.add(arg); out.add(v); }
            }
        }
        void code(Part out, int offset) {
            if (offset == 0) { out.add("abstract"); return; }
            int count = u32(offset + 12), start = offset + 16, end = start + count * 2;
            if (count < 0 || end < start || end > data.length) throw new IllegalStateException("Invalid DEX code");
            out.add(u16(offset)); out.add(u16(offset + 2)); out.add(u16(offset + 4));
            int tries = u16(offset + 6); out.add(tries); out.add(count);
            for (int p = start; p < end;) {
                int word = u16(p), op = word & 255, width = width(op);
                if (op == 0 && word != 0) {
                    if (word == 0x100) width = 4 + 2 * u16(p + 2);
                    else if (word == 0x200) width = 2 + 4 * u16(p + 2);
                    else if (word == 0x300) width = 4 + (int) (((long) u16(p + 2) * u32(p + 4) + 1) / 2);
                    else throw new IllegalStateException("Unknown DEX payload");
                }
                if (width <= 0 || p + (long) width * 2 > end) throw new IllegalStateException("Unsupported DEX opcode " + op);
                out.add(word);
                int skip = 0;
                if (op == 0x1a || op == 0x1b) { out.add(strings[op == 0x1a ? u16(p + 2) : u32(p + 2)]); skip = op == 0x1a ? 1 : 2; }
                else if (op == 0x1c || op == 0x1f || op == 0x20 || (op >= 0x22 && op <= 0x25)) { out.type(types[u16(p + 2)]); skip = 1; }
                else if (op >= 0x52 && op <= 0x6d) { field(out, u16(p + 2)); skip = 1; }
                else if ((op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78)) { method(out, u16(p + 2)); skip = 1; }
                else if (op >= 0xfa) throw new IllegalStateException("Extended DEX instruction unsupported");
                for (int j = 1 + skip; j < width; j++) out.add(u16(p + j * 2));
                p += width * 2;
            }
            if (tries == 0) return;
            int p = end + (count % 2) * 2;
            for (int i = 0; i < tries; i++, p += 8) { out.add(u32(p)); out.add(u16(p + 4)); out.add(u16(p + 6)); }
            Cursor c = new Cursor(p); int handlers = c.leb(); out.add(handlers);
            for (int i = 0; i < handlers; i++) {
                int size = c.signed(); out.add(size);
                for (int j = 0; j < Math.abs(size); j++) { out.type(types[c.leb()]); out.add(c.leb()); }
                if (size <= 0) out.add(c.leb());
            }
        }
        int width(int op) {
            if (op >= 0x3e && op <= 0x43 || op == 0x73 || op == 0x79 || op == 0x7a || op >= 0xe3) return 0;
            if (op == 0x18) return 5;
            if (op == 3 || op == 6 || op == 9 || op == 0x14 || op == 0x17 || op == 0x1b
                    || op >= 0x24 && op <= 0x26 || op >= 0x2a && op <= 0x2c
                    || op >= 0x6e && op <= 0x78) return 3;
            if (op == 2 || op == 5 || op == 8 || op >= 0x13 && op <= 0x1c
                    || op == 0x1f || op == 0x20 || op == 0x22 || op == 0x23 || op == 0x29
                    || op >= 0x2d && op <= 0x6d || op >= 0x90 && op <= 0xaf || op >= 0xd0) return 2;
            return 1;
        }
        void classes(Map<String, Node> result, Set<String> wantedShapes, Set<String> wantedNames, Set<String> seen) throws Exception {
            int count = u32(96), offset = u32(100);
            for (int i = 0; i < count; i++) {
                int p = offset + i * 32; String name = types[u32(p)];
                if (!seen.add(name)) throw new IllegalStateException("Duplicate DEX class " + name);
                Part header = new Part(name); header.add(u32(p + 4));
                if (u32(p + 8) != -1) header.type(types[u32(p + 8)]);
                typeList(header, u32(p + 12));
                int classData = u32(p + 24), statics = u32(p + 28);
                List<Part> declarations = new ArrayList<>(); List<int[]> bodies = new ArrayList<>();
                if (classData != 0) {
                    Cursor c = new Cursor(classData);
                    int sf = c.leb(), inf = c.leb(), dm = c.leb(), vm = c.leb();
                    for (int n : new int[]{sf, inf}) {
                        int index = 0;
                        for (int j = 0; j < n; j++) { index += c.leb(); Part f = new Part(name); f.add("field"); f.add(c.leb()); field(f, index); declarations.add(f); }
                    }
                    for (int n : new int[]{dm, vm}) {
                        int index = 0;
                        for (int j = 0; j < n; j++) {
                            index += c.leb(); int flags = c.leb(), body = c.leb();
                            Part m = new Part(name); m.add("method"); m.add(flags); method(m, index); declarations.add(m);
                            bodies.add(new int[]{index, flags, body});
                        }
                    }
                }
                Collections.sort(declarations); Part shape = new Part(name); shape.append(header);
                for (Part member : declarations) shape.append(member);
                String shapeHash = hash(shape.text.toString());
                if (wantedNames != null && !wantedNames.contains(name) || wantedShapes != null && !wantedShapes.contains(shapeHash)) continue;
                try {
                    List<Part> implementations = new ArrayList<>();
                    for (int[] body : bodies) {
                        Part m = new Part(name); method(m, body[0]); m.add(body[1]); code(m, body[2]); implementations.add(m);
                    }
                    Collections.sort(implementations); Part all = new Part(name); all.append(shape);
                    for (Part body : implementations) all.append(body);
                    if (statics != 0) {
                        Cursor c = new Cursor(statics); int size = c.leb(); all.add(size);
                        for (int j = 0; j < size; j++) value(all, c, 0);
                    }
                    if (result.put(name, new Node(name, shapeHash, hash(all.text.toString()), all.refs)) != null)
                        throw new IllegalStateException("Duplicate DEX class " + name);
                } catch (IllegalStateException unsupported) {
                    if (wantedNames != null) throw unsupported;
                    // Unsupported instruction formats cannot become heuristic candidates.
                }
            }
        }
    }
}
