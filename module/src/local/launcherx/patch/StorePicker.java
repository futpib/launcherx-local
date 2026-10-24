package local.launcherx.patch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.BooleanSupplier;

/** Route the native Add apps action through stores' advertised Android entry points. */
final class StorePicker {
    private static final String TAG = "LauncherXLocal";
    private static final String DESTINATION = "https://tv.google.com/destination/apps";
    private final Method launch;
    private final BooleanSupplier enabled;

    StorePicker(LauncherSymbols loader, BooleanSupplier enabled) throws Exception {
        this.enabled = enabled;
        launch = loader.loadClass("fyt").getDeclaredMethod("c", Activity.class,
                loader.loadClass("aaht"), loader.loadClass("vhe"), String.class,
                loader.loadClass("fyw"), loader.loadClass("xnc"));
        loader.loadClass("aaht").getDeclaredField("d");
        loader.loadClass("aaht").getDeclaredField("e");
    }

    XC_MethodHook.Unhook install() {
        return XposedBridge.hookMethod(launch, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam hook) throws Throwable {
                if (!enabled.getAsBoolean()) return;
                Object link = hook.args[1];
                if (!DESTINATION.equals(XposedHelpers.getObjectField(link, "d"))
                        || !Compatibility.TARGET.equals(XposedHelpers.getObjectField(link, "e"))) return;
                Context context = (Context) hook.args[0];
                try {
                    show(context);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Cannot open app stores", error);
                    Toast.makeText(context, "App stores are unavailable. Try Add apps again.", Toast.LENGTH_LONG).show();
                }
                hook.setResult(null);
            }
        });
    }

    private static void show(Context context) {
        PackageManager manager = context.getPackageManager();
        Intent market = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET);
        // Some stores advertise APP_MARKET without DEFAULT. Resolve all enabled handlers,
        // then pass explicit targets so the system chooser includes those stores too.
        List<ResolveInfo> matches = manager.queryIntentActivities(market, 0);
        matches.sort(new ResolveInfo.DisplayNameComparator(manager));
        List<Intent> choices = new ArrayList<>();
        Set<ComponentName> seen = new HashSet<>();
        for (ResolveInfo match : matches) {
            ActivityInfo activity = match.activityInfo;
            if (activity == null || !activity.exported || !activity.enabled
                    || !activity.applicationInfo.enabled
                    || (activity.permission != null && context.checkSelfPermission(activity.permission)
                        != PackageManager.PERMISSION_GRANTED)) continue;
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            if (seen.add(component)) choices.add(new Intent(market).setComponent(component));
        }
        Log.i(TAG, "opening APP_MARKET chooser: " + choices.size() + " activities");
        if (choices.isEmpty()) {
            new AlertDialog.Builder(context).setTitle("Add apps")
                    .setMessage("No app stores are available. Install or enable an app store, then try again.")
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        Intent chooser = Intent.createChooser(choices.remove(0), "Choose app store");
        chooser.putExtra(Intent.EXTRA_ALTERNATE_INTENTS, choices.toArray(new Intent[0]));
        context.startActivity(chooser);
    }
}
