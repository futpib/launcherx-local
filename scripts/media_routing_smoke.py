#!/usr/bin/env python3
"""Exercise mapped Play/Pause in the real TV Bro with no Android media session."""
import argparse
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import threading
import time

from all_shortcuts_smoke import send
from build_publisher import build
from key_actions_smoke import choose
from lab import ARTIFACTS, ROOT, adb, guard, root, shell
from remote_smoke import FIXTURE, STORE
from smoke import BROWSER, TARGET, fresh_home, resumed, screen


@contextmanager
def preserved_data():
    backup = '/data/local/tmp/launcherx-media-routing-' + str(time.time_ns())
    root('mkdir ' + backup)
    directories = []
    try:
        root('am force-stop ' + TARGET)
        root('am force-stop ' + BROWSER)
        for name, path in [('store', STORE), ('browser', '/data/user/0/' + BROWSER),
                           ('browser-de', '/data/user_de/0/' + BROWSER)]:
            existed = root('test -d ' + path + ' && echo yes || echo no') == 'yes'
            if existed:
                root('cp -a ' + path + ' ' + backup + '/' + name)
            directories.append((name, path, existed))
        assert root('pm clear ' + BROWSER) == 'Success'
        root('rm -rf ' + STORE)
        yield
    finally:
        root('am force-stop ' + BROWSER)
        root('am force-stop ' + TARGET)
        for name, path, existed in directories:
            root('rm -rf ' + path)
            if existed:
                root('cp -a ' + backup + '/' + name + ' ' + path)
                # -D is required to relabel children inside Android app-data directories.
                root('restorecon -DRF ' + path)
                def manifest(directory):
                    return root('cd ' + directory + ' && find . -type f -exec sha256sum {} \\; | sort')
                assert manifest(path) == manifest(backup + '/' + name), name + ' restoration failed'
        root('rm -rf ' + backup + ' ' + FIXTURE)
        fresh_home()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expect-session-bypass', action='store_true',
                        help='Require the old failure, with normal input as a positive control')
    parser.add_argument('--output', type=Path, default=ROOT / 'validation-media-routing.json')
    args = parser.parse_args()
    guard()
    package = shell('dumpsys package ' + BROWSER)
    assert 'versionName=2.1.6' in package, package
    browser_apk = shell('pm path ' + BROWSER).removeprefix('package:')
    browser_hash = shell('sha256sum ' + shlex.quote(browser_apk)).split()[0]
    assert browser_hash == 'd8634edfe8d94b4fb9a52005d68a090a7f1e85b7f39c2cf01a25dc9dd60942b2', browser_hash
    module = shell('pm path local.launcherx.patch').removeprefix('package:')
    module_hash = shell('sha256sum ' + shlex.quote(module)).split()[0]
    module_version = re.search(r'versionName=(\S+)', shell('dumpsys package local.launcherx.patch')).group(1)
    uid = int(re.search(r'uid:(\d+)', shell('cmd package list packages -U local.launcherx.patch')).group(1))
    query = 'SELECT uid,policy,until,logging,notification FROM policies WHERE uid=' + str(uid)
    def sql(command):
        return root('magisk --sqlite ' + shlex.quote(command))
    previous_policy = sql(query)
    samples = []
    checks = []
    prefix = args.output.stem
    with tempfile.TemporaryDirectory(prefix='launcherx-media-') as directory:
        video = Path(directory) / 'test.mp4'
        subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i',
                        'testsrc2=size=640x360:rate=15', '-t', '12', '-an',
                        '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
                        str(video)], check=True)
        page = b'''<!doctype html><meta name="viewport" content="width=device-width">
<style>body{background:#111;color:white;font:32px sans-serif}video{width:80vw}</style>
<h1>LauncherX TV Bro playback test</h1><video id="v" src="/test.mp4" autoplay muted loop controls></video>
<pre id="state"></pre><script>
const v=document.getElementById('v');let transitions=0;
for(const e of ['play','pause'])v.addEventListener(e,()=>{transitions++;report(e)});
function report(event='poll'){
 const s={paused:v.paused,time:v.currentTime,ready:v.readyState,transitions,event};
 document.getElementById('state').textContent=JSON.stringify(s);
 fetch('/state',{method:'POST',body:JSON.stringify(s)}).catch(()=>{});
}
setInterval(report,200);
</script>'''

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *unused):
                pass

            def do_GET(self):
                body = video.read_bytes() if self.path == '/test.mp4' else page
                self.send_response(200)
                self.send_header('Content-Type', 'video/mp4' if self.path == '/test.mp4' else 'text/html')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def do_POST(self):
                value = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                samples.append({'host_time': time.monotonic(), **value})
                self.send_response(204)
                self.end_headers()

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        # Let adb reserve a free device port, avoiding other lab forwards.
        device_port = adb('reverse', 'tcp:0', 'tcp:' + str(server.server_port))
        assert device_port.isdigit(), device_port
        try:
            sql('REPLACE INTO policies (uid,policy,until,logging,notification) VALUES ('
                + str(uid) + ',1,0,1,1)')
            denied = sql(query)
            assert '|policy=1|' in denied
            with preserved_data():
                adb('push', build('remote'), FIXTURE)
                root('chmod 444 ' + FIXTURE)
                adb('logcat', '-c')
                choose(313, 0, 'Play / Pause')
                url = 'http://127.0.0.1:' + device_port + '/'
                shell('am start -W -a android.intent.action.VIEW -d ' + shlex.quote(url)
                      + ' -n ' + BROWSER + '/.activity.main.MainActivity')

                def wait_for(predicate, timeout=20):
                    deadline = time.monotonic() + timeout
                    while not samples or not predicate(samples[-1]):
                        assert time.monotonic() < deadline, samples[-5:]
                        time.sleep(.1)
                    assert time.monotonic() - samples[-1]['host_time'] < 2, 'Stale playback telemetry'
                    return dict(samples[-1])

                wait_for(lambda state: state['ready'] >= 2 and not state['paused'] and state['time'] > .5)
                resumed(BROWSER)
                screen(prefix + '-playing')
                sessions = shell('dumpsys media_session')
                (ARTIFACTS / (prefix + '-sessions.txt')).write_text(sessions)
                assert 'Media button session is null' in sessions, sessions
                assert 'package=' + BROWSER not in sessions, sessions
                checks.append('Real TV Bro plays an HTML5 video without a registered media-button session')

                def toggle(action, paused):
                    before = dict(samples[-1])
                    action()
                    after = wait_for(lambda state: state['paused'] == paused
                                     and state['transitions'] == before['transitions'] + 1)
                    time.sleep(1)
                    assert samples[-1]['paused'] == paused
                    assert samples[-1]['transitions'] == after['transitions'], samples[-5:]
                    resumed(BROWSER)
                    return after

                if args.expect_session_bypass:
                    before = dict(samples[-1])
                    send(313, 'short')
                    time.sleep(2)
                    assert not samples[-1]['paused']
                    assert samples[-1]['transitions'] == before['transitions']
                    checks.append('Baseline mapped Play/Pause fails to pause TV Bro')
                    toggle(lambda: shell('input keyevent KEYCODE_MEDIA_PLAY_PAUSE'), True)
                    toggle(lambda: shell('input keyevent KEYCODE_MEDIA_PLAY_PAUSE'), False)
                    checks.append('Normal Android input pauses and resumes the same TV Bro video')
                else:
                    toggle(lambda: send(313, 'short'), True)
                    screen(prefix + '-paused')
                    toggle(lambda: send(313, 'short'), False)
                    screen(prefix + '-resumed')
                    checks.append('Saved Play/Pause pauses and resumes the real TV Bro video exactly once per press')
                    root('am force-stop local.launcherx.patch')
                    toggle(lambda: send(313, 'short'), True)
                    toggle(lambda: send(313, 'short'), False)
                    checks.append('Cold companion recovery retains focused-app delivery without duplicate toggles')
                assert sql(query) == denied
                checks.append('Companion superuser policy remains denied throughout playback checks')
                logs = adb('logcat', '-d', binary=True)
                (ARTIFACTS / (prefix + '-logcat.raw')).write_bytes(logs)
                for error in [b'Could not send shortcut key', b'Could not save shortcut key', b'FATAL EXCEPTION']:
                    assert error not in logs, error
        finally:
            (ARTIFACTS / (prefix + '-final-logcat.raw')).write_bytes(adb('logcat', '-d', binary=True))
            (ARTIFACTS / (prefix + '-playback.json')).write_text(json.dumps(samples, indent=2) + '\n')
            if previous_policy:
                row = dict(field.split('=', 1) for field in previous_policy.split('|'))
                sql('REPLACE INTO policies (uid,policy,until,logging,notification) VALUES ('
                    + ','.join(str(int(row[key])) for key in ['uid', 'policy', 'until', 'logging', 'notification']) + ')')
            else:
                sql('DELETE FROM policies WHERE uid=' + str(uid))
            assert sql(query) == previous_policy
            adb('reverse', '--remove', 'tcp:' + device_port)
            server.shutdown()
            server.server_close()
            thread.join()
    report = {'result': 'baseline-reproduced' if args.expect_session_bypass else 'passed',
              'apk_sha256': module_hash, 'module_version': module_version,
              'tvbro_version': '2.1.6', 'tvbro_sha256': browser_hash,
              'companion_policy_during_tests': denied, 'checks': checks, 'physical_tv_tested': False,
              'limitations': ['Real TV Bro in the Google TV emulator plays a generated local HTML5 video; the user website and physical Ditoo are not exercised.',
                              'Source events enter the actual Google receiver through the emulator-only sender.'],
              'cleanup': 'Original TV Bro data, shortcut store and companion root policy restored; sender, reverse port and local server removed.'}
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2), flush=True)


if __name__ == '__main__':
    main()
