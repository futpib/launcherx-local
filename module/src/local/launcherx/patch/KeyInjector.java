package local.launcherx.patch;

import android.os.Process;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import java.lang.reflect.Method;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/** Root entry point in this APK; generated events cannot recursively send another key. */
public final class KeyInjector {
    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 0 || args.length != 1) throw new SecurityException("Root is required");
        Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object instance = thread.getMethod("systemMain").invoke(null);
        Context context = (Context) thread.getMethod("getSystemContext").invoke(instance);
        Class<?> manager = Class.forName("android.hardware.input.InputManager");
        Object input = context.getSystemService(Context.INPUT_SERVICE);
        Method inject = manager.getMethod("injectInputEvent", InputEvent.class, int.class);
        if ("--stream".equals(args[0])) serve(input, inject);
        else send(input, inject, KeyActions.parse(args[0]));
        System.exit(0);
    }

    private static void serve(Object input, Method inject) throws Exception {
        long started = SystemClock.elapsedRealtime();
        AtomicLong active = new AtomicLong(started);
        Thread expiry = new Thread(() -> {
            while (true) {
                SystemClock.sleep(1000);
                long now = SystemClock.elapsedRealtime();
                // Exit on idle or a bounded total lifetime, including a stuck input call.
                if (now - active.get() >= 60000 || now - started >= 300000) {
                    System.out.println("BYE");
                    System.out.flush();
                    System.exit(0);
                }
            }
        }, "LauncherX-key-expiry");
        expiry.setDaemon(true);
        expiry.start();
        System.out.println("READY");
        System.out.flush();
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                active.set(SystemClock.elapsedRealtime());
                try {
                    int code = KeyActions.validate(Integer.parseInt(line));
                    send(input, inject, code);
                    System.out.println("OK");
                    System.out.flush();
                } catch (Exception error) {
                    System.out.println("ERROR");
                    System.out.flush();
                    return;
                }
            }
        }
    }

    private static void send(Object input, Method inject, int code) throws Exception {
        long down = SystemClock.uptimeMillis();
        boolean delivered = true;
        for (int action : new int[] {KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(down, SystemClock.uptimeMillis(), action, code, 0, 0, -1, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_FALLBACK, InputDevice.SOURCE_KEYBOARD);
            delivered &= (Boolean) inject.invoke(input, event, 2);
        }
        if (!delivered) throw new IllegalStateException("Android could not deliver " + KeyEvent.keyCodeToString(code));
    }
}
