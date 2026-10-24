package local.launcherx.patch;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Compatibility {
    public static final String TARGET = "com.google.android.apps.tv.launcherx";

    public static long verify(Context context) throws Exception {
        long version = context.getPackageManager().getPackageInfo(TARGET, 0).getLongVersionCode();
        for (int index = 0; index < BuildConfig.VERSIONS.length; index++) {
            if (version == BuildConfig.VERSIONS[index]) {
                verify(context, TARGET, version, BuildConfig.PROFILE_HASHES[index]);
                return version;
            }
        }
        throw new IllegalStateException("Unsupported launcher version " + version);
    }

    static void verify(Context context, String target, long version, String[][] hashes) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(target, 0);
        if (info.getLongVersionCode() != version) {
            throw new IllegalStateException("Unsupported launcher version " + info.getLongVersionCode());
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
