#!/bin/bash
# Build/install/launch Debug on a verified AVD; existing app data is preserved.
# LOLITA_AVD, LOLITA_EMULATOR_PORT, LOLITA_EMULATOR_SERIAL (or ANDROID_SERIAL)
# select the target. A mismatched AVD, signer or downgrade stops before install.
# Usage: bash scripts/debug_emulator.sh [test-command [args...]]
# With no command, a terminal waits for Enter; noninteractive runs clean up on exit.
set -euo pipefail

EMULATOR_PID=""
WORK_DIR=""
cleanup() {
    local result=$?
    trap - EXIT INT TERM
    if [[ -n "$EMULATOR_PID" ]]; then
        echo "关闭本次启动的模拟器……"
        # Only address the console after checking both our child and its AVD.
        # Bound adb calls: an offline device must not hang EXIT cleanup.
        if kill -0 "$EMULATOR_PID" 2>/dev/null; then
            python3 - "$ADB" "$DEVICE" "$AVD_NAME" <<'PY_STOP' || true
import subprocess, sys
adb, device, expected = sys.argv[1:]
try:
    result = subprocess.run([adb, '-s', device, 'emu', 'avd', 'name'],
                            capture_output=True, text=True, timeout=3)
    lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if result.returncode == 0 and lines and lines[0] == expected:
        subprocess.run([adb, '-s', device, 'emu', 'kill'],
                       capture_output=True, timeout=5)
except (OSError, subprocess.TimeoutExpired):
    pass
PY_STOP
        fi
        # The emulator itself allows 20 seconds for orderly teardown/snapshot save.
        # Give it 30 seconds before escalating; TERM is only a fallback.
        for ((stop_attempt=0; stop_attempt<60; stop_attempt++)); do
            kill -0 "$EMULATOR_PID" 2>/dev/null || break
            sleep 0.5
        done
        if kill -0 "$EMULATOR_PID" 2>/dev/null; then
            echo "模拟器未响应正常退出，尝试终止本次进程……" >&2
            kill -TERM "$EMULATOR_PID" 2>/dev/null || true
            for ((stop_attempt=0; stop_attempt<60; stop_attempt++)); do
                kill -0 "$EMULATOR_PID" 2>/dev/null || break
                sleep 0.5
            done
        fi
        if kill -0 "$EMULATOR_PID" 2>/dev/null; then
            echo "模拟器退出超时，强制结束本次进程；日志：$LOG_FILE" >&2
            kill -KILL "$EMULATOR_PID" 2>/dev/null || true
        fi
        wait "$EMULATOR_PID" 2>/dev/null || true
    fi
    [[ -z "$WORK_DIR" ]] || rm -rf "$WORK_DIR"
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
AVD_NAME="${LOLITA_AVD:-Lolita_API_35}"
ADB="${ADB:-$SDK_DIR/platform-tools/adb}"
EMULATOR="${LOLITA_EMULATOR_BIN:-$SDK_DIR/emulator/emulator}"
PKG="com.lolita.app"
BOOT_ATTEMPTS="${LOLITA_BOOT_ATTEMPTS:-150}"
BOOT_POLL_SECONDS="${LOLITA_BOOT_POLL_SECONDS:-2}"

abort() { echo "ERROR: $*" >&2; exit 2; }
[[ -x "$ADB" ]] || abort "找不到 adb：$ADB"
command -v python3 >/dev/null || abort "需要 Python 3 核对端口与 Android 构建工具"
[[ "$BOOT_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] || abort "LOLITA_BOOT_ATTEMPTS 必须为正整数"
[[ "$BOOT_POLL_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]] || abort "LOLITA_BOOT_POLL_SECONDS 必须为非负秒数"
DEVICE="${LOLITA_EMULATOR_SERIAL:-${ANDROID_SERIAL:-}}"
PORT="${LOLITA_EMULATOR_PORT:-}"
if [[ -n "$DEVICE" ]]; then
    [[ "$DEVICE" =~ ^emulator-([0-9]+)$ ]] || abort "调试脚本仅支持模拟器序列号：$DEVICE"
    SERIAL_PORT="${BASH_REMATCH[1]}"
    [[ -z "$PORT" || "$PORT" == "$SERIAL_PORT" ]] || abort "模拟器端口与序列号不一致：$PORT / $DEVICE"
    PORT="$SERIAL_PORT"
else
    PORT="${PORT:-5554}"
    DEVICE="emulator-$PORT"
fi
[[ "$PORT" =~ ^[0-9]{4}$ ]] || abort "模拟器端口必须为 5554 至 5682 的偶数"
[[ "$PORT" -ge 5554 && "$PORT" -le 5682 && $((PORT % 2)) -eq 0 ]] || abort "模拟器端口必须为 5554 至 5682 的偶数"
LOG_FILE="${LOLITA_EMULATOR_LOG:-/tmp/lolita-emulator-$PORT.log}"
d() { "$ADB" -s "$DEVICE" "$@"; }
avd_name() {
    local output
    output="$(d emu avd name 2>/dev/null | tr -d '\r')" || return 1
    printf '%s\n' "$output" | awk 'NF && !printed && $0 != "OK" && $0 !~ /^KO:/ && $0 !~ /^ERROR:/ {print; printed=1}'
}
verify_target() {
    local actual
    actual="$(avd_name)" || abort "无法核对 $DEVICE 的 AVD 名称；保留现有模拟器，请检查目标"
    [[ -n "$actual" && "$actual" == "$AVD_NAME" ]] || abort "目标 AVD 不匹配：$DEVICE 为 ${actual:-未知}，期望 ${AVD_NAME}；请选择专用调试端口"
}

DEVICE_LIST="$("$ADB" devices -l)" || abort "无法读取 adb 设备列表"
EXISTING_STATE="$(printf '%s\n' "$DEVICE_LIST" | awk -v serial="$DEVICE" '$1 == serial {print $2}')"
if [[ -n "$EXISTING_STATE" ]]; then
    [[ "$EXISTING_STATE" == device ]] || abort "$DEVICE 未就绪（${EXISTING_STATE}），保留现有进程，请检查目标"
    verify_target
else
    [[ -x "$EMULATOR" ]] || abort "找不到模拟器程序：$EMULATOR"
    AVD_LIST="$("$EMULATOR" -list-avds)" || abort "无法读取本机 AVD 列表"
    grep -Fxq "$AVD_NAME" <<< "$AVD_LIST" || abort "本机不存在 AVD：$AVD_NAME"
    if python3 - "$PORT" <<'PY'
import socket,sys
with socket.socket() as s:
    s.settimeout(0.5)
    raise SystemExit(0 if s.connect_ex(('127.0.0.1',int(sys.argv[1]))) == 0 else 1)
PY
    then abort "端口 $PORT 已被占用，但 adb 未报告目标；请换专用端口"; fi
    echo "启动 ${AVD_NAME}（${DEVICE}），日志：$LOG_FILE"
    GPU=auto
    [[ "$(uname -s)" != Darwin ]] || GPU=host
    "$EMULATOR" -avd "$AVD_NAME" -port "$PORT" -gpu "$GPU" -no-boot-anim -no-snapshot-load \
        > "$LOG_FILE" 2>&1 < /dev/null &
    EMULATOR_PID=$!
fi

echo "等待 $DEVICE 系统就绪……"
READY=false
for ((attempt=0; attempt<BOOT_ATTEMPTS; attempt++)); do
    if [[ "$(d shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
        READY=true
        break
    fi
    sleep "$BOOT_POLL_SECONDS"
done
[[ "$READY" == true ]] || abort "模拟器启动超时；日志：$LOG_FILE"
verify_target

# Require APK inspection tools before invoking the build. A failed or mismatched
# signature check has no uninstall/clear fallback.
build_tool() {
    local name="$1"
    python3 - "$SDK_DIR" "$name" <<'PY'
import pathlib,re,sys
root=pathlib.Path(sys.argv[1])/'build-tools'
paths=[p/sys.argv[2] for p in root.glob('*') if (p/sys.argv[2]).is_file()]
paths.sort(key=lambda p: tuple(int(v) for v in re.findall(r'\d+',p.parent.name)))
if not paths: raise SystemExit(1)
print(paths[-1])
PY
}
APKSIGNER="${APKSIGNER:-$(build_tool apksigner)}" || abort "未找到 apksigner"
AAPT="${AAPT:-$(build_tool aapt)}" || abort "未找到 aapt"
[[ -x "$APKSIGNER" && -x "$AAPT" ]] || abort "缺少可执行的 apksigner 或 aapt"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lolita-debug-signature.XXXXXX")"
APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

# Preserve pre-build package/signature evidence and verify it again immediately
# before install, in case the target changes while Gradle is running.
installed_apk() {
    local paths
    paths="$(d shell pm path "$PKG" | tr -d '\r')" || abort "无法读取 $DEVICE 的已安装应用信息"
    if [[ -n "$paths" && "$paths" != package:* ]]; then abort "已安装应用路径响应异常"; fi
    printf '%s\n' "$paths" | awk '/^package:/ && !printed {sub(/^package:/, ""); print; printed=1}'
}
certs() {
    local output digests
    output="$("$APKSIGNER" verify --print-certs "$1" 2>&1)" || abort "APK 签名校验失败：$1"
    digests="$(printf '%s\n' "$output" | sed -nE 's/^Signer #[0-9]+ certificate SHA-256 digest: ([[:xdigit:]]+)$/\1/p' | tr '[:upper:]' '[:lower:]' | sort -u)"
    [[ -n "$digests" ]] || abort "无法读取 APK 签名摘要：$1"
    printf '%s\n' "$digests"
}
INSTALLED_PATH="$(installed_apk)"
INSTALLED_CERTS=""
if [[ -n "$INSTALLED_PATH" ]]; then
    d pull "$INSTALLED_PATH" "$WORK_DIR/installed-before.apk" > /dev/null || abort "无法读取现有 APK 签名；保留应用数据"
    INSTALLED_CERTS="$(certs "$WORK_DIR/installed-before.apk")"
fi
cd "$PROJECT_DIR"
./gradlew assembleDebug
[[ -s "$APK" ]] || abort "Debug 构建未生成 APK"
APK_METADATA="$("$AAPT" dump badging "$APK")" || abort "无法读取构建产物"
BUILT_PACKAGE="$(printf '%s\n' "$APK_METADATA" | sed -nE "s/^package: name='([^']+)'.*/\1/p")"
BUILT_VERSION="$(printf '%s\n' "$APK_METADATA" | sed -nE "s/^package:.*versionCode='([0-9]+)'.*/\1/p")"
[[ "$BUILT_PACKAGE" == "$PKG" && "$BUILT_VERSION" =~ ^[0-9]{1,10}$ ]] || abort "APK 包名或版本信息不匹配，停止安装"
NEW_CERTS="$(certs "$APK")"
if [[ -n "$INSTALLED_CERTS" && "$INSTALLED_CERTS" != "$NEW_CERTS" ]]; then
    abort "现有应用与 Debug APK 签名不同；请选择专用调试 AVD。现有应用和数据已保留"
fi
verify_target
CURRENT_PATH="$(installed_apk)"
if [[ -n "$CURRENT_PATH" ]]; then
    d pull "$CURRENT_PATH" "$WORK_DIR/installed-current.apk" > /dev/null || abort "无法复核现有 APK 签名"
    [[ "$(certs "$WORK_DIR/installed-current.apk")" == "$NEW_CERTS" ]] || abort "安装前签名复核不匹配，已停止"
    CURRENT_METADATA="$("$AAPT" dump badging "$WORK_DIR/installed-current.apk")" || abort "无法读取已安装应用版本"
    CURRENT_VERSION="$(printf '%s\n' "$CURRENT_METADATA" | sed -nE "s/^package:.*versionCode='([0-9]+)'.*/\1/p")"
    [[ "$CURRENT_VERSION" =~ ^[0-9]{1,10}$ ]] || abort "无法核对已安装应用版本，停止安装"
    [[ "$BUILT_VERSION" -ge "$CURRENT_VERSION" ]] || abort "Debug APK 版本低于现有应用，保留现有应用与数据"
fi
# adb install -r refuses downgrades as well as signature mismatches. Never use
# -d, uninstall, pm clear, or a replacement emulator to circumvent a refusal.
echo "AVD 与签名已核对，安装到 ${DEVICE}……"
d install -r "$APK"
LAUNCH_OUTPUT="$(d shell am start -W -n "$PKG/.ui.MainActivity" 2>&1)" || abort "启动应用失败"
printf '%s\n' "$LAUNCH_OUTPUT"
if grep -Eq 'Error:|Error type|Exception' <<< "$LAUNCH_OUTPUT"; then abort "启动应用失败"; fi

if (( $# > 0 )); then
    ANDROID_SERIAL="$DEVICE" "$@"
elif [[ -t 0 ]]; then
    read -r -p "调试结束后按回车退出（关闭本次启动的模拟器）……" || true
fi
