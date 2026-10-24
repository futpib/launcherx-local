package local.launcherx.patch;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A private pipe to a bounded root worker; no listening socket or command shell protocol. */
final class KeyInjectorConnection {
    private Process process;
    private Writer input;
    private BlockingQueue<String> replies;
    private long startedAt;
    private long usedAt;

    synchronized void prepare(Context context) throws Exception {
        start(context, 90);
    }

    synchronized void send(Context context, int code) throws Exception {
        KeyActions.validate(code);
        try {
            start(context, 5);
            usedAt = SystemClock.elapsedRealtime();
            input.write(Integer.toString(code) + "\n");
            input.flush();
            String result = replies.poll(5, TimeUnit.SECONDS);
            if (!"OK".equals(result)) throw new IllegalStateException(result == null
                    ? "Key delivery timed out" : "Key delivery failed: " + result);
        } catch (Exception error) {
            close();
            // Delivery may have happened before a lost reply. Never replay that press.
            throw error;
        }
    }

    private void start(Context context, int timeout) throws Exception {
        long now = SystemClock.elapsedRealtime();
        if (process != null && process.isAlive() && replies.peek() == null
                && now - usedAt < 50000 && now - startedAt < 290000) return;
        close();
        String apk = context.getApplicationInfo().sourceDir;
        String quoted = "'" + apk.replace("'", "'\\''") + "'";
        process = new ProcessBuilder("su", "-c", "CLASSPATH=" + quoted
                + " app_process /system/bin --nice-name=launcherx-key-injector"
                + " local.launcherx.patch.KeyInjector --stream").start();
        input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        replies = queue;
        Process worker = process;
        Thread reader = new Thread(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                    worker.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    queue.offer(line);
                    if ("BYE".equals(line)) {
                        stop(worker);
                        return;
                    }
                }
            } catch (java.io.IOException ignored) {
            } finally {
                queue.offer("Worker exited");
                stop(worker);
            }
        }, "LauncherX-key-replies");
        reader.setDaemon(true);
        reader.start();
        Thread errors = new Thread(() -> {
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                    worker.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) Log.w("LauncherXLocal", "Key worker: " + line);
            } catch (java.io.IOException ignored) {}
        }, "LauncherX-key-errors");
        errors.setDaemon(true);
        errors.start();
        try {
            String result = queue.poll(timeout, TimeUnit.SECONDS);
            if (!"READY".equals(result)) throw new IllegalStateException(result == null
                    ? "Root request timed out. Check Magisk's Superuser page."
                    : "Key worker could not start. Check Magisk's Superuser page. " + result);
            startedAt = usedAt = SystemClock.elapsedRealtime();
        } catch (Exception error) {
            close();
            throw error;
        }
    }

    private void close() {
        if (input != null) {
            try { input.close(); } catch (java.io.IOException ignored) {}
        }
        if (process != null) stop(process);
        input = null;
        process = null;
        replies = null;
    }

    private static void stop(Process worker) {
        // Magisk's relay can wait for stdin even after the root command exits.
        try { worker.getOutputStream().close(); } catch (java.io.IOException ignored) {}
        worker.destroyForcibly();
    }
}
