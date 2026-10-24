package local.launcherx.patch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Only the verified owner-user Google launcher may ask this app to inject a key. */
public final class KeyActionProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String argument, Bundle extras) {
        int caller = Binder.getCallingUid();
        try {
            if (caller / 100000 != 0 || caller != getContext().getPackageManager()
                    .getApplicationInfo(Compatibility.TARGET, 0).uid) {
                throw new SecurityException("Only Google TV may use this key service");
            }
            Compatibility.verify(getContext());
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
            if ("prepare".equals(method)) {
                if (!"0".equals(SetupActivity.root("id -u").trim())) {
                    throw new IllegalStateException("Allow LauncherX Local root access in Magisk");
                }
            } else {
                String apk = getContext().getApplicationInfo().sourceDir;
                String quoted = "'" + apk.replace("'", "'\\''") + "'";
                SetupActivity.root("CLASSPATH=" + quoted
                        + " app_process /system/bin local.launcherx.patch.KeyInjector " + code, 5);
            }
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
