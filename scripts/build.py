#!/usr/bin/env python3
"""Build the runtime module; never package the proprietary launcher or Xposed API."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
API_SHA256 = "f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25"
LAUNCHER_SHA256 = "3187adb10afe8c242d1b67d3d4f16fb77f600a0d72686339a4a3ff75db882ab5"


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launcher", type=Path, default=ROOT / "artifacts/launcher-original.apk")
    parser.add_argument("--api", type=Path, default=ROOT / "artifacts/xposed-api-82.jar")
    parser.add_argument("--mode", choices=["apps", "full"], default="apps")
    parser.add_argument("--emulator-network", action="store_true",
                        help="Use Android HttpEngine in the ARM-on-x86 emulator fixture")
    args = parser.parse_args()
    for path, expected in [(args.launcher, LAUNCHER_SHA256), (args.api, API_SHA256)]:
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Unexpected input SHA-256: {path}")
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
    tools = sdk / "build-tools/37.0.0"
    android = sdk / "platforms/android-34/android.jar"
    build = ROOT / "build" / (args.mode + ("-emulator" if args.emulator_network else ""))
    build.mkdir(parents=True, exist_ok=True)
    for name in ["classes", "dex"]:
        shutil.rmtree(build / name, ignore_errors=True)
        (build / name).mkdir()
    with zipfile.ZipFile(args.launcher) as apk:
        hashes = [(name, hashlib.sha256(apk.read(name)).hexdigest())
                  for name in sorted(apk.namelist()) if name.endswith(".dex") and "/" not in name]
    generated = build / "BuildConfig.java"
    entries = ",\n".join("{" + json.dumps(name) + "," + json.dumps(digest) + "}" for name, digest in hashes)
    generated.write_text("package local.launcherx.patch;\npublic final class BuildConfig {\n"
                         "public static final long VERSION_CODE = 702401L;\n"
                         f'public static final String MODE = "{args.mode}";\n'
                         f"public static final boolean APPS_ONLY = {str(args.mode == 'apps').lower()};\n"
                         f"public static final boolean EMULATOR_NETWORK = {str(args.emulator_network).lower()};\n"
                         "public static final String[][] DEX_HASHES = {" + entries + "};\n}\n")
    run("javac", "-source", "8", "-target", "8", "-Xlint:-options", "-bootclasspath", android,
        "-classpath", f"{android}:{args.api}", "-d", build / "classes", generated,
        *sorted((ROOT / "module/src").rglob("*.java")))
    run(tools / "d8", "--min-api", "29", "--lib", android, "--classpath", args.api,
        "--output", build / "dex", *sorted((build / "classes").rglob("*.class")))
    run(tools / "aapt", "package", "-f", "-M", ROOT / "module/AndroidManifest.xml",
        "-S", ROOT / "module/res", "-A", ROOT / "module/assets", "-I", android,
        "-F", build / "unsigned.apk")
    with zipfile.ZipFile(build / "unsigned.apk", "a", compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(build / "dex/classes.dex", "classes.dex")
    run(tools / "zipalign", "-f", "4", build / "unsigned.apk", build / "aligned.apk")
    key = ROOT / ".cache/local.keystore"
    key.parent.mkdir(exist_ok=True)
    if not key.exists():
        run("keytool", "-genkeypair", "-keystore", key, "-alias", "local", "-storepass", "android",
            "-keypass", "android", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
            "-dname", "CN=LauncherX Local Experiment")
    output = build / "launcherx-local.apk"
    run(tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", "local", "--ks-pass", "pass:android",
        "--key-pass", "pass:android", "--out", output, build / "aligned.apk")
    run(tools / "apksigner", "verify", output)
    (build / "receipt.json").write_text(json.dumps({"mode": args.mode,
        "emulator_network": args.emulator_network, "apk": str(output),
        "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "launcher_dex": hashes}, indent=2) + "\n")
    print(output)


if __name__ == "__main__":
    main()
