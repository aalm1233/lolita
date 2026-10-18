"""Read-only export of the latest completed native screenshot matrix, never partial probes."""
import hashlib, json, pathlib, struct, subprocess
base = pathlib.Path(__file__).parent / "final47" / "recognition-ui-native"
base.mkdir(parents=True, exist_ok=True)
adb = ["/Users/dengtong/Library/Android/sdk/platform-tools/adb", "-s", "emulator-5586"]
def read(path):
    return subprocess.check_output(adb + ["exec-out", "run-as", "com.lolita.app", "cat", path])
paths = subprocess.check_output(adb + ["shell", "run-as", "com.lolita.app", "find", "files/recognition-flow-screenshots", "-name", "manifest.json"], text=True).splitlines()
ordered = sorted(paths, key=lambda p: int(subprocess.check_output(adb + ["shell", "run-as", "com.lolita.app", "stat", "-c", "%Y", p], text=True)))
entries = []
for path in ordered[-2:]:
    matrix = json.loads(read(path))
    assert len(matrix) == 14
    for entry in matrix:
        device_path = str(pathlib.PurePosixPath(path).parent / entry["file"])
        data = read(device_path)
        assert hashlib.sha256(data).hexdigest() == entry["sha256"]
        assert data[:8] == b"\x89PNG\r\n\x1a\n" and struct.unpack(">II", data[16:24]) == (1080, 2400)
        (base / entry["file"]).write_bytes(data)
        entries.append({**entry, "devicePath": device_path})
assert len(entries) == len({(e["skin"], e["dark"], e["page"]) for e in entries}) == 28
assert len({e["sha256"] for e in entries}) == 28
manifest = {"serial": "emulator-5586", "avd": "Lolita_Wardrobe_Final_35", "sourceLog": "../ui3-instrumentation.log", "sourceApks": "../apks.json", "entries": entries}
(base / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
state = {}
for key, command in {"wm-density": ["shell", "wm", "density"], "font-scale": ["shell", "settings", "get", "system", "font_scale"], "night-mode": ["shell", "cmd", "uimode", "night"], "avd": ["emu", "avd", "name"]}.items():
    state[key] = subprocess.check_output(adb + command, text=True).strip()
(base / "restored-device-settings.json").write_text(json.dumps(state, indent=2) + "\n")
print(json.dumps({"screenshots": len(entries), "restoredDeviceSettings": state}))
