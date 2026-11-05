"""Isolate the persistent checkpoint alongside each smoke test's native-store backup."""
from contextlib import contextmanager
import time

from lab import root

TARGET = 'com.google.android.apps.tv.launcherx'
SHADOW = '/data/user/0/' + TARGET + '/files/launcherx_shortcut_backup'


@contextmanager
def isolated_backup():
    backup = '/data/local/tmp/launcherx-checkpoint-' + str(time.time_ns())
    root('am force-stop ' + TARGET)
    existed = root('test -d ' + SHADOW + ' && echo yes || echo no') == 'yes'
    root('mkdir ' + backup)
    if existed:
        root('cp -a ' + SHADOW + ' ' + backup + '/checkpoint')
    try:
        root('rm -rf ' + SHADOW)
        yield
    finally:
        root('am force-stop ' + TARGET)
        root('rm -rf ' + SHADOW)
        if existed:
            root('cp -a ' + backup + '/checkpoint ' + SHADOW)
            root('restorecon -DRF ' + SHADOW)
        root('rm -rf ' + backup)
