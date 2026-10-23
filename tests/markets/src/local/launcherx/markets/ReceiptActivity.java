package local.launcherx.markets;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/** Records the actual intent delivered by the system chooser. */
public final class ReceiptActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"ranchu".equals(Build.HARDWARE)) {
            finish();
            return;
        }
        String receipt = getIntent().getComponent().getClassName() + "\n"
                + getIntent().getAction() + "\n" + getIntent().getCategories();
        Log.i("MarketFixture", receipt.replace('\n', ' '));
        TextView text = new TextView(this);
        text.setText(receipt);
        text.setTextSize(28);
        text.setPadding(64, 64, 64, 64);
        setContentView(text);
    }
}
