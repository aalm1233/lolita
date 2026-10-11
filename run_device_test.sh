#!/bin/bash
# USB navigation smoke tests for the current 4-bottom-tab / 4-home-tab UI.
# Profile: portrait 1116x2480, 480 dpi. Select a USB serial with ANDROID_SERIAL.
# This suite checks navigation and picker cancellation; real restores/imports are SKIP.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ADB="${ADB:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
PKG="com.lolita.app"
SCREEN_WIDTH=1116
SCREEN_HEIGHT=2480
SCREEN_DENSITY=480
UI_WAIT="${LOLITA_UI_WAIT_SECONDS:-1}"
RESULTS_FILE="${LOLITA_RESULTS_FILE:-$PROJECT_DIR/test_results.txt}"
SCREENSHOT_DIR="${LOLITA_SCREENSHOT_DIR:-$PROJECT_DIR/test_screenshots/usb-$(date '+%Y%m%d-%H%M%S')-$$}"
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
DEVICE=""

abort() { echo "ERROR: $*" >&2; exit 2; }
[[ -x "$ADB" ]] || abort "找不到 adb：$ADB"
command -v python3 >/dev/null || abort "需要 Python 3 解析 UI 树和校验截图"
[[ "$UI_WAIT" =~ ^[0-9]+([.][0-9]+)?$ ]] || abort "LOLITA_UI_WAIT_SECONDS 必须为非负秒数"
DEVICE_LIST="$("$ADB" devices -l)" || abort "无法读取 adb 设备列表"
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    DEVICE="$ANDROID_SERIAL"
    [[ "$DEVICE" != emulator-* ]] || abort "USB 套件拒绝模拟器：$DEVICE"
    DEVICE_ROW="$(printf '%s\n' "$DEVICE_LIST" | awk -v serial="$DEVICE" '$1 == serial {print}')"
    [[ "$(printf '%s\n' "$DEVICE_ROW" | awk '{print $2}')" == device ]] || abort "指定 USB 设备未就绪：$DEVICE"
else
    USB_SERIALS=""
    CANDIDATES="$(printf '%s\n' "$DEVICE_LIST" | awk '$2 == "device" && $1 !~ /^emulator-/ {print $1}')"
    while IFS= read -r candidate; do
        [[ -n "$candidate" ]] || continue
        candidate_transport="$("$ADB" -s "$candidate" get-devpath 2>/dev/null | tr -d '\r')" || continue
        if [[ "$candidate_transport" == usb:* ]]; then USB_SERIALS="${USB_SERIALS}${candidate}"$'\n'; fi
    done <<< "$CANDIDATES"
    USB_COUNT="$(printf '%s\n' "$USB_SERIALS" | awk 'NF {count++} END {print count+0}')"
    [[ "$USB_COUNT" == 1 ]] || abort "需要唯一的已授权本机 USB 设备（当前 $USB_COUNT 台）；请设置 ANDROID_SERIAL"
    DEVICE="$(printf '%s\n' "$USB_SERIALS" | awk 'NF && !printed {print; printed=1}')"
fi
# A network or emulator transport must not be accepted as a physical USB device.
d() { "$ADB" -s "$DEVICE" "$@"; }
TRANSPORT="$(d get-devpath | tr -d '\r')" || abort "无法核对 USB 传输：$DEVICE"
[[ "$TRANSPORT" == usb:* ]] || abort "目标不是本机 USB 传输：${DEVICE}（${TRANSPORT}）"
SIZE_INFO="$(d shell wm size | tr -d '\r')" || abort "无法读取屏幕尺寸"
DENSITY_INFO="$(d shell wm density | tr -d '\r')" || abort "无法读取屏幕密度"
EFFECTIVE_SIZE="$(printf '%s\n' "$SIZE_INFO" | awk -F': ' '/Physical size:|Override size:/ {size=$2} END {print size}')"
EFFECTIVE_DENSITY="$(printf '%s\n' "$DENSITY_INFO" | awk -F': ' '/Physical density:|Override density:/ {density=$2} END {print density}')"
[[ "$EFFECTIVE_SIZE" == "${SCREEN_WIDTH}x${SCREEN_HEIGHT}" && "$EFFECTIVE_DENSITY" == "$SCREEN_DENSITY" ]] ||
    abort "未覆盖此屏幕配置：$EFFECTIVE_SIZE / $EFFECTIVE_DENSITY dpi；要求 ${SCREEN_WIDTH}x${SCREEN_HEIGHT} / $SCREEN_DENSITY dpi"
