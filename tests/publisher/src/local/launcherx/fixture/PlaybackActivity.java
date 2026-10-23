package local.launcherx.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class PlaybackActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setTextSize(32);
        text.setText("Playback link received\n" + getIntent().getDataString()
                + "\nseek_ms=" + getIntent().getLongExtra("seek_ms", -1));
        setContentView(text);
    }
}
