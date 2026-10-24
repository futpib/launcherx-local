package local.launcherx.patch;

import android.os.Process;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import java.lang.reflect.Method;

/** Root entry point in this APK; generated events cannot recursively send another key. */
public final class KeyInjector {
    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 0 || args.length != 1) throw new SecurityException("Root is required");
        int code = KeyActions.parse(args[0]);
        Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object instance = thread.getMethod("systemMain").invoke(null);
        Context context = (Context) thread.getMethod("getSystemContext").invoke(instance);
        Class<?> manager = Class.forName("android.hardware.input.InputManager");
        Object input = context.getSystemService(Context.INPUT_SERVICE);
        Method inject = manager.getMethod("injectInputEvent", InputEvent.class, int.class);
        long down = SystemClock.uptimeMillis();
        boolean delivered = true;
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(down, SystemClock.uptimeMillis(), action, code, 0, 0, -1, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_FALLBACK, InputDevice.SOURCE_KEYBOARD);
            delivered &= (Boolean) inject.invoke(input, event, 2);
        }
        if (!delivered) throw new IllegalStateException("Android could not deliver " + KeyEvent.keyCodeToString(code));
        System.exit(0);
    }
}
