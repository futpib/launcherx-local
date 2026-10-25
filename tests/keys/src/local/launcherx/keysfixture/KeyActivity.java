package local.launcherx.keysfixture;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.TextView;

/** Real Android media-session and focused-window delivery, with a silent audio stream. */
public final class KeyActivity extends Activity {
    private MediaSession session;
    private AudioTrack player;
    private TextView text;
    private boolean playing = true;
    private int media;
    private int windowMedia;
    private int keys;
    private String last = "none";
    private long received;
    private long eventTime;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        text = new TextView(this);
        text.setTextSize(30);
        text.setPadding(80, 80, 80, 80);
        setContentView(text);
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        session = new MediaSession(this, "LauncherX key delivery fixture");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_UP) {
                    received = SystemClock.uptimeMillis();
                    eventTime = event.getEventTime();
                    media++;
                    playing = !playing;
                    update();
                }
                return true;
            }
        });
        session.setActive(true);
        player = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(44100)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(88200).build();
        player.write(new short[44100], 0, 44100);
        player.setLoopPoints(0, 44100, -1);
        update();
    }

    private void update() {
        if (playing) player.play();
        else player.pause();
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, 0, playing ? 1 : 0)
                .build());
        text.setText("Key delivery fixture\nState: " + (playing ? "PLAYING" : "PAUSED")
                + "\nMedia presses: " + media + "\nWindow presses: " + keys + "\nLast key: " + last);
        getSharedPreferences("events", MODE_PRIVATE).edit().putBoolean("playing", playing)
                .putInt("media", media).putInt("window_media", windowMedia).putInt("keys", keys).putString("last", last)
                .putLong("received", received).putLong("event_time", eventTime).commit();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                && event.getAction() == KeyEvent.ACTION_UP) windowMedia++;
        // Leave media keys unhandled so Android routes them to the real session.
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if ((code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12)
                || (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z)) {
            received = SystemClock.uptimeMillis();
            eventTime = event.getEventTime();
            keys++;
            last = KeyEvent.keyCodeToString(code);
            update();
            return true;
        }
        return super.onKeyUp(code, event);
    }

    @Override protected void onDestroy() {
        session.release();
        player.release();
        super.onDestroy();
    }
}
