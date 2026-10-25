package local.launcherx.patch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;

/** Only the verified owner-user Google launcher may ask this app to inject a key. */
public final class KeyActionProvider extends ContentProvider {
    private String keyHost;
    private String verifiedIdentity;

    @Override public boolean onCreate() { return true; }

    private synchronized void request(String operation, int code) throws Exception {
        if (keyHost == null || "prepare".equals(operation)) {
            keyHost = null;
            for (String host : new String[] {XiaomiRemote.PACKAGE, "android"}) {
                try {
                    String reply = HookRequest.call(getContext(), new Intent(KeyInjection.ACTION)
                            .setPackage(host).putExtra("operation", "prepare").putExtra("code", code));
                    if ("OK".equals(reply)) {
                        keyHost = host;
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (keyHost == null) throw new IllegalStateException(
                    "Select TV service (Xiaomi) or System Framework in LSPosed, then reboot");
        }
        if ("send".equals(operation)) {
            String reply = HookRequest.call(getContext(), new Intent(KeyInjection.ACTION)
                    .setPackage(keyHost).putExtra("operation", "send").putExtra("code", code));
            if (!"OK".equals(reply)) throw new IllegalStateException(reply);
        }
    }

    private synchronized void authorize(int caller) throws Exception {
        PackageInfo info = getContext().getPackageManager().getPackageInfo(Compatibility.TARGET, 0);
        if (caller / 100000 != 0 || caller != info.applicationInfo.uid) {
            throw new SecurityException("Only Google TV may use this key service");
        }
        String path = info.applicationInfo.sourceDir;
        StructStat file = Os.stat(path);
        String identity = caller + ":" + path + ":" + info.getLongVersionCode() + ":" + info.lastUpdateTime
                + ":" + file.st_dev + ":" + file.st_ino + ":" + file.st_size + ":" + file.st_mtime + ":" + file.st_ctime;
        if (!identity.equals(verifiedIdentity)) {
            Compatibility.verify(getContext());
            verifiedIdentity = identity;
            Log.i("LauncherXLocal", "Verified key-action caller installation");
        }
    }

    @Override public Bundle call(String method, String argument, Bundle extras) {
        int caller = Binder.getCallingUid();
        try {
            authorize(caller);
        } catch (SecurityException error) {
            throw error;
        } catch (Exception error) {
            throw new SecurityException("Unsupported Google TV caller", error);
        }
        if (!"prepare".equals(method) && !"send".equals(method)) {
            throw new IllegalArgumentException("Unknown key service operation");
        }
        int code = KeyActions.parse(argument == null ? "" : argument);
        long identity = Binder.clearCallingIdentity();
        Bundle result = new Bundle();
        try {
            request(method, code);
            result.putBoolean("ok", true);
        } catch (Exception error) {
            result.putString("error", error.getMessage());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException();
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
