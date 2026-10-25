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
import android.widget.Switch;
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
    private Switch volumeMute;
    private TextView volumeStatus;
    private boolean volumeMuteDisabled;

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
        text("Incognito and Google profiles for supported Google TV Home versions.\n"
                + "Enable Zygisk in Magisk and install LSPosed. In LSPosed, enable LauncherX Local "
                + "with Google TV selected, then restart Google TV or reboot. Home activates automatically. "
                + "On the supported Xiaomi TV, also select TV service (mitv.service) and reboot to customize all six app buttons.\n"
                + "Use Home's profile button to add or choose a Google account, or return to Incognito. "
                + "Your selected profile is remembered. Apps keep their own accounts and history.\n"
                + "Hold an app button to choose its shortcut. Restore default or Restore all button defaults removes custom assignments. "
                + "Send key adds Play/Pause, other Android keys and No action. To send keys, select TV service (Xiaomi) or System Framework in LSPosed, then reboot. No companion-app superuser grant is needed. "
                + "To return to all stock button handlers, disable the module in LSPosed and reboot.", 17);
        status = text("Checking compatibility…", 18);
        button("Choose app content", () -> channels(null));
        volumeMute = new Switch(this);
        volumeMute.setText("Disable double volume-down mute");
        volumeMute.setTextSize(18);
        content.addView(volumeMute, new LinearLayout.LayoutParams(-1, -2));
        volumeStatus = text("Optional Xiaomi TV fix. Select System Framework in LSPosed and reboot once. "
                + "This option works independently of the launcher and app-button patches.", 17);
        volumeMute.setOnClickListener(view -> volumeMute(volumeMute.isChecked()));
        volumeMute(null);
        check();
    }

    private void volumeMute(Boolean value) {
        volumeMute.setEnabled(false);
        new Thread(() -> {
            try {
                verifyUser();
                Intent request = new Intent(SystemAudio.ACTION).setPackage("android")
                        .putExtra("operation", value == null ? "get" : "set");
                if (value != null) request.putExtra("disabled", value.booleanValue());
                String reply = HookRequest.call(this, request);
                if (!"disabled".equals(reply) && !"enabled".equals(reply)) throw new IllegalStateException(reply);
                boolean disabled = "disabled".equals(reply);
                runOnUiThread(() -> {
                    volumeMuteDisabled = disabled;
                    volumeMute.setChecked(disabled);
                    volumeMute.setEnabled(true);
                    volumeStatus.setText(disabled
                            ? "Double volume-down only lowers the volume. The mute key and hold-to-mute still work."
                            : "Xiaomi's double volume-down mute is enabled. This switch affects only that gesture.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    volumeMute.setChecked(volumeMuteDisabled);
                    volumeMute.setEnabled(value != null);
                    volumeStatus.setText("Volume gesture control unavailable. It requires a supported Xiaomi firmware, "
                            + "System Framework selected in LSPosed, and a reboot after installation. "
                            + "Other module features work independently. " + error.getMessage());
                });
            }
        }, "launcherx-volume-gesture").start();
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
                message = "Compatible launcher. Home is " + (enabled ? "selected" : "not selected")
                        + ". This checks the installed launcher; activation follows the module in LSPosed.";
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
