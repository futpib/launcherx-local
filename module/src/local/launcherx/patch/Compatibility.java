package local.launcherx.patch;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Compatibility {
    public static final String TARGET = "com.google.android.apps.tv.launcherx";
    private static final String SIGNER = "5202f9072a0f75c831b2296428755df2548296947d11804e7579a934a5f700ae";
    private static String cachedKey;
    private static Result cached;

    static final class Result {
        final long version, bindings;
        final String mode;
        final Map<String, String> classes;
        Result(long version, long bindings, String mode, Map<String, String> classes) {
            this.version = version; this.bindings = bindings; this.mode = mode; this.classes = classes;
        }
    }

    public static long verify(Context context) throws Exception {
        return resolve(context).bindings;
    }

    static synchronized Result resolve(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(TARGET, PackageManager.GET_SIGNING_CERTIFICATES);
        long version = info.getLongVersionCode();
        String key = version + ":" + info.lastUpdateTime + ":" + info.applicationInfo.sourceDir;
        if (key.equals(cachedKey)) return cached;
        for (int index = 0; index < BuildConfig.VERSIONS.length; index++) {
            if (version == BuildConfig.VERSIONS[index]) {
                verify(context, TARGET, version, BuildConfig.PROFILE_HASHES[index]);
                return remember(key, version, version, "reviewed", Collections.emptyMap());
            }
        }
        Signature[] signers = info.signingInfo == null ? new Signature[0] : info.signingInfo.getApkContentsSigners();
        if (signers.length != 1 || !SIGNER.equals(hex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray()))))
            throw new IllegalStateException("Unrecognized launcher signing certificate");
        // Architecture variants can share every executable byte with a reviewed build.
        for (int index = 0; index < BuildConfig.VERSIONS.length; index++) {
            try {
                verify(context, TARGET, version, BuildConfig.PROFILE_HASHES[index]);
                return remember(key, version, BuildConfig.VERSIONS[index], "identical-code", Collections.emptyMap());
            } catch (IllegalStateException mismatch) {
                // Try the next reviewed executable profile.
            }
        }
        if (version <= 837595) throw new IllegalStateException("Unsupported launcher version " + version);
        Map<String, String> classes = HeuristicCompatibility.resolve(context, info.applicationInfo);
        return remember(key, version, 837595, "heuristic", classes);
    }

    private static Result remember(String key, long version, long bindings, String mode, Map<String, String> classes) {
        cachedKey = key;
        cached = new Result(version, bindings, mode, Collections.unmodifiableMap(classes));
        Log.i("LauncherXLocal", "launcher compatibility: " + mode + " version=" + version + " bindings=" + bindings);
        return cached;
    }

    private static String hex(byte[] digest) {
        StringBuilder value = new StringBuilder();
        for (byte b : digest) value.append("0123456789abcdef".charAt((b & 255) >>> 4))
                .append("0123456789abcdef".charAt(b & 15));
        return value.toString();
    }

    static void verify(Context context, String target, long version, String[][] hashes) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(target, 0);
        if (info.getLongVersionCode() != version) {
            throw new IllegalStateException("Unsupported launcher version " + info.getLongVersionCode());
        }
        if (info.applicationInfo.splitSourceDirs != null) {
            for (String split : info.applicationInfo.splitSourceDirs) try (ZipFile apk = new ZipFile(split)) {
                java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.endsWith(".dex") && !name.contains("/"))
                        throw new IllegalStateException("Unexpected executable split");
                }
            }
        }
        // Gate on executable code, allowing separately documented emulator ABI adaptation.
        try (ZipFile apk = new ZipFile(info.applicationInfo.sourceDir)) {
            Set<String> dexNames = new HashSet<>();
            java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".dex") && !name.contains("/")) dexNames.add(name);
            }
            if (dexNames.size() != hashes.length) {
                throw new IllegalStateException("Unexpected executable code entries");
            }
            for (String[] expected : hashes) {
                ZipEntry entry = apk.getEntry(expected[0]);
                if (entry == null) throw new IllegalStateException("Missing " + expected[0]);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = apk.getInputStream(entry)) {
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                }
                StringBuilder hex = new StringBuilder();
                for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
                if (!expected[1].contentEquals(hex)) throw new IllegalStateException("Code mismatch: " + expected[0]);
            }
        }
    }

}
