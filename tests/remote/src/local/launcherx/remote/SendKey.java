package local.launcherx.remote;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

/** Send actual receiver events as root; the AVD has no Xiaomi global-key overlay. */
public final class SendKey {
    private static final String TARGET = "com.google.android.apps.tv.launcherx";
    private static boolean bridge;

    public static void main(String[] args) throws Exception {
        Class<?> properties = Class.forName("android.os.SystemProperties");
        String avd = (String) properties.getMethod("get", String.class)
                .invoke(null, "ro.boot.qemu.avd_name");
        String qemu = (String) properties.getMethod("get", String.class)
                .invoke(null, "ro.kernel.qemu");
        if (Process.myUid() != 0 || !"launcherx_local14".equals(avd) || !"1".equals(qemu)) {
            throw new IllegalStateException("Only the rooted launcherx_local14 fixture is allowed");
        }
        Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object instance = thread.getMethod("systemMain").invoke(null);
        Context context = (Context) thread.getMethod("getSystemContext").invoke(instance);
        bridge = args.length == 3 && "bridge".equals(args[2]);
        if ("app".equals(args[0])) {
            Intent launch = new Intent("com.google.global_button.ACTION_LAUNCH_APP")
                    .setComponent(new ComponentName(TARGET, TARGET
                            + ".globalbutton.GlobalButtonLaunch_Receiver"))
                    .putExtra("launchPackageName", args[1]);
            if (args.length == 3) {
                launch.putExtra("launchIntent", new Intent(Intent.ACTION_MAIN).setPackage(args[2]));
            }
            context.sendBroadcastAsUser(launch, Process.myUserHandle());
        } else {
            int code = Integer.parseInt(args[0]);
            long down = SystemClock.uptimeMillis();
            send(context, down, code, KeyEvent.ACTION_DOWN, 0, 0);
            if ("long".equals(args[1])) {
                Thread.sleep(600);
                send(context, down, code, KeyEvent.ACTION_DOWN, 1, KeyEvent.FLAG_LONG_PRESS);
            }
            Thread.sleep(250);
            send(context, down, code, KeyEvent.ACTION_UP, 0,
                    "cancel".equals(args[1]) ? KeyEvent.FLAG_CANCELED : 0);
        }
        Thread.sleep(500);
        System.exit(0);
    }

    private static void send(Context context, long down, int code, int action, int repeat, int flags) throws Exception {
        KeyEvent event = new KeyEvent(down, SystemClock.uptimeMillis(), action, code, repeat,
                0, -1, 0, flags | KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        Intent intent = new Intent("android.intent.action.GLOBAL_BUTTON")
                .setComponent(new ComponentName(TARGET, TARGET
                        + ".remotecontrol.GlobalKeyListener_Receiver"))
                .putExtra("android.intent.extra.KEY_EVENT", event);
        if (!bridge) {
            context.sendBroadcastAsUser(intent, Process.myUserHandle());
            return;
        }
        intent.putExtra("local.launcherx.remote.bridge", true);
        // app_process has no registered app record for a result callback. Check
        // the actual receiving process's acknowledgement in the host-side test.
        context.sendOrderedBroadcast(intent, null);
    }
}
