package local.launcherx.patch;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.List;

/** Open Ambient settings for local Home without inventing a Google account. */
final class AmbientSettings {
    private static final String DREAM = "com.google.android.apps.tv.dreamx";
    private static final String AUTHORITY = Compatibility.TARGET + ".coreservices.account.AccountModeContentProvider";
    private static final String TAG = "LauncherXLocal";

    static boolean enabled(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(new ComponentName(
                Compatibility.TARGET, Compatibility.TARGET + ".home.HomeActivity"))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    static void install(Context context, LauncherSymbols loader, List<XC_MethodHook.Unhook> hooks)
            throws Exception {
        Method query = loader.loadClass("ambient_account_provider").getDeclaredMethod("query",
                Uri.class, String[].class, String.class, String[].class, String.class);
        hooks.add(XposedBridge.hookMethod(query, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                if (hook.hasThrowable() || !enabled(context)) return;
                ContentProvider provider = (ContentProvider) hook.thisObject;
                Uri uri = (Uri) hook.args[0];
                // Run the original query and its caller checks first. Only Ambient
                // sees this local fallback; the launcher and other clients keep mode 2.
                if (!DREAM.equals(provider.getCallingPackage()) || uri == null
                        || !AUTHORITY.equals(uri.getAuthority()) || !"/queryAccountMode".equals(uri.getPath())) return;
                Cursor original = (Cursor) hook.getResult();
                Cursor adapted = AmbientModeCursor.adapt(original);
                if (adapted != original) {
                    hook.setResult(adapted);
                    Log.i(TAG, "Ambient settings use the account-free fallback; Incognito identity retained");
                }
            }
        }));
    }

}
