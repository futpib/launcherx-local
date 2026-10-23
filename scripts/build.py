#!/usr/bin/env python3
"""Build the runtime module; never package the proprietary launcher or Xposed API."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
API_SHA256 = "f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25"
ANDROID = "http://schemas.android.com/apk/res/android"


def manifest_version(path):
    manifest = ET.parse(path).getroot()
    return manifest.get(f"{{{ANDROID}}}versionName"), int(manifest.get(f"{{{ANDROID}}}versionCode"))


def prepare_manifest(source, destination, name=None, code=None):
    manifest = ET.parse(source)
    previous_name, previous_code = manifest_version(source)
    name = previous_name if name is None else name
    code = previous_code if code is None else code
    if not name or any(character.isspace() for character in name):
        raise ValueError("Version name must be nonempty and contain no whitespace")
    if not 1 <= code <= 2100000000:
        raise ValueError("Android version code must be between 1 and 2100000000")
    ET.register_namespace("android", ANDROID)
    manifest.getroot().set(f"{{{ANDROID}}}versionName", name)
    manifest.getroot().set(f"{{{ANDROID}}}versionCode", str(code))
    manifest.write(destination, encoding="utf-8", xml_declaration=True)
    return name, code


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--launcher", type=Path, help="Optionally verify a privately obtained launcher APK")
    parser.add_argument("--api", type=Path, default=ROOT / "artifacts/xposed-api-82.jar")
    parser.add_argument("--mode", choices=["apps", "full"], default="apps")
    parser.add_argument("--emulator-network", action="store_true",
                        help="Use Android HttpEngine in the ARM-on-x86 emulator fixture")
    parser.add_argument("--keystore", type=Path, help="Release key; passwords come from LAUNCHERX_STORE_PASS and LAUNCHERX_KEY_PASS")
    parser.add_argument("--key-alias", default="local")
    parser.add_argument("--version-name", help="Override only the output APK's module version name")
    parser.add_argument("--version-code", type=int, help="Override only the output APK's module version code")
    args = parser.parse_args()
    if args.keystore:
        if not args.keystore.is_file():
            raise SystemExit("Release keystore does not exist")
        if not all(os.environ.get(name) for name in ["LAUNCHERX_STORE_PASS", "LAUNCHERX_KEY_PASS"]):
            raise SystemExit("Set LAUNCHERX_STORE_PASS and LAUNCHERX_KEY_PASS for release signing")
    compatibility = json.loads((ROOT / "compatibility.json").read_text())
    inputs = [(args.api, API_SHA256)]
    if args.launcher:
        inputs.append((args.launcher, compatibility["apk_sha256"]))
    for path, expected in inputs:
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Unexpected input SHA-256: {path}")
    sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Android/Sdk")))
    tools = sdk / "build-tools/37.0.0"
    android = sdk / "platforms/android-34/android.jar"
    build = ROOT / "build" / (args.mode + ("-emulator" if args.emulator_network else ""))
    build.mkdir(parents=True, exist_ok=True)
    manifest = build / "AndroidManifest.xml"
    try:
        version_name, version_code = prepare_manifest(ROOT / "module/AndroidManifest.xml", manifest,
                                                      args.version_name, args.version_code)
    except ValueError as error:
        raise SystemExit(str(error)) from error
    for name in ["classes", "dex"]:
        shutil.rmtree(build / name, ignore_errors=True)
        (build / name).mkdir()
    hashes = compatibility["dex_sha256"]
    if args.launcher:
        with zipfile.ZipFile(args.launcher) as apk:
            actual = [[name, hashlib.sha256(apk.read(name)).hexdigest()]
                      for name in sorted(apk.namelist()) if name.endswith(".dex") and "/" not in name]
        if actual != hashes:
            raise SystemExit("Launcher DEX hashes differ from compatibility.json")
    generated = build / "BuildConfig.java"
    entries = ",\n".join("{" + json.dumps(name) + "," + json.dumps(digest) + "}" for name, digest in hashes)
    generated.write_text("package local.launcherx.patch;\npublic final class BuildConfig {\n"
                         f"public static final long VERSION_CODE = {compatibility['version_code']}L;\n"
                         f'public static final String MODE = "{args.mode}";\n'
                         f"public static final boolean APPS_ONLY = {str(args.mode == 'apps').lower()};\n"
                         f"public static final boolean EMULATOR_NETWORK = {str(args.emulator_network).lower()};\n"
                         "public static final String[][] DEX_HASHES = {" + entries + "};\n}\n")
    run("javac", "-source", "8", "-target", "8", "-Xlint:-options", "-bootclasspath",
        f"{android}:{tools / 'core-lambda-stubs.jar'}",
        "-classpath", f"{android}:{args.api}", "-d", build / "classes", generated,
        *sorted((ROOT / "module/src").rglob("*.java")))
    run(tools / "d8", "--min-api", "29", "--lib", android, "--classpath", args.api,
        "--output", build / "dex", *sorted((build / "classes").rglob("*.class")))
    run(tools / "aapt", "package", "-f", "-M", manifest,
        "-S", ROOT / "module/res", "-A", ROOT / "module/assets", "-I", android,
        "-F", build / "unsigned.apk")
    with zipfile.ZipFile(build / "unsigned.apk", "a", compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(build / "dex/classes.dex", "classes.dex")
    run(tools / "zipalign", "-f", "4", build / "unsigned.apk", build / "aligned.apk")
    key = args.keystore or ROOT / ".cache/local.keystore"
    key.parent.mkdir(exist_ok=True)
    if not key.exists():
        run("keytool", "-genkeypair", "-keystore", key, "-alias", "local", "-storepass", "android",
            "-keypass", "android", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
            "-dname", "CN=LauncherX Local Experiment")
    output = build / "launcherx-local.apk"
    run(tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", args.key_alias,
        "--ks-pass", "env:LAUNCHERX_STORE_PASS" if args.keystore else "pass:android",
        "--key-pass", "env:LAUNCHERX_KEY_PASS" if args.keystore else "pass:android",
        "--out", output, build / "aligned.apk")
    run(tools / "apksigner", "verify", output)
    (build / "receipt.json").write_text(json.dumps({"mode": args.mode,
        "version_name": version_name, "version_code": version_code,
        "emulator_network": args.emulator_network, "apk": str(output),
        "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "launcher_dex": hashes}, indent=2) + "\n")
    (build / "SHA256SUMS").write_text(hashlib.sha256(output.read_bytes()).hexdigest() + "  " + output.name + "\n")
    print(output)


if __name__ == "__main__":
    main()
