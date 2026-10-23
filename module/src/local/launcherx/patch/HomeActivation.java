package local.launcherx.patch;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

/** Select Home when the verified module loads in the launcher's own process. */
final class HomeActivation implements Application.ActivityLifecycleCallbacks {
    private static final String HOME = Compatibility.TARGET + ".home.HomeActivity";
    private static final String BASIC = Compatibility.TARGET + ".home.VanillaModeHomeActivity";
    private final Application application;

    private HomeActivation(Application application) {
        this.application = application;
    }

    static void install(Application application, Context context) {
        selectHome(context);
        application.registerActivityLifecycleCallbacks(new HomeActivation(application));
        Log.i("LauncherXLocal", "Home selected by the loaded module");
    }

    private static void selectHome(Context context) {
        PackageManager manager = context.getPackageManager();
        ComponentName home = new ComponentName(Compatibility.TARGET, HOME);
        ComponentName basic = new ComponentName(Compatibility.TARGET, BASIC);
        int oldHome = manager.getComponentEnabledSetting(home);
        int oldBasic = manager.getComponentEnabledSetting(basic);
        try {
            if (oldHome != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                manager.setComponentEnabledSetting(home, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
            if (oldBasic != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                manager.setComponentEnabledSetting(basic, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (RuntimeException error) {
            manager.setComponentEnabledSetting(home, oldHome, PackageManager.DONT_KILL_APP);
            manager.setComponentEnabledSetting(basic, oldBasic, PackageManager.DONT_KILL_APP);
            throw error;
        }
    }

    @Override public void onActivityResumed(Activity activity) {
        String name = activity.getClass().getName();
        if (!HOME.equals(name) && !BASIC.equals(name)) return;
        // Only redirect the Basic TV launch already in flight when the process loaded.
        // Later native recovery or account setup must not enter a redirect loop.
        application.unregisterActivityLifecycleCallbacks(this);
        if (BASIC.equals(name)) {
            try {
                // Basic TV's peer disables Home in its constructor, after process activation.
                selectHome(activity);
                activity.startActivity(new Intent().setComponent(new ComponentName(Compatibility.TARGET, HOME))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                activity.finish();
            } catch (RuntimeException error) {
                Log.e("LauncherXLocal", "Cannot open Home from Basic TV", error);
                activity.getPackageManager().setComponentEnabledSetting(
                        new ComponentName(Compatibility.TARGET, BASIC),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
