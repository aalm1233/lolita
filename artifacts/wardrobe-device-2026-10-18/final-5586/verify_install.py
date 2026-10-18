from pathlib import Path
import subprocess, hashlib, json, os, shutil, sys, zipfile, re

root = Path(__file__).resolve().parents[3]
out = Path(__file__).resolve().parent
label = sys.argv[1]
expected_version = int(sys.argv[2]) if len(sys.argv) > 2 else 113
assert 113 <= expected_version <= 2147483647
assert label.replace("-", "").isalnum()
stage = Path("/tmp/lolita-wardrobe-device-20261018") / ("final-" + label)
stage.mkdir(exist_ok=True)
adb = "/Users/dengtong/Library/Android/sdk/platform-tools/adb"
serial = "emulator-5586"
avd = "Lolita_Wardrobe_Final_35"
buildtools = "/Users/dengtong/Library/Android/sdk/build-tools/35.0.0/"
env = os.environ.copy()
env["JAVA_HOME"] = "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
expected_signer = "b7832feba195d280dfa51957f8f6a38946d82a7d633ef3e2a11b23c04f01a8d2"

def run(args):
    return subprocess.check_output(args, text=True, env=env)

def digest(output):
    return next(line.split("digest: ", 1)[1].strip() for line in output.splitlines() if "certificate SHA-256 digest: " in line)

def version(output):
    return int(output.split("versionCode='", 1)[1].split("'", 1)[0] or "0")

assert run([adb, "-s", serial, "emu", "avd", "name"]).splitlines()[0].strip() == avd
assert run([adb, "-s", serial, "shell", "getprop", "sys.boot_completed"]).strip() == "1"
records, evidence = [], []
for source, package in [("app/build/outputs/apk/debug/app-debug.apk", "com.lolita.app"),
                        ("app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk", "com.lolita.app.test")]:
    apk = stage / Path(source).name
    shutil.copyfile(root / source, apk)
    certificate = run([buildtools + "apksigner", "verify", "--print-certs", str(apk)])
    assert digest(certificate) == expected_signer
    metadata = run([buildtools + "aapt", "dump", "badging", str(apk)])
    assert "name='" + package + "'" in metadata.splitlines()[0]
    new_version = version(metadata)
    if package == "com.lolita.app":
        assert new_version == expected_version
    probe = subprocess.run([adb, "-s", serial, "shell", "pm", "path", package],
                           text=True, capture_output=True, env=env)
    assert probe.returncode == 0 or probe.returncode == 1 and not probe.stdout.strip() and not probe.stderr.strip(), probe
    installed = probe.stdout.strip().splitlines()
    if installed:
        assert installed[0].startswith("package:")
        previous = stage / (package + "-installed.apk")
        subprocess.run([adb, "-s", serial, "pull", installed[0][8:], str(previous)], capture_output=True, check=True)
        assert digest(run([buildtools + "apksigner", "verify", "--print-certs", str(previous)])) == expected_signer
        assert new_version >= version(run([buildtools + "aapt", "dump", "badging", str(previous)]))
    records.append({"package": package, "path": str(apk), "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                    "versionCode": new_version, "signerSha256": expected_signer, "previousInstalled": bool(installed)})
    evidence.append(certificate)
for record in records:
    result = run([adb, "-s", serial, "install", "-r", record["path"]])
    assert "Success" in result
    evidence.append(result)
    print(record["package"], result.strip())
(out / (label + "-apks.json")).write_text(json.dumps({"serial": serial, "avd": avd, "apks": records}, indent=2))
(out / (label + "-install-evidence.txt")).write_text("\n".join(evidence))