PACKAGE_INFO="$(d shell dumpsys package "$PKG")" || abort "无法核对应用"
grep -q DEBUGGABLE <<< "$PACKAGE_INFO" || abort "请先安装 Debug APK；当前应用不存在或不是可调试版本"

mkdir -p "$(dirname "$RESULTS_FILE")" "$SCREENSHOT_DIR"
: > "$RESULTS_FILE"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/lolita-usb.XXXXXX")"
UI_FILE="$WORK_DIR/ui.xml"
DEVICE_UI="/sdcard/lolita-usb-$$.xml"
DEVICE_SCREEN="/sdcard/lolita-usb-$$.png"
cleanup() {
    d shell rm -f "$DEVICE_UI" "$DEVICE_SCREEN" >/dev/null 2>&1 || true
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT
log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$RESULTS_FILE"; }
pass() { PASS_COUNT=$((PASS_COUNT + 1)); log "PASS: $*"; }
fail() { FAIL_COUNT=$((FAIL_COUNT + 1)); log "FAIL: $*"; }
skip() { SKIP_COUNT=$((SKIP_COUNT + 1)); log "SKIP: $*"; }
wait_ui() { sleep "$UI_WAIT"; }

# Android Compose selection is on a text node or its ancestor. Use exact labels
# and bounds so e.g. the home "心愿" tab is never mistaken for a bottom tab.
cat > "$WORK_DIR/ui.py" <<'PY'
import hashlib, re, struct, sys, zlib, xml.etree.ElementTree as ET
mode, path = sys.argv[1:3]
if mode == 'png':
    data = open(path, 'rb').read()
    if data[:8] != b'\x89PNG\r\n\x1a\n' or len(data) < 24:
        raise SystemExit('截图不是完整 PNG')
    if struct.unpack('>II', data[16:24]) != (1116, 2480):
        raise SystemExit('截图方向/尺寸与 USB 测试配置不符')
    offset, compressed, complete = 8, bytearray(), False
    while offset + 12 <= len(data):
        length = struct.unpack('>I', data[offset:offset+4])[0]
        end = offset + 12 + length
        if end > len(data): raise SystemExit('截图 PNG 数据不完整')
        tag = data[offset+4:offset+8]
        payload = data[offset+8:offset+8+length]
        crc = struct.unpack('>I', data[offset+8+length:end])[0]
        if zlib.crc32(tag+payload) & 0xffffffff != crc: raise SystemExit('截图 PNG 校验失败')
        if tag == b'IDAT': compressed.extend(payload)
        offset = end
        if tag == b'IEND': complete = True; break
    if not complete or not compressed or not zlib.decompress(compressed):
        raise SystemExit('截图 PNG 缺少有效像素数据')
    raise SystemExit(0)
root = ET.parse(path).getroot()
parents = {child: node for node in root.iter() for child in node}
def bounds(node):
    values = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
    return values if len(values) == 4 else [0, 0, 0, 0]
def selected(node):
    while node is not None:
        if node.get('selected') == 'true' or node.get('checked') == 'true':
            return True
        node = parents.get(node)
    return False
if mode == 'signature':
    # Exclude system clock/navigation; report visible app text and positions.
    rows = sorted((node.get('text'), bounds(node)) for node in root.iter('node')
                  if node.get('text') and bounds(node)[1] > 270 and bounds(node)[3] < 2250)
    print(hashlib.sha256(repr(rows).encode()).hexdigest())
    raise SystemExit(0)
label, zone = sys.argv[3:5]
matches = []
for node in root.iter('node'):
    if node.get('text') != label:
        continue
    x1, y1, x2, y2 = bounds(node)
    if x2 <= x1 or y2 <= y1:
        continue
    if zone == 'bottom' and y1 < 2100:
        continue
    if zone == 'top' and not (90 <= y1 < 300):
        continue
    if zone == 'body' and not (270 <= y1 < 2250):
        continue
    matches.append(node)
if mode == 'contains':
    raise SystemExit(0 if matches else 1)
if mode == 'selected':
    raise SystemExit(0 if any(selected(node) for node in matches) else 1)
if mode == 'point' and len(matches) == 1:
    target = matches[0]
    if zone == 'bottom':
        # A label may extend into the system gesture area. Tap its nearest
        # clickable navigation ancestor, restricted to the safe bar area.
        ancestor = target
        while ancestor is not None:
            ax1, ay1, ax2, ay2 = bounds(ancestor)
            if ancestor.get('clickable') == 'true' and ay1 >= 2100 and ax2 > ax1 and ay2 > ay1:
                target = ancestor
                break
            ancestor = parents.get(ancestor)
    x1, y1, x2, y2 = bounds(target)
    if zone == 'bottom':
        y1, y2 = max(y1, 2100), min(y2, 2372)
        if y2 <= y1:
            raise SystemExit('底部导航控件没有位于手势区域上方的安全点击范围')
    tap_y = (y1+y2-1)//2 if zone == 'bottom' else (y1+y2)//2
    print((x1+x2)//2, tap_y)
    raise SystemExit(0)
raise SystemExit(1)
PY

ui_dump() {
    rm -f "$UI_FILE"
    d shell rm -f "$DEVICE_UI" >/dev/null 2>&1 || return 1
    d shell uiautomator dump --compressed "$DEVICE_UI" > /dev/null 2>&1 || return 1
    d pull "$DEVICE_UI" "$UI_FILE" > /dev/null 2>&1 || return 1
    python3 -c 'import sys,xml.etree.ElementTree as E; E.parse(sys.argv[1])' "$UI_FILE" || return 1
}
foreground() {
    d shell dumpsys activity activities | tr -d '\r' | awk '!printed && /mResumedActivity:|topResumedActivity:|mFocusedActivity:/ {print; printed=1}'
}
app_foreground() {
    local active
    active="$(foreground)" || return 1
    grep -Fq "$PKG/" <<< "$active"
}
contains() { python3 "$WORK_DIR/ui.py" contains "$UI_FILE" "$1" "${2:-any}"; }
is_selected() { python3 "$WORK_DIR/ui.py" selected "$UI_FILE" "$1" "$2"; }
screenshot() {
    local name="$1"
    d shell screencap -p "$DEVICE_SCREEN" || return 1
    d pull "$DEVICE_SCREEN" "$SCREENSHOT_DIR/$name.png" > /dev/null 2>&1 || return 1
    python3 "$WORK_DIR/ui.py" png "$SCREENSHOT_DIR/$name.png" || return 1
}
capture() {
    local name="$1"
    screenshot "$name" || return 1
    ui_dump || return 1
    cp "$UI_FILE" "$SCREENSHOT_DIR/$name.xml" || return 1
    foreground > "$SCREENSHOT_DIR/$name.activity.txt" || return 1
}
tap_text() {
    local point
    ui_dump || return 1
    point="$(python3 "$WORK_DIR/ui.py" point "$UI_FILE" "$1" "${2:-any}")" || return 1
    read -r point_x point_y <<< "$point"
    d shell input tap "$point_x" "$point_y" || return 1
    wait_ui
}
bottom() {
    tap_text "$1" bottom || return 1
    capture "$2" || return 1
    app_foreground && is_selected "$1" bottom
}
top() {
    tap_text "$1" top || return 1
    capture "$2" || return 1
    app_foreground && is_selected "$1" top
}
go_home() { bottom 首页 "$1"; }
back() { d shell input keyevent KEYCODE_BACK && wait_ui; }
open_editor() {
    go_home "$1-home" || return 1
    top "$2" "$1-tab" || return 1
    d shell input tap 984 2168 || return 1
    wait_ui
    capture "$1-open" || return 1
    app_foreground && contains "$3"
}
scroll_to_text() {
    local label="$1" attempt
    for ((attempt=0; attempt<5; attempt++)); do
        ui_dump || return 1
        if contains "$label" body; then return 0; fi
        d shell input swipe 540 1800 540 1000 400 || return 1
        wait_ui
    done
    return 1
}
settings_menu() {
    go_home "$1-home" || return 1
    bottom 个人 "$1-settings" || return 1
    # Return to the top of settings before searching for a menu entry.
    d shell input swipe 540 650 540 1800 400 || return 1
    wait_ui
    scroll_to_text "$2" || return 1
    tap_text "$2" body || return 1
    capture "$1-page" || return 1
    app_foreground && contains "$2"
}
picker_roundtrip() {
    local prefix="$1" button="$2" title="$3" activity
    scroll_to_text "$button" || return 1
    tap_text "$button" body || return 1
    capture "$prefix-picker" || return 1
    activity="$(foreground)" || return 1
    [[ "$activity" == *com.android.documentsui/* || "$activity" == *com.google.android.documentsui/* ]] || return 1
    back || return 1
    capture "$prefix-return" || return 1
    app_foreground && contains "$title"
}
check_scroll() {
    local group="$1" before after
    before="$(python3 "$WORK_DIR/ui.py" signature "$UI_FILE")" || return 1
    d shell input swipe 540 1800 540 650 400 || return 1
    wait_ui
    capture "$group-scrolled" || return 1
    app_foreground || return 1
    after="$(python3 "$WORK_DIR/ui.py" signature "$UI_FILE")" || return 1
    if [[ "$before" != "$after" ]]; then
        pass "$group 滚动：可见应用文本/位置改变（截图与 UI 树已保存）"
    else
        skip "$group 滚动：未获得文本位移证据，页面可能未溢出；不计滚动通过"
    fi
}
run_group() {
    local group="$1" description="$2" function_name="$3"
    log "=== $group: $description ==="
    if "$function_name"; then pass "$group $description"; else fail "$group ${description}：未获得所需页面、UI 或活动证据"; fi
}

log "=== Lolita USB 导航测试；目标 ${DEVICE}，$EFFECTIVE_SIZE / $EFFECTIVE_DENSITY dpi ==="
log "每次操作都使用 adb -s；截图、UI 树和前台活动保存在 $SCREENSHOT_DIR"
START_DEVICE_TIME="$(d shell date '+%m-%d %H:%M:%S.000' | tr -d '\r')" || abort "无法读取设备日志起始时间"
[[ "$START_DEVICE_TIME" =~ ^[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}\.000$ ]] || abort "设备日志时间格式不受支持"

g1() {
    d shell am force-stop "$PKG" || return 1
    d shell am start -W -n "$PKG/.ui.MainActivity" > "$SCREENSHOT_DIR/g1-launch.txt" || return 1
    wait_ui
    capture g1-launch || return 1
    app_foreground || return 1
    local label
    for label in 首页 穿搭 统计 个人; do contains "$label" bottom || return 1; done
    for label in 位置 服饰 套装 心愿; do contains "$label" top || return 1; done
}
g2() {
    bottom 穿搭 g2-outfit || return 1
    contains 穿搭日记 || return 1
    bottom 统计 g2-stats || return 1
    contains 已拥有 || return 1
    bottom 个人 g2-settings || return 1
    contains 属性管理 || return 1
    go_home g2-home
}
g3() {
    go_home g3-home || return 1
    top 位置 g3-location || return 1
    top 服饰 g3-items || return 1
    top 套装 g3-coordinate || return 1
    top 心愿 g3-wishlist
}
g4() { open_editor g4 服饰 添加服饰 && back && capture g4-return && app_foreground && is_selected 服饰 top; }
g5() { open_editor g5 套装 新建套装 && back && capture g5-return && app_foreground && is_selected 套装 top; }
g6() {
    open_editor g6 心愿 添加服饰 || return 1
    # Opening the form is checked here; actual wishlist persistence is not performed.
    back && capture g6-return && app_foreground && is_selected 心愿 top
}
g7() { bottom 个人 g7-settings && contains 属性管理 && check_scroll G7; }
g8() { bottom 统计 g8-stats && contains 已拥有 && check_scroll G8; }
g9() { bottom 穿搭 g9-outfit && contains 穿搭日记; }
g10() { go_home g10-home && top 心愿 g10-wishlist; }
g11() { open_editor g11 服饰 添加服饰 && back && capture g11-return && app_foreground && is_selected 服饰 top; }
g12() {
    d logcat -d -T "$START_DEVICE_TIME" -v threadtime > "$SCREENSHOT_DIR/g12-logcat.txt" || return 1
    python3 - "$SCREENSHOT_DIR/g12-logcat.txt" "$PKG" <<'PY'
import re,sys
text=open(sys.argv[1],errors='replace').read()
pkg=re.escape(sys.argv[2])
crashes=re.findall(r'AndroidRuntime.*Process: '+pkg+r'(?:,|\s*$)',text,re.M)
anrs=re.findall(r'ANR in '+pkg+r'(?:\s|$)',text,re.M)
if crashes or anrs:
    print('本次日志中应用崩溃/ANR：',len(crashes)+len(anrs))
    raise SystemExit(1)
PY
}
g13() { settings_menu g13 数据备份与恢复 && picker_roundtrip g13 选择备份文件 数据备份与恢复; }
g14() { settings_menu g14 淘宝订单导入 && picker_roundtrip g14 '选择文件（可多选）' 淘宝订单导入; }

if g1; then
    pass "G1 启动、4 个底部导航与首页4个顶部页签"
    run_group G2 底部导航切换 g2
    run_group G3 首页顶部页签切换 g3
    run_group G4 新增服饰入口与回退 g4
    run_group G5 新建套装入口与回退 g5
    run_group G6 心愿页新增入口与回退 g6
    run_group G7 '个人页加载（滚动单独记录）' g7
    run_group G8 '统计页加载（滚动单独记录）' g8
    run_group G9 穿搭日记页加载 g9
    run_group G10 首页心愿页加载 g10
    run_group G11 编辑页返回导航 g11
    run_group G13 备份选择器打开与安全回退 g13
    run_group G14 淘宝订单选择器打开与安全回退 g14
else
    fail "G1 启动或当前导航结构未通过"
    for group in G2 G3 G4 G5 G6 G7 G8 G9 G10 G11 G13 G14; do skip "${group}：G1 未通过，未执行"; done
fi
run_group G12 本次运行的应用崩溃与ANR日志检查 g12
skip "G6 保存心愿服饰：未录入或保存测试记录"
skip "G13 实际备份恢复及逐字段/图片校验：本套件未执行，不能证明恢复无损"
skip "G14 Excel 解析、下一步与实际导入：未选择输入文件，本套件未执行"
log "=== 完成：$PASS_COUNT 条通过，$FAIL_COUNT 条失败，$SKIP_COUNT 条跳过 ==="
log "完整结果：$RESULTS_FILE"
if [[ "$FAIL_COUNT" -gt 0 ]]; then exit 1; fi
