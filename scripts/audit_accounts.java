import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.*;
import jadx.api.*;

/** Audit a privately obtained APK with jadx 1.5.6 on the host classpath. */
class AccountAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) {
            throw new IllegalArgumentException("APK [OUTPUT_DIRECTORY CLASS_LIST_FILE]");
        }
        if (args.length == 1) {
            try (var zip = new ZipFile(args[0])) {
                for (var entry : Collections.list(zip.entries())) {
                    if (!entry.getName().endsWith(".dex")) continue;
                    var dex = new DexBackedDexFile(null, zip.getInputStream(entry).readAllBytes());
                    for (var cls : dex.getClasses()) for (var method : cls.getMethods()) {
                        var impl = method.getImplementation();
                        if (impl == null) continue;
                        Set<String> hits = new LinkedHashSet<>();
                        for (var insn : impl.getInstructions()) {
                            if (!(insn instanceof ReferenceInstruction ri)) continue;
                            var ref = ri.getReference();
                            if (ref instanceof MethodReference mr && mr.getDefiningClass().equals("Lnnd;")
                                    && Set.of("c", "d", "e").contains(mr.getName())) hits.add(mr.toString());
                            if (ref instanceof StringReference sr && (sr.getString().equals("pseudonymous")
                                    || sr.getString().equals("vanilla_mode")
                                    || sr.getString().startsWith("VANILLA_"))) hits.add(sr.getString());
                        }
                        if (!hits.isEmpty()) System.out.println(method + " : " + hits);
                    }
                }
            }
            return;
        }
        var options = new JadxArgs();
        options.setInputFiles(List.of(new java.io.File(args[0])));
        options.setSkipResources(true);
        options.setThreadsCount(4);
        options.setInlineAnonymousClasses(false);
        options.setInlineMethods(false);
        try (var jadx = new JadxDecompiler(options)) {
            jadx.load();
            Files.createDirectories(Path.of(args[1]));
            for (var name : Files.readAllLines(Path.of(args[2]))) {
                name = name.replace('/', '.');
                var cls = jadx.searchJavaClassByOrigFullName(name);
                if (cls == null) throw new IllegalArgumentException(name);
                Files.writeString(Path.of(args[1], name + ".java"), cls.getCode());
                Files.writeString(Path.of(args[1], name + ".smali"), cls.getSmali());
                System.out.println("Saved " + name);
            }
        }
    }
}
