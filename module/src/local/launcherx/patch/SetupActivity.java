package local.launcherx.patch;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SetupActivity extends Activity {
    private static final String TARGET = Compatibility.TARGET;
    private static final String HOME = TARGET + "/.home.HomeActivity";
    private static final String BASIC = TARGET + "/.home.VanillaModeHomeActivity";
    private final List<Button> buttons = new ArrayList<>();
    private TextView status;
    private LinearLayout content;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (32 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        text("LauncherX Local", 28);
        text("Account-free Google TV Home for launcher 1.0.785572138 (702401).\n"
                + "Enable Zygisk in Magisk, install LSPosed and reboot. In LSPosed, enable "
                + "LauncherX Local and select only Google TV. Then enable local Home below.\n"
                + "Magisk will ask for root access to switch Home. Google recommendations "
                + "and the Play Store still need an account.", 17);
        status = text("Checking compatibility…", 18);
        button("Enable local Home", () -> work(true));
        button("Restore Basic TV", () -> work(false));
        button("Open LSPosed", () -> {
            try {
                startActivity(new Intent().setComponent(new ComponentName("org.lsposed.manager",
                        "org.lsposed.manager.ui.activity.MainActivity")));
            } catch (RuntimeException error) {
                status.setText("Open LSPosed from its notification, or install its manager APK. "
                        + "Enable LauncherX Local with only Google TV selected.");
            }
        });
        button("Check compatibility", this::check);
        check();
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 0, 0, 16);
        content.addView(view);
        return view;
    }

    private void button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        buttons.add(button);
    }

    private void busy(boolean value, String message) {
        for (Button button : buttons) button.setEnabled(!value);
        status.setText(message);
    }

    private void verifyUser() {
        if (android.os.Process.myUid() / 100000 != 0) {
            throw new IllegalStateException("Setup currently supports the TV's owner user only.");
        }
    }

    private void check() {
        busy(true, "Checking the installed launcher's executable code…");
        new Thread(() -> {
            String message;
            try {
                verifyUser();
                Compatibility.verify(this);
                boolean enabled = getPackageManager().getComponentEnabledSetting(new ComponentName(
                        TARGET, TARGET + ".home.HomeActivity")) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                message = "Compatible launcher. Local Home is " + (enabled ? "selected" : "not selected")
                        + ". Enable checks the running LSPosed hook before switching.";
            } catch (Exception error) {
                message = "Cannot enable local Home: " + error.getMessage();
            }
            final String result = message;
            runOnUiThread(() -> busy(false, result));
        }, "launcherx-check").start();
    }

    private void work(boolean enable) {
        busy(true, "Waiting for Magisk root access. Allow LauncherX Local when prompted…");
        new Thread(() -> {
            try {
                verifyUser();
                if (enable) Compatibility.verify(this);
                String basic = "pm enable --user 0 " + BASIC + " && pm disable --user 0 " + HOME
                        + " && am force-stop --user 0 " + TARGET + " && am start --user 0 -W -n " + BASIC;
                String command = basic;
                if (enable) {
                    command = "am force-stop --user 0 " + TARGET + " && "
                            + "am start --user 0 -W -a android.intent.action.MAIN "
                            + "-c android.intent.category.HOME -p " + TARGET + " >/dev/null 2>&1; "
                            + "ready=0; for attempt in 1 2 3 4 5; do "
                            + "probe=$(am broadcast --user 0 --receiver-include-background -a "
                            + Compatibility.PROBE + " -p " + TARGET + " 2>&1); "
                            + "case \"$probe\" in *'data=\"" + Compatibility.READY + "\"'*) ready=1; break;; esac; "
                            + "sleep 1; done; "
                            + "if [ \"$ready\" != 1 ]; then echo 'Hook not ready. Enable this module in LSPosed, "
                            + "select only Google TV, restart the launcher, and use a profile without a Google account.'; exit 1; fi; "
                            + "if pm enable --user 0 " + HOME + " && pm disable --user 0 " + BASIC
                            + " && am force-stop --user 0 " + TARGET + " && am start --user 0 -W -n " + HOME
                            + "; then exit 0; else " + basic + "; exit 1; fi";
                }
                root(command);
                runOnUiThread(this::finish);
            } catch (Exception error) {
                runOnUiThread(() -> {
                    startActivity(new Intent(this, SetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    busy(false, "No switch completed: " + error.getMessage());
                });
            }
        }, "launcherx-setup").start();
    }

    private static void root(String command) throws Exception {
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Root request timed out. Check Magisk's Superuser page.");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            if (process.exitValue() != 0) throw new IllegalStateException(output.toString("UTF-8").trim());
        } finally {
            process.destroy();
        }
    }
}
