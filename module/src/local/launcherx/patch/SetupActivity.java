package local.launcherx.patch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SetupActivity extends Activity {
    private static final String TARGET = Compatibility.TARGET;
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
        text("Enable LauncherX Local in LSPosed. Select the features you want:\n"
                + "Google TV: local Home and app shortcuts.\n"
                + "TvService: Xiaomi remote buttons.\n"
                + "System Framework: disable Xiaomi double volume-down mute.\n\n"
                + "Reboot after changing scope. Hold an app button to change its shortcut.", 17);
        status = text("Checking launcher…", 18);
        button("Choose app content", () -> channels(null));
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
        busy(true, "Checking launcher…");
        new Thread(() -> {
            String message;
            try {
                verifyUser();
                Compatibility.Result compatibility = Compatibility.resolve(this);
                boolean enabled = getPackageManager().getComponentEnabledSetting(new ComponentName(
                        TARGET, TARGET + ".home.HomeActivity")) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                message = ("heuristic".equals(compatibility.mode)
                        ? "Launcher matched automatically; this version has not been reviewed. Home is "
                        : "Compatible launcher. Home is ") + (enabled ? "selected" : "not selected")
                        + ".";
            } catch (Exception error) {
                message = "Unsupported launcher: " + error.getMessage();
            }
            final String result = message;
            runOnUiThread(() -> busy(false, result));
        }, "launcherx-check").start();
    }

    private void channels(String changes) {
        busy(true, "Reading app content choices…");
        new Thread(() -> {
            try {
                verifyUser();
                Compatibility.verify(this);
                Intent request = new Intent(AppChannels.ACTION).setPackage(TARGET);
                if (changes != null) request.putExtra("changes", changes);
                String response = HookRequest.call(this, request);
                JSONObject result = new JSONObject(new String(Base64.decode(response, Base64.DEFAULT), StandardCharsets.UTF_8));
                if (result.has("error")) throw new IllegalStateException(result.getString("error"));
                JSONArray entries = result.getJSONArray("channels");
                String[] labels = new String[entries.length()];
                boolean[] checked = new boolean[entries.length()];
                long[] ids = new long[entries.length()];
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    labels[i] = entry.getString("title") + " — " + entry.getString("app");
                    checked[i] = entry.getBoolean("enabled");
                    ids[i] = entry.getLong("id");
                }
                runOnUiThread(() -> {
                    busy(false, changes == null ? "Choose the app rows to show on Home." : "App content choices saved. Press Home to see them.");
                    if (changes != null) return;
                    if (labels.length == 0) {
                        status.setText("No app content channels yet. Open a compatible TV app and enable its Home recommendations.");
                        return;
                    }
                    boolean[] original = checked.clone();
                    new AlertDialog.Builder(this).setTitle("App content on Home")
                            .setMultiChoiceItems(labels, checked, (dialog, index, value) -> checked[index] = value)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save", (dialog, which) -> {
                                StringBuilder update = new StringBuilder();
                                for (int i = 0; i < ids.length; i++) {
                                    if (checked[i] == original[i]) continue;
                                    if (update.length() > 0) update.append(',');
                                    update.append(ids[i]).append(':').append(checked[i] ? '1' : '0');
                                }
                                if (update.length() > 0) channels(update.toString());
                            }).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> busy(false, "Cannot change app content: " + error.getMessage()));
            }
        }, "launcherx-channels").start();
    }

}
